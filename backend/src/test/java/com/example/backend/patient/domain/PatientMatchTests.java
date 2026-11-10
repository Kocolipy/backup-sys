package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A Patient search's match, and the page of them: a match holds its Patient, its Last visit
 * optional, and prints by id alone; a page holds no negative total and a list no caller can
 * change under it.
 */
class PatientMatchTests {

    private static final Patient TAN_HUI_MIN = new Patient(new PatientId("dev-patient-05"),
            "S9012345A", new PatientName("Tan Hui Min", "Tan", List.of("Hui", "Min")),
            AdministrativeGender.FEMALE, LocalDate.of(1990, 2, 4));

    @Test
    void aMatchMayHaveNoLastVisit() {
        assertThat(new PatientMatch(TAN_HUI_MIN, null).lastVisit()).isNull();
    }

    @Test
    void aMatchHoldsAPatient() {
        assertThatThrownBy(() -> new PatientMatch(null, LocalDate.of(2026, 11, 2)))
                .isInstanceOf(NullPointerException.class).hasMessage("patient");
    }

    /** Printed, a match names its Patient by id alone: no NRIC, name or date of birth. */
    @Test
    void aMatchPrintsItsPatientsIdAlone() {
        assertThat(new PatientMatch(TAN_HUI_MIN, LocalDate.of(2026, 11, 2)))
                .hasToString("PatientMatch[Patient[id=dev-patient-05]]");
    }

    @Test
    void aPageCountsNoFewerThanNoPatients() {
        assertThatThrownBy(() -> new PatientMatches(-1, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A search matches no fewer than no Patients");
    }

    @Test
    void aPageMayCountNone() {
        assertThat(new PatientMatches(0, List.of()).totalResults()).isZero();
    }

    @Test
    void aPagesMatchesAreItsOwnCopy() {
        List<PatientMatch> matches = new ArrayList<>(
                List.of(new PatientMatch(TAN_HUI_MIN, null)));
        PatientMatches page = new PatientMatches(1, matches);

        matches.clear();

        assertThat(page.patients()).hasSize(1);
    }
}
