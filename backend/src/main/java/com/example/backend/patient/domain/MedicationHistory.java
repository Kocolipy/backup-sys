package com.example.backend.patient.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * One page of a patient's Medication History, as the patient source answered it: the page's
 * Medication Records, in the source's order or the sort's, and how many the filters select in
 * all. The service pages, orders and filters nothing itself (ADR 0019, D7).
 *
 * @param totalResults how many Medication Records the filters select, every page together
 * @param records      the page's Medication Records, in the order the source answered
 */
public record MedicationHistory(int totalResults, List<MedicationRecord> records) {

    /**
     * A Medication Record's Start Date: a date-time to the minute, the source's
     * {@code start-date}.
     */
    public static final HistoryColumn<LocalDateTime> START_DATE =
            new HistoryColumn<>("startDate", "start-date", DatePrecision.DATE_TIME);

    /**
     * A Medication Record's End Date: a date-time to the minute, the source's {@code end-date}.
     * Every record has one.
     */
    public static final HistoryColumn<LocalDateTime> END_DATE =
            new HistoryColumn<>("endDate", "end-date", DatePrecision.DATE_TIME);

    /** The date columns a Medication History filters and sorts by ({@link HistoryColumn}). */
    public static final List<HistoryColumn<?>> DATE_COLUMNS = List.of(START_DATE, END_DATE);

    public MedicationHistory {
        if (totalResults < 0) {
            throw new IllegalArgumentException(
                    "A patient has no fewer than no Medication Records");
        }
        records = List.copyOf(Objects.requireNonNull(records, "records"));
    }
}
