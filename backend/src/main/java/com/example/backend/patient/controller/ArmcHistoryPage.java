package com.example.backend.patient.controller;

import com.example.backend.patient.domain.ArmcBoard;
import com.example.backend.patient.domain.ArmcHistory;
import com.fasterxml.jackson.annotation.JsonInclude;
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
     * One ARMC Board: its id, each closed set's member as its code, its dates as
     * {@code YYYY-MM-DD} — no {@code endDate} at all for a board still open — and its two
     * Medical Officers' names as the source gave them. The SPA names each code with its label.
     */
    public record Row(String id, String boardType, String outcome, String startDate,
            @JsonInclude(JsonInclude.Include.NON_NULL) String endDate, String medicalOfficer1,
            String medicalOfficer2) {

        static Row of(ArmcBoard board) {
            LocalDate ended = board.endDate();
            return new Row(
                    board.id(),
                    board.boardType().code(),
                    board.outcome().code(),
                    board.startDate().toString(),
                    ended == null ? null : ended.toString(),
                    board.medicalOfficer1(),
                    board.medicalOfficer2());
        }

        /** An ARMC Board is Restricted data: printed, it names the board by id alone. */
        @Override
        public String toString() {
            return "Row[id=" + id + "]";
        }
    }
}
