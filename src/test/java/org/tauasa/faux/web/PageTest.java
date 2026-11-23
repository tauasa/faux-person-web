package org.tauasa.faux.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class PageTest extends HttpTest {

    private static final Pattern ROW = Pattern.compile("<tr>\\s*((?:<td>[^<]*</td>\\s*)+)</tr>");
    private static final Pattern CELL = Pattern.compile("<td>([^<]*)</td>");

    /** The table's body rows, each as its cells. */
    private static List<List<String>> rows(String html) {
        List<List<String>> rows = new ArrayList<>();
        Matcher row = ROW.matcher(html);
        while (row.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cell = CELL.matcher(row.group(1));
            while (cell.find()) {
                cells.add(cell.group(1).replace("&#39;", "'").replace("&amp;", "&"));
            }
            rows.add(cells);
        }
        return rows;
    }

    @Test
    void thePageShowsTheSeedItUsedAndEveryLinkKeepsIt() {
        String html = get("/people?count=120").body();
        Matcher seed = Pattern.compile("<code id=\"shown-seed\">(-?\\d+)</code>").matcher(html);
        assertTrue(seed.find(), "the seed is shown");
        String chosen = seed.group(1);
        assertTrue(html.contains("name=\"seed\"") && html.contains("value=\"" + chosen + "\""),
                "the seed box holds it, as faux-person-fx does");
        Matcher links = Pattern.compile("href=\"(people\\?[^\"]+)\"").matcher(html);
        int count = 0;
        while (links.find()) {
            assertTrue(links.group(1).contains("seed=" + chosen), links.group(1));
            count++;
        }
        assertTrue(count > 20, "headers, pages, sizes and exports all link: " + count);
    }

    @Test
    void pagesHoldTheRightSliceOfTheSortedRows() {
        String csv = get("/names?count=120&seed=5&format=csv&sort=firstName&dir=desc").body();
        List<String> all = Arrays.asList(csv.split("\r\n")).subList(1, 121);

        List<List<String>> page2 = rows(get("/names?count=120&seed=5&sort=firstName&dir=desc&page=2&size=50").body());
        assertEquals(50, page2.size());
        for (int i = 0; i < 50; i++) {
            assertEquals(all.get(50 + i), String.join(",", page2.get(i)));
        }
        List<List<String>> last = rows(get("/names?count=120&seed=5&sort=firstName&dir=desc&page=3&size=50").body());
        assertEquals(20, last.size(), "the last page holds what's left");
        assertEquals(all.get(119), String.join(",", last.get(19)));
    }

    @Test
    void aHeaderSortsAToZThenReversesOnASecondClick() {
        String html = get("/companies?count=30&seed=2").body();
        assertTrue(html.contains("aria-sort=\"ascending\""), "opens sorted by Name, A to Z");
        assertTrue(html.contains("sort=name&amp;dir=desc"), "Name's link now reverses it");
        assertTrue(html.contains("sort=city&amp;dir=asc"), "another column starts A to Z");

        List<List<String>> rows = rows(get("/companies?count=30&seed=2&sort=state&dir=desc&size=50").body());
        for (int i = 1; i < rows.size(); i++) {
            assertTrue(Column.VALUE_ORDER.compare(rows.get(i - 1).get(2), rows.get(i).get(2)) >= 0);
        }
    }

    @Test
    void regionAndRealCompaniesShowOnlyWhereTheyApply() {
        String names = get("/names?count=5&seed=1").body();
        String companies = get("/companies?count=5&seed=1").body();
        String people = get("/people?count=5&seed=1").body();
        assertTrue(names.contains("name=\"region\""));
        assertFalse(names.contains("name=\"real\""));
        assertFalse(companies.contains("name=\"region\""));
        assertTrue(companies.contains("name=\"real\""));
        assertTrue(people.contains("name=\"region\""));
        assertTrue(people.contains("name=\"real\""));
    }

    @Test
    void theActiveTabIsMarked() {
        String html = get("/companies?count=5&seed=1").body();
        Matcher tab = Pattern.compile("<a href=\"companies\"([^>]*)>Companies</a>").matcher(html);
        assertTrue(tab.find(), html);
        assertTrue(tab.group(1).contains("class=\"active\""), tab.group(1));
        assertTrue(tab.group(1).contains("aria-current=\"page\""), tab.group(1));
        Matcher other = Pattern.compile("<a href=\"people\"([^>]*)>People</a>").matcher(html);
        assertTrue(other.find());
        assertFalse(other.group(1).contains("active"), "only one tab is active");
    }

    @Test
    void thePageLoadsOnlyItsOwnScriptAndStyles() {
        String html = get("/people?count=5&seed=1").body();
        assertTrue(html.contains("<script src=\"/faux-person-web/app.js\" defer></script>"));
        assertFalse(Pattern.compile("<script>|style=\"|onclick=").matcher(html).find(),
                "no inline script or style, so the CSP can forbid them");
        assertEquals(200, get("/app.js").statusCode());
        assertEquals(200, get("/app.css").statusCode());
    }
}
