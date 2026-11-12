package com.example.backend.patient.controller;

import com.example.backend.patient.domain.Ffi;
import com.example.backend.patient.domain.FfiHistory;
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
     * One FFI: its id, each closed set's member as its code, and its dates as
     * {@code YYYY-MM-DD}. The SPA names each code with its label.
     */
    public record Row(String id, String type, String outcome, String issueDate,
            String expiryDate, String higherAuthority, String submittingMc, String status) {

        static Row of(Ffi ffi) {
            return new Row(
                    ffi.id(),
                    ffi.type().code(),
                    ffi.outcome().code(),
                    ffi.issueDate().toString(),
                    ffi.expiryDate().toString(),
                    ffi.higherAuthority().code(),
                    ffi.submittingMc().code(),
                    ffi.status().code());
        }

        /** An FFI is Restricted data: printed, it names the FFI by id alone. */
        @Override
        public String toString() {
            return "Row[id=" + id + "]";
        }
    }
}
