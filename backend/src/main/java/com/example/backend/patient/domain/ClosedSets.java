package com.example.backend.patient.domain;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * How a history's filter holds the codes of a closed set it narrows by ({@link FfiFilter},
 * {@link NumcFilter}, {@link MedicationFilter}): in the set's own order, whatever order they were given in, so the same
 * filters always reach the source alike.
 */
final class ClosedSets {

    private ClosedSets() {
    }

    /** {@code codes}, none for {@code null}, as an unmodifiable copy in the enum's own order. */
    static <E extends Enum<E>> Set<E> inSetOrder(Set<E> codes) {
        return codes == null ? Set.of() : Collections.unmodifiableSortedSet(new TreeSet<>(codes));
    }
}
