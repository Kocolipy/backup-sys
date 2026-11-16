package com.example.backend.patient.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * The filters narrowing a Medication History (/docs/domain/medication.md, "Sort and filters"):
 * any of the Dispense Statuses, and a range for the Start Date and for the End Date, each a range
 * of date-times to the minute, Singapore time, over its declared column
 * ({@link MedicationHistory#DATE_COLUMNS}). Each is optional — an empty set of codes or
 * {@link DateRange#any()} is no filter on its column — and they combine with AND; the patient
 * source applies them, and the service filters nothing itself (ADR 0019, D7). The Dispensary and
 * the free text take no filter.
 *
 * <p>The Dispense Statuses are held in their closed set's own order, whatever order they were
 * given in, so the same filters always reach the source alike.
 *
 * @param dispenseStatuses the Dispense Statuses a Medication Record may have, any of them
 * @param startDate        the range the Start Date falls in
 * @param endDate          the range the End Date falls in
 */
public record MedicationFilter(Set<DispenseStatus> dispenseStatuses,
        DateRange<LocalDateTime> startDate, DateRange<LocalDateTime> endDate) {

    /** No filter: every Medication Record of the patient. */
    public static final MedicationFilter NONE =
            new MedicationFilter(Set.of(), DateRange.any(), DateRange.any());

    public MedicationFilter {
        dispenseStatuses = ClosedSets.inSetOrder(dispenseStatuses);
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
    }
}
