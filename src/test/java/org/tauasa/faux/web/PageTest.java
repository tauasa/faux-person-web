package org.tauasa.faux.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
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

    /** The shipped settings: 100 rows, 25 to a page, 5,000 at most. */
    @Test
    void aPageOpensOnTwentyFiveOfAHundredRows() {
        String html = get("/people").body();
        assertEquals(25, rows(html).size());
        assertTrue(html.contains("Page 1 of 4"), "100 rows");
        assertTrue(Pattern.compile("class=\"active\"[^>]*>25</a>").matcher(html).find(), "25 is marked");
        assertTrue(html.contains("max=\"5000\""), "the count box's limit");
        assertEquals(200, get("/people?count=5000&seed=1").statusCode());
        assertEquals(400, get("/people?count=5001&seed=1").statusCode());
    }

    @Test
    void pageLinksAreAboveAndBelowTheTableAndRowsPerPageComesFirstBelow() {
        String html = get("/names?count=120&seed=5&page=2&size=50").body();
        String above = html.substring(html.indexOf("class=\"summary\""), html.indexOf("<table>"));
        String below = html.substring(html.indexOf("</table>"), html.indexOf("class=\"exports\""));
        for (String part : List.of(above, below)) {
            for (String label : List.of("First", "Previous", "Next", "Last")) {
                assertTrue(part.contains(">" + label + "</a>"), label + " in " + part);
            }
        }
        int range = above.indexOf("class=\"range\"");
        assertTrue(above.indexOf("shown-seed") < range && range < above.indexOf(">First</a>"),
                "seed, then rows, then page links");
        int sizes = below.indexOf("Rows per page:");
        assertTrue(sizes >= 0 && sizes < below.indexOf(">First</a>"), "rows per page, then page links");
    }

    @Test
    void thePageNumberAndLinksAreTheSameAboveAndBelowTheTable() {
        for (String query : List.of("count=120&seed=5&page=2&size=50", "count=5&seed=5")) {
            String html = get("/names?" + query).body();
            List<String> pagers = Pattern.compile("<nav class=\"pages\"[^>]*>(.*?)</nav>", Pattern.DOTALL)
                    .matcher(html).results().map(m -> m.group(1).replaceAll("\\s+", " ")).toList();
            assertEquals(2, pagers.size(), query);
            assertEquals(pagers.get(0), pagers.get(1), query);
            assertTrue(pagers.get(0).contains(query.contains("page=2") ? "Page 2 of 3" : "Page 1 of 1"),
                    pagers.get(0));
        }
    }

    @Test
    void thePageAndItsErrorPageHaveTheSiteHeaderAndFooter() {
        assertEquals(200, get("/people?count=5&seed=1").statusCode());
        assertEquals(400, get("/people?count=0").statusCode());
        for (String path : List.of("/people?count=5&seed=1", "/people?count=0")) {
            String html = get(path).body();
            String header = between(html, "<header class=\"site-header\">", "</header>");
            for (String link : List.of("https://tauasa.org/", "https://tauasa.org/#home",
                    "https://tauasa.org/#projects", "https://tauasa.org/#about")) {
                assertTrue(header.contains("href=\"" + link + "\""), link + " on " + path);
            }
            String footer = between(html, "<footer class=\"site-footer\">", "</footer>");
            assertTrue(footer.contains("Tauasa Timoteo"), footer);
            for (String name : List.of("GitHub", "LinkedIn", "YouTube", "Instagram", "Facebook")) {
                assertTrue(footer.contains("aria-label=\"" + name + "\""), name + " on " + path);
            }
            assertFalse(Pattern.compile("<script>|style=\"|onclick=").matcher(html).find(), path);
        }
    }

    @Test
    void thePageAndItsErrorPageShowTheVersionAndWhenItWasBuilt() throws IOException {
        Matcher pom = Pattern.compile("<artifactId>faux-person-web</artifactId>\\s*<version>([^<]+)</version>")
                .matcher(Files.readString(Path.of("pom.xml")));
        assertTrue(pom.find(), "the pom's version");
        for (String path : List.of("/people?count=5&seed=1", "/people?count=0")) {
            String line = between(get(path).body(), "<p class=\"build\">", "</p>").replaceAll("<[^>]*>", "");
            Matcher shown = Pattern.compile("Version\\[Build\\]: v([^\\s\\[]+)\\[(\\d{8}-\\d{4})\\]").matcher(line);
            assertTrue(shown.matches(), line);
            assertEquals(pom.group(1), shown.group(1), path);
            // The page's bytes hold the build time, so the version in every ETag must too.
            assertTrue(environment.getProperty("faux.web.version").endsWith("+" + shown.group(2)), path);
        }
    }

    /** Hidden until the script finds it can use the clipboard, so nobody gets a button that does nothing. */
    @Test
    void theCopyIconFollowsTheSeedAndStartsHidden() {
        String html = get("/people?count=5&seed=1").body();
        assertTrue(Pattern.compile("<code id=\"shown-seed\">1</code>\\s*<button type=\"button\" id=\"copy-seed\"[^>]* hidden")
                .matcher(html).find(), "right after the seed, hidden");
        assertTrue(Pattern.compile("id=\"copy-seed\"[^>]* aria-label=\"Copy seed\"").matcher(html).find(), "named");
        assertTrue(html.contains("id=\"seed-copied\" role=\"status\""), "the script needs it, to announce the copy");
        assertFalse(get("/people?count=0").body().contains("copy-seed"), "no seed to copy on the error page");
    }

    @Test
    void thePageOffersEveryFormatOnceInAZip() {
        String html = get("/people?count=5&seed=1").body();
        assertTrue(html.contains("type=\"checkbox\" name=\"zip\" id=\"zip\" value=\"true\""), "an option on the form");
        for (String format : List.of("csv", "psv", "tsv", "json")) {
            assertTrue(html.contains("format=" + format + "&amp;zip=true\""), format);
            assertFalse(html.contains("format=" + format + "\""), format + " has no unzipped button");
        }
        assertEquals(4, html.split("class=\"pill\"", -1).length - 1, "one row of buttons");
        assertTrue(html.contains("aria-label=\"CSV, zipped\""), "said to screen readers");
        assertTrue(html.contains("<select name=\"format\" id=\"format\">"), "the script turns Zip off for HTML by it");
    }

    private static String between(String html, String start, String end) {
        int from = html.indexOf(start);
        assertTrue(from >= 0, start);
        return html.substring(from, html.indexOf(end, from));
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
        assertTrue(Pattern.compile("<script src=\"/faux-person-web/app\\.js\\?v=[0-9a-f]{16}\" defer></script>")
                .matcher(html).find());
        assertFalse(Pattern.compile("<script>|style=\"|onclick=").matcher(html).find(),
                "no inline script or style, so the CSP can forbid them");
        assertEquals(200, get("/app.js").statusCode());
        assertEquals(200, get("/app.css").statusCode());
    }

    /** A browser holding an older release's file asks again only if its address changes. */
    @Test
    void eachFilesAddressCarriesAHashOfItsContentOnEveryPage() throws Exception {
        for (String path : List.of("/people?count=5&seed=1", "/people?count=0")) {
            String html = get(path, "Accept", "text/html").body();
            for (String file : List.of("app.css", "app.js")) {
                Matcher link = Pattern.compile("\"/faux-person-web(/" + Pattern.quote(file) + "\\?v=([^\"]*))\"")
                        .matcher(html);
                assertTrue(link.find(), file + " is versioned on " + path);
                HttpResponse<String> served = get(link.group(1));
                assertEquals(200, served.statusCode());
                byte[] hash = MessageDigest.getInstance("SHA-256")
                        .digest(served.body().getBytes(StandardCharsets.UTF_8));
                assertEquals(HexFormat.of().formatHex(hash, 0, 8), link.group(2), file);
            }
        }
    }

    /** Browsers may keep a seeded page for a day, so its older addresses must still load. */
    @Test
    void anOlderPagesFileAddressStillLoads() {
        assertEquals(200, get("/app.css?v=0000000000000000").statusCode());
        assertEquals(200, get("/app.js?v=0000000000000000").statusCode());
    }
}
