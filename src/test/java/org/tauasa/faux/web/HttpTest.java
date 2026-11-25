package org.tauasa.faux.web;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * Starts the real app on a random port and calls it over HTTP, through
 * Tomcat, the filters and the error pages, as nginx and cURL would.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class HttpTest {

    static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Autowired
    Environment environment;

    @Autowired
    RowStore store;

    String base() {
        return "http://127.0.0.1:" + environment.getProperty("local.server.port") + "/faux-person-web";
    }

    HttpResponse<String> get(String path, String... headers) {
        return send(HttpRequest.newBuilder(URI.create(base() + path)).GET(), headers);
    }

    HttpResponse<String> send(HttpRequest.Builder builder, String... headers) {
        for (int i = 0; i < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        try {
            return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** A GET whose body is bytes, not text. */
    HttpResponse<byte[]> getBytes(String path, String... headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + path)).GET();
        for (int i = 0; i < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        try {
            return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse(null);
    }
}
