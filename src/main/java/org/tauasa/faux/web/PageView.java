package org.tauasa.faux.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.tauasa.faux.model.Region;

/**
 * Everything the page template shows, worked out here so the template only
 * lays it out. Every string is escaped by Thymeleaf when it's written.
 *
 * @param kind      the kind on screen
 * @param tabs      one per kind
 * @param form      the controls' values
 * @param table     the rows on this page, or {@code null} when there's an error instead
 * @param downloads the same rows in each export format
 * @param curl      the cURL command for the same rows as CSV, or {@code null}
 * @param error     what was wrong with the request, or {@code null}
 */
public record PageView(Kind kind, List<Tab> tabs, Form form, Table table, List<Download> downloads, String curl,
        String error) {

    public record Tab(String title, String href, boolean active) {
    }

    public record Option(String value, String label, boolean selected) {
    }

    /**
     * @param seed the seed behind the rows on screen, or empty
     */
    public record Form(String action, int count, int maxCount, String seed, boolean usesRegion,
            List<Option> regions, boolean usesReal, boolean real, List<Option> formats, String sort, String dir,
            int size) {
    }

    /**
     * @param arrow "▲", "▼" or empty: the sorted column's direction
     */
    public record Header(String title, String href, String arrow, String ariaSort) {
    }

    public record PageLink(String label, String href) {
    }

    /**
     * @param pageLinks First, Previous, Next, Last: those that lead somewhere
     * @param sizeLinks one per page size: its link as {@code value}, the size as {@code label}
     */
    public record Table(List<Header> headers, List<List<String>> rows, int from, int to, int total, int page,
            int pages, List<PageLink> pageLinks, List<Option> sizeLinks) {
    }

    public record Download(String label, String href) {
    }

    /** The tabs, with {@code active} on. */
    public static List<Tab> tabs(Kind active) {
        return Arrays.stream(Kind.values()).map(k -> new Tab(k.title(), k.path(), k == active)).toList();
    }

    /** Mixed, then every faux-person region. */
    public static List<Option> regions(Region selected) {
        List<Option> options = new ArrayList<>();
        options.add(new Option(RowsRequest.MIXED, "Mixed", selected == null));
        for (Region region : Region.values()) {
            options.add(new Option(region.name(), region.name(), region == selected));
        }
        return options;
    }

    public static List<Option> formats(Format selected) {
        return Arrays.stream(Format.values()).map(f -> new Option(f.param(), f.label(), f == selected)).toList();
    }

    /** The page for a request that couldn't be used: the controls at their defaults, and the error. */
    public static PageView error(Kind kind, FauxWebProperties properties, String message) {
        Form form = new Form(kind.path(), properties.defaultCount(), properties.maxCount(), "", kind.usesRegion(),
                regions(null), kind.usesReal(), false, formats(Format.HTML), kind.defaultSort().key(), "asc",
                RowsRequest.PAGE_SIZES.get(0));
        return new PageView(kind, tabs(kind), form, null, List.of(), null, message);
    }
}
