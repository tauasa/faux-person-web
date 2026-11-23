package org.tauasa.faux.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.http.HttpResponse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SecurityHeadersTest extends HttpTest {

    @ParameterizedTest
    @CsvSource({
            "/people?count=3&seed=1,             200",
            "/people?count=3&seed=1&format=csv,  200",
            "/people?count=0,                    400",
            "/people?count=0&format=csv,         400",
            "/nowhere,                           404",
            "/,                                  302",
            "/health,                            200",
            "/app.css,                           200"})
    void everyResponseCarriesTheHeaders(String path, int status) {
        HttpResponse<String> r = get(path);
        assertEquals(status, r.statusCode());
        assertEquals(SecurityHeadersFilter.CSP, header(r, "Content-Security-Policy"));
        assertEquals("nosniff", header(r, "X-Content-Type-Options"));
        assertEquals("no-referrer", header(r, "Referrer-Policy"));
    }
}
