package com.example.backend.patient.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.patient.domain.SourceField;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * How a patient-source field is classified as it is read (ADR 0020): present, missing — no key,
 * {@code null}, or a blank string — or invalid — the wrong JSON type, or a string that is no
 * value of the field's kind: text, a calendar date or a date-time with its offset. Nothing is
 * refused, and nothing of a value's meaning is checked.
 */
class ReceivedFieldsTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The field {@code f} of the object {@code {"f": json}}, as the source answered it. */
    private static JsonNode field(String json) {
        return JSON.readTree("{\"f\": " + json + "}").get("f");
    }

    /** A key the source never sent: no node at all. */
    private static final JsonNode NO_KEY = JSON.readTree("{}").get("f");

    // ---- text: a code, an id, free text ---------------------------------------------------

    @Test
    void textIsPresentAsItCameUntrimmed() {
        assertThat(ReceivedFields.text(field("\" Submariner Duties \"")))
                .isEqualTo(SourceField.present(" Submariner Duties "));
    }

    @Test
    void textWithNoKeyIsMissing() {
        assertThat(ReceivedFields.text(NO_KEY)).isEqualTo(SourceField.missing());
    }

    /** A key looked up by path where there is none is Jackson's missing node: missing too. */
    @Test
    void textOfAMissingNodeIsMissing() {
        assertThat(ReceivedFields.text(JSON.readTree("{}").path("f")))
                .isEqualTo(SourceField.missing());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\" \"", "\" \\t\\n \""})
    void nullOrBlankTextIsMissing(String json) {
        assertThat(ReceivedFields.text(field(json))).isEqualTo(SourceField.missing());
    }

    /** A number is not coerced into a string: the wrong JSON type is invalid, as any other is. */
    @ParameterizedTest
    @ValueSource(strings = {"7", "0", "1.5", "true", "false", "{}", "{\"code\": \"fit\"}", "[]",
        "[\"fit\"]"})
    void textOfAnotherJsonTypeIsInvalid(String json) {
        assertThat(ReceivedFields.text(field(json))).isEqualTo(SourceField.invalid());
    }

    // ---- a calendar date -----------------------------------------------------------------

    /** A day the calendar has is present, whatever it says: one in the future too. */
    @ParameterizedTest
    @ValueSource(strings = {"2024-02-29", "2999-12-31", "0001-01-01"})
    void aCalendarDateIsPresent(String date) {
        assertThat(ReceivedFields.day(field("\"" + date + "\"")))
                .isEqualTo(SourceField.present(LocalDate.parse(date)));
    }

    @Test
    void aDateWithNoKeyIsMissing() {
        assertThat(ReceivedFields.day(NO_KEY)).isEqualTo(SourceField.missing());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"  \""})
    void aNullOrBlankDateIsMissing(String json) {
        assertThat(ReceivedFields.day(field(json))).isEqualTo(SourceField.missing());
    }

    /**
     * A date must be {@code YYYY-MM-DD} and a day the calendar has: a month, a time, no such day,
     * another year's spelling or a padded date is invalid.
     */
    @ParameterizedTest
    @ValueSource(strings = {"2017-02", "2018", "2017-02-13T00:00:00", "2018-02-30",
        "2023-13-01", "+10000-01-01", "10000-01-01", " 2024-01-10", "14 Oct 2023",
        "2024/01/10"})
    void aStringThatIsNoCalendarDateIsInvalid(String date) {
        assertThat(ReceivedFields.day(field("\"" + date + "\"")))
                .isEqualTo(SourceField.invalid());
    }

    @ParameterizedTest
    @ValueSource(strings = {"20240110", "true", "{}", "[\"2024-01-10\"]"})
    void aDateOfAnotherJsonTypeIsInvalid(String json) {
        assertThat(ReceivedFields.day(field(json))).isEqualTo(SourceField.invalid());
    }

    // ---- a date-time with its offset -----------------------------------------------------

    /**
     * A date-time with an offset is present with the offset it came in, {@code Z} among them,
     * its seconds and their fraction optional: whatever instant it names, one in the future too.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        2017-02-13T10:00:00+08:00     | 2017-02-13T10:00+08:00
        2017-02-15T02:00:00Z          | 2017-02-15T02:00Z
        2017-02-13T10:00+08:00        | 2017-02-13T10:00+08:00
        2017-02-13T10:00:30.25-05:30  | 2017-02-13T10:00:30.250-05:30
        2999-12-31T23:59:59+14:00     | 2999-12-31T23:59:59+14:00
        """)
    void aDateTimeWithAnOffsetIsPresentInItsOffset(String dateTime, String read) {
        assertThat(ReceivedFields.dateTime(field("\"" + dateTime + "\"")))
                .isEqualTo(SourceField.present(OffsetDateTime.parse(read)));
    }

    /** The offset a date-time came in is kept, not turned to another naming the same instant. */
    @Test
    void aDateTimeKeepsItsOwnOffset() {
        assertThat(ReceivedFields.dateTime(field("\"2017-02-13T10:00:00+08:00\"")).value()
                .getOffset()).isEqualTo(ZoneOffset.ofHours(8));
    }

    @Test
    void aDateTimeWithNoKeyIsMissing() {
        assertThat(ReceivedFields.dateTime(NO_KEY)).isEqualTo(SourceField.missing());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"  \""})
    void aNullOrBlankDateTimeIsMissing(String json) {
        assertThat(ReceivedFields.dateTime(field(json))).isEqualTo(SourceField.missing());
    }

    /**
     * A date-time must be ISO-8601 with an offset, on a day the calendar has at a time the clock
     * has: a date alone, one with no offset, no such day or time, an offset past 18 hours, another
     * year's spelling, a lower-case {@code t} or {@code z}, a padded one or another spelling is
     * invalid.
     */
    @ParameterizedTest
    @ValueSource(strings = {"2017-02-13", "2017-02-13T10:00", "2017-02-15T10:00:00",
        "2017-02-30T10:00:00+08:00", "2017-02-13T24:00:00+08:00", "2017-02-13T10:60:00Z",
        "2017-02-13T10:00:00+19:00", "+10000-01-01T00:00:00Z", "2017-02-13t10:00:00z",
        " 2017-02-13T10:00:00Z", "2017-02-13 10:00:00+08:00", "2017-02-13T10:00:00+0800",
        "13 Feb 2017 10:00", "2017-02-13T10:00:00+08:00[Asia/Singapore]"})
    void aStringThatIsNoDateTimeWithAnOffsetIsInvalid(String dateTime) {
        assertThat(ReceivedFields.dateTime(field("\"" + dateTime + "\"")))
                .isEqualTo(SourceField.invalid());
    }

    @ParameterizedTest
    @ValueSource(strings = {"20170213100000", "true", "{}", "[\"2017-02-13T10:00:00Z\"]"})
    void aDateTimeOfAnotherJsonTypeIsInvalid(String json) {
        assertThat(ReceivedFields.dateTime(field(json))).isEqualTo(SourceField.invalid());
    }
}
