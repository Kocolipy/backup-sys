package com.example.backend.patient.domain;

import java.util.regex.Pattern;

/**
 * FHIR's id grammar, {@code [A-Za-z0-9\-.]{1,64}}: letters, digits, {@code -} and {@code .}, 1
 * to 64 of them. Every id the patient source hands out is held to it, a Patient's
 * ({@link PatientId}) among them.
 */
public final class FhirId {

    private static final Pattern GRAMMAR = Pattern.compile("[A-Za-z0-9\\-.]{1,64}");

    private FhirId() {
    }

    /** Whether {@code value} is in FHIR's id grammar; {@code null} is not. */
    public static boolean matches(String value) {
        return value != null && GRAMMAR.matcher(value).matches();
    }
}
