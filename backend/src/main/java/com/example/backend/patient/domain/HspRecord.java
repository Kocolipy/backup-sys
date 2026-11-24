package com.example.backend.patient.domain;

import static java.util.Map.entry;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One HSP Record in a patient's HSP History, as the patient source holds it
 * (/docs/domain/hsp.md, ADR 0019). Read from the source on every read, never held, and shown as
 * received (ADR 0020): each field is a {@link SourceField}, present, missing or invalid, and none
 * is refused for what it holds — an id outside FHIR's grammar, a code outside its closed set or
 * an End Date before the Start Date included. Nothing is derived from the End Date.
 *
 * <p>Printed by its id alone: an HSP Record is the patient's Restricted data, so neither a record
 * nor a message may carry what it says.
 *
 * @param id           the record's id at the source, never checked
 * @param outcome      its Outcome's code, known to {@link HspOutcome} or not
 * @param startDate    its Start Date, a calendar date
 * @param endDate      its End Date, a calendar date; nothing is derived from it
 * @param submittingMc its Submitting MC's code, known to {@link MedicalCentre} or not
 * @param status       its Status's code, the source's own, known to {@link HspStatus} or not
 */
public record HspRecord(SourceField<String> id, SourceField<String> outcome,
        SourceField<LocalDate> startDate, SourceField<LocalDate> endDate,
        SourceField<String> submittingMc, SourceField<String> status) implements ReceivedRecord {

    public HspRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        Objects.requireNonNull(submittingMc, "submittingMc");
        Objects.requireNonNull(status, "status");
    }

    @Override
    public List<String> invalid() {
        return SourceField.invalidAmong(entry("id", id), entry("outcome", outcome),
                entry("startDate", startDate), entry("endDate", endDate),
                entry("submittingMc", submittingMc), entry("status", status));
    }

    @Override
    public String toString() {
        return "HspRecord[" + id.orElse("no id") + "]";
    }
}
