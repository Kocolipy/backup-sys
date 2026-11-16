package com.example.backend.patient.domain;

import java.util.List;
import java.util.Objects;

/**
 * A Patient's official name. {@code text} is authoritative: it is the name as the person writes
 * it, shown as received (ADR 0020) — present, missing or in the wrong format — and never rebuilt
 * from the split into {@code family} and {@code given} names, which is optional, because many
 * Singapore names do not split cleanly into the two.
 *
 * @param text   the whole name, as the source sent it
 * @param family the family name, or {@code null} when the name is not split
 * @param given  the given names in order; empty when the name is not split
 */
public record PatientName(SourceField<String> text, String family, List<String> given) {

    public PatientName {
        Objects.requireNonNull(text, "text");
        given = given == null ? List.of() : List.copyOf(given);
    }

    /** An official name whose {@code text} the source sent as it should. */
    public PatientName(String text, String family, List<String> given) {
        this(SourceField.present(Objects.requireNonNull(text, "text")), family, given);
    }
}
