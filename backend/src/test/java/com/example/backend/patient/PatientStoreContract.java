package com.example.backend.patient;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.domain.AdministrativeGender;
import com.example.backend.patient.domain.Patient;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.PatientMatch;
import com.example.backend.patient.domain.PatientMatches;
import com.example.backend.patient.domain.PatientName;
import com.example.backend.patient.domain.PatientQuery;
import com.example.backend.patient.domain.PatientStore;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What every {@link PatientStore} reads, whatever it reads from, given the patient source's twelve
 * development fixtures and its canned searches ({@code patient-source-mock/mappings},
 * /docs/domain/patient.md): each fixture by its id, exactly as the domain holds it, nothing under
 * an id no fixture holds, and each documented search's page of matches with its Last visits and
 * its total, in the source's order — newest Last visit first.
 *
 * <p>The expected Patients are written out here, not derived from the stub files, so a stub that
 * drifted from the fixture table in /docs/domain/patient.md fails here.
 */
public abstract class PatientStoreContract {

    /** The patient port under test, reading the development fixtures. */
    protected abstract PatientStore patients();

    @Test
    void the_chinese_fixture_is_read_with_its_family_name_first() {
        assertThat(patients().findById(new PatientId("eq081-VQEgP8drUUqCWzHfw3"))).contains(
                new Patient(
                        new PatientId("eq081-VQEgP8drUUqCWzHfw3"),
                        "S1234567A",
                        new PatientName("Tan Wei Ming", "Tan", List.of("Wei", "Ming")),
                        AdministrativeGender.MALE,
                        LocalDate.of(1985, 3, 12)));
    }

    @Test
    void the_malay_fixture_is_read_with_its_name_left_unsplit() {
        assertThat(patients().findById(new PatientId("erXuFYUfucBZaryVksYEcMg3"))).contains(
                new Patient(
                        new PatientId("erXuFYUfucBZaryVksYEcMg3"),
                        "S9212345A",
                        new PatientName("Siti Nurhaliza binte Ahmad", null, List.of()),
                        AdministrativeGender.FEMALE,
                        LocalDate.of(1992, 8, 21)));
    }

    @Test
    void the_indian_fixture_is_read_as_a_single_given_name_with_no_family_name() {
        assertThat(patients().findById(new PatientId("e0w0LEDCYtfckT6N.CkJKCw3"))).contains(
                new Patient(
                        new PatientId("e0w0LEDCYtfckT6N.CkJKCw3"),
                        "T0312345C",
                        new PatientName("Kumaravel", null, List.of("Kumaravel")),
                        AdministrativeGender.UNKNOWN,
                        LocalDate.of(2003, 11, 5)));
    }

    @Test
    void the_eurasian_fixture_is_read_with_given_names_first_and_a_multi_word_family_name() {
        assertThat(patients().findById(new PatientId("egqBHVfQlt4Bw3XGXoxVxHg3"))).contains(
                new Patient(
                        new PatientId("egqBHVfQlt4Bw3XGXoxVxHg3"),
                        "S7812345B",
                        new PatientName("Marcus Anthony de Souza", "de Souza",
                                List.of("Marcus", "Anthony")),
                        AdministrativeGender.OTHER,
                        LocalDate.of(1978, 6, 30)));
    }

    /** {@code not-a-uuid} is a FHIR id like any other, and one no fixture is held under. */
    @Test
    void a_well_formed_id_no_patient_is_held_under_finds_nothing() {
        assertThat(patients().findById(new PatientId("not-a-uuid"))).isEmpty();
    }

    // ---- the search fixtures, by id ------------------------------------------------------

