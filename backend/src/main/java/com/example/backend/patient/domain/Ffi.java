package com.example.backend.patient.domain;

import static java.util.Map.entry;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One FFI (Fit for Instruction) in a patient's FFI History, as the patient source holds it
 * (/docs/domain/ffi.md, ADR 0019). Read from the source on every read, never held, and shown as
 * received (ADR 0020): each field is a {@link SourceField}, present, missing or invalid, and none
 * is refused for what it holds — an id outside FHIR's grammar, a code outside its closed set or
 * an Expiry Date before the Issue Date included. Nothing is derived from the Expiry Date.
 *
 * <p>Printed by its id alone: an FFI is the patient's Restricted data, so neither a record nor a
 * message may carry what it says.
 *
 * @param id              the FFI's id at the source, never checked
 * @param type            its FFI Type's code, known to {@link FfiType} or not
 * @param outcome         its Outcome's code, known to {@link FfiOutcome} or not
 * @param issueDate       its Issue Date, a calendar date
 * @param expiryDate      its Expiry Date, a calendar date; nothing is derived from it
 * @param higherAuthority its Higher Authority's code, known to {@link FfiHigherAuthority} or not
 * @param submittingMc    its Submitting MC's code, known to {@link MedicalCentre} or not
 * @param status          its Status's code, the source's own, known to {@link FfiStatus} or not
 */
public record Ffi(SourceField<String> id, SourceField<String> type, SourceField<String> outcome,
        SourceField<LocalDate> issueDate, SourceField<LocalDate> expiryDate,
        SourceField<String> higherAuthority, SourceField<String> submittingMc,
        SourceField<String> status) implements ReceivedRecord {

    public Ffi {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(expiryDate, "expiryDate");
        Objects.requireNonNull(higherAuthority, "higherAuthority");
        Objects.requireNonNull(submittingMc, "submittingMc");
        Objects.requireNonNull(status, "status");
    }

    @Override
    public List<String> invalid() {
        return SourceField.invalidAmong(entry("id", id), entry("type", type),
                entry("outcome", outcome), entry("issueDate", issueDate),
                entry("expiryDate", expiryDate), entry("higherAuthority", higherAuthority),
                entry("submittingMc", submittingMc), entry("status", status));
    }

    @Override
    public String toString() {
        return "Ffi[" + id.orElse("no id") + "]";
    }
}
