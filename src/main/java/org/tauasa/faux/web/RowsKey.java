package org.tauasa.faux.web;

import java.util.Objects;

import org.tauasa.faux.model.Region;

/**
 * What decides which rows are generated, and so the rows cache's key. A
 * parameter that doesn't apply to the kind is cleared, so it can't split the
 * cache: companies with any region are the same companies.
 *
 * @param kind   what to generate
 * @param count  how many rows
 * @param seed   the seed, given or chosen
 * @param region one region, or {@code null} for mixed
 * @param real   real employer names and domains
 */
public record RowsKey(Kind kind, int count, long seed, Region region, boolean real) {

    public RowsKey {
        Objects.requireNonNull(kind, "kind");
        if (!kind.usesRegion()) {
            region = null;
        }
        if (!kind.usesReal()) {
            real = false;
        }
    }

    /** The rows key in the order a {@code sort} and {@code dir} give. */
    public record Sorted(RowsKey rows, String sort, boolean descending) {
    }

    /** A finished body: sorted rows in one export format. */
    public record Body(Sorted sorted, Format format) {
    }
}
