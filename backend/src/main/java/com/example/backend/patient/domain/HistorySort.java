package com.example.backend.patient.domain;

/**
 * How a history read through the patient source is ordered (/docs/domain/ffi.md,
 * /docs/domain/numc.md and /docs/domain/armc.md, "Sort and filters"): an FFI History or a NUMC
 * History by the Issue Date or by the Expiry Date ({@link IssueOrExpiry}), an ARMC History by
 * the Start Date or by the End Date ({@link StartOrEnd}), one column at a time, in either
 * direction — or, with no column named, in the patient source's own order
 * ({@link #SOURCE_ORDER}). The source sorts; the service sorts nothing itself (ADR 0019).
 *
 * @param field the column sorted by, or {@code null} for the source's own order
 * @param order the direction, or {@code null} for the source's own order
 */
public record HistorySort(Column field, SortOrder order) {

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
    public static HistorySort of(Column field, SortOrder order) {
        if (field == null) {
            return SOURCE_ORDER;
        }
        return new HistorySort(field, order == null ? SortOrder.ASCENDING : order);
    }

    /** A column a history sorts by: each history's own set of them. */
    public sealed interface Column permits IssueOrExpiry, StartOrEnd {
    }

    /** A column an FFI or a NUMC History sorts by, spelled as the record on our API names its field. */
    public enum IssueOrExpiry implements Column {
        ISSUE_DATE("issueDate"),
        EXPIRY_DATE("expiryDate");

        private final String spelled;

        IssueOrExpiry(String spelled) {
            this.spelled = spelled;
        }

        /**
         * The column {@code text} spells, exactly, or {@code null} for a blank one.
         *
         * @throws IllegalArgumentException for any other spelling
         */
        public static IssueOrExpiry of(String text) {
            return Spelling.spelledAs(values(), text, field -> field.spelled,
                    "A history sorts by issueDate or expiryDate");
        }
    }

    /** A column an ARMC History sorts by, spelled as an ARMC Board on our API names its field. */
    public enum StartOrEnd implements Column {
        START_DATE("startDate"),
        END_DATE("endDate");

        private final String spelled;

        StartOrEnd(String spelled) {
            this.spelled = spelled;
        }

        /**
         * The column {@code text} spells, exactly, or {@code null} for a blank one.
         *
         * @throws IllegalArgumentException for any other spelling
         */
        public static StartOrEnd of(String text) {
            return Spelling.spelledAs(values(), text, field -> field.spelled,
                    "An ARMC History sorts by startDate or endDate");
        }
    }
}
