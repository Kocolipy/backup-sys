package com.example.backend.patient.domain;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

/**
 * How a Patient search is ordered (/docs/domain/patient.md, "Patient search"): by the date of
 * birth or by the Last visit, one column at a time, in either direction, and newest Last visit
 * first when no column is named. The patient source sorts, breaking ties by id and placing a
 * Patient with no Last visit last in both directions; the service sorts nothing itself
 * (ADR 0018).
 *
 * @param field the column sorted by
 * @param order the direction
 */
public record PatientSort(Field field, Order order) {

    /** The order a search with no column named takes: newest Last visit first. */
    public static final PatientSort DEFAULT = new PatientSort(Field.LAST_VISIT, Order.DESCENDING);

    public PatientSort {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(order, "order");
    }

    /**
     * The sort {@code field} and {@code order} name: the {@link #DEFAULT} with no column, whatever
     * the direction — which then names a direction for no order — and ascending with a column but
     * no direction.
     */
    public static PatientSort of(Field field, Order order) {
        if (field == null) {
            return DEFAULT;
        }
        return new PatientSort(field, order == null ? Order.ASCENDING : order);
    }

    /** A column a Patient search sorts by, spelled as the search's answer names its field. */
    public enum Field {
        BIRTH_DATE("birthDate"),
        LAST_VISIT("lastVisit");

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
            return spelledAs(values(), text, field -> field.spelled,
                    "A Patient search sorts by birthDate or lastVisit");
        }
    }

    /** A direction, spelled in full. */
    public enum Order {
        ASCENDING("ascending"),
        DESCENDING("descending");

        private final String spelled;

        Order(String spelled) {
            this.spelled = spelled;
        }

        /**
         * The direction {@code text} spells, exactly, or {@code null} for a blank one.
         *
         * @throws IllegalArgumentException for any other spelling
         */
        public static Order of(String text) {
            return spelledAs(values(), text, order -> order.spelled,
                    "A sort order is ascending or descending");
        }
    }

    private static <E> E spelledAs(E[] values, String text,
            Function<E, String> spelling, String refusal) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return Arrays.stream(values)
                .filter(value -> spelling.apply(value).equals(text))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(refusal));
    }
}
