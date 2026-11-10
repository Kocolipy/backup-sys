package com.example.backend.audit.domain;

/**
 * A filter a {@code PATIENT_SEARCH} narrowed by: which of a Patient search's filters were used,
 * the one thing about them the audit trail records, beside the query's kind in the filter shape.
 * A closed set, as every classification crossing the {@link AuditTrail} is: a filter's value — a
 * gender, a date of birth, a date of a visit — is Restricted data about whom the search was for,
 * and is never recorded (/docs/domain/patient.md, "Patient reads are not audited").
 *
 * <p>Declared in the order the shape lists them, each rendered as a comparison whose value is
 * {@code ?}, as a SCIM filter's shape is ({@link AuditFilterShape}).
 */
public enum AuditPatientSearchFilter {

    /** A gender, male or female. */
    GENDER("gender eq ?"),

    /** The earliest date of birth. */
    BIRTH_DATE_FROM("birthDate ge ?"),

    /** The latest date of birth. */
    BIRTH_DATE_TO("birthDate le ?"),

    /** The earliest Last visit. */
    LAST_VISIT_FROM("lastVisit ge ?"),

    /** The latest Last visit. */
    LAST_VISIT_TO("lastVisit le ?");

    private final String shape;

    AuditPatientSearchFilter(String shape) {
        this.shape = shape;
    }

    /** The comparison the shape lists this filter as, its value {@code ?}. */
    String shape() {
        return shape;
    }
}
