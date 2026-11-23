package org.tauasa.faux.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.tauasa.faux.model.Region;

class RowsRequestTest {

    private static final FauxWebProperties PROPS =
            new FauxWebProperties(100, 10_000, new FauxWebProperties.Cache(1000, 1000), "test", 4, 2000);

    @Test
    void anEmptyRequestTakesTheDefaults() {
        RowsRequest r = RowsRequest.parse(Kind.PEOPLE, Map.of(), PROPS);
        assertEquals(100, r.count());
        assertNull(r.seed());
        assertNull(r.region());
        assertFalse(r.real());
        assertEquals(Format.HTML, r.format());
        assertEquals("lastName", r.sort().key());
        assertFalse(r.descending());
        assertEquals(1, r.page());
        assertEquals(50, r.size());
    }

    @Test
    void readsEveryParameter() {
        RowsRequest r = RowsRequest.parse(Kind.PEOPLE, Map.of("count", "10000", "seed", "-5", "region", "jpn",
                "real", "TRUE", "format", "html", "sort", "dateOfBirth", "dir", "desc", "page", "3", "size", "250"),
                PROPS);
        assertEquals(10_000, r.count());
        assertEquals(-5L, r.seed());
        assertEquals(Region.JPN, r.region());
        assertTrue(r.real());
        assertEquals("dateOfBirth", r.sort().key());
        assertTrue(r.descending());
        assertEquals(3, r.page());
        assertEquals(250, r.size());
    }

    @ParameterizedTest
    @CsvSource({
            "count, 0", "count, 10001", "count, ten", "count, 1.5",
            "seed, 12x", "seed, 99999999999999999999",
            "region, MARS",
            "real, yes",
            "format, xml",
            "sort, age", "sort, Age",
            "dir, up",
            "page, 0", "page, one",
            "size, 30"})
    void aBadValueIsRefusedNamingTheParameter(String parameter, String value) {
        BadParameterException e = assertThrows(BadParameterException.class,
                () -> RowsRequest.parse(Kind.PEOPLE, Map.of(parameter, value), PROPS));
        assertEquals(parameter, e.parameter());
        assertTrue(e.getMessage().startsWith(parameter + " must be"), e.getMessage());
    }

    @Test
    void theCountLimitComesFromTheSettings() {
        FauxWebProperties small = new FauxWebProperties(5, 20, new FauxWebProperties.Cache(1000, 1000), "test", 4, 2000);
        assertEquals(5, RowsRequest.parse(Kind.NAMES, Map.of(), small).count());
        BadParameterException e = assertThrows(BadParameterException.class,
                () -> RowsRequest.parse(Kind.NAMES, Map.of("count", "21"), small));
        assertEquals("count must be a whole number from 1 to 20", e.getMessage());
    }

    @Test
    void parametersThatDoNotApplyAreIgnoredEvenWhenBad() {
        RowsRequest companies = RowsRequest.parse(Kind.COMPANIES, Map.of("region", "MARS"), PROPS);
        assertNull(companies.region());
        RowsRequest names = RowsRequest.parse(Kind.NAMES, Map.of("real", "maybe"), PROPS);
        assertFalse(names.real());
        RowsRequest csv = RowsRequest.parse(Kind.NAMES, Map.of("format", "csv", "page", "zero", "size", "7"), PROPS);
        assertEquals(1, csv.page());
    }

    @Test
    void mixedAndABlankSeedMeanNone() {
        RowsRequest r = RowsRequest.parse(Kind.PEOPLE, Map.of("region", "Mixed", "seed", "  "), PROPS);
        assertNull(r.region());
        assertNull(r.seed());
    }

    @Test
    void theAppRefusesSettingsThatMakeNoSense() {
        FauxWebProperties.Cache cache = new FauxWebProperties.Cache(1, 1);
        assertThrows(IllegalArgumentException.class, () -> new FauxWebProperties(0, 10, cache, "v", 4, 2000));
        assertThrows(IllegalArgumentException.class, () -> new FauxWebProperties(11, 10, cache, "v", 4, 2000));
        assertThrows(IllegalArgumentException.class, () -> new FauxWebProperties(1, 0, cache, "v", 4, 2000));
        assertThrows(IllegalArgumentException.class, () -> new FauxWebProperties.Cache(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new FauxWebProperties.Cache(1, 0));
    }
}
