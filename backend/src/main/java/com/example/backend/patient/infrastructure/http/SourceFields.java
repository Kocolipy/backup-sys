package com.example.backend.patient.infrastructure.http;

import com.example.backend.patient.domain.SourceCode;
import com.example.backend.patient.infrastructure.http.PatientSourceException.Failure;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * How an entry of a history the patient source pages and the service reads strictly — a
 * Medication Record — reads its flat fields: a closed set's code, a date-time with its offset, and
 * text an entry must have, each failure named by the history's own {@link Failure}, quoting no
 * value. Optional free text needs no reading: it is the source's string, or {@code null} when the
 * source sent none. A history shown as received (ADR 0020), the FFI, the NUMC or the ARMC
 * History, reads its fields by {@link ReceivedFields} instead.
 */
final class SourceFields {

    private SourceFields() {
    }

    /** The member of {@code set} {@code code} spells: {@code none} without one, {@code unknown} for another. */
    static <E extends Enum<E> & SourceCode> E coded(Class<E> set, String code, Failure none,
            Failure unknown) {
        if (code == null) {
            throw PatientSourceException.failed(none);
        }
        return SourceCode.of(set, code).orElseThrow(() -> PatientSourceException.failed(unknown));
    }

    /**
     * The date-time {@code dateTime} is, which must be there and be ISO-8601 with an offset,
     * {@code Z} among them: one with no offset, or no time the calendar has, is
     * {@code notADateTime}.
     */
    static OffsetDateTime dateTime(String dateTime, Failure none, Failure notADateTime) {
        if (dateTime == null) {
            throw PatientSourceException.failed(none);
        }
        try {
            return OffsetDateTime.parse(dateTime);
        } catch (DateTimeParseException noDateTimeWithAnOffset) {
            throw PatientSourceException.failed(notADateTime);
        }
    }

    /**
     * The text {@code name} is — a name, or other free text an entry must have — as the source
     * gives it: one missing or blank is {@code none}.
     */
    static String named(String name, Failure none) {
        if (name == null || name.isBlank()) {
            throw PatientSourceException.failed(none);
        }
        return name;
    }
}
