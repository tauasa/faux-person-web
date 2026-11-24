package org.tauasa.faux.web;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.tauasa.faux.model.Region;

/**
 * One request's parameters, checked. Parameters that don't apply to the kind
 * are ignored without being checked, as faux-person-fx ignores controls that
 * don't apply to a tab.
 *
 * @param kind       what to generate
 * @param count      how many rows
 * @param seed       the seed given, or {@code null} to choose one
 * @param region     one region, or {@code null} for mixed
 * @param real       real employer names and domains
 * @param format     the output format
 * @param sort       the column to sort by
 * @param descending sort Z to A
 * @param page       the page to show, from 1. HTML only
 * @param size       rows per page. HTML only
 */
public record RowsRequest(Kind kind, int count, Long seed, Region region, boolean real, Format format,
        Column sort, boolean descending, int page, int size) {

    /** The page sizes the page offers, in order. */
    public static final List<Integer> PAGE_SIZES = List.of(25, 50, 100, 250);
    public static final String MIXED = "MIXED";

    /**
     * Reads a request's query parameters.
     *
     * @throws BadParameterException naming the first parameter that can't be used
     */
    public static RowsRequest parse(Kind kind, Map<String, String> params, FauxWebProperties properties) {
        int count = count(params.get("count"), properties);
        Long seed = seed(params.get("seed"));
        Region region = kind.usesRegion() ? region(params.get("region")) : null;
        boolean real = kind.usesReal() && real(params.get("real"));
        Format format = format(params.get("format"));
        Column sort = sort(kind, params.get("sort"));
        boolean descending = descending(params.get("dir"));
        int page = 1;
        int size = properties.defaultSize();
        if (format == Format.HTML) {
            page = page(params.get("page"));
            size = size(params.get("size"), properties);
        }
        return new RowsRequest(kind, count, seed, region, real, format, sort, descending, page, size);
    }

    /** The rows key, with the seed given or chosen. */
    public RowsKey.Sorted sortedKey(long chosenSeed) {
        return new RowsKey.Sorted(new RowsKey(kind, count, chosenSeed, region, real), sort.key(), descending);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static int count(String value, FauxWebProperties properties) {
        if (blank(value)) {
            return properties.defaultCount();
        }
        String message = "count must be a whole number from 1 to " + properties.maxCount();
        try {
            int count = Integer.parseInt(value.strip());
            if (count < 1 || count > properties.maxCount()) {
                throw new BadParameterException("count", message);
            }
            return count;
        } catch (NumberFormatException e) {
            throw new BadParameterException("count", message);
        }
    }

    private static Long seed(String value) {
        if (blank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            throw new BadParameterException("seed",
                    "seed must be a whole number from " + Long.MIN_VALUE + " to " + Long.MAX_VALUE);
        }
    }

    private static Region region(String value) {
        if (blank(value) || value.strip().equalsIgnoreCase(MIXED)) {
            return null;
        }
        try {
            return Region.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadParameterException("region", "region must be " + MIXED + " or one of: "
                    + Arrays.stream(Region.values()).map(Enum::name).collect(Collectors.joining(", ")));
        }
    }

    private static boolean real(String value) {
        if (blank(value) || value.strip().equalsIgnoreCase("false")) {
            return false;
        }
        if (value.strip().equalsIgnoreCase("true")) {
            return true;
        }
        throw new BadParameterException("real", "real must be true or false");
    }

    private static Format format(String value) {
        if (blank(value)) {
            return Format.HTML;
        }
        return Format.forParam(value).orElseThrow(() -> new BadParameterException("format",
                "format must be one of: " + Arrays.stream(Format.values()).map(Format::param)
                        .collect(Collectors.joining(", "))));
    }

    private static Column sort(Kind kind, String value) {
        if (blank(value)) {
            return kind.defaultSort();
        }
        return kind.column(value.strip()).orElseThrow(() -> new BadParameterException("sort",
                "sort must be one of: " + kind.columns().stream().map(Column::key)
                        .collect(Collectors.joining(", "))));
    }

    private static boolean descending(String value) {
        if (blank(value) || value.strip().equalsIgnoreCase("asc")) {
            return false;
        }
        if (value.strip().equalsIgnoreCase("desc")) {
            return true;
        }
        throw new BadParameterException("dir", "dir must be asc or desc");
    }

    private static int page(String value) {
        if (blank(value)) {
            return 1;
        }
        try {
            int page = Integer.parseInt(value.strip());
            if (page >= 1) {
                return page;
            }
        } catch (NumberFormatException e) {
            // falls through to the message
        }
        throw new BadParameterException("page", "page must be a whole number, 1 or more");
    }

    private static int size(String value, FauxWebProperties properties) {
        if (blank(value)) {
            return properties.defaultSize();
        }
        try {
            int size = Integer.parseInt(value.strip());
            if (PAGE_SIZES.contains(size)) {
                return size;
            }
        } catch (NumberFormatException e) {
            // falls through to the message
        }
        throw new BadParameterException("size", "size must be one of: "
                + PAGE_SIZES.stream().map(String::valueOf).collect(Collectors.joining(", ")));
    }
}
