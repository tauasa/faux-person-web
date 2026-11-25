package org.tauasa.faux.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.servlet.http.HttpServletResponse;

/**
 * The app's routes. Every one is public: the app makes synthetic data for
 * anyone and holds nothing real or stored (SPEC.md, Routes).
 */
@Controller
public class RowsController {

    /** The header that names the seed behind a response's rows. */
    public static final String SEED_HEADER = "X-Faux-Seed";
    private static final String ZIP_TYPE = "application/zip";

    private static final CacheControl FOREVER = CacheControl.maxAge(java.time.Duration.ofDays(1))
            .cachePublic().immutable();

    private final RowStore store;
    private final FauxWebProperties properties;

    public RowsController(RowStore store, FauxWebProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    /** Public: it only redirects to the People page. */
    @GetMapping("/")
    public String root() {
        return "redirect:/people";
    }

    /** Public: nginx and the deploy smoke test check it, and it reveals nothing. */
    @GetMapping(value = "/health", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String health() {
        return "ok";
    }

    /** Public: the app's purpose, synthetic rows for anyone. */
    @GetMapping("/{kind:names|companies|people}")
    public Object rows(@PathVariable("kind") String path, @RequestParam Map<String, String> params,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            HttpServletResponse response) {
        Kind kind = Kind.forPath(path).orElseThrow();
        RowsRequest request = RowsRequest.parse(kind, params, properties);
        // The count says how many pages there are, so a page past the last is
        // refused before anything is generated.
        int pages = Math.max(1, (request.count() + request.size() - 1) / request.size());
        if (request.format() == Format.HTML && request.page() > pages) {
            throw new BadParameterException("page", "page must be from 1 to " + pages);
        }
        boolean seeded = request.seed() != null;
        long seed = seeded ? request.seed() : ThreadLocalRandom.current().nextLong();
        RowsKey.Sorted key = request.sortedKey(seed);

        HttpHeaders headers = new HttpHeaders();
        headers.set(SEED_HEADER, String.valueOf(seed));
        if (seeded) {
            String etag = ETags.of(properties.version(), inputs(request, key));
            headers.setETag(etag);
            headers.setCacheControl(FOREVER);
            if (ETags.matches(ifNoneMatch, etag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).headers(headers).build();
            }
        } else {
            headers.setCacheControl(CacheControl.noStore());
        }

        if (request.format() != Format.HTML) {
            RowsKey.Body file = new RowsKey.Body(key, request.format(), request.zip());
            byte[] body = store.body(file);
            headers.setContentType(MediaType.parseMediaType(
                    request.zip() ? ZIP_TYPE : request.format().contentType()));
            headers.set(HttpHeaders.CONTENT_DISPOSITION, (request.zip() ? "attachment" : "inline")
                    + "; filename=\"" + file.downloadName() + "\"");
            return ResponseEntity.ok().headers(headers).body(body);
        }

        List<?> sorted = store.sorted(key);
        PageView view = page(request, seed, sorted);
        headers.forEach((name, values) -> values.forEach(v -> response.addHeader(name, v)));
        return new ModelAndView("rows", Map.of("view", view));
    }

    /** Everything that decides a response's bytes, for its ETag. */
    private static String inputs(RowsRequest request, RowsKey.Sorted key) {
        RowsKey rows = key.rows();
        String common = String.join("|", rows.kind().path(), String.valueOf(rows.count()),
                String.valueOf(rows.seed()), String.valueOf(rows.region()), String.valueOf(rows.real()),
                key.sort(), String.valueOf(key.descending()), request.format().param());
        if (request.format() == Format.HTML) {
            return common + "|" + request.page() + "|" + request.size();
        }
        return request.zip() ? common + "|zip" : common;
    }

    private PageView page(RowsRequest request, long seed, List<?> sorted) {
        Kind kind = request.kind();
        Links links = new Links(request, seed);
        int total = sorted.size();
        int size = request.size();
        int pages = Math.max(1, (total + size - 1) / size);
        int page = request.page(); // checked against the count in rows()
        int from = (page - 1) * size;
        int to = Math.min(total, from + size);

        String sortKey = request.sort().key();
        List<PageView.Header> headers = new ArrayList<>();
        for (Column column : kind.columns()) {
            boolean sortedHere = column.key().equals(sortKey);
            // A click sorts A to Z, and a second click on the sorted column reverses it.
            boolean nextDescending = sortedHere && !request.descending();
            String arrow = sortedHere ? (request.descending() ? "▼" : "▲") : "";
            String ariaSort = sortedHere ? (request.descending() ? "descending" : "ascending") : "none";
            headers.add(new PageView.Header(column.title(), links.page(column.key(), nextDescending, 1, size),
                    arrow, ariaSort));
        }

        List<List<String>> cells = new ArrayList<>(to - from);
        for (Object row : sorted.subList(from, to)) {
            cells.add(kind.columns().stream().map(c -> String.valueOf(c.valueOf(row))).toList());
        }

        List<PageView.PageLink> pageLinks = new ArrayList<>();
        if (page > 1) {
            pageLinks.add(new PageView.PageLink("First", links.page(sortKey, request.descending(), 1, size)));
            pageLinks.add(new PageView.PageLink("Previous", links.page(sortKey, request.descending(), page - 1, size)));
        }
        if (page < pages) {
            pageLinks.add(new PageView.PageLink("Next", links.page(sortKey, request.descending(), page + 1, size)));
            pageLinks.add(new PageView.PageLink("Last", links.page(sortKey, request.descending(), pages, size)));
        }
        List<PageView.Option> sizeLinks = RowsRequest.PAGE_SIZES.stream()
                .map(s -> new PageView.Option(links.page(sortKey, request.descending(), 1, s), String.valueOf(s),
                        s == size))
                .toList();

        PageView.Table table = new PageView.Table(headers, cells, total == 0 ? 0 : from + 1, to, total, page, pages,
                pageLinks, sizeLinks);

        PageView.Form form = new PageView.Form(kind.path(), request.count(), properties.maxCount(),
                String.valueOf(seed), kind.usesRegion(), PageView.regions(request.region()), kind.usesReal(),
                request.real(), PageView.formats(Format.HTML), sortKey, request.descending() ? "desc" : "asc", size);

        List<PageView.Download> downloads = new ArrayList<>();
        List<PageView.Download> zips = new ArrayList<>();
        for (Format format : Format.values()) {
            if (format != Format.HTML) {
                downloads.add(new PageView.Download(format.label(), links.format(format)));
                zips.add(new PageView.Download(format.label(), links.zip(format)));
            }
        }
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        String curl = "curl '" + links.absolute(base, Format.CSV) + "'";

        return new PageView(kind, PageView.tabs(kind), form, table, downloads, zips, curl, null);
    }
}
