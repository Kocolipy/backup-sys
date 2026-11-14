package com.example.backend.patient.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One page of a patient's NUMC History, as the patient source answered it: the page's NUMC
 * Records, in the source's own order, and how many the patient has in all. The service pages and
 * orders nothing itself (ADR 0019, D5).
 *
 * @param totalResults how many NUMC Records the patient has, every page together
 * @param records      the page's NUMC Records, in the source's order
 */
public record NumcHistory(int totalResults, List<NumcRecord> records) {

    /** A NUMC Record's Issue Date: a calendar date, the source's {@code issue-date}. */
    public static final HistoryColumn<LocalDate> ISSUE_DATE =
            new HistoryColumn<>("issueDate", "issue-date", DatePrecision.CALENDAR_DATE);

    /** A NUMC Record's Expiry Date: a calendar date, the source's {@code expiry-date}. */
    public static final HistoryColumn<LocalDate> EXPIRY_DATE =
            new HistoryColumn<>("expiryDate", "expiry-date", DatePrecision.CALENDAR_DATE);

    /** The date columns a NUMC History filters and sorts by ({@link HistoryColumn}). */
    public static final List<HistoryColumn<?>> DATE_COLUMNS = List.of(ISSUE_DATE, EXPIRY_DATE);

    public NumcHistory {
        if (totalResults < 0) {
            throw new IllegalArgumentException("A patient has no fewer than no NUMC Records");
        }
        records = List.copyOf(Objects.requireNonNull(records, "records"));
    }
}
