package com.example.backend.patient.domain;

import java.util.Objects;

/**
 * The contact a Patient names as their next of kin, each detail shown as received (ADR 0020) —
 * present, missing or in the wrong format. Restricted: {@link #toString()} prints none of it.
 *
 * @param name         the contact's name as written
 * @param relationship how they are related to the Patient, as written ({@code Mother})
 * @param phone        their phone
 */
public record NextOfKin(SourceField<String> name, SourceField<String> relationship,
        SourceField<String> phone) {

    public NextOfKin {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(relationship, "relationship");
        Objects.requireNonNull(phone, "phone");
    }

    @Override
    public String toString() {
        return "NextOfKin[redacted]";
    }
}
