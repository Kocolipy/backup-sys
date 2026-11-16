package com.example.backend.patient.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One FFI (Fit for Instruction) in a patient's FFI History, as the patient source holds it
 * (/docs/domain/ffi.md, ADR 0019). Read from the source on every read, never held. Every field is
 * required.
 *
 * <p>Printed by its id alone: an FFI is the patient's Restricted data, so neither a record nor a
 * message may carry what it says.
 *
 * @param id              the FFI's id at the source, in FHIR's id grammar
 * @param type            its FFI Type
 * @param outcome         its Outcome
 * @param issueDate       its Issue Date, a calendar date
 * @param expiryDate      its Expiry Date, a calendar date; nothing is derived from it
 * @param higherAuthority its Higher Authority
 * @param submittingMc    its Submitting MC
 * @param status          its Status, the source's own
 */
public record Ffi(String id, FfiType type, FfiOutcome outcome, LocalDate issueDate,
        LocalDate expiryDate, FfiHigherAuthority higherAuthority, MedicalCentre submittingMc,
        FfiStatus status) {

    public Ffi {
        if (!FhirId.matches(id)) {
            throw new IllegalArgumentException("An FFI's id must match the FHIR id grammar");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(expiryDate, "expiryDate");
        Objects.requireNonNull(higherAuthority, "higherAuthority");
        Objects.requireNonNull(submittingMc, "submittingMc");
        Objects.requireNonNull(status, "status");
    }

    @Override
    public String toString() {
        return "Ffi[" + id + "]";
    }
}
