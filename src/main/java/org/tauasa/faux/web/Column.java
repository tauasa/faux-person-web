package org.tauasa.faux.web;

import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * One column: its title, its key, and the value it shows for a row. The value
 * is {@link Comparable}, so a date sorts as a date.
 *
 * @param title the header, as faux-person-fx shows it
 * @param key   the title in camelCase: the {@code sort} parameter's value and the JSON key
 * @param value the cell's value for a row, never {@code null}
 */
public record Column(String title, String key, Function<Object, ? extends Comparable<?>> value) {

    /**
     * The order every column sorts in, as in faux-person-fx: text ignoring
     * case, then by its exact characters, so equal-looking values keep a fixed
     * order. Anything else by its natural order.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final Comparator<Object> VALUE_ORDER = (a, b) -> {
        if (a instanceof String s && b instanceof String t) {
            int c = String.CASE_INSENSITIVE_ORDER.compare(s, t);
            return c != 0 ? c : s.compareTo(t);
        }
        return ((Comparable) a).compareTo(b);
    };

    public Column {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
    }

    /** A column of {@code type} rows, keyed by its title in camelCase. */
    public static <T> Column of(Class<T> type, String title, Function<T, ? extends Comparable<?>> value) {
        return new Column(title, keyOf(title), row -> value.apply(type.cast(row)));
    }

    /** This column's value for a row. */
    public Object valueOf(Object row) {
        return value.apply(row);
    }

    /** Rows in this column's ascending order. */
    public Comparator<Object> comparator() {
        return (a, b) -> VALUE_ORDER.compare(valueOf(a), valueOf(b));
    }

    /** "Date of birth" becomes "dateOfBirth". */
    public static String keyOf(String title) {
        StringBuilder key = new StringBuilder();
        for (String word : title.trim().split("[^\\p{Alnum}]+")) {
            if (word.isEmpty()) {
                continue;
            }
            String lower = word.toLowerCase(Locale.ROOT);
            key.append(key.length() == 0 ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1));
        }
        return key.toString();
    }
}
