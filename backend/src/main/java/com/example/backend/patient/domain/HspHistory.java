package com.example.backend.patient.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One page of a patient's HSP History, as the patient source answered it: the page's HSP Records,
 * in the source's own order, and how many of the patient's the filters select in all. The
 * service pages and orders nothing itself (ADR 0019).
 *
 * @param totalResults how many of the patient's HSP Records the filters select, every page
 *                     together
 * @param records      the page's HSP Records, in the source's order
 */
public record HspHistory(int totalResults, List<HspRecord> records) {

    /** An HSP Record's Start Date: a calendar date, the source's {@code start-date}. */
    public static final HistoryColumn<LocalDate> START_DATE =
            new HistoryColumn<>("startDate", "start-date", DatePrecision.CALENDAR_DATE);

    /** An HSP Record's End Date: a calendar date, the source's {@code end-date}. */
    public static final HistoryColumn<LocalDate> END_DATE =
            new HistoryColumn<>("endDate", "end-date", DatePrecision.CALENDAR_DATE);

    /** The date columns an HSP History filters and sorts by ({@link HistoryColumn}). */
    public static final List<HistoryColumn<?>> DATE_COLUMNS = List.of(START_DATE, END_DATE);

    public HspHistory {
        if (totalResults < 0) {
            throw new IllegalArgumentException("A patient has no fewer than no HSP Records");
        }
        records = List.copyOf(Objects.requireNonNull(records, "records"));
    }
}
