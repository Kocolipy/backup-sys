package com.example.backend.patient.domain;

/**
 * How an FFI History is ordered (/docs/domain/ffi.md, "Sort and filters"): by the Issue Date or
 * by the Expiry Date, one column at a time, in either direction — or, with no column named, in
 * the patient source's own order ({@link #SOURCE_ORDER}). The source sorts, breaking ties by id;
 * the service sorts nothing itself (ADR 0019).
 *
 * @param field the column sorted by, or {@code null} for the source's own order
 * @param order the direction, or {@code null} for the source's own order
 */
public record FfiSort(Field field, SortOrder order) {

    /** No column named: the source's own order, for which no sort is asked of it. */
    public static final FfiSort SOURCE_ORDER = new FfiSort(null, null);

    public FfiSort {
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
    public static FfiSort of(Field field, SortOrder order) {
        if (field == null) {
            return SOURCE_ORDER;
        }
        return new FfiSort(field, order == null ? SortOrder.ASCENDING : order);
    }

    /** A column an FFI History sorts by, spelled as an FFI on our API names its field. */
    public enum Field {
        ISSUE_DATE("issueDate"),
        EXPIRY_DATE("expiryDate");

        private final String spelled;

        Field(String spelled) {
            this.spelled = spelled;
        }

        /**
         * The column {@code text} spells, exactly, or {@code null} for a blank one.
         *
         * @throws IllegalArgumentException for any other spelling
         */
        public static Field of(String text) {
            return Spelling.spelledAs(values(), text, field -> field.spelled,
                    "An FFI History sorts by issueDate or expiryDate");
        }
    }
}
