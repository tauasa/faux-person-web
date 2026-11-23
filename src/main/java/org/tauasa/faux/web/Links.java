package org.tauasa.faux.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * The query strings a page writes. Each keeps the rows the page shows (count,
 * seed, region, real companies) and changes only what the link is for.
 * Links are relative to the kind's path, so they work under any context path
 * and behind nginx.
 */
public final class Links {

    private final RowsRequest request;
    private final long seed;

    public Links(RowsRequest request, long seed) {
        this.request = request;
        this.seed = seed;
    }

    /** The parameters that pick the rows, in a fixed order. */
    private Map<String, String> rowParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("count", String.valueOf(request.count()));
        params.put("seed", String.valueOf(seed));
        if (request.kind().usesRegion()) {
            params.put("region", request.region() == null ? RowsRequest.MIXED : request.region().name());
        }
        if (request.kind().usesReal()) {
            params.put("real", String.valueOf(request.real()));
        }
        return params;
    }

    /** This page's rows, sorted and paged as given. */
    public String page(String sort, boolean descending, int page, int size) {
        Map<String, String> params = rowParams();
        params.put("sort", sort);
        params.put("dir", descending ? "desc" : "asc");
        params.put("page", String.valueOf(page));
        params.put("size", String.valueOf(size));
        return build(request.kind().path(), params);
    }

    /** Every row in another format, sorted as now. */
    public String format(Format format) {
        Map<String, String> params = rowParams();
        params.put("sort", request.sort().key());
        params.put("dir", request.descending() ? "desc" : "asc");
        params.put("format", format.param());
        return build(request.kind().path(), params);
    }

    /** The same as {@link #format}, as an absolute URL under {@code base}. */
    public String absolute(String base, Format format) {
        return base + "/" + format(format);
    }

    private static String build(String path, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        params.forEach(builder::queryParam);
        return builder.encode().build().toUriString();
    }
}
