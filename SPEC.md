# faux-person-web: spec

A Spring Boot web app that generates random names, companies and people with
[faux-person](https://github.com/tauasa/faux-person), for a browser or for
cURL. It is the web counterpart of
[faux-person-fx](https://github.com/tauasa/faux-person-fx): the same three
kinds, the same controls, and the same columns, sorted the same way.

It runs on one EC2 instance behind nginx, at
`https://apps.tauasa.org/faux-person-web`.

## Constraints

| Rule | What it means here |
|---|---|
| Stateless | No session, no cookie, no login. Every request carries everything it needs in its query string |
| No authentication or authorization | Every route is public. The reasons are in [Routes](#routes) |
| Nothing written to disk | Results live only in memory. No temp files, no disk cache in nginx. A restart empties the cache |
| Idempotent | On one release of the app, the same inputs, seed included, give byte-for-byte the same output, on any day, before and after a restart |
| Cached | Once made, a result is served from memory without generating it again |
| Bounded | At most `faux.web.max-count` rows a request (default 10,000), 100 by default. The cache has a ceiling, and only a few cache misses are built at once |

**Why "on one release".** A release can change the output: a fix to an export
format, or a newer faux-person. So the promise holds per release, and every
ETag names the release (see [Caching](#caching)). Within a release nothing
depends on the date: see [the reference date](#the-reference-date).

## Data model

Generation is faux-person's. The app adds no fields of its own.

| Kind | Path | Columns, in order | Opens sorted by |
|---|---|---|---|
| Names | `/names` | First name, Last name | Last name |
| Companies | `/companies` | Name, City, State, Domain | Name |
| People | `/people` | First name, Last name, Gender, Region, Date of birth, Email, Phone, Street, City, State, Postal code, Country, Employer, Employer city, Employer state, Employer domain | Last name |

Each column has a key, its title in camelCase (`dateOfBirth`,
`employerDomain`). The key names it in the `sort` parameter and in JSON.

### The reference date

faux-person picks a person's date of birth relative to its clock's today,
which by default is the system clock. With that, `/people?seed=42` would give
people born one day later for each day that passes. So the app gives
faux-person a fixed clock, **2026-01-01**, and every person's date of birth
is worked out from that date, whatever day it is. `GenerationTest` checks the
app's people are those of the reference date and not of another date.

For the same reason **there is no Age column**, unlike faux-person-fx: an age
changes with the date. Date of birth is there, and any age can be worked out
from it.

### A request

| Parameter | Values | Default | Applies to |
|---|---|---|---|
| `count` | 1 to `faux.web.max-count` | `faux.web.default-count` (100) | all |
| `seed` | any `long` | none: a random seed is chosen | all |
| `region` | `MIXED` or a faux-person `Region` name, any case | `MIXED` | names, people |
| `real` | `true` or `false` | `false`: employers are `[faux]` under `.example` | companies, people |
| `format` | `html`, `csv`, `psv`, `tsv`, `json` | `html` | all |
| `sort` | a column key | the kind's default column | all |
| `dir` | `asc`, `desc` | `asc` | all |
| `page` | 1 to the last page | 1 | `html` only |
| `size` | 25, 50, 100, 250 | 50 | `html` only |

A parameter that doesn't apply to the kind is ignored, as in faux-person-fx.
A bad value (a count over the limit, a seed that isn't a number, an unknown
region, sort key or format, a page past the last) is a `400 Bad Request`
naming the parameter and what it accepts. Every check runs before anything is
generated: the last page is worked out from the count.

**The seed.** With a seed, the rows are the ones that seed always gives. With
none, the app picks one, and every response names it in an `X-Faux-Seed`
header; the HTML page also puts it in the seed box and in every link it
writes, so paging and sorting keep the same rows. To get the same rows again,
pass that seed.

## Routes

All under the context path `/faux-person-web` (`/faux-person-web-staging` on
staging). All `GET`.

| Route | Returns | Public because |
|---|---|---|
| `/` | `302` to `/people` | It only redirects |
| `/names`, `/companies`, `/people` | The rows, in the requested format | The app's purpose: synthetic data for anyone. It holds no real or stored data |
| `/health` | `200 ok`, plain text | nginx and the deploy smoke test check it. It reveals nothing |

Anything else is `404`. Every other method is `405`. When the app is too busy
to build a cache miss, the answer is `503` with `Retry-After: 2`.

### Formats

| `format` | Content type | Body |
|---|---|---|
| `html` | `text/html` | The page: controls, a sortable paged table, links to the other formats, and the cURL command for the CSV |
| `csv` | `text/csv; charset=utf-8` | RFC 4180: header row of column titles, CRLF line ends, a field quoted when it holds a comma, quote, CR or LF, with quotes doubled |
| `psv` | `text/plain; charset=utf-8` | The same with `\|` for the comma and LF line ends |
| `tsv` | `text/tab-separated-values; charset=utf-8` | The same with a tab and LF line ends |
| `json` | `application/json` | An array of objects keyed by column key. Strings, and dates as `YYYY-MM-DD` strings |

The non-HTML formats hold every row, sorted by `sort` and `dir`, with no
paging. They carry `Content-Disposition: inline; filename="<kind>-<seed>.<ext>"`,
so a browser that saves one names it usefully. All files are UTF-8 with no
byte-order mark.

The CSV, pipe and JSON writers are faux-person-fx's `Exporter`, extended with
tab-delimited output.

## Caching

Three layers, all in memory. Caffeine provides each one, bounded by weight.
Past a ceiling it drops entries, choosing by how often and how recently each
was used.

| Cache | Key | Holds | Weighed by |
|---|---|---|---|
| Rows | kind, count, seed, region, real | The generated rows | Row count |
| Sorted | rows key + sort + dir | The same rows in that order (references, not copies) | Row count |
| Bodies | sorted key + format (not `html`) | The finished body bytes | Bytes |

- **One generation per key.** Concurrent requests for the same key wait on
  one computation instead of each running it.
- **The ceilings are configurable:** `faux.web.cache.max-rows` (default
  200,000 across the rows and sorted caches) and `faux.web.cache.max-body-bytes`
  (default 64 MB). A dropped entry is made again on demand, the same as
  before, because generation is deterministic.
- **A limit on cache misses.** A request that misses takes one of
  `faux.web.max-concurrent` work permits (default 4) for its whole path:
  generating, sorting and writing the body. It takes the permit before it
  touches the caches, so nothing waits for a permit inside the cache's own
  locking. A miss that can't get one within `faux.web.max-wait-millis`
  (default 2,000) is refused with `503`. So however many requests miss at
  once, only four are being built, and memory stays bounded. Cache hits need
  no permit. Tomcat serves at most 32 requests at once.
- **HTML pages are built per request** from the cached sorted rows: one page
  of at most 250 rows through a template. That keeps the body cache from
  holding a copy of every page of every table. It's the only per-request work
  left, and it does no generating.
- **HTTP caching.** Every response for a request that names its seed has a
  strong `ETag` and `Cache-Control: public, max-age=86400, immutable`. The
  ETag is a hash of the request's inputs and the release:
  `<app version>+faux-person-<version>+<commit>`, the commit set by the deploy
  (`-Dbuild.id`). A new release gives new ETags, so no client is told an old
  body is current. An `If-None-Match` that matches gets `304` before anything
  is generated or rendered. A response to a request without a seed gets
  `Cache-Control: no-store`, because a repeat would pick another seed.
- **The page's own files** are linked as `/app.css?v=<hash of its content>`, and
  `/app.js` the same way, so a new page never uses a file kept from an older
  release. It's a query, not part of the name, so a page kept from an older
  release can still load its files.
- **Nothing reaches disk.** nginx proxies without `proxy_cache` and buffers in
  memory (`proxy_max_temp_file_size 0`). The app's `/tmp` is in memory.

What "no compute" means precisely: a cached request generates nothing and
formats nothing. It still costs a hash lookup and the copy of the bytes to
the socket, and an HTML page still renders its template.

### Memory, measured

With a 384 MB heap, 30 distinct 10,000-person requests in JSON and CSV (past
both ceilings) left 180 MB of heap live after a full collection, and eight
more 10,000-person requests at once all succeeded. The process used 555 MB.
So each environment runs with `-Xmx384m` and systemd's `MemoryMax=768M`, and
both fit on the 2 GB instance with nginx.

## The page

One server-rendered page per kind (Thymeleaf, escaping on). It works without
JavaScript. A small script adds what the desktop app has and a plain form
can't do.

- **tauasa.org's header and footer**, copied from its `web/index.html`: the
  logo and Home, Projects and About links (to `https://tauasa.org/`), and the
  copyright and social icons, in that site's colors. A change there has to be
  copied here.
- **Tabs** Names, Companies, People, as links.
- **Controls**, in faux-person-fx's order: Count, Region (names and people),
  Seed, a **Random** button that clears the seed, Real companies (companies
  and people), Format, and **Generate**. Region and Real companies show only
  where they apply.
- **The table.** Each header is a link that sorts by that column, then
  reverses on a second click, with an arrow on the sorted column. Above it,
  on one line: the seed, "Rows 51–100 of 10,000" centered, and "Page 2 of 200"
  with the page links on the right. Below it: rows per page on the left, the
  same page number and links on the right. Up to 900px wide the top line stacks.
- **After generating**, the seed box shows the seed behind the rows, as
  faux-person-fx does.
- **Seed history.** The seed box offers the seeds this browser used before,
  newest first: up to 20 each for Names, Companies and People. The script
  keeps them in the browser's `localStorage`. The server never sees the list.
- **Other formats.** Links to the same rows as CSV, pipe, tab-delimited and
  JSON, and the cURL command for the CSV, with a Copy button.
- **Light and dark** follow the browser's `prefers-color-scheme`.

Not in the first version: faux-person-fx's record dialog on double-click.

## Security

The app is public and unauthenticated, so the risks are abuse, injection and
the deploy chain, not access.

**Abuse.**
- **Cost per request is capped:** `max-count` rows, at most four cache misses
  built at once (others wait two seconds, then `503`), and cache ceilings that
  bound memory however many distinct seeds are asked for.
- **nginx limits each client address:** 10 requests a second with bursts of
  10, and 4 connections at once. Past either, `429`. Production and staging
  have separate limits.
- **Slow readers are dropped** after 15 seconds (`send_timeout`), so they
  can't hold the app's threads.
- **Each environment's memory is capped** by systemd (`MemoryMax=768M`), so
  one can't starve the other or nginx. Staging also gets at most half of one
  CPU (`CPUQuota=50%`), so traffic to it can't use up the CPU credits
  production needs.
- **The instance uses standard CPU credits,** so a CPU flood slows it down
  rather than running up a bill.

**Injection.**
- Thymeleaf escapes every value on the page, and the exporter escapes JSON
  strings. The values are generated, never taken from the request, except the
  parameters, which are parsed into numbers and enums before use. Error
  messages don't repeat what was sent.
- The two headers built from a request, `Content-Disposition` and
  `X-Faux-Seed`, carry only a parsed `long`.
- nginx passes a fixed `Host` and `X-Forwarded-*` to the app, so links and the
  cURL command always name `https://apps.tauasa.org`, whatever a client sends.
- SSM parameters are checked by pattern (letters, digits, `.`, `_`, `-`)
  before they reach a shell.

**Headers on every response:**
- `Content-Security-Policy: default-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'`. The page has no inline script or style.
- `X-Content-Type-Options: nosniff`
- `Referrer-Policy: no-referrer`
- nginx adds `Strict-Transport-Security`.

**The host.**
- The app listens on `127.0.0.1` only. nginx is the only way in, over HTTPS.
  Port 80 only redirects and answers Let's Encrypt's checks. IPv4 only.
- No SSH: the instance is reached through AWS Systems Manager.
- Each environment runs as its own unprivileged user, can bind only its own
  port, sees a read-only file system, and can't reach the instance's metadata
  service, where the instance role's credentials are.

**The deploy chain.** The repo is public, so its pull requests come from
anyone.
- **Staging can't touch production.** Staging's SSM document runs only the
  fixed commands in `infra/host.yaml`: it swaps staging's jar and restarts
  staging. It runs no script from the package and changes no shared file.
  Everything shared (packages, users, the unit, nginx, the certificate) is
  set up only by `deploy/install.sh`, which only production's document runs,
  and production deploys only on a person's typed confirmation.
- **Build code never runs where AWS credentials can be had.** Maven, its
  plugins, the tests and every dependency run in a `build` job with read-only
  permissions and no environment. The `deploy` job, the only one that can
  request an AWS token, runs no project build code.
- **Each deploy role** trusts only its own GitHub environment in this
  repository, can write only its own prefix in the bucket, and can run only
  its own document. Both GitHub environments accept only `main`.
- **The smoke test runs in a job of its own,** with no AWS token, after the
  deploy job.
- **Accepted:** reading an SSM command's output can't be scoped by resource,
  so either deploy role can read the output of any SSM command in the
  account. The outputs here hold install logs, no secrets.
- **Accepted:** the staging app can open a connection to production's port on
  the instance, getting around nginx's per-client limits. Only code merged to
  `main` runs as staging, and the same code reaches production with its next
  deploy, so this adds no new way in. A firewall rule per user would close it
  if that ever changes.

## Configuration

`src/main/resources/application.properties`, or any Spring Boot way of
setting a property. On the server, set one as an environment variable in
`deploy/env/<environment>.env` (`FAUX_WEB_MAX_COUNT=5000`), which the next
production deploy installs.

| Property | Default | Does |
|---|---|---|
| `faux.web.default-count` | 100 | The count when none is given |
| `faux.web.max-count` | 10000 | The most rows one request may ask for |
| `faux.web.cache.max-rows` | 200000 | The rows the row and sorted caches hold together |
| `faux.web.cache.max-body-bytes` | 67108864 | The bytes the body cache holds |
| `faux.web.max-concurrent` | 4 | Cache misses built at once |
| `faux.web.max-wait-millis` | 2000 | How long a miss waits for its turn before `503` |
| `server.port` | 8080 | Staging uses 8081 |
| `server.servlet.context-path` | `/faux-person-web` | Staging uses `/faux-person-web-staging` |

The app refuses to start if `default-count` isn't from 1 to `max-count`, a
ceiling or `max-concurrent` isn't positive, or `max-wait-millis` is negative.

## Hosting

| Piece | Choice |
|---|---|
| Instance | One `t4g.small` (2 vCPU Arm, 2 GB), Ubuntu 24.04, in `us-east-1`, standard CPU credits |
| Address | An Elastic IP, and an `A` record `apps.tauasa.org` in tauasa.org's Route 53 zone |
| TLS | Let's Encrypt through certbot, renewed by certbot's timer, which reloads nginx |
| Processes | A systemd template unit: `faux-person-web@production` on 8080 and `faux-person-web@staging` on 8081, each as its own user, `-Xmx384m` |
| nginx | `/faux-person-web/` to 8080 and `/faux-person-web-staging/` to 8081, with the limits above |
| Infrastructure as code | `infra/host.yaml`, one CloudFormation stack: instance, security group (80 and 443 only), Elastic IP, DNS record, an S3 bucket for releases, the instance's role, and one deploy role and one SSM document per environment |
| Deploy | GitHub Actions through OIDC, no stored keys. A `build` job tests and packages; a `deploy` job uploads the release to S3, runs the environment's SSM document, and smoke-tests over HTTPS |
| Staging | Deploys the jar on every merge to `main` |
| Production | Starts by hand, from `main` only, when the person types `deploy to production`. Also sets up the host |

The stack is applied by hand, never by CI, as tauasa.org's bootstrap is: CI
can deploy code but can't change infrastructure.

**The first deploy is production's,** because production's deploy is what
sets up the host; staging's document refuses to run on a host that isn't set
up. After that, staging deploys on every merge. A change to `deploy/` (nginx,
the unit, the settings) reaches the host only with a production deploy, so
staging doesn't try it first: check such a change locally before deploying
it.

**Rollback:**
- A bad release: revert the change on `main` and run the production deploy.
- Faster, on the instance: each environment keeps the release before the
  current one as `faux-person-web.jar.previous`. Put it back and restart
  (infra/README.md has the commands).
- The bucket keeps each release 30 days, for reference; no workflow
  redeploys one from it.

## Beyond the request

The request asked for the app at `/faux-person-web` behind nginx on one
instance. These were added; each can be dropped.

| Addition | Why | Cost |
|---|---|---|
| A staging copy at `/faux-person-web-staging` on the same instance | The factory's rule: staging before production, on production's configuration | About 555 MB of the instance's 2 GB, and a public second address |
| nginx limits (rate and connections) and the app's limit on concurrent misses | A public, unauthenticated service has to cap what one client can make it do | Heavy users see `429` or `503` and retry |
| HTML pages rendered per request from cached rows | Caching every page of every table would multiply memory for little gain | A template render per page view |
| No Age column, and a fixed reference date | Output that changed with the date wouldn't be idempotent | Ages are worked out from the date of birth |

## Tests, and what each protects

| Test | Protects |
|---|---|
| `GenerationTest` | The same key gives equal rows from separate generators; people don't depend on today's date; parameters that don't apply can't split the cache |
| `RowsRequestTest` | Defaults, the count limit, bad seeds, regions, sort keys, formats, pages and sizes are refused with the parameter named; settings that make no sense stop the app |
| `ExporterTest` | CSV, pipe, tab and JSON bodies: quoting, line ends, escaping, the header row, no byte-order mark |
| `CacheTest` | A repeat generates nothing; orders and formats share one generation; the ceilings hold; a restarted store makes the same bytes; one key generates once under concurrency; when every permit is taken a miss is refused and a hit is served |
| `RowsControllerTest` | Each format's status, type, file name and seed header; the same bytes and ETag for the same seed; `304`; `no-store` without a seed; `400` naming the parameter, as text and as the page; a page past the last; `503` with `Retry-After` when busy; root, health, `404`, `405`; the cURL command's address |
| `PageTest` | The page shows its seed and every link keeps it; pages hold the right slice; headers sort and reverse; Region and Real companies show only where they apply; one active tab; no inline script or style, on the error page too; the script's and stylesheet's addresses carry a hash of their content, and older addresses still load; the same page number and links above and below the table, in order; the site header and footer, on the HTML 400 page too |
| `SecurityHeadersTest` | The headers on every response, error pages and redirects included |

`HttpTest` is their base: it starts the app on a random port and calls it
over HTTP. `mvn verify` runs them all; CI runs it on every pull request, and
smoke-tests the jar.
