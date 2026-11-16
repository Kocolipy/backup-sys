package com.example.backend.patient.domain;

import java.util.Objects;

/**
 * The contact a Patient names as their next of kin. Restricted: {@link #toString()} prints none
 * of it.
 *
 * @param name         the contact's name as written, never blank
 * @param relationship how they are related to the Patient, as written ({@code Mother}), or
 *                     {@code null} when the source says not
 * @param phone        their phone, or {@code null} when the source lists none
 */
public record NextOfKin(String name, String relationship, String phone) {

    public NextOfKin {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("A next of kin's name is required");
        }
    }

    @Override
    public String toString() {
        return "NextOfKin[redacted]";
    }
}
