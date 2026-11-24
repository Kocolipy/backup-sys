package com.example.backend.patient.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * The filters narrowing an HSP History (/docs/domain/hsp.md, "Sort and filters"): any of the
 * Outcomes and Submitting MCs, one Status, and a range for the Start Date and for the End Date,
 * each a range of calendar dates over its declared column ({@link HspHistory#DATE_COLUMNS}). Each
 * is optional — an empty set of codes, {@link DateRange#any()} or no Status is no filter on its
 * column — and they combine with AND; the patient source applies them, and the service filters
 * nothing itself (ADR 0019).
 *
 * <p>Each set of codes is held in its closed set's own order, whatever order it was given in, so
 * the same filters always reach the source alike.
 *
 * @param outcomes      the Outcomes a record may have, any of them
 * @param startDate     the range the Start Date falls in
 * @param endDate       the range the End Date falls in
 * @param submittingMcs the Submitting MCs a record may come from, any of them
 * @param status        the Status a record has, or {@code null} for either
 */
public record HspFilter(Set<HspOutcome> outcomes, DateRange<LocalDate> startDate,
        DateRange<LocalDate> endDate, Set<MedicalCentre> submittingMcs, HspStatus status) {

    /** No filter: every HSP Record of the patient. */
    public static final HspFilter NONE = new HspFilter(Set.of(), DateRange.any(),
            DateRange.any(), Set.of(), null);

    public HspFilter {
        outcomes = ClosedSets.inSetOrder(outcomes);
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        submittingMcs = ClosedSets.inSetOrder(submittingMcs);
    }
}
