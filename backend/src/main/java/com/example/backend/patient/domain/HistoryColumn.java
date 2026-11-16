package com.example.backend.patient.domain;

import java.util.Objects;

/**
 * A date column a history read through the patient source declares (/docs/domain/ffi.md, "Sort
 * and filters"), each its own range filter and a column the history sorts by. The shared sort
 * and filter read and send a column only through its declaration, so none of them names a
 * history's column.
 *
 * @param name            the column as our API names it: its range is {@code <name>From} and
 *                        {@code <name>To}, and a sort by it {@code sortBy=<name>}
 * @param searchParameter the patient source's search parameter for the column, which its range
 *                        and its sort are sent as
 * @param precision       how finely the column is written: a calendar date or a date-time to the
 *                        minute
 * @param <T>             the value an end of a range over the column holds
 */
public record HistoryColumn<T extends Comparable<? super T>>(String name, String searchParameter,
        DatePrecision<T> precision) {

    public HistoryColumn {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(searchParameter, "searchParameter");
        Objects.requireNonNull(precision, "precision");
    }
}
