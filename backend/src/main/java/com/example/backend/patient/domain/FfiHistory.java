package com.example.backend.patient.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One page of a patient's FFI History, as the patient source answered it: the page's FFIs, in the
 * source's own order, and how many FFIs the patient has in all. The service pages and orders
 * nothing itself (ADR 0019).
 *
 * @param totalResults how many FFIs the patient has, every page together
 * @param ffis         the page's FFIs, in the source's order
 */
public record FfiHistory(int totalResults, List<Ffi> ffis) {

    /** An FFI's Issue Date: a calendar date, the source's {@code issue-date}. */
    public static final HistoryColumn<LocalDate> ISSUE_DATE =
            new HistoryColumn<>("issueDate", "issue-date", DatePrecision.CALENDAR_DATE);

    /** An FFI's Expiry Date: a calendar date, the source's {@code expiry-date}. */
    public static final HistoryColumn<LocalDate> EXPIRY_DATE =
            new HistoryColumn<>("expiryDate", "expiry-date", DatePrecision.CALENDAR_DATE);

    /** The date columns an FFI History filters and sorts by ({@link HistoryColumn}). */
    public static final List<HistoryColumn<?>> DATE_COLUMNS = List.of(ISSUE_DATE, EXPIRY_DATE);

    public FfiHistory {
        if (totalResults < 0) {
            throw new IllegalArgumentException("A patient has no fewer than no FFIs");
        }
        ffis = List.copyOf(Objects.requireNonNull(ffis, "ffis"));
    }
}
