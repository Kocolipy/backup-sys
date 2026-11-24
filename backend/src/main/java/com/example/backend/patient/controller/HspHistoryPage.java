package com.example.backend.patient.controller;

import com.example.backend.patient.domain.HspHistory;
import com.example.backend.patient.domain.HspRecord;
import java.time.LocalDate;
import java.util.List;

/**
 * One page of a patient's HSP History, as {@code GET /api/patient-hsp/{patientId}} answers it in
 * plain JSON — the application API's own shape, not FHIR — beside the paged reads'
 * {@code totalResults} and {@code startIndex} (ADR 0017's vocabulary, ADR 0019's source).
 *
 * @param totalResults how many of the patient's HSP Records the filters select, every page
 *                     together, as the patient source counted them
 * @param startIndex   the one-based index of the page's first record, as the page was asked for
 * @param records      the page's HSP Records, in the source's order
 */
public record HspHistoryPage(int totalResults, int startIndex, List<Row> records) {

    /** The page {@code history} answered for the page starting at {@code startIndex}. */
    static HspHistoryPage of(HspHistory history, int startIndex) {
        return new HspHistoryPage(history.totalResults(), startIndex,
                history.records().stream().map(Row::of).toList());
    }

    /**
     * One HSP Record as received (ADR 0020): each field the source sent, each closed set's code as
     * it came — known to its set or not — and its dates as {@code YYYY-MM-DD}. A field the source
     * left out, sent blank or sent in the wrong format is {@code null}, and {@code invalid} names
     * those whose format was wrong: a {@code null} field it does not name is missing. The SPA names
     * each known code with its label.
     *
     * @param invalid the fields whose format was wrong, in the row's order; none when each field
     *                is present or missing
     */
    public record Row(String id, String outcome, String startDate, String endDate,
            String submittingMc, String status, List<String> invalid) {

        static Row of(HspRecord hsp) {
            return new Row(
                    hsp.id().value(),
                    hsp.outcome().value(),
                    hsp.startDate().map(LocalDate::toString).value(),
                    hsp.endDate().map(LocalDate::toString).value(),
                    hsp.submittingMc().value(),
                    hsp.status().value(),
                    hsp.invalid());
        }

        /** An HSP Record is Restricted data: printed, it names the record by id alone. */
        @Override
        public String toString() {
            return "Row[id=" + id + "]";
        }
    }
}
