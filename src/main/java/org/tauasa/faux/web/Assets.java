package org.tauasa.faux.web;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * A version for each of the page's own files, a hash of its content. The page
 * puts it in the file's address as {@code ?v=}, so after a release changes a
 * file, a new page doesn't use the copy a browser kept. It's a query and not
 * part of the name because browsers may keep a page for a day: an older page's
 * addresses must still load, though they get the current files.
 */
@Component("assets")
public class Assets {

    private final Map<String, String> versions = Map.of("app.css", hash("app.css"), "app.js", hash("app.js"));

    /** @param file a file under {@code static/}, such as {@code app.css} */
    public String version(String file) {
        String version = versions.get(file);
        if (version == null) {
            throw new IllegalArgumentException("Not one of the page's files: " + file);
        }
        return version;
    }

    private static String hash(String file) {
        try {
            byte[] content = new ClassPathResource("static/" + file).getContentAsByteArray();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content), 0, 8);
        } catch (IOException e) {
            throw new IllegalStateException("The app is missing static/" + file, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java has SHA-256", e);
        }
    }
}
