package com.example.backend.patient.infrastructure.http;

import com.example.backend.patient.domain.SourceField;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
 * set is text, kept as the source sent it, known to the set or not. A read strict about its
 * fields reads them by {@link SourceFields}.
 */
final class ReceivedFields {

    /** A calendar date's shape, {@code YYYY-MM-DD}: a four-digit year, unsigned. */
    private static final Pattern CALENDAR_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

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
        SourceField<String> text = text(node);
        if (!text.isPresent()) {
            return text.map(LocalDate::parse);
        }
        if (!CALENDAR_DATE.matcher(text.value()).matches()) {
            return SourceField.invalid();
        }
        try {
            return SourceField.present(LocalDate.parse(text.value()));
        } catch (DateTimeParseException noSuchDay) {
            return SourceField.invalid();
        }
    }
}
