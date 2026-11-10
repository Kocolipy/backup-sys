package com.example.backend.audit.domain;

/**
 * Which search a {@code PATIENT_SEARCH} ran: the one thing about a Patient search's query the
 * audit trail records, as its filter shape. A closed set, as every classification crossing the
 * {@link AuditTrail} is: the query itself — a name or an NRIC — is Restricted data and is never
 * recorded (/docs/domain/patient.md, "Patient reads are not audited").
 */
public enum AuditPatientQueryKind {

    /** An exact search by a full NRIC. */
    NRIC,

    /** A search by name. */
    NAME
}
