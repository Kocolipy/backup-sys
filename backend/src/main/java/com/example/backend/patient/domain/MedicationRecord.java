package com.example.backend.patient.domain;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * One Medication Record in a patient's Medication History: one prescription, with how it was
 * dispensed and who prescribed it, as the patient source holds it (/docs/domain/medication.md,
 * ADR 0019, D7). Read from the source on every read, never held. Six elements are required —
 * the Medication, Dispense Status, Start Date, End Date, Dispensary and Prescribing Doctor — and
 * six optional, each {@code null} when the source has none. The End Date is on or after the
 * Start Date.
 *
 * <p>The free-text elements are as the source gives them: nothing here reads a Duration, a
 * Quantity or a Dosage, and the Duration is never worked out from the dates.
 *
 * <p>Printed by its id alone: a Medication Record is the patient's Restricted data, so neither a
 * record nor a message may carry what it says.
 *
 * @param id                the record's id at the source, in FHIR's id grammar
 * @param medication        its Medication, free text
 * @param dosage            its Dosage, free text, or {@code null}
 * @param route             its Route, free text, or {@code null}
 * @param frequency         its Frequency, free text, or {@code null}
 * @param step              its Step, free text, or {@code null}
 * @param quantity          its Quantity, free text, or {@code null}
 * @param dispenseStatus    its Dispense Status
 * @param duration          its Duration, free text, or {@code null}
 * @param startDate         its Start Date, a date-time with its offset
 * @param endDate           its End Date, a date-time with its offset, not before the Start Date
 * @param dispensary        its Dispensary, a Medical Centre
 * @param prescribingDoctor its Prescribing Doctor, a name as the source gives it, linked to no
 *                          User
 */
public record MedicationRecord(String id, String medication, String dosage, String route,
        String frequency, String step, String quantity, DispenseStatus dispenseStatus,
        String duration, OffsetDateTime startDate, OffsetDateTime endDate,
        MedicalCentre dispensary, String prescribingDoctor) {

    public MedicationRecord {
        if (!FhirId.matches(id)) {
            throw new IllegalArgumentException(
                    "A Medication Record's id must match the FHIR id grammar");
        }
        notBlank(Objects.requireNonNull(medication, "medication"), "Medication");
        Objects.requireNonNull(dispenseStatus, "dispenseStatus");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        Objects.requireNonNull(dispensary, "dispensary");
        notBlank(Objects.requireNonNull(prescribingDoctor, "prescribingDoctor"),
                "Prescribing Doctor");
        if (endsBeforeItStarts(startDate, endDate)) {
            throw new IllegalArgumentException(
                    "A Medication Record's End Date must not be before its Start Date");
        }
    }

    /**
     * Whether a record from {@code startDate} to {@code endDate} ends before it starts, which no
     * Medication Record does. The two are compared as instants, whatever offset each carries.
     */
    public static boolean endsBeforeItStarts(OffsetDateTime startDate, OffsetDateTime endDate) {
        return endDate.isBefore(startDate);
    }

    private static void notBlank(String text, String element) {
        if (text.isBlank()) {
            throw new IllegalArgumentException(
                    "A Medication Record's " + element + " must not be blank");
        }
    }

    @Override
    public String toString() {
        return "MedicationRecord[" + id + "]";
    }
}
