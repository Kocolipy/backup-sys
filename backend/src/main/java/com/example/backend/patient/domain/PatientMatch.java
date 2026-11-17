package com.example.backend.patient.domain;

import static java.util.Map.entry;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One Patient a Patient search matched, with its Last visit (/docs/domain/patient.md, "Patient
 * search"). Read from the patient source's search, never held. The Last visit is shown as
 * received (ADR 0020): present, missing or in the wrong format, and never refused for what it
 * holds; the Patient's five elements are read strictly (ADR 0018).
 *
 * <p>Printed as its Patient is, by id alone: the Patient's {@code toString} keeps its NRIC, name
 * and date of birth out of any record or message, and so does this.
 *
 * @param patient   the Patient, its five elements each present
 * @param lastVisit the date of the Patient's Last visit, missing for a Patient the source records
 *                  none for
 */
public record PatientMatch(Patient patient, SourceField<LocalDate> lastVisit)
        implements ReceivedRecord {

    /** The Last visit's name, as the API spells it and {@link #invalid()} names it. */
    public static final String LAST_VISIT = "lastVisit";

    public PatientMatch {
        Objects.requireNonNull(patient, "patient");
        Objects.requireNonNull(lastVisit, "lastVisit");
    }

    /** {@value #LAST_VISIT} when the Last visit's format was wrong, and none otherwise. */
    @Override
    public List<String> invalid() {
        return SourceField.invalidAmong(entry(LAST_VISIT, lastVisit));
    }

    @Override
    public String toString() {
        return "PatientMatch[" + patient + "]";
    }
}
