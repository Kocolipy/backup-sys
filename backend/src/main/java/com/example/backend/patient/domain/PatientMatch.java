package com.example.backend.patient.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One Patient a Patient search matched, with its Last visit (/docs/domain/patient.md, "Patient
 * search"). Read from the patient source's search, never held.
 *
 * <p>Printed as its Patient is, by id alone: the Patient's {@code toString} keeps its NRIC, name
 * and date of birth out of any record or message, and so does this.
 *
 * @param patient   the Patient, its five elements copied as a read by id copies them
 * @param lastVisit the date of the Patient's Last visit, or {@code null} for a Patient the source
 *                  records none for
 */
public record PatientMatch(Patient patient, LocalDate lastVisit) {

    public PatientMatch {
        Objects.requireNonNull(patient, "patient");
    }

    @Override
    public String toString() {
        return "PatientMatch[" + patient + "]";
    }
}
