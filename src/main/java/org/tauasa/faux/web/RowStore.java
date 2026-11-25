package org.tauasa.faux.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Generated rows, sorted orders and finished bodies, all in memory and
 * bounded. Each is made once per key and served from memory after, until
 * evicted or the app restarts. Generation is deterministic, so an evicted
 * entry made again is the same as before.
 *
 * <p>Making an entry (generating, sorting or writing a body) takes one of a
 * few work permits, so however many requests miss the cache at once, only
 * that many are building at a time and memory stays bounded. A request that
 * can't get a permit soon is refused with {@link BusyException}. Cache hits
 * need no permit.
 */
@Service
public class RowStore {

    /** Rows and sorted orders share one cache, so one ceiling covers both. */
    private final Cache<Object, List<?>> rows;
    private final Cache<RowsKey.Body, byte[]> bodies;
    private final AtomicLong generations = new AtomicLong();
    private final AtomicLong renders = new AtomicLong();
    private final Semaphore work;
    private final long waitMillis;

    public RowStore(FauxWebProperties properties) {
        this.work = new Semaphore(properties.maxConcurrent(), true);
        this.waitMillis = properties.maxWaitMillis();
        this.rows = Caffeine.newBuilder()
                .maximumWeight(properties.cache().maxRows())
                .<Object, List<?>>weigher((key, value) -> Math.max(1, value.size()))
                .build();
        this.bodies = Caffeine.newBuilder()
                .maximumWeight(properties.cache().maxBodyBytes())
                .<RowsKey.Body, byte[]>weigher((key, value) -> Math.max(1, value.length))
                .build();
    }

    // Each public method serves a hit straight from the cache. A miss takes one
    // permit for its whole path (generate, sort, write) before it touches the
    // caches' computations, so nothing waits for a permit inside Caffeine, and
    // nothing a miss has built is held while it waits for another permit.

    /** The generated rows, in the order generated. */
    public List<?> rows(RowsKey key) {
        List<?> hit = rows.getIfPresent(key);
        return hit != null ? hit : limited(() -> buildRows(key));
    }

    /** The rows in a sorted order. Ties keep the order generated. */
    public List<?> sorted(RowsKey.Sorted key) {
        List<?> hit = rows.getIfPresent(key);
        return hit != null ? hit : limited(() -> buildSorted(key));
    }

    /** The finished body: every sorted row in one export format. */
    public byte[] body(RowsKey.Body key) {
        byte[] hit = bodies.getIfPresent(key);
        return hit != null ? hit : limited(() -> buildBody(key));
    }

    private List<?> buildRows(RowsKey key) {
        return rows.get(key, k -> {
            generations.incrementAndGet();
            return List.copyOf(key.kind().generate(key));
        });
    }

    private List<?> buildSorted(RowsKey.Sorted key) {
        // Read the rows first: Caffeine can't compute an entry inside another
        // computation on the same cache.
        List<?> generated = buildRows(key.rows());
        return rows.get(key, k -> {
            Column column = key.rows().kind().column(key.sort()).orElseThrow();
            Comparator<Object> order = key.descending() ? column.comparator().reversed() : column.comparator();
            List<Object> sorted = new ArrayList<>(generated);
            sorted.sort(order);
            return List.copyOf(sorted);
        });
    }

    private byte[] buildBody(RowsKey.Body key) {
        List<?> sorted = buildSorted(key.sorted());
        return bodies.get(key, k -> {
            renders.incrementAndGet();
            byte[] file = Exporter.bytes(key.sorted().rows().kind().columns(), sorted, key.format());
            return key.zip() ? Exporter.zip(key.fileName(), file) : file;
        });
    }

    /** Runs one piece of work under a permit, waiting a little for one. */
    private <T> T limited(Supplier<T> task) {
        boolean acquired;
        try {
            acquired = work.tryAcquire(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusyException();
        }
        if (!acquired) {
            throw new BusyException();
        }
        try {
            return task.get();
        } finally {
            work.release();
        }
    }

    /** Every work permit is taken: the request is refused rather than queued. */
    public static class BusyException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public BusyException() {
            super("busy", null, false, false);
        }
    }

    /** The work permits, so tests can take them all. */
    Semaphore work() {
        return work;
    }

    /** How many times rows were generated since start. For tests and checks. */
    public long generations() {
        return generations.get();
    }

    /** How many export bodies were written since start. For tests and checks. */
    public long renders() {
        return renders.get();
    }

    /** Applies pending evictions now. Caffeine otherwise does it in the background. */
    void cleanUp() {
        rows.cleanUp();
        bodies.cleanUp();
    }

    /** The rows the row cache holds now, for tests of the ceiling. */
    long rowsHeld() {
        cleanUp();
        return rows.policy().eviction().flatMap(e -> e.weightedSize().stream().boxed().findFirst()).orElse(0L);
    }

    /** The bytes the body cache holds now, for tests of the ceiling. */
    long bodyBytesHeld() {
        cleanUp();
        return bodies.policy().eviction().flatMap(e -> e.weightedSize().stream().boxed().findFirst()).orElse(0L);
    }
}
