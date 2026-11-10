package com.example.backend.patient.domain;

import java.util.Objects;
import java.util.Set;

/**
 * The filters narrowing a Patient search (/docs/domain/patient.md, "Patient search"): a gender,
 * and a range for the date of birth and for the Last visit. They narrow the query they are
 * asked with, and combine with AND; the patient source applies them, and the service filters
 * nothing itself (ADR 0018).
 *
 * <p>A gender filter is male or female: a Patient whose gender is other or unknown is found only
 * with no gender filter. A Patient with no Last visit is outside every Last visit range but
 * {@link DateRange#ANY}, as the source matches a date it does not hold.
 *
 * @param gender    {@link AdministrativeGender#MALE} or {@link AdministrativeGender#FEMALE}, or
 *                  {@code null} for every gender
 * @param birthDate the range the date of birth falls in
 * @param lastVisit the range the Last visit falls in
 */
public record PatientFilter(AdministrativeGender gender, DateRange birthDate, DateRange lastVisit) {

    /** No filter: every Patient the query matches. */
    public static final PatientFilter NONE = new PatientFilter(null, DateRange.ANY, DateRange.ANY);

    private static final Set<AdministrativeGender> FILTERABLE =
            Set.of(AdministrativeGender.MALE, AdministrativeGender.FEMALE);

    private static final String GENDERS = "A gender filter is male or female";

    public PatientFilter {
        if (gender != null && !FILTERABLE.contains(gender)) {
            throw new IllegalArgumentException(GENDERS);
        }
        Objects.requireNonNull(birthDate, "birthDate");
        Objects.requireNonNull(lastVisit, "lastVisit");
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
