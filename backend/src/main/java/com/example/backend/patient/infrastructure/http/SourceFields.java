package com.example.backend.patient.infrastructure.http;

import com.example.backend.patient.domain.SourceCode;
import com.example.backend.patient.infrastructure.http.PatientSourceException.Failure;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * How an entry of a history the patient source pages — an FFI, a NUMC Record — reads its flat
 * fields: a closed set's code and a calendar date, each required, each failure named by the
 * history's own {@link Failure}, quoting no value.
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
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException notADay) {
            throw PatientSourceException.failed(notToTheDay);
        }
    }
}
