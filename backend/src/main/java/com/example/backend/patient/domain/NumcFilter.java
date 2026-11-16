package com.example.backend.patient.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * The filters narrowing a NUMC History (/docs/domain/numc.md, "Sort and filters"): any of the
 * NUMC Types, any of the NUMC Outcomes, and a range for the Issue Date and for the Expiry Date,
 * each a range of calendar dates over its declared column ({@link NumcHistory#DATE_COLUMNS}).
 * Each is optional — an empty set of codes or {@link DateRange#any()} is no filter on its column
 * — and they combine with AND; the patient source applies them, and the service filters nothing
 * itself (ADR 0019).
 *
 * <p>Each set of codes is held in its closed set's own order, whatever order it was given in, so
 * the same filters always reach the source alike.
 *
 * @param types      the NUMC Types a NUMC Record may have, any of them
 * @param outcomes   the NUMC Outcomes a NUMC Record may have, any of them
 * @param issueDate  the range the Issue Date falls in
 * @param expiryDate the range the Expiry Date falls in
 */
public record NumcFilter(Set<NumcType> types, Set<NumcOutcome> outcomes,
        DateRange<LocalDate> issueDate, DateRange<LocalDate> expiryDate) {

    /** No filter: every NUMC Record of the patient. */
    public static final NumcFilter NONE =
            new NumcFilter(Set.of(), Set.of(), DateRange.any(), DateRange.any());

    public NumcFilter {
        types = ClosedSets.inSetOrder(types);
        outcomes = ClosedSets.inSetOrder(outcomes);
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(expiryDate, "expiryDate");
    }
}
