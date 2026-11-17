package com.example.backend.patient.domain;

import static java.util.Map.entry;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One entry of a Patient search's page that is shown (/docs/domain/patient.md, "Patient search").
 * Read from the patient source's search, never held: either a Patient's match with its Last
 * visit ({@link OfPatient}), or an entry whose resource is no Patient ({@link NotAPatient}).
 *
 * <p>Shown as received (ADR 0020): a Patient's NRIC, official name's text, gender and birth date
 * and the Last visit are each present, missing or in the wrong format, and none is refused for
 * what it holds. Its Patient's id is the one exception: a match is shown only under an id in
 * FHIR's grammar, which selecting its row opens the Patient by, and an entry without one is
 * dropped from the page ({@link PatientMatches#droppedResults()}, ADR 0018).
 *
 * <p>Printed by its Patient's id alone, or as no Patient's: the Patient's {@code toString} keeps
 * its NRIC, name and date of birth out of any record or message, and so does this.
 */
public sealed interface PatientMatch extends ReceivedRecord
        permits PatientMatch.OfPatient, PatientMatch.NotAPatient {

    /** The official name's text, as the API spells it and {@link #invalid()} names it. */
    String NAME = "name";

    /** The NRIC's name, as the API spells it and {@link #invalid()} names it. */
    String NRIC = "nric";

    /** The gender's name, as the API spells it and {@link #invalid()} names it. */
    String GENDER = "gender";

    /** The birth date's name, as the API spells it and {@link #invalid()} names it. */
    String BIRTH_DATE = "birthDate";

    /** The Last visit's name, as the API spells it and {@link #invalid()} names it. */
    String LAST_VISIT = "lastVisit";

    /**
     * A Patient's match, which its row selects the Patient by.
     *
     * @param patient   the Patient, each of its elements as received
     * @param lastVisit the date of the Patient's Last visit, missing for a Patient the source
     *                  records none for
     */
    record OfPatient(Patient patient, SourceField<LocalDate> lastVisit) implements PatientMatch {

        public OfPatient {
            Objects.requireNonNull(patient, "patient");
            Objects.requireNonNull(lastVisit, "lastVisit");
        }

        /**
         * Its fields whose format was wrong, by the row's names for them, in the row's order: the
         * official name's text, the NRIC, the gender, the birth date and the Last visit.
         */
        @Override
        public List<String> invalid() {
            return SourceField.invalidAmong(
                    entry(NAME, patient.nameText()),
                    entry(NRIC, patient.nric()),
                    entry(GENDER, patient.gender()),
                    entry(BIRTH_DATE, patient.birthDate()),
                    entry(LAST_VISIT, lastVisit));
        }

        @Override
        public String toString() {
            return "PatientMatch[" + patient + "]";
        }
    }

    /**
     * An entry of the search whose resource is no Patient: its row is "Error" in every cell, and
     * has no Patient to select.
     */
    record NotAPatient() implements PatientMatch {

        /** Every one of the row's fields, in its order: none was received as a Patient's. */
        @Override
        public List<String> invalid() {
            return List.of(NAME, NRIC, GENDER, BIRTH_DATE, LAST_VISIT);
        }

        @Override
        public String toString() {
            return "PatientMatch[no Patient]";
        }
    }
}
