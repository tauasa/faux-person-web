package org.tauasa.faux.web;

import java.util.Locale;
import java.util.Optional;

/** The output formats, by their {@code format} parameter value. */
public enum Format {
    HTML("html", "HTML", "text/html;charset=UTF-8", "html"),
    CSV("csv", "CSV", "text/csv;charset=UTF-8", "csv"),
    PSV("psv", "Pipe-delimited", "text/plain;charset=UTF-8", "psv"),
    TSV("tsv", "Tab-delimited", "text/tab-separated-values;charset=UTF-8", "tsv"),
    JSON("json", "JSON", "application/json", "json");

    private final String param;
    private final String label;
    private final String contentType;
    private final String extension;

    Format(String param, String label, String contentType, String extension) {
        this.param = param;
        this.label = label;
        this.contentType = contentType;
        this.extension = extension;
    }

    /** "csv": the {@code format} parameter's value. */
    public String param() {
        return param;
    }

    /** "CSV": what the page calls it. */
    public String label() {
        return label;
    }

    public String contentType() {
        return contentType;
    }

    /** The file name's extension, without the dot. */
    public String extension() {
        return extension;
    }

    /** The format a parameter value names, any case. */
    public static Optional<Format> forParam(String value) {
        String wanted = value.strip().toLowerCase(Locale.ROOT);
        for (Format format : values()) {
            if (format.param.equals(wanted)) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }
}
