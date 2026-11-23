package org.tauasa.faux.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Strong ETags made from what decides a response's bytes, not from the bytes:
 * the app's version and the request's inputs. A matching {@code If-None-Match}
 * is answered before anything is generated or rendered.
 */
public final class ETags {

    private ETags() {
    }

    /**
     * @param version the app's version: a new release never matches an old ETag
     * @param inputs  everything that decides the bytes, in a fixed order
     */
    public static String of(String version, String inputs) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((version + "\n" + inputs).getBytes(StandardCharsets.UTF_8));
            return '"' + HexFormat.of().formatHex(hash, 0, 16) + '"';
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java has SHA-256", e);
        }
    }

    /** Whether an If-None-Match header names this ETag (or is {@code *}). */
    public static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String tag = candidate.strip();
            if (tag.equals("*") || tag.equals(etag) || tag.equals("W/" + etag)) {
                return true;
            }
        }
        return false;
    }
}
