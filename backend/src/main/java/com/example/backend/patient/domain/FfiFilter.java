package com.example.backend.patient.domain;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The filters narrowing an FFI History (/docs/domain/ffi.md, "Sort and filters"): any of the
 * FFI Types, Outcomes, Higher Authorities and Submitting MCs, one Status, and a range for the
 * Issue Date and for the Expiry Date. Each is optional — an empty set of codes, {@link
 * DateRange#ANY} or no Status is no filter on its column — and they combine with AND; the patient
 * source applies them, and the service filters nothing itself (ADR 0019).
 *
 * <p>Each set of codes is held in its closed set's own order, whatever order it was given in, so
 * the same filters always reach the source alike.
 *
 * @param types             the FFI Types an FFI may have, any of them
 * @param outcomes          the Outcomes an FFI may have, any of them
 * @param issueDate         the range the Issue Date falls in
 * @param expiryDate        the range the Expiry Date falls in
 * @param higherAuthorities the Higher Authorities an FFI may answer to, any of them
 * @param submittingMcs     the Submitting MCs an FFI may come from, any of them
 * @param status            the Status an FFI has, or {@code null} for either
 */
public record FfiFilter(Set<FfiType> types, Set<FfiOutcome> outcomes, DateRange issueDate,
        DateRange expiryDate, Set<FfiHigherAuthority> higherAuthorities,
        Set<FfiSubmittingMc> submittingMcs, FfiStatus status) {

    /** No filter: every FFI of the patient. */
    public static final FfiFilter NONE = new FfiFilter(Set.of(), Set.of(), DateRange.ANY,
            DateRange.ANY, Set.of(), Set.of(), null);

    public FfiFilter {
        types = inSetOrder(types);
        outcomes = inSetOrder(outcomes);
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(expiryDate, "expiryDate");
        higherAuthorities = inSetOrder(higherAuthorities);
        submittingMcs = inSetOrder(submittingMcs);
    }

    /** {@code codes}, none for {@code null}, as an unmodifiable copy in the enum's own order. */
    private static <E extends Enum<E>> Set<E> inSetOrder(Set<E> codes) {
        return codes == null ? Set.of() : Collections.unmodifiableSortedSet(new TreeSet<>(codes));
    }
}
