package com.example.backend.patient.domain;

/**
 * A sort's direction, spelled in full: the one a history read through the patient source is
 * ordered in by its one sorted column.
 */
public enum SortOrder {
    ASCENDING("ascending"),
    DESCENDING("descending");

    private final String spelled;

    SortOrder(String spelled) {
        this.spelled = spelled;
    }

    /**
     * The direction {@code text} spells, exactly, or {@code null} for a blank one.
     *
     * @throws IllegalArgumentException for any other spelling
     */
    public static SortOrder of(String text) {
        return Spelling.spelledAs(values(), text, order -> order.spelled,
                "A sort order is ascending or descending");
    }
}
