package com.example.backend.patient.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The filters narrowing an ARMC History (/docs/domain/armc.md, "Sort and filters"): one ARMC
 * Outcome, and a range for the Start Date and for the End Date, each a range of calendar dates
 * over its declared column ({@link ArmcHistory#DATE_COLUMNS}). Each is optional — no Outcome or
 * {@link DateRange#any()} is no filter on its column — and they combine with AND; the patient
 * source applies them, a board with no End Date matching no End Date range, and the service
 * filters nothing itself (ADR 0019). The ARMC Board Type takes no filter while ACES is its one
 * code.
 *
 * @param outcome   the ARMC Outcome an ARMC Board must have, or {@code null} for any
 * @param startDate the range the Start Date falls in
 * @param endDate   the range the End Date falls in
 */
public record ArmcFilter(ArmcOutcome outcome, DateRange<LocalDate> startDate,
        DateRange<LocalDate> endDate) {

    /** No filter: every ARMC Board of the patient. */
    public static final ArmcFilter NONE = new ArmcFilter(null, DateRange.any(), DateRange.any());

    public ArmcFilter {
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
    }
}
