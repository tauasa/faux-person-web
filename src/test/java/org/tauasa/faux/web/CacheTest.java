package org.tauasa.faux.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

class CacheTest {

    private static RowStore store(long maxRows, long maxBytes) {
        return new RowStore(new FauxWebProperties(100, 5_000, 25, new FauxWebProperties.Cache(maxRows, maxBytes), "t", 4, 2000));
    }

    private static RowsKey.Sorted sorted(long seed, String sort, boolean desc) {
        return new RowsKey.Sorted(new RowsKey(Kind.PEOPLE, 100, seed, null, false), sort, desc);
    }

    @Test
    void aSecondIdenticalRequestGeneratesNothing() {
        RowStore store = store(100_000, 10_000_000);
        RowsKey key = new RowsKey(Kind.PEOPLE, 500, 9L, null, false);
        List<?> first = store.rows(key);
        List<?> second = store.rows(key);
        assertSame(first, second);
        assertEquals(1, store.generations());
    }

    @Test
    void sortedOrdersAndBodiesAreMadeOnceAndShareTheGeneratedRows() {
        RowStore store = store(100_000, 10_000_000);
        RowsKey.Body csv = new RowsKey.Body(sorted(9L, "email", true), Format.CSV, false);
        byte[] first = store.body(csv);
        byte[] second = store.body(csv);
        assertSame(first, second);
        store.body(new RowsKey.Body(sorted(9L, "email", true), Format.JSON, false));
        store.sorted(sorted(9L, "city", false));
        assertEquals(1, store.generations(), "every order and format came from one generation");
        assertEquals(2, store.renders(), "CSV once, JSON once");
    }

    @Test
    void aZipIsItsOwnBodyMadeOnceFromTheSameRows() {
        RowStore store = store(100_000, 10_000_000);
        byte[] plain = store.body(new RowsKey.Body(sorted(9L, "email", true), Format.CSV, false));
        byte[] zip = store.body(new RowsKey.Body(sorted(9L, "email", true), Format.CSV, true));
        assertSame(zip, store.body(new RowsKey.Body(sorted(9L, "email", true), Format.CSV, true)));
        assertTrue(zip.length < plain.length, "compressed: " + zip.length + " of " + plain.length);
        assertEquals(1, store.generations());
        assertEquals(2, store.renders());
    }

    @Test
    void descendingIsTheReverseOrder() {
        RowStore store = store(100_000, 10_000_000);
        List<?> asc = store.sorted(sorted(3L, "dateOfBirth", false));
        List<?> desc = store.sorted(sorted(3L, "dateOfBirth", true));
        Column born = Kind.PEOPLE.column("dateOfBirth").orElseThrow();
        assertTrue(Column.VALUE_ORDER.compare(born.valueOf(asc.get(0)), born.valueOf(asc.get(99))) <= 0);
        assertEquals(born.valueOf(asc.get(0)), born.valueOf(desc.get(99)));
        assertEquals(born.valueOf(asc.get(99)), born.valueOf(desc.get(0)));
    }

    @Test
    void distinctRequestsPastTheCeilingEvictInsteadOfGrowing() {
        RowStore store = store(1_000, 10_000_000);
        for (long seed = 0; seed < 50; seed++) {
            store.rows(new RowsKey(Kind.NAMES, 100, seed, null, false));
        }
        assertTrue(store.rowsHeld() <= 1_000, "held " + store.rowsHeld());
        assertEquals(50, store.generations());
    }

    @Test
    void aRestartedStoreMakesTheSameBytes() {
        // A restart empties the cache; what's made again must be what was there.
        RowsKey.Body key = new RowsKey.Body(sorted(5L, "lastName", false), Format.CSV, false);
        byte[] before = store(100_000, 10_000_000).body(key);
        byte[] after = store(100_000, 10_000_000).body(key);
        assertEquals(new String(before), new String(after));
    }

    @Test
    void theBodyCeilingHoldsToo() {
        RowStore store = store(100_000, 50_000);
        for (long seed = 0; seed < 20; seed++) {
            store.body(new RowsKey.Body(sorted(seed, "lastName", false), Format.JSON, false));
        }
        assertEquals(20, store.renders());
        assertTrue(store.bodyBytesHeld() <= 50_000, "held " + store.bodyBytesHeld());
    }

    @Test
    void whenEveryPermitIsTakenAMissIsRefusedButAHitIsServed() throws Exception {
        RowStore store = new RowStore(new FauxWebProperties(100, 5_000, 25,
                new FauxWebProperties.Cache(100_000, 10_000_000), "t", 2, 50));
        RowsKey cached = new RowsKey(Kind.NAMES, 10, 1L, null, false);
        store.rows(cached);
        store.work().acquire(2);
        try {
            assertThrows(RowStore.BusyException.class,
                    () -> store.rows(new RowsKey(Kind.NAMES, 10, 2L, null, false)));
            assertEquals(10, store.rows(cached).size(), "a cache hit needs no permit");
        } finally {
            store.work().release(2);
        }
        assertEquals(10, store.rows(new RowsKey(Kind.NAMES, 10, 2L, null, false)).size(),
                "the refused key isn't stuck: it's made once a permit is free");
    }

    @Test
    void concurrentRequestsForOneKeyGenerateOnce() throws Exception {
        RowStore store = store(100_000, 10_000_000);
        RowsKey key = new RowsKey(Kind.PEOPLE, 5_000, 77L, null, false);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<?>>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return store.rows(key);
            }));
        }
        start.countDown();
        for (Future<List<?>> result : results) {
            assertEquals(5_000, result.get().size());
        }
        pool.shutdown();
        assertEquals(1, store.generations());
    }
}
