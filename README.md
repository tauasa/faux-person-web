# faux-person-web

A web app that generates random names, companies and people with
[faux-person](https://github.com/tauasa/faux-person), in your browser or from
cURL. It's the web counterpart of the
[faux-person-fx](https://github.com/tauasa/faux-person-fx) desktop app: the
same three kinds, the same controls, the same columns.

**https://apps.tauasa.org/faux-person-web**

- **The same inputs give the same output.** Give a seed and you get the same
  rows every time, byte for byte, on any day, for as long as the same release
  of the app is running. Leave it out and the app picks one and tells you
  which. A new release can change the output, for example with a newer
  faux-person.
- **Made once, then served from memory.** A repeat request generates nothing.
- **Nothing is stored.** No accounts, no cookies, nothing written to disk.
  Results live in the server's memory until it restarts.

## In a browser

1. Open https://apps.tauasa.org/faux-person-web. It opens on **People**; the
   tabs switch to **Companies** or **Names**.
2. Set the controls:
   - **Count**: how many rows, 1 to 5,000. 100 to start.
   - **Region** (names and people): where first and last names come from.
     **Mixed** draws from all of them.
   - **Seed**: leave it blank for new random rows, or type a whole number to
     get the rows that number always gives.
   - **Real companies** (companies and people): real employer names and email
     domains. Left off, employers are marked `[faux]` and live under
     `.example`, which never delivers mail.
   - **Format**: **HTML** shows the table. **CSV**, **Pipe-delimited**,
     **Tab-delimited** and **JSON** open or download every row as a file.
3. Press **Generate**.

The table shows 25 rows per page to start. Click a column header to sort by it, and
again to reverse it. The page links are above and below the table, on the
right. **Rows per page** (25, 50, 100, 250) is below it, on the left.

**Getting the same rows again.** After you generate, the seed box shows the
seed behind the rows on screen. Press **Generate** again and you get the same
rows; press **Random** first to clear it for new ones. Click the seed box to
pick a seed you used before: up to 20 each for Names, Companies and People.
That list stays in this browser and is never sent to the server. The icon
after the seed above the table copies it.

**The same rows as a file.** Under the table, **The same rows, every one (zipped)**
downloads the rows on screen in each format, in a zip file: all rows, sorted as
the table is, named for example `people-42.csv.zip` after the seed. For a file
that isn't zipped, pick a **Format** and press **Generate**; tick **Zip file**
there to zip it. It's off for HTML. Under the buttons is the cURL command that
fetches the rows as CSV, with a **Copy** button; change `format=csv` at its end
for another format, or add `&zip=true` for a zip file.

## With cURL

Every page is a plain `GET` with the settings in the query string, so cURL
works with nothing else to set up.

```bash
# 100 people as CSV, with a new random seed
curl 'https://apps.tauasa.org/faux-person-web/people?format=csv'

# The same 25 people every time: give a seed
curl 'https://apps.tauasa.org/faux-person-web/people?count=25&seed=42&format=csv'

# 10 Japanese names as JSON
curl 'https://apps.tauasa.org/faux-person-web/names?count=10&region=JPN&format=json'

# 500 companies with real names, tab-delimited, sorted by state Z to A, into a file
curl -o companies.tsv \
  'https://apps.tauasa.org/faux-person-web/companies?count=500&real=true&format=tsv&sort=state&dir=desc'

# People in a zip file, with people-42.csv inside
curl -fo people-42.csv.zip 'https://apps.tauasa.org/faux-person-web/people?count=5000&seed=42&format=csv&zip=true'

# Pipe-delimited people, sorted by date of birth
curl 'https://apps.tauasa.org/faux-person-web/people?count=50&seed=7&format=psv&sort=dateOfBirth'
```

Quote the URL: an unquoted `&` would end the command in your shell.

**Which seed did I get?** When you don't give one, the response says which it
chose in its `X-Faux-Seed` header. Show it with `-D -` (headers to the
screen) or `-i`:

```bash
curl -s -D - -o people.csv 'https://apps.tauasa.org/faux-person-web/people?count=20&format=csv' | grep -i x-faux-seed
# x-faux-seed: -3982716455012893350

# Those same 20 people, again, any time:
curl 'https://apps.tauasa.org/faux-person-web/people?count=20&format=csv&seed=-3982716455012893350'
```

**Every request takes these parameters:**

| Parameter | Values | Default | Applies to |
|---|---|---|---|
| `count` | 1 to 5,000 | 100 | all |
| `seed` | any whole number from -9223372036854775808 to 9223372036854775807 | a random one | all |
| `region` | `MIXED`, or one of `WESTERN`, `ASIA`, `RUS`, `ROK`, `MEX`, `IRN`, `HEB`, `PSE`, `JPN`, `PHL`, `VNM`, `THA`, `NAF`, `CAF`, `SAF`, `BRA`, `GBR`, `FRA`, `ESP`, `PRT`, `GRC`, `NOR`, `SWE`, `FIN` (any case) | `MIXED` | names, people |
| `real` | `true`, `false` | `false` | companies, people |
| `format` | `html`, `csv`, `psv`, `tsv`, `json` | `html` | all |
| `zip` | `true`, `false` | `false` | every format but `html` |
| `sort` | a column key (below) | `lastName` (names, people), `name` (companies) | all |
| `dir` | `asc`, `desc` | `asc` | all |
| `page` | 1 or more | 1 | `html` only |
| `size` | 25, 50, 100, 250 | 25 | `html` only |

A parameter that doesn't apply is ignored, so `region` does nothing on
`/companies`.

**The paths and their columns.** The column keys are the `sort` values and
the JSON keys.

| Path | Column keys |
|---|---|
| `/names` | `firstName`, `lastName` |
| `/companies` | `name`, `city`, `state`, `domain` |
| `/people` | `firstName`, `lastName`, `gender`, `region`, `dateOfBirth`, `email`, `phone`, `street`, `city`, `state`, `postalCode`, `country`, `employer`, `employerCity`, `employerState`, `employerDomain` |

People have no age column, unlike faux-person-fx: an age changes with the
date, and the same seed must give the same output on any day. For the same
reason, dates of birth are worked out from a fixed date, 2026-01-01, rather
than today: people are 18 to 90 years old on that date. Work out an age from
`dateOfBirth`.

**The formats:**

| `format` | Content type | What's in it |
|---|---|---|
| `csv` | `text/csv` | A header row of column titles, then a line per row. RFC 4180: a field holding a comma, quote or line break is in double quotes, with its quotes doubled. CRLF line ends |
| `psv` | `text/plain` | The same with `\|` for the comma, LF line ends |
| `tsv` | `text/tab-separated-values` | The same with a tab, LF line ends |
| `json` | `application/json` | An array of objects, one per row, keyed by column key. Dates are `YYYY-MM-DD` strings |
| `html` | `text/html` | The page, one page of rows |

Every format but `html` holds all the rows, sorted by `sort` and `dir`. Files
are UTF-8 with no byte-order mark. Each response names a file,
`<path>-<seed>.<format>`, which a browser uses when it saves one. With
`zip=true` the response is `application/zip`, named
`<path>-<seed>.<ext>.zip`, and holds that one file as `<path>-<seed>.<ext>`.
There `<ext>` is `csv` or `json`, and `txt` for both `psv` and `tsv`.

**Errors.** A value the app can't use gets `400 Bad Request` and one line
saying which parameter and what it takes:

```bash
curl 'https://apps.tauasa.org/faux-person-web/people?count=20000&format=csv'
# 400 Bad Request: count must be a whole number from 1 to 5000
```

**Caching.** A response for a request with a seed doesn't change while the same
release is running, so it carries an `ETag` and `Cache-Control: public,
max-age=86400, immutable`. Each release has its own ETags. Send the ETag
back and an unchanged response is `304 Not Modified`, with no body:

```bash
etag=$(curl -s -D - -o /dev/null 'https://apps.tauasa.org/faux-person-web/names?count=5&seed=1&format=csv' | awk 'tolower($1)=="etag:" {print $2}' | tr -d '\r')
curl -s -o /dev/null -w '%{http_code}\n' -H "If-None-Match: $etag" \
  'https://apps.tauasa.org/faux-person-web/names?count=5&seed=1&format=csv'
# 304
```

A response without a seed is `Cache-Control: no-store`, since asking again
picks a new seed.

**Limits.** Each address may make 10 requests a second, with bursts of 10,
and have 4 connections open at once; beyond that the answer is `429 Too Many
Requests`. The server also builds only a few new results at a time: when it's
busy, a request for rows it hasn't made yet gets `503 Service Unavailable`
with `Retry-After: 2`. Rows it has already made are always served. In both
cases, wait a moment and retry; with cURL, `--retry 3` does it for you on
`429` and `503`.

## Run it yourself

Needs JDK 17 or later and Maven.

```bash
./run.sh                      # builds the jar if needed, then starts the app
./run.sh --rebuild            # clean build first
./run.sh --server.port=9090   # other arguments go to the app
```

Ctrl-C stops it. Use `--rebuild` after deleting a source file.
`FAUX_WEB_JAVA_OPTS` passes options to the JVM, such as
`FAUX_WEB_JAVA_OPTS=-Xmx1g`. `run.sh` is for macOS and Linux; anywhere, you
can use Maven directly:

```bash
mvn spring-boot:run
# or
mvn package && java -jar target/faux-person-web.jar
```

Then open http://127.0.0.1:8080/faux-person-web, or use the cURL examples
with `http://127.0.0.1:8080` in place of `https://apps.tauasa.org`. The app
listens on 127.0.0.1 only; on the server, nginx is the way in.

## Settings

`src/main/resources/application.properties`, or any Spring Boot way of
setting a property: `--faux.web.max-count=2000` on the command line, or
`FAUX_WEB_MAX_COUNT=2000` in the environment. On the server, add the
environment variable to `deploy/env/production.env` or `staging.env`; the
next production deploy installs both.

| Property | Default | Does |
|---|---|---|
| `faux.web.default-count` | 100 | The count when a request gives none |
| `faux.web.max-count` | 5000 | The most rows one request may ask for |
| `faux.web.default-size` | 25 | Rows per page when a request gives none: 25, 50, 100 or 250 |
| `faux.web.cache.max-rows` | 200000 | The rows the in-memory caches hold, generated and sorted together. Past it, the least-used are dropped and made again if asked for, the same as before |
| `faux.web.cache.max-body-bytes` | 67108864 | The bytes of finished files (CSV and the rest) held, 64 MB |
| `faux.web.max-concurrent` | 4 | New results built at once. Others wait for a turn |
| `faux.web.max-wait-millis` | 2000 | How long a request waits for its turn before `503` |
| `server.port` | 8080 | |
| `server.servlet.context-path` | `/faux-person-web` | |

The app won't start with a default count outside 1 to `max-count`, a default
size the page doesn't offer, a
ceiling or `max-concurrent` under 1, or a negative wait.

## Build and test

```bash
mvn verify
```

The tests start the app on a random port and call it over HTTP, as cURL does.
`scripts/smoke.sh <url>` checks a running copy: `scripts/smoke.sh
http://127.0.0.1:8080/faux-person-web`. [SPEC.md](SPEC.md) has the design,
the caching, and what each test protects.

## How it's hosted

One EC2 instance runs nginx and two copies of the app:

| Environment | Address | Deploys |
|---|---|---|
| Staging | https://apps.tauasa.org/faux-person-web-staging | On every merge to `main` |
| Production | https://apps.tauasa.org/faux-person-web | By hand: Actions → *deploy-production* → *Run workflow*, typing `deploy to production` |

nginx serves HTTPS with a Let's Encrypt certificate and passes requests to
the app. Each deploy builds and tests, uploads the release to S3, installs it
on the instance through AWS Systems Manager, then runs `scripts/smoke.sh`
against the live address. Staging's deploy only swaps staging's app; only a
production deploy changes the host itself (nginx, the services, the
certificate). [infra/README.md](infra/README.md) has the
one-time setup and how to roll back.

## License

MIT. See [LICENSE](LICENSE). [NOTICE](NOTICE) lists where faux-person's data
comes from, including the GeoNames data under CC BY 4.0, which the page also
credits under every table.
