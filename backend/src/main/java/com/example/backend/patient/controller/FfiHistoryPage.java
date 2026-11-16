package com.example.backend.patient.controller;

import com.example.backend.patient.domain.Ffi;
import com.example.backend.patient.domain.FfiHistory;
import java.time.LocalDate;
import java.util.List;

/**
 * One page of a patient's FFI History, as {@code GET /api/patient-ffi/{patientId}} answers it in
 * plain JSON — the application API's own shape, not FHIR — beside the paged reads'
 * {@code totalResults} and {@code startIndex} (ADR 0017's vocabulary, ADR 0019's source).
 *
 * @param totalResults how many FFIs the patient has, as the patient source counted them
 * @param startIndex   the one-based index of the page's first FFI, as the page was asked for
 * @param ffis         the page's FFIs, in the source's order
 */
public record FfiHistoryPage(int totalResults, int startIndex, List<Row> ffis) {

    /** The page {@code history} answered for the page starting at {@code startIndex}. */
    static FfiHistoryPage of(FfiHistory history, int startIndex) {
        return new FfiHistoryPage(history.totalResults(), startIndex,
                history.ffis().stream().map(Row::of).toList());
    }

    /**
     * One FFI as received (ADR 0020): each field the source sent, each closed set's code as it
     * came — known to its set or not — and its dates as {@code YYYY-MM-DD}. A field the source left
     * out, sent blank or sent in the wrong format is {@code null}, and {@code invalid} names those
     * whose format was wrong: a {@code null} field it does not name is missing. The SPA names each
     * known code with its label.
     *
     * @param invalid the fields whose format was wrong, in the row's order; none when each field
     *                is present or missing
     */
    public record Row(String id, String type, String outcome, String issueDate,
            String expiryDate, String higherAuthority, String submittingMc, String status,
            List<String> invalid) {

        static Row of(Ffi ffi) {
            return new Row(
                    ffi.id().value(),
                    ffi.type().value(),
                    ffi.outcome().value(),
                    ffi.issueDate().map(LocalDate::toString).value(),
                    ffi.expiryDate().map(LocalDate::toString).value(),
                    ffi.higherAuthority().value(),
                    ffi.submittingMc().value(),
                    ffi.status().value(),
                    ffi.invalid());
        }

        /** An FFI is Restricted data: printed, it names the FFI by id alone. */
        @Override
        public String toString() {
            return "Row[id=" + id + "]";
        }
    }
}
