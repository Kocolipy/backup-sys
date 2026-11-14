package com.example.backend.patient.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One NUMC Record in a patient's NUMC History: whether the patient was found fit for one
 * underwater duty, as the patient source holds it (/docs/domain/numc.md, ADR 0019, D5). Read from
 * the source on every read, never held. Every element is required, and nothing is derived from
 * the Expiry Date.
 *
 * <p>Printed by its id alone: a NUMC Record is the patient's Restricted data, so neither a record
 * nor a message may carry what it says.
 *
 * @param id         the record's id at the source, in FHIR's id grammar
 * @param type       its NUMC Type
 * @param outcome    its NUMC Outcome
 * @param issueDate  its Issue Date, a calendar date
 * @param expiryDate its Expiry Date, a calendar date; nothing is derived from it
 */
public record NumcRecord(String id, NumcType type, NumcOutcome outcome, LocalDate issueDate,
        LocalDate expiryDate) {

    public NumcRecord {
        if (!FhirId.matches(id)) {
            throw new IllegalArgumentException(
                    "A NUMC Record's id must match the FHIR id grammar");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(expiryDate, "expiryDate");
    }

    @Override
    public String toString() {
        return "NumcRecord[" + id + "]";
    }
}
