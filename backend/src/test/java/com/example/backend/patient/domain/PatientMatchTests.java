package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A Patient search's match, and the page of them: a match holds its Patient and its Last visit as
 * received (ADR 0020), names each of its fields whose format was wrong by the row's name for it,
 * and prints by id alone; an entry that is no Patient is a match whose every field is invalid; a
 * page holds no negative total or dropped count, and a list no caller can change under it.
 */
class PatientMatchTests {

    private static final Patient TAN_HUI_MIN = new Patient(new PatientId("dev-patient-05"),
            "S9012345A", new PatientName("Tan Hui Min", "Tan", List.of("Hui", "Min")),
            AdministrativeGender.FEMALE, LocalDate.of(1990, 2, 4));

    private static final SourceField<LocalDate> VISITED =
            SourceField.present(LocalDate.of(2026, 11, 2));

    /** A match with no Last visit holds it as missing, never as no field at all. */
    @Test
    void aMatchHoldsItsLastVisitAsReceived() {
        assertThatThrownBy(() -> new PatientMatch.OfPatient(TAN_HUI_MIN, null))
                .isInstanceOf(NullPointerException.class).hasMessage("lastVisit");
    }

    /** A Patient's match has its Patient: an entry that is none is {@code NotAPatient}. */
    @Test
    void aPatientsMatchNeedsItsPatient() {
        assertThatThrownBy(() -> new PatientMatch.OfPatient(null, VISITED))
                .isInstanceOf(NullPointerException.class).hasMessage("patient");
    }

    /** A match names its Last visit invalid when its format was wrong, and nothing otherwise. */
    @Test
    void aMatchNamesAnInvalidLastVisit() {
        assertThat(List.of(new PatientMatch.OfPatient(TAN_HUI_MIN, SourceField.invalid()).invalid(),
                new PatientMatch.OfPatient(TAN_HUI_MIN, SourceField.missing()).invalid(),
                new PatientMatch.OfPatient(TAN_HUI_MIN, VISITED).invalid()))
                .containsExactly(List.of("lastVisit"), List.of(), List.of());
    }

    /**
     * A match names each of its Patient's fields whose format was wrong as the row spells it, in
     * the row's order: the name, the NRIC, the gender and the birth date.
     */
    @Test
    void aMatchNamesEachInvalidFieldOfItsPatientAsTheRowSpellsIt() {
        Patient everyFieldInvalid = new Patient(new PatientId("dev-patient-05"),
                SourceField.invalid(), new PatientName(SourceField.invalid(), null, null),
                SourceField.invalid(), SourceField.invalid(), Demographics.NONE);

        assertThat(new PatientMatch.OfPatient(everyFieldInvalid, SourceField.invalid()).invalid())
                .containsExactly("name", "nric", "gender", "birthDate", "lastVisit");
    }

    /** A field missing — an official name with no text, or none at all — is named by no one. */
    @Test
    void aMatchNamesNoMissingField() {
        Patient everyFieldMissing = new Patient(new PatientId("dev-patient-05"),
                SourceField.missing(), null, SourceField.missing(), SourceField.missing(),
                Demographics.NONE);

        assertThat(new PatientMatch.OfPatient(everyFieldMissing, SourceField.missing()).invalid()).isEmpty();
    }

    /** A gender outside the four codes is present, as it came: no field of the match is invalid. */
    @Test
    void aGenderOutsideTheFourIsNoInvalidField() {
        Patient unknownCode = new Patient(new PatientId("dev-patient-05"),
                SourceField.present("S9012345A"), TAN_HUI_MIN.name(), SourceField.present("F"),
                TAN_HUI_MIN.birthDate(), Demographics.NONE);

        assertThat(new PatientMatch.OfPatient(unknownCode, VISITED).invalid()).isEmpty();
    }

    /**
     * An entry of the search that is no Patient is a match with no Patient, its every field
     * invalid, so its row shows "Error" in each cell.
     */
    @Test
    void anEntryThatIsNoPatientIsAMatchWhoseEveryFieldIsInvalid() {
        assertThat(new PatientMatch.NotAPatient().invalid())
                .containsExactly("name", "nric", "gender", "birthDate", "lastVisit");
    }

    /** Printed, a match names its Patient by id alone: no NRIC, name or date of birth. */
    @Test
    void aMatchPrintsItsPatientsIdAlone() {
        assertThat(new PatientMatch.OfPatient(TAN_HUI_MIN, VISITED))
                .hasToString("PatientMatch[Patient[id=dev-patient-05]]");
    }

    @Test
    void anEntryThatIsNoPatientPrintsAsSuch() {
        assertThat(new PatientMatch.NotAPatient()).hasToString("PatientMatch[no Patient]");
    }

    @Test
    void aPageCountsNoFewerThanNoPatients() {
        assertThatThrownBy(() -> new PatientMatches(-1, List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A search matches no fewer than no Patients");
    }

    @Test
    void aPageMayCountNone() {
        assertThat(new PatientMatches(0, List.of(), 0).totalResults()).isZero();
    }

    @Test
    void aPageDropsNoFewerThanNone() {
        assertThatThrownBy(() -> new PatientMatches(1, List.of(), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A page drops no fewer than no matches");
    }

    /** The dropped are the page's own count, beside the total the source counted. */
    @Test
    void aPageKeepsTheSourcesTotalBesideWhatItDropped() {
        PatientMatches page = new PatientMatches(7, List.of(), 2);

        assertThat(List.of(page.totalResults(), page.droppedResults())).containsExactly(7, 2);
    }

    @Test
    void aPagesMatchesAreItsOwnCopy() {
        List<PatientMatch> matches = new ArrayList<>(
                List.of(new PatientMatch.OfPatient(TAN_HUI_MIN, SourceField.missing())));
        PatientMatches page = new PatientMatches(1, matches, 0);

        matches.clear();

        assertThat(page.patients()).hasSize(1);
    }
}
