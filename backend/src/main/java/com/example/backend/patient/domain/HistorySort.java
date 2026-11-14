package com.example.backend.patient.domain;

import java.util.List;
import java.util.stream.Collectors;

/**
 * How a history read through the patient source is ordered (/docs/domain/ffi.md, "Sort and
 * filters"): by one of the date columns the history declares ({@link HistoryColumn}) — a
 * date-time column by its whole date-time — in either direction, or, with no column named, in
 * the patient source's own order ({@link #SOURCE_ORDER}). The source sorts; the service sorts
 * nothing itself (ADR 0019).
 *
 * @param field the column sorted by, or {@code null} for the source's own order
 * @param order the direction, or {@code null} for the source's own order
 */
public record HistorySort(HistoryColumn<?> field, SortOrder order) {

    /** No column named: the source's own order, for which no sort is asked of it. */
    public static final HistorySort SOURCE_ORDER = new HistorySort(null, null);

    public HistorySort {
        if ((field == null) != (order == null)) {
            throw new IllegalArgumentException(
                    "A sort names both its column and its direction, or neither");
        }
    }

    /**
     * The sort {@code field} and {@code order} name: the {@link #SOURCE_ORDER} with no column,
     * whatever the direction — which then names a direction for no order — and ascending with a
     * column but no direction.
     */
    public static HistorySort of(HistoryColumn<?> field, SortOrder order) {
        if (field == null) {
            return SOURCE_ORDER;
        }
        return new HistorySort(field, order == null ? SortOrder.ASCENDING : order);
    }

    /**
     * The one of a history's {@code columns} that {@code text} names, spelled exactly as our API
     * names it, or {@code null} for a blank one.
     *
     * @throws IllegalArgumentException for any other spelling — another history's column among
     *     them — naming the columns the history sorts by
     */
    public static HistoryColumn<?> column(List<HistoryColumn<?>> columns, String text) {
        return Spelling.spelledAs(columns.toArray(HistoryColumn<?>[]::new), text,
                HistoryColumn::name, "A history sorts by " + columns.stream()
                        .map(HistoryColumn::name).collect(Collectors.joining(" or ")));
    }
}
