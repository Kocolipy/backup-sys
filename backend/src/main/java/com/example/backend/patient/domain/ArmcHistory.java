package com.example.backend.patient.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One page of a patient's ARMC History, as the patient source answered it: the page's ARMC
 * Boards, in the source's own order, and how many the patient has in all. The service pages and
 * orders nothing itself (ADR 0019, D6).
 *
 * @param totalResults how many ARMC Boards the patient has, every page together
 * @param boards       the page's ARMC Boards, in the source's order
 */
public record ArmcHistory(int totalResults, List<ArmcBoard> boards) {

    /** An ARMC Board's Start Date: a calendar date, the source's {@code start-date}. */
    public static final HistoryColumn<LocalDate> START_DATE =
            new HistoryColumn<>("startDate", "start-date", DatePrecision.CALENDAR_DATE);

    /**
     * An ARMC Board's End Date: a calendar date, the source's {@code end-date}. A board still
     * open has none, and the source leaves it out of any End Date range.
     */
    public static final HistoryColumn<LocalDate> END_DATE =
            new HistoryColumn<>("endDate", "end-date", DatePrecision.CALENDAR_DATE);

    /** The date columns an ARMC History filters and sorts by ({@link HistoryColumn}). */
    public static final List<HistoryColumn<?>> DATE_COLUMNS = List.of(START_DATE, END_DATE);

    public ArmcHistory {
        if (totalResults < 0) {
            throw new IllegalArgumentException("A patient has no fewer than no ARMC Boards");
        }
        boards = List.copyOf(Objects.requireNonNull(boards, "boards"));
    }
}
