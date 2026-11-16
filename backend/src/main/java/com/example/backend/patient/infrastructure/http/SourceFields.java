package com.example.backend.patient.infrastructure.http;

import com.example.backend.patient.domain.SourceCode;
import com.example.backend.patient.infrastructure.http.PatientSourceException.Failure;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * How an entry of a history the patient source pages and the service reads strictly, an ARMC
 * Board, reads its flat fields: a closed set's code, a calendar date, required or optional, and
 * text an entry must have, each failure named by the history's own {@link Failure}, quoting no
 * value. A date is required unless its history says otherwise: an ARMC Board's End Date is the
 * one optional date. A history shown as received (ADR 0020), the FFI, the NUMC or the Medication
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

    /** The calendar date {@code date} is, which must be there and to the day. */
    static LocalDate day(String date, Failure none, Failure notToTheDay) {
        if (date == null) {
            throw PatientSourceException.failed(none);
        }
        return toTheDay(date, notToTheDay);
    }

    /**
     * The calendar date {@code date} is, to the day, or {@code null} when the source sent none:
     * an optional date's absence is no failure.
     */
    static LocalDate optionalDay(String date, Failure notToTheDay) {
        return date == null ? null : toTheDay(date, notToTheDay);
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

    private static LocalDate toTheDay(String date, Failure notToTheDay) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException notADay) {
            throw PatientSourceException.failed(notToTheDay);
        }
    }
}
