package com.example.backend.patient.controller;

import com.example.backend.patient.domain.NumcHistory;
import com.example.backend.patient.domain.NumcRecord;
import java.util.List;

/**
 * One page of a patient's NUMC History, as {@code GET /api/patient-numc/{patientId}} answers it in
 * plain JSON — the application API's own shape, not FHIR — beside the paged reads'
 * {@code totalResults} and {@code startIndex} (ADR 0017's vocabulary, ADR 0019's source, D5).
 *
 * @param totalResults how many NUMC Records the patient has, as the patient source counted them
 * @param startIndex   the one-based index of the page's first record, as the page was asked for
 * @param records      the page's NUMC Records, in the source's order
 */
public record NumcHistoryPage(int totalResults, int startIndex, List<Row> records) {

    /** The page {@code history} answered for the page starting at {@code startIndex}. */
    static NumcHistoryPage of(NumcHistory history, int startIndex) {
        return new NumcHistoryPage(history.totalResults(), startIndex,
                history.records().stream().map(Row::of).toList());
    }

    /**
     * One NUMC Record: its id, each closed set's member as its code, and its dates as
     * {@code YYYY-MM-DD}. The SPA names each code with its label.
     */
    public record Row(String id, String type, String outcome, String issueDate,
            String expiryDate) {

        static Row of(NumcRecord record) {
            return new Row(
                    record.id(),
                    record.type().code(),
                    record.outcome().code(),
                    record.issueDate().toString(),
                    record.expiryDate().toString());
        }

        /** A NUMC Record is Restricted data: printed, it names the record by id alone. */
        @Override
        public String toString() {
            return "Row[id=" + id + "]";
        }
    }
}
