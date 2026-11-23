package org.tauasa.faux.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The {@code faux.web.*} settings. The app refuses to start with values that
 * make no sense, rather than failing on the first request.
 *
 * @param defaultCount the count when a request gives none
 * @param maxCount     the most rows one request may ask for
 * @param cache         the in-memory caches' ceilings
 * @param version       the build's version, part of every ETag
 * @param maxConcurrent how many cache misses may be built at once
 * @param maxWaitMillis how long a cache miss waits for its turn before 503
 */
@ConfigurationProperties("faux.web")
public record FauxWebProperties(
        @DefaultValue("100") int defaultCount,
        @DefaultValue("10000") int maxCount,
        @DefaultValue Cache cache,
        @DefaultValue("dev") String version,
        @DefaultValue("4") int maxConcurrent,
        @DefaultValue("2000") long maxWaitMillis) {

    public FauxWebProperties {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("faux.web.max-concurrent must be 1 or more: " + maxConcurrent);
        }
        if (maxWaitMillis < 0) {
            throw new IllegalArgumentException("faux.web.max-wait-millis must be 0 or more: " + maxWaitMillis);
        }
        if (maxCount < 1) {
            throw new IllegalArgumentException("faux.web.max-count must be 1 or more: " + maxCount);
        }
        if (defaultCount < 1 || defaultCount > maxCount) {
            throw new IllegalArgumentException(
                    "faux.web.default-count must be from 1 to faux.web.max-count (" + maxCount + "): " + defaultCount);
        }
    }

    /**
     * @param maxRows      the rows the row and sorted caches hold together
     * @param maxBodyBytes the bytes of finished bodies the body cache holds
     */
    public record Cache(
            @DefaultValue("200000") long maxRows,
            @DefaultValue("67108864") long maxBodyBytes) {

        public Cache {
            if (maxRows < 1) {
                throw new IllegalArgumentException("faux.web.cache.max-rows must be 1 or more: " + maxRows);
            }
            if (maxBodyBytes < 1) {
                throw new IllegalArgumentException("faux.web.cache.max-body-bytes must be 1 or more: " + maxBodyBytes);
            }
        }
    }
}
