package com.example.backend.patient.domain;

import static java.util.Map.entry;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One NUMC Record in a patient's NUMC History: whether the patient was found fit for one
 * underwater duty, as the patient source holds it (/docs/domain/numc.md, ADR 0019, D5). Read from
 * the source on every read, never held, and shown as received (ADR 0020): each element is a
 * {@link SourceField}, present, missing or invalid, and none is refused for what it holds — an id
 * outside FHIR's grammar, a code outside its closed set or an Expiry Date before the Issue Date
 * included. Nothing is derived from the Expiry Date.
 *
 * <p>Printed by its id alone: a NUMC Record is the patient's Restricted data, so neither a record
 * nor a message may carry what it says.
 *
 * @param id         the record's id at the source, never checked
 * @param type       its NUMC Type's code, known to {@link NumcType} or not
 * @param outcome    its NUMC Outcome's code, known to {@link NumcOutcome} or not
 * @param issueDate  its Issue Date, a calendar date
 * @param expiryDate its Expiry Date, a calendar date; nothing is derived from it
 */
public record NumcRecord(SourceField<String> id, SourceField<String> type,
        SourceField<String> outcome, SourceField<LocalDate> issueDate,
        SourceField<LocalDate> expiryDate) implements ReceivedRecord {

    public NumcRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(expiryDate, "expiryDate");
    }

    @Override
    public List<String> invalid() {
        return SourceField.invalidAmong(entry("id", id), entry("type", type),
                entry("outcome", outcome), entry("issueDate", issueDate),
                entry("expiryDate", expiryDate));
    }

    @Override
    public String toString() {
        return "NumcRecord[" + id.orElse("no id") + "]";
    }
}
