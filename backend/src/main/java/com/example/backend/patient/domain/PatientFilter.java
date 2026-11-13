package com.example.backend.patient.domain;

import java.util.Set;

/**
 * The filter narrowing a Patient search (/docs/domain/patient.md, "Patient search"): a gender. It
 * narrows the query it is asked with; the patient source applies it, and the service filters
 * nothing itself (ADR 0018).
 *
 * <p>A gender filter is male or female: a Patient whose gender is other or unknown is found only
 * with no gender filter.
 *
 * @param gender {@link AdministrativeGender#MALE} or {@link AdministrativeGender#FEMALE}, or
 *               {@code null} for every gender
 */
public record PatientFilter(AdministrativeGender gender) {

    /** No filter: every Patient the query matches. */
    public static final PatientFilter NONE = new PatientFilter(null);

    private static final Set<AdministrativeGender> FILTERABLE =
            Set.of(AdministrativeGender.MALE, AdministrativeGender.FEMALE);

    private static final String GENDERS = "A gender filter is male or female";

    public PatientFilter {
        if (gender != null && !FILTERABLE.contains(gender)) {
            throw new IllegalArgumentException(GENDERS);
        }
    }

    /**
     * The gender filter {@code code} spells, as FHIR codes it — {@code male} or {@code female} —
     * or {@code null} for a blank one, which is no filter.
     *
     * @throws IllegalArgumentException for any other spelling
     */
    public static AdministrativeGender genderOf(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return AdministrativeGender.fromCode(code)
                .filter(FILTERABLE::contains)
                .orElseThrow(() -> new IllegalArgumentException(GENDERS));
    }
}
