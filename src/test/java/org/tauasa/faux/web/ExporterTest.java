package org.tauasa.faux.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class ExporterTest {

    record Row(String name, LocalDate born, Integer n) {
    }

    private static final List<Column> COLUMNS = List.of(
            Column.of(Row.class, "Full name", Row::name),
            Column.of(Row.class, "Date of birth", Row::born),
            Column.of(Row.class, "N", Row::n));

    private static final List<Row> ROWS = List.of(
            new Row("Ana, \"Bo\" Ruiz", LocalDate.of(1990, 1, 2), 7),
            new Row("Tab\there|pipe", LocalDate.of(2001, 12, 31), 8),
            new Row("Line\nbreak\\é", LocalDate.of(1970, 6, 15), 9));

    private static String text(Format format) {
        return new String(Exporter.bytes(COLUMNS, ROWS, format), StandardCharsets.UTF_8);
    }

    @Test
    void csvQuotesWhatNeedsItAndEndsLinesWithCrlf() {
        assertEquals("Full name,Date of birth,N\r\n"
                + "\"Ana, \"\"Bo\"\" Ruiz\",1990-01-02,7\r\n"
                + "Tab\there|pipe,2001-12-31,8\r\n"
                + "\"Line\nbreak\\é\",1970-06-15,9\r\n", text(Format.CSV));
    }

    @Test
    void pipeQuotesFieldsHoldingAPipeAndEndsLinesWithLf() {
        assertEquals("Full name|Date of birth|N\n"
                + "\"Ana, \"\"Bo\"\" Ruiz\"|1990-01-02|7\n"
                + "\"Tab\there|pipe\"|2001-12-31|8\n"
                + "\"Line\nbreak\\é\"|1970-06-15|9\n", text(Format.PSV));
    }

    @Test
    void tabQuotesFieldsHoldingATab() {
        assertEquals("Full name\tDate of birth\tN\n"
                + "\"Ana, \"\"Bo\"\" Ruiz\"\t1990-01-02\t7\n"
                + "\"Tab\there|pipe\"\t2001-12-31\t8\n"
                + "\"Line\nbreak\\é\"\t1970-06-15\t9\n", text(Format.TSV));
    }

    @Test
    void jsonKeysByColumnKeyWithNumbersAsNumbersAndEscapedStrings() {
        assertEquals("[\n"
                + "  {\n    \"fullName\": \"Ana, \\\"Bo\\\" Ruiz\",\n    \"dateOfBirth\": \"1990-01-02\",\n    \"n\": 7\n  },\n"
                + "  {\n    \"fullName\": \"Tab\\there|pipe\",\n    \"dateOfBirth\": \"2001-12-31\",\n    \"n\": 8\n  },\n"
                + "  {\n    \"fullName\": \"Line\\nbreak\\\\é\",\n    \"dateOfBirth\": \"1970-06-15\",\n    \"n\": 9\n  }\n"
                + "]\n", text(Format.JSON));
    }

    @Test
    void noRowsIsJustTheHeaderOrAnEmptyArray() {
        assertEquals("Full name,Date of birth,N\r\n",
                new String(Exporter.bytes(COLUMNS, List.of(), Format.CSV), StandardCharsets.UTF_8));
        assertEquals("[]\n", new String(Exporter.bytes(COLUMNS, List.of(), Format.JSON), StandardCharsets.UTF_8));
    }

    @Test
    void utf8WithNoByteOrderMark() {
        byte[] bytes = Exporter.bytes(COLUMNS, ROWS.subList(2, 3), Format.CSV);
        assertEquals('F', bytes[0], "no byte-order mark before the header");
        String expected = "Full name,Date of birth,N\r\n\"Line\nbreak\\é\",1970-06-15,9\r\n";
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    void htmlIsNotAnExport() {
        assertThrows(IllegalArgumentException.class, () -> Exporter.bytes(COLUMNS, ROWS, Format.HTML));
    }

    @Test
    void keysAreTitlesInCamelCase() {
        assertEquals("dateOfBirth", Column.keyOf("Date of birth"));
        assertEquals("employerDomain", Column.keyOf("Employer domain"));
        assertEquals("name", Column.keyOf("Name"));
    }
}
