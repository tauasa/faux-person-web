package org.tauasa.faux.web;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.tauasa.faux.CompanyGenerator;
import org.tauasa.faux.NameGenerator;
import org.tauasa.faux.PersonGenerator;
import org.tauasa.faux.model.Company;
import org.tauasa.faux.model.Name;
import org.tauasa.faux.model.NativeName;
import org.tauasa.faux.model.Person;

/**
 * What a page generates: its path, its columns, which parameters apply to it,
 * and how faux-person makes its rows.
 */
public enum Kind {
    NAMES("names", "Names", true, false, "lastName", List.of(
            Column.of(Name.class, "First name", Name::firstName),
            Column.of(Name.class, "Last name", Name::lastName),
            Column.of(Name.class, "Native name", Kind::nativeName))) {
        @Override
        List<?> generate(RowsKey key) {
            NameGenerator.Builder builder = NameGenerator.builder().region(key.region()).seed(key.seed());
            return builder.build().next(key.count());
        }
    },

    COMPANIES("companies", "Companies", false, true, "name", List.of(
            Column.of(Company.class, "Name", Company::name),
            Column.of(Company.class, "City", Company::city),
            Column.of(Company.class, "State", Company::state),
            Column.of(Company.class, "Country", Company::country),
            Column.of(Company.class, "Domain", Company::domain))) {
        @Override
        List<?> generate(RowsKey key) {
            CompanyGenerator.Builder builder = CompanyGenerator.builder()
                    .realCompanies(key.real()).seed(key.seed());
            return builder.build().next(key.count());
        }
    },

    // faux-person-fx's people columns, less Age: an age changes with the date,
    // and the same seed must give the same output on any day.
    PEOPLE("people", "People", true, true, "lastName", List.of(
            Column.of(Person.class, "First name", Person::firstName),
            Column.of(Person.class, "Last name", Person::lastName),
            Column.of(Person.class, "Native name", p -> nativeName(p.name())),
            // Enums show and sort by name, A to Z, not in declaration order.
            Column.of(Person.class, "Gender", p -> p.gender().name()),
            Column.of(Person.class, "Region", p -> p.region().name()),
            Column.of(Person.class, "Date of birth", Person::dateOfBirth),
            Column.of(Person.class, "Email", Person::email),
            Column.of(Person.class, "Phone", Person::phone),
            Column.of(Person.class, "Street", p -> p.address().street()),
            Column.of(Person.class, "City", p -> p.address().city()),
            Column.of(Person.class, "State", p -> p.address().state()),
            Column.of(Person.class, "Postal code", p -> p.address().postalCode()),
            Column.of(Person.class, "Country", p -> p.address().country()),
            Column.of(Person.class, "Employer", p -> p.employer().name()),
            Column.of(Person.class, "Employer city", p -> p.employer().city()),
            Column.of(Person.class, "Employer state", p -> p.employer().state()),
            Column.of(Person.class, "Employer country", p -> p.employer().country()),
            Column.of(Person.class, "Employer domain", p -> p.employer().domain()))) {
        @Override
        List<?> generate(RowsKey key) {
            PersonGenerator.Builder builder = PersonGenerator.builder()
                    .region(key.region()).realCompanies(key.real()).seed(key.seed()).clock(REFERENCE_CLOCK);
            return builder.build().next(key.count());
        }
    };

    /**
     * The "today" faux-person works out birth dates from. Fixed, because
     * faux-person's default is the system clock: with it, the same seed would
     * give people born a day later for each day that passes.
     */
    /**
     * The whole name in its own script, as faux-person 0.3.0 gives it: Hangul
     * for a South Korean (전영민), and "" for every other region.
     */
    static String nativeName(Name n) {
        return n.nativeName().map(NativeName::fullName).orElse("");
    }

    public static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 1, 1);
    static final Clock REFERENCE_CLOCK = Clock.fixed(REFERENCE_DATE.atStartOfDay(ZoneOffset.UTC).toInstant(),
            ZoneOffset.UTC);

    private final String path;
    private final String title;
    private final boolean usesRegion;
    private final boolean usesReal;
    private final String defaultSort;
    private final List<Column> columns;

    Kind(String path, String title, boolean usesRegion, boolean usesReal, String defaultSort, List<Column> columns) {
        this.path = path;
        this.title = title;
        this.usesRegion = usesRegion;
        this.usesReal = usesReal;
        this.defaultSort = defaultSort;
        this.columns = columns;
    }

    /** Makes the rows a key names. Deterministic: the same key gives equal rows. */
    abstract List<?> generate(RowsKey key);

    /** "people": the path under the context path, and the file name's start. */
    public String path() {
        return path;
    }

    /** "People": the tab's title. */
    public String title() {
        return title;
    }

    /** Whether the region parameter applies. */
    public boolean usesRegion() {
        return usesRegion;
    }

    /** Whether the real-companies parameter applies. */
    public boolean usesReal() {
        return usesReal;
    }

    /** The columns, in the order shown. */
    public List<Column> columns() {
        return columns;
    }

    /**
     * The columns the page's table shows for these rows: all of them, less
     * Native name when none of the rows has one. Downloads keep every column,
     * so a file's layout doesn't depend on the seed.
     */
    public List<Column> shownColumns(List<?> rows) {
        return columns.stream()
                .filter(c -> !c.key().equals(NATIVE_NAME) || rows.stream().anyMatch(r -> !"".equals(c.valueOf(r))))
                .toList();
    }

    /** Whether a column holds names in their own script, which may run right to left. */
    public static boolean ownScript(Column column) {
        return column.key().equals(NATIVE_NAME);
    }

    private static final String NATIVE_NAME = "nativeName";

    /** The column the table opens sorted by. */
    public Column defaultSort() {
        return column(defaultSort).orElseThrow();
    }

    /** The column with this key. */
    public Optional<Column> column(String key) {
        return columns.stream().filter(c -> c.key().equals(key)).findFirst();
    }

    /** The kind at this path. */
    public static Optional<Kind> forPath(String path) {
        for (Kind kind : values()) {
            if (kind.path.equals(path)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