    /** The development fixtures beyond the first four, each as the domain holds it. */
    static List<Patient> searchFixtures() {
        return List.of(
                patient("dev-patient-05", "S9012345A",
                        new PatientName("Tan Hui Min", "Tan", List.of("Hui", "Min")),
                        AdministrativeGender.FEMALE, LocalDate.of(1990, 2, 4)),
                patient("dev-patient-06", "S7212345A",
                        new PatientName("Stanley Goh Kok Wee", "Goh", List.of("Stanley", "Kok Wee")),
                        AdministrativeGender.MALE, LocalDate.of(1972, 7, 19)),
                patient("dev-patient-07", "S6812345A",
                        new PatientName("Lee Mei Ling", "Lee", List.of("Mei", "Ling")),
                        AdministrativeGender.FEMALE, LocalDate.of(1968, 12, 1)),
                patient("dev-patient-08", "T0112345A",
                        new PatientName("Nur Intan binte Yusof", null, List.of()),
                        AdministrativeGender.FEMALE, LocalDate.of(2001, 4, 17)),
                patient("dev-patient-09", "S9512345A",
                        new PatientName("Kavitha Tanabalan", "Tanabalan", List.of("Kavitha")),
                        AdministrativeGender.FEMALE, LocalDate.of(1995, 9, 9)),
                patient("dev-patient-10", "S5512345A",
                        new PatientName("Tan Boon Huat", "Tan", List.of("Boon", "Huat")),
                        AdministrativeGender.MALE, LocalDate.of(1955, 1, 23)),
                patient("dev-patient-11", "S8312345B",
                        new PatientName("Ahmad bin Ismail", null, List.of()),
                        AdministrativeGender.MALE, LocalDate.of(1983, 10, 10)),
                patient("dev-patient-12", "S9912345B",
                        new PatientName("Rajesh Kumar", "Kumar", List.of("Rajesh")),
                        AdministrativeGender.MALE, LocalDate.of(1999, 5, 28)));
    }

    private static Patient patient(String id, String nric, PatientName name,
            AdministrativeGender gender, LocalDate birthDate) {
        return new Patient(new PatientId(id), nric, name, gender, birthDate);
    }

    @ParameterizedTest
    @MethodSource("searchFixtures")
    void each_search_fixture_is_read_by_its_id(Patient fixture) {
        assertThat(patients().findById(fixture.id())).contains(fixture);
    }

    // ---- the canned searches -------------------------------------------------------------

    /** The first page of five, by Last visit newest first: the search spans two pages of seven. */
    private static final PageRequest FIRST_FIVE = new PageRequest(1, 5);

    private static final PageRequest NEXT_FIVE = new PageRequest(6, 5);

    /** Each match on a page, as its id and its Last visit, {@code null} for none. */
    private static List<String> idsAndLastVisits(PatientMatches matches) {
        return matches.patients().stream()
                .map(match -> match.patient().id() + " " + match.lastVisit())
                .toList();
    }

    @Test
    void a_name_search_reads_its_first_page_of_five_newest_last_visit_first_of_seven() {
        PatientMatches matches = patients().search(new PatientQuery.ByName("tan"), FIRST_FIVE);

        assertThat(List.of(matches.totalResults(), idsAndLastVisits(matches))).containsExactly(
                7,
                List.of(
                        "dev-patient-05 2026-11-02",
                        "dev-patient-06 2026-10-28",
                        "eq081-VQEgP8drUUqCWzHfw3 2026-10-15",
                        "dev-patient-07 2026-09-30",
                        "dev-patient-08 2026-08-19"));
    }

    /** The last page: the one match with no Last visit comes last. */
    @Test
    void a_name_search_reads_its_second_page_with_the_patient_with_no_last_visit_last() {
        PatientMatches matches = patients().search(new PatientQuery.ByName("tan"), NEXT_FIVE);

        assertThat(List.of(matches.totalResults(), idsAndLastVisits(matches))).containsExactly(
                7, List.of("dev-patient-09 2026-05-07", "dev-patient-10 null"));
    }

    /**
     * Lee Mei Ling matches by her maiden name, Tan Mei Ling: the search matches as the source
     * decides, and the match is still read by its official name.
     */
    @Test
    void a_patient_matched_by_another_name_is_read_by_the_official_name() {
        PatientMatches matches = patients().search(new PatientQuery.ByName("tan"), FIRST_FIVE);

        assertThat(matches.patients().get(3)).isEqualTo(new PatientMatch(
                searchFixtures().get(2), LocalDate.of(2026, 9, 30)));
    }

    @Test
    void an_nric_search_reads_the_one_patient_holding_it() {
        assertThat(patients().search(new PatientQuery.ByNric("S9212345A"), FIRST_FIVE))
                .isEqualTo(new PatientMatches(1, List.of(new PatientMatch(
                        new Patient(
                                new PatientId("erXuFYUfucBZaryVksYEcMg3"),
                                "S9212345A",
                                new PatientName("Siti Nurhaliza binte Ahmad", null, List.of()),
                                AdministrativeGender.FEMALE,
                                LocalDate.of(1992, 8, 21)),
                        LocalDate.of(2026, 7, 11)))));
    }

    @Test
    void a_search_nothing_matches_reads_no_patients_and_a_total_of_none() {
        assertThat(patients().search(new PatientQuery.ByName("nobody"), FIRST_FIVE))
                .isEqualTo(new PatientMatches(0, List.of()));
    }
}
