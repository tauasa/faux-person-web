package org.tauasa.faux.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RowsControllerTest extends HttpTest {

    @ParameterizedTest
    @CsvSource({
            "csv,  text/csv;charset=UTF-8,                        people-42.csv",
            "psv,  text/plain;charset=UTF-8,                      people-42.psv",
            "tsv,  text/tab-separated-values;charset=UTF-8,       people-42.tsv",
            "json, application/json,                              people-42.json"})
    void eachExportFormatHasItsTypeAndFileName(String format, String type, String file) {
        HttpResponse<String> r = get("/people?count=7&seed=42&format=" + format);
        assertEquals(200, r.statusCode());
        assertEquals(type, header(r, "Content-Type"));
        assertEquals("inline; filename=\"" + file + "\"", header(r, "Content-Disposition"));
        assertEquals("42", header(r, RowsController.SEED_HEADER));
    }

    @Test
    void csvHoldsEveryRowSortedAsAsked() {
        String body = get("/names?count=300&seed=1&format=csv&sort=firstName&dir=desc").body();
        String[] lines = body.split("\r\n");
        assertEquals("First name,Last name", lines[0]);
        assertEquals(301, lines.length, "a header and every row, no paging");
        for (int i = 2; i < lines.length; i++) {
            String previous = lines[i - 1].split(",")[0];
            String current = lines[i].split(",")[0];
            assertTrue(Column.VALUE_ORDER.compare(previous, current) >= 0, previous + " before " + current);
        }
    }

    @Test
    void theDefaultCountIs100() {
        String body = get("/companies?seed=3&format=tsv").body();
        assertEquals(101, body.split("\n").length);
    }

    @Test
    void theSameSeedGivesTheSameBytesAndTheSameETag() {
        HttpResponse<String> a = get("/people?count=500&seed=99&format=json&region=JPN&real=true");
        HttpResponse<String> b = get("/people?count=500&seed=99&format=json&region=jpn&real=TRUE");
        assertEquals(a.body(), b.body());
        assertEquals(header(a, "ETag"), header(b, "ETag"));
        assertNotNull(header(a, "ETag"));
        assertEquals("max-age=86400, public, immutable", header(a, "Cache-Control"));
    }

    @Test
    void aSecondRequestGeneratesNothing() {
        get("/people?count=250&seed=123456&format=csv");
        long generations = store.generations();
        long renders = store.renders();
        get("/people?count=250&seed=123456&format=csv");
        get("/people?count=250&seed=123456");
        assertEquals(generations, store.generations());
        assertEquals(renders, store.renders());
    }

    @Test
    void withoutASeedOneIsChosenNamedAndNotCached() {
        HttpResponse<String> r = get("/names?count=5&format=csv");
        String seed = header(r, RowsController.SEED_HEADER);
        assertNotNull(seed);
        assertEquals("no-store", header(r, "Cache-Control"));
        assertNull(header(r, "ETag"));
        // The seed it named replays the same rows.
        assertEquals(r.body(), get("/names?count=5&format=csv&seed=" + seed).body());
    }

    @Test
    void aMatchingIfNoneMatchIsNotModifiedWithNoBody() {
        HttpResponse<String> first = get("/companies?count=20&seed=8&format=json");
        String etag = header(first, "ETag");
        HttpResponse<String> again = get("/companies?count=20&seed=8&format=json", "If-None-Match", etag);
        assertEquals(304, again.statusCode());
        assertEquals("", again.body());
        assertEquals(etag, header(again, "ETag"));
        HttpResponse<String> other = get("/companies?count=21&seed=8&format=json", "If-None-Match", etag);
        assertEquals(200, other.statusCode());
    }

    @Test
    void theETagDependsOnFormatSortAndPage() {
        String csv = header(get("/names?count=10&seed=4&format=csv"), "ETag");
        String json = header(get("/names?count=10&seed=4&format=json"), "ETag");
        String desc = header(get("/names?count=10&seed=4&format=csv&dir=desc"), "ETag");
        String page1 = header(get("/names?count=100&seed=4&page=1&size=25"), "ETag");
        String page2 = header(get("/names?count=100&seed=4&page=2&size=25"), "ETag");
        assertNotEquals(csv, json);
        assertNotEquals(csv, desc);
        assertNotEquals(page1, page2);
    }

    @Test
    void aBadParameterIsA400NamingItAsPlainTextForExports() {
        HttpResponse<String> r = get("/people?count=5001&format=csv");
        assertEquals(400, r.statusCode());
        assertTrue(header(r, "Content-Type").startsWith("text/plain"));
        assertEquals("400 Bad Request: count must be a whole number from 1 to 5000\n", r.body());

        HttpResponse<String> format = get("/people?format=xml");
        assertEquals(400, format.statusCode());
        assertTrue(format.body().contains("format must be one of: html, csv, psv, tsv, json"));
    }

    @Test
    void aBadParameterIsA400NamingItOnThePageForHtml() {
        HttpResponse<String> r = get("/people?seed=abc");
        assertEquals(400, r.statusCode());
        assertTrue(header(r, "Content-Type").startsWith("text/html"));
        assertTrue(r.body().contains("seed must be a whole number"), r.body());
        assertTrue(r.body().contains("<form"), "the controls are there to try again");
    }

    @Test
    void aPagePastTheLastIsA400() {
        HttpResponse<String> r = get("/names?count=60&seed=1&page=3&size=50");
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains("page must be from 1 to 2"));
    }

    @Test
    void whenBusyAMissIs503WithRetryAfter() throws Exception {
        int permits = store.work().availablePermits();
        store.work().acquire(permits);
        try {
            HttpResponse<String> r = get("/people?count=5&seed=8675309&format=csv");
            assertEquals(503, r.statusCode());
            assertEquals("2", header(r, "Retry-After"));
            assertEquals("503 Service Unavailable: busy, try again shortly\n", r.body());
        } finally {
            store.work().release(permits);
        }
        assertEquals(200, get("/people?count=5&seed=8675309&format=csv").statusCode());
    }

    @Test
    void rootRedirectsToPeopleAndHealthSaysOk() {
        HttpResponse<String> root = get("/");
        assertEquals(302, root.statusCode());
        assertTrue(header(root, "Location").endsWith("/faux-person-web/people"), header(root, "Location"));
        HttpResponse<String> health = get("/health");
        assertEquals(200, health.statusCode());
        assertEquals("ok", health.body());
    }

    @Test
    void unknownPathsAre404AndOtherMethods405() {
        assertEquals(404, get("/animals").statusCode());
        HttpResponse<String> post = send(HttpRequest.newBuilder(URI.create(base() + "/people"))
                .POST(HttpRequest.BodyPublishers.noBody()));
        assertEquals(405, post.statusCode());
    }

    @Test
    void theCurlCommandUsesTheAddressNginxForwards() {
        String body = get("/people?count=5&seed=11", "X-Forwarded-Proto", "https", "X-Forwarded-Host",
                "apps.tauasa.org", "X-Forwarded-Port", "443").body();
        assertTrue(body.contains(
                "curl &#39;https://apps.tauasa.org/faux-person-web/people?count=5&amp;seed=11&amp;region=MIXED"
                        + "&amp;real=false&amp;sort=lastName&amp;dir=asc&amp;format=csv&#39;"), body);
    }
}
