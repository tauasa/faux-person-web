package org.tauasa.faux.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.io.StringWriter;
import java.time.temporal.TemporalAccessor;
import java.util.List;

/**
 * Writes rows as CSV, pipe-delimited, tab-delimited or JSON, one field per
 * column, in the order given. faux-person-fx's exporter, with tab-delimited
 * added.
 *
 * <ul>
 * <li><b>CSV</b>: RFC 4180. A header row of column titles, CRLF line ends, and
 * a field in double quotes when it holds the delimiter, a quote, CR or LF,
 * with its quotes doubled.</li>
 * <li><b>Pipe</b> and <b>tab</b>: the same, with {@code |} or a tab for the
 * comma, and LF line ends.</li>
 * <li><b>JSON</b>: an array of objects, one per row, keyed by column key.
 * Numbers are numbers, dates ISO-8601 strings, everything else a string.</li>
 * </ul>
 */
public final class Exporter {

    private Exporter() {
    }

    /** The whole body, as UTF-8 bytes with no byte-order mark. */
    public static byte[] bytes(List<Column> columns, List<?> rows, Format format) {
        StringWriter out = new StringWriter(rows.size() * 128 + 256);
        try {
            write(columns, rows, format, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e); // a StringWriter never throws
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static void write(List<Column> columns, List<?> rows, Format format, Writer out) throws IOException {
        switch (format) {
            case CSV -> delimited(columns, rows, ',', "\r\n", out);
            case PSV -> delimited(columns, rows, '|', "\n", out);
            case TSV -> delimited(columns, rows, '\t', "\n", out);
            case JSON -> json(columns, rows, out);
            case HTML -> throw new IllegalArgumentException("HTML is the page, not an export");
        }
        out.flush();
    }

    private static void delimited(List<Column> columns, List<?> rows, char delimiter, String lineEnd, Writer out)
            throws IOException {
        line(columns.stream().map(Column::title).toList(), delimiter, lineEnd, out);
        for (Object row : rows) {
            line(columns.stream().map(c -> String.valueOf(c.valueOf(row))).toList(), delimiter, lineEnd, out);
        }
    }

    private static void line(List<String> fields, char delimiter, String lineEnd, Writer out) throws IOException {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.write(delimiter);
            }
            out.write(field(fields.get(i), delimiter));
        }
        out.write(lineEnd);
    }

    static String field(String value, char delimiter) {
        boolean quote = value.indexOf(delimiter) >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
        return quote ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }

    private static void json(List<Column> columns, List<?> rows, Writer out) throws IOException {
        out.write('[');
        for (int r = 0; r < rows.size(); r++) {
            out.write(r == 0 ? "\n  {" : ",\n  {");
            Object row = rows.get(r);
            for (int c = 0; c < columns.size(); c++) {
                out.write(c == 0 ? "\n    " : ",\n    ");
                out.write(jsonString(columns.get(c).key()));
                out.write(": ");
                out.write(jsonValue(columns.get(c).valueOf(row)));
            }
            out.write("\n  }");
        }
        out.write(rows.isEmpty() ? "]\n" : "\n]\n");
    }

    private static String jsonValue(Object value) {
        if (value instanceof Integer || value instanceof Long) {
            return value.toString();
        }
        if (value instanceof TemporalAccessor) {
            return jsonString(value.toString());
        }
        return jsonString(String.valueOf(value));
    }

    static String jsonString(String s) {
        StringBuilder out = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
