package com.example.backend.observability;

/**
 * A read of the in-house patient source that shows its data as received (ADR 0020), as its
 * invalid fields are reported ({@link InvalidSourceFields}): the closed set of the {@code read}
 * tag and of {@link InvalidSourceFields#READ}. A read joins it when it adopts the rule.
 */
public enum SourceRead {

    /** A patient's FFI History. */
    FFI("ffi"),

    /** A patient's NUMC History. */
    NUMC("numc"),

    /** A patient's Medication History. */
    MEDICATION("medication");

    private final String tag;

    SourceRead(String tag) {
        this.tag = tag;
    }

    /** How the record and the counter spell the read. */
    public String tag() {
        return tag;
    }
}
