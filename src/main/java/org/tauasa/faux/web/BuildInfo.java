package org.tauasa.faux.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The version and build time the page shows, which Maven writes into application.properties. */
@Component("buildInfo")
public class BuildInfo {

    private final String label;

    public BuildInfo(@Value("${faux.web.build-label:dev}") String label) {
        this.label = label;
    }

    /** Such as {@code v1.2.3 [20260131-1745]}, the time in UTC. */
    public String label() {
        return label;
    }
}
