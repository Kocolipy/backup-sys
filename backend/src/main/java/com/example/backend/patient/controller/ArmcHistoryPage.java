package com.example.backend.patient.controller;

import com.example.backend.patient.domain.ArmcBoard;
import com.example.backend.patient.domain.ArmcHistory;
import java.time.LocalDate;
import java.util.List;

/**
 * One page of a patient's ARMC History, as {@code GET /api/patient-armc/{patientId}} answers it in
 * plain JSON — the application API's own shape, not FHIR — beside the paged reads'
 * {@code totalResults} and {@code startIndex} (ADR 0017's vocabulary, ADR 0019's source, D6).
 *
 * @param totalResults how many ARMC Boards the patient has, as the patient source counted them
 * @param startIndex   the one-based index of the page's first board, as the page was asked for
 * @param records      the page's ARMC Boards, in the source's order
 */
public record ArmcHistoryPage(int totalResults, int startIndex, List<Row> records) {

    /** The page {@code history} answered for the page starting at {@code startIndex}. */
    static ArmcHistoryPage of(ArmcHistory history, int startIndex) {
        return new ArmcHistoryPage(history.totalResults(), startIndex,
                history.boards().stream().map(Row::of).toList());
    }

    /**
     * One ARMC Board as received (ADR 0020): each field the source sent, each closed set's code
     * as it came — known to its set or not — its dates as {@code YYYY-MM-DD} and its two Medical
     * Officers' names as the source gave them. A field the source left out, sent blank or sent in
     * the wrong format is {@code null} — the End Date of a board still open among them — and
     * {@code invalid} names those whose format was wrong: a {@code null} field it does not name
     * is missing. The SPA names each known code with its label.
     *
     * @param invalid the fields whose format was wrong, in the row's order; none when each field
     *                is present or missing
     */
    public record Row(String id, String boardType, String outcome, String startDate,
            String endDate, String medicalOfficer1, String medicalOfficer2,
            List<String> invalid) {

        static Row of(ArmcBoard board) {
            return new Row(
                    board.id().value(),
                    board.boardType().value(),
                    board.outcome().value(),
                    board.startDate().map(LocalDate::toString).value(),
                    board.endDate().map(LocalDate::toString).value(),
                    board.medicalOfficer1().value(),
                    board.medicalOfficer2().value(),
                    board.invalid());
        }

        /** An ARMC Board is Restricted data: printed, it names the board by id alone. */
        @Override
        public String toString() {
            return "Row[id=" + id + "]";
        }
    }
}
