package org.tauasa.faux.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.tauasa.faux.PersonGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tauasa.faux.model.Region;

/** What idempotency rests on: a key always gives the same rows. */
class GenerationTest {

    @ParameterizedTest
    @EnumSource(Kind.class)
    void theSameKeyGivesEqualRowsFromSeparateGenerators(Kind kind) {
        RowsKey key = new RowsKey(kind, 200, 42L, Region.JPN, true);
        List<?> first = kind.generate(key);
        List<?> second = kind.generate(key);
        assertEquals(200, first.size());
        assertEquals(first, second);
        // And byte for byte once written out.
        assertEquals(new String(Exporter.bytes(kind.columns(), first, Format.JSON)),
                new String(Exporter.bytes(kind.columns(), second, Format.JSON)));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void anotherSeedGivesOtherRows(Kind kind) {
        assertNotEquals(kind.generate(new RowsKey(kind, 50, 1L, null, false)),
                kind.generate(new RowsKey(kind, 50, 2L, null, false)));
    }

    @Test
    void aParameterThatDoesNotApplyIsClearedSoItCannotSplitTheCache() {
        RowsKey companies = new RowsKey(Kind.COMPANIES, 10, 1L, Region.JPN, false);
        assertNull(companies.region());
        assertEquals(new RowsKey(Kind.COMPANIES, 10, 1L, null, false), companies);

        RowsKey names = new RowsKey(Kind.NAMES, 10, 1L, null, true);
        assertFalse(names.real());
        assertEquals(new RowsKey(Kind.NAMES, 10, 1L, null, false), names);
    }

    @Test
    void peopleDoNotDependOnTodaysDate() {
        // faux-person picks birth dates relative to its clock's today. The app
        // pins that clock, so its people are those of the reference date...
        RowsKey key = new RowsKey(Kind.PEOPLE, 300, 42L, null, false);
        List<?> app = Kind.PEOPLE.generate(key);
        assertEquals(PersonGenerator.builder().seed(42L).clock(fixedOn(Kind.REFERENCE_DATE)).build().next(300), app);
        // ...and not of whatever day it is. A year on, the same seed would otherwise differ.
        assertNotEquals(PersonGenerator.builder().seed(42L).clock(fixedOn(Kind.REFERENCE_DATE.plusYears(1)))
                .build().next(300), app);
    }

    private static Clock fixedOn(LocalDate date) {
        return Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    }

    @Test
    void peopleHaveNoAgeColumnBecauseItWouldChangeWithTheDate() {
        assertEquals(List.of("firstName", "lastName", "nativeName", "gender", "region", "dateOfBirth", "email", "phone",
                "street", "city", "state", "postalCode", "country", "employer", "employerCity", "employerState",
                "employerCountry", "employerDomain"), Kind.PEOPLE.columns().stream().map(Column::key).toList());
    }
}
