package com.example.backend.patient.domain;

import static java.util.Map.entry;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * One Medication Record in a patient's Medication History: one prescription, with how it was
 * dispensed and who prescribed it, as the patient source holds it (/docs/domain/medication.md,
 * ADR 0019, D7). Read from the source on every read, never held, and shown as received (ADR
 * 0020): each element is a {@link SourceField}, present, missing or invalid, and none is refused
 * for what it holds — an id outside FHIR's grammar, a code outside its closed set or an End Date
 * before the Start Date included.
 *
 * <p>The free-text elements are as the source gives them: nothing here reads a Duration, a
 * Quantity or a Dosage, and the Duration is never worked out from the dates.
 *
 * <p>Printed by its id alone: a Medication Record is the patient's Restricted data, so neither a
 * record nor a message may carry what it says.
 *
 * @param id                the record's id at the source, never checked
 * @param medication        its Medication, free text
 * @param dosage            its Dosage, free text
 * @param route             its Route, free text
 * @param frequency         its Frequency, free text
 * @param step              its Step, free text
 * @param quantity          its Quantity, free text
 * @param dispenseStatus    its Dispense Status's code, known to {@link DispenseStatus} or not
 * @param duration          its Duration, free text
 * @param startDate         its Start Date, a date-time with its offset
 * @param endDate           its End Date, a date-time with its offset
 * @param dispensary        its Dispensary's code, known to {@link MedicalCentre} or not
 * @param prescribingDoctor its Prescribing Doctor, a name as the source gives it, linked to no
 *                          User
 */
public record MedicationRecord(SourceField<String> id, SourceField<String> medication,
        SourceField<String> dosage, SourceField<String> route, SourceField<String> frequency,
        SourceField<String> step, SourceField<String> quantity,
        SourceField<String> dispenseStatus, SourceField<String> duration,
        SourceField<OffsetDateTime> startDate, SourceField<OffsetDateTime> endDate,
        SourceField<String> dispensary, SourceField<String> prescribingDoctor)
        implements ReceivedRecord {

    public MedicationRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(medication, "medication");
        Objects.requireNonNull(dosage, "dosage");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(dispenseStatus, "dispenseStatus");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        Objects.requireNonNull(dispensary, "dispensary");
        Objects.requireNonNull(prescribingDoctor, "prescribingDoctor");
    }

    @Override
    public List<String> invalid() {
        return SourceField.invalidAmong(entry("id", id), entry("medication", medication),
                entry("dosage", dosage), entry("route", route), entry("frequency", frequency),
                entry("step", step), entry("quantity", quantity),
                entry("dispenseStatus", dispenseStatus), entry("duration", duration),
                entry("startDate", startDate), entry("endDate", endDate),
                entry("dispensary", dispensary), entry("prescribingDoctor", prescribingDoctor));
    }

    @Override
    public String toString() {
        return "MedicationRecord[" + id.orElse("no id") + "]";
    }
}
