package com.example.backend.patient.domain;

import java.time.LocalDate;

/**
 * A range of calendar dates a history read through the patient source narrows by: from {@code
 * from} to {@code to}, both inclusive, either end optional. A range one day long is a from-date
 * equal to its to-date; a from-date after its to-date names no day and is refused.
 *
 * @param from the first day in the range, or {@code null} for a range open before
 * @param to   the last day in the range, or {@code null} for a range open after
 */
public record DateRange(LocalDate from, LocalDate to) {

    /** No range: every date, and none. */
    public static final DateRange ANY = new DateRange(null, null);

    public DateRange {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "A date range's from-date may not be after its to-date");
        }
    }
}
