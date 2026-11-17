package com.example.backend.patient.infrastructure.http;

import com.example.backend.patient.domain.SourceField;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.function.Function;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * How a read that shows the patient source's data as received (ADR 0020) reads each field of a
 * record: bound as the raw JSON it arrived as, so that Jackson's coercion of a number into a
 * string cannot hide a wrong type, and classified rather than refused ({@link SourceField}):
 *
 * <ul>
 *   <li><b>missing</b>: no key, {@code null}, or a string that is empty or only whitespace;
 *   <li><b>invalid</b>: any other JSON type than a string — a number, a boolean, an object or an
 *       array — or a string that is no value of the field's kind;
 *   <li><b>present</b>: anything else, as it came, untrimmed.
 * </ul>
 *
 * <p>Nothing of a value's meaning is checked: a date in the future is present. A code of a closed
 * set is text, kept as the source sent it, known to the set or not.
 */
final class ReceivedFields {

    /** A calendar date's shape, {@code YYYY-MM-DD}: a four-digit year, unsigned. */
    private static final Pattern CALENDAR_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    /**
     * A date-time with an offset's shape, as the SPA reads it too: a calendar date's, {@code T},
     * the hour and minute, seconds and a fraction where given, and {@code Z} or {@code ±hh:mm}.
     */
    private static final Pattern OFFSET_DATE_TIME = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d{1,9})?)?(Z|[+-]\\d{2}:\\d{2})");

    private ReceivedFields() {
    }

    /** Text — an id, a code, free text — as the source sent it. */
    static SourceField<String> text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return SourceField.missing();
        }
        if (!node.isString()) {
            return SourceField.invalid();
        }
        String text = node.stringValue();
        return text.isBlank() ? SourceField.missing() : SourceField.present(text);
    }

    /**
     * A calendar date, {@code YYYY-MM-DD} and a day the calendar has: text that is no such date
     * is invalid.
     */
    static SourceField<LocalDate> day(JsonNode node) {
        return parsed(node, CALENDAR_DATE, LocalDate::parse);
    }

    /**
     * A date-time with its offset, ISO-8601 — {@code YYYY-MM-DDTHH:mm}, seconds and their fraction
     * where given, then {@code Z} or {@code ±hh:mm} — at a time the calendar and the clock have,
     * its offset no further than 18 hours from UTC, kept as it came: text that is no such
     * date-time, one with no offset among them, is invalid.
     */
    static SourceField<OffsetDateTime> dateTime(JsonNode node) {
        return parsed(node, OFFSET_DATE_TIME, OffsetDateTime::parse);
    }

    /**
     * Text read as a value of a kind of its own: missing or invalid as the text is, invalid when
     * it is not of the kind's {@code shape} or {@code parse} refuses it, and present with what
     * {@code parse} makes of it otherwise.
     */
    private static <T> SourceField<T> parsed(JsonNode node, Pattern shape,
            Function<String, T> parse) {
        SourceField<String> text = text(node);
        if (!text.isPresent()) {
            return new SourceField<>(text.received(), null);
        }
        if (!shape.matcher(text.value()).matches()) {
            return SourceField.invalid();
        }
        try {
            return SourceField.present(parse.apply(text.value()));
        } catch (DateTimeParseException noSuchValue) {
            return SourceField.invalid();
        }
    }
}
