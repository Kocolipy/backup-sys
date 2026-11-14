package com.example.backend.patient.domain;

/**
 * A range a history read through the patient source narrows one of its date columns by, at the
 * column's precision ({@link DatePrecision}): from {@code from} to {@code to}, both inclusive,
 * either end optional. A range one day long — or, over a date-time, one minute long — is a
 * from-end equal to its to-end; a from-end after its to-end names no time and is refused.
 *
 * @param from the first day or minute in the range, or {@code null} for a range open before
 * @param to   the last day or minute in the range, or {@code null} for a range open after
 * @param <T>  the value each end holds: a calendar date or a date-time to the minute
 */
public record DateRange<T extends Comparable<? super T>>(T from, T to) {

    public DateRange {
        if (from != null && to != null && from.compareTo(to) > 0) {
            throw new IllegalArgumentException(
                    "A date range's from-date may not be after its to-date");
        }
    }

    /** No range: every date, and none. */
    public static <T extends Comparable<? super T>> DateRange<T> any() {
        return new DateRange<>(null, null);
    }
}
