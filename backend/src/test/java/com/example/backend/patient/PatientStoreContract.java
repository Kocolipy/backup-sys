package com.example.backend.patient;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.domain.AdministrativeGender;
import com.example.backend.patient.domain.ArmcBoard;
import com.example.backend.patient.domain.ArmcBoardType;
import com.example.backend.patient.domain.ArmcHistory;
import com.example.backend.patient.domain.ArmcOutcome;
import com.example.backend.patient.domain.DateRange;
import com.example.backend.patient.domain.Ffi;
import com.example.backend.patient.domain.FfiHigherAuthority;
import com.example.backend.patient.domain.FfiFilter;
import com.example.backend.patient.domain.FfiHistory;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.FfiOutcome;
import com.example.backend.patient.domain.FfiStatus;
import com.example.backend.patient.domain.FfiSubmittingMc;
import com.example.backend.patient.domain.FfiType;
import com.example.backend.patient.domain.NumcFilter;
import com.example.backend.patient.domain.NumcHistory;
import com.example.backend.patient.domain.NumcOutcome;
import com.example.backend.patient.domain.NumcRecord;
import com.example.backend.patient.domain.NumcType;
import com.example.backend.patient.domain.Patient;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.PatientMatch;
import com.example.backend.patient.domain.PatientMatches;
import com.example.backend.patient.domain.PatientName;
import com.example.backend.patient.domain.PatientFilter;
import com.example.backend.patient.domain.PatientQuery;
import com.example.backend.patient.domain.PatientStore;
import com.example.backend.patient.domain.SortOrder;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What every {@link PatientStore} reads, whatever it reads from, given the patient source's twelve
 * development fixtures and its canned searches ({@code patient-source-mock/mappings},
 * /docs/domain/patient.md): each fixture by its id, exactly as the domain holds it, nothing under
 * an id no fixture holds, each documented search's page of matches with its Last visits and
 * its total, in the source's own order, and each FFI History, NUMC History and ARMC History the
 * mock serves (/docs/domain/ffi.md, "The patient source's FFI fixtures"; /docs/domain/numc.md,
 * "The patient source's NUMC fixtures"; /docs/domain/armc.md, "The patient source's ARMC
 * fixtures"), page by page.
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

    /** The first page of five: the {@code tan} search spans two pages of seven. */
    private static final PageRequest FIRST_FIVE = new PageRequest(1, 5);

    private static final PageRequest NEXT_FIVE = new PageRequest(6, 5);

    /** Each match on a page, as its id and its Last visit, {@code null} for none. */
    private static List<String> idsAndLastVisits(PatientMatches matches) {
        return matches.patients().stream()
                .map(match -> match.patient().id() + " " + match.lastVisit())
                .toList();
    }

    @Test
    void a_name_search_reads_its_first_page_of_five_of_seven_in_the_sources_order() {
        PatientMatches matches = patients().search(
                new PatientQuery.ByName("tan"), PatientFilter.NONE, FIRST_FIVE);

        assertThat(List.of(matches.totalResults(), idsAndLastVisits(matches))).containsExactly(
                7,
                List.of(
                        "dev-patient-05 2026-11-02",
                        "dev-patient-06 2026-10-28",
                        "eq081-VQEgP8drUUqCWzHfw3 2026-10-15",
                        "dev-patient-07 2026-09-30",
                        "dev-patient-08 2026-08-19"));
    }

    /** The last page: the two matches after the first five, in the source's order. */
    @Test
    void a_name_search_reads_its_second_page_of_two() {
        PatientMatches matches = patients().search(
                new PatientQuery.ByName("tan"), PatientFilter.NONE, NEXT_FIVE);

        assertThat(List.of(matches.totalResults(), idsAndLastVisits(matches))).containsExactly(
                7, List.of("dev-patient-09 2026-05-07", "dev-patient-10 null"));
    }

    /**
     * Lee Mei Ling matches by her maiden name, Tan Mei Ling: the search matches as the source
     * decides, and the match is still read by its official name.
     */
    @Test
    void a_patient_matched_by_another_name_is_read_by_the_official_name() {
        PatientMatches matches = patients().search(
                new PatientQuery.ByName("tan"), PatientFilter.NONE, FIRST_FIVE);

        assertThat(matches.patients().get(3)).isEqualTo(new PatientMatch(
                searchFixtures().get(2), LocalDate.of(2026, 9, 30)));
    }

    @Test
    void an_nric_search_reads_the_one_patient_holding_it() {
        assertThat(patients().search(
                new PatientQuery.ByNric("S9212345A"), PatientFilter.NONE, FIRST_FIVE))
                .isEqualTo(new PatientMatches(1, List.of(new PatientMatch(
                        new Patient(
                                new PatientId("erXuFYUfucBZaryVksYEcMg3"),
                                "S9212345A",
                                new PatientName("Siti Nurhaliza binte Ahmad", null, List.of()),
                                AdministrativeGender.FEMALE,
                                LocalDate.of(1992, 8, 21)),
                        LocalDate.of(2026, 7, 11)))));
    }

    private static PatientFilter gender(AdministrativeGender gender) {
        return new PatientFilter(gender);
    }

    /**
     * Each canned filtered {@code tan} search, as the source answers it: the case, the filter,
     * then the total and the page's ids in the source's order.
     */
    static Stream<Arguments> cannedFilteredSearches() {
        return Stream.of(
                Arguments.of("male", gender(AdministrativeGender.MALE), 3,
                        List.of("dev-patient-06", "eq081-VQEgP8drUUqCWzHfw3", "dev-patient-10")),
                Arguments.of("female", gender(AdministrativeGender.FEMALE), 4,
                        List.of("dev-patient-05", "dev-patient-07", "dev-patient-08",
                                "dev-patient-09")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cannedFilteredSearches")
    void a_canned_filtered_search_reads_the_sources_page(String search, PatientFilter filter,
            int total, List<String> ids) {
        PatientMatches matches =
                patients().search(new PatientQuery.ByName("tan"), filter, FIRST_FIVE);

        assertThat(List.of(matches.totalResults(), matches.patients().stream()
                .map(match -> match.patient().id().value()).toList()))
                .containsExactly(total, ids);
    }

    /** A filtered page no canned search is matches nothing in the mock. */
    @Test
    void a_filtered_search_no_stub_names_reads_no_patients() {
        assertThat(patients().search(new PatientQuery.ByName("tan"),
                gender(AdministrativeGender.MALE), NEXT_FIVE))
                .isEqualTo(new PatientMatches(0, List.of()));
    }

    @Test
    void a_search_nothing_matches_reads_no_patients_and_a_total_of_none() {
        assertThat(patients().search(
                new PatientQuery.ByName("nobody"), PatientFilter.NONE, FIRST_FIVE))
                .isEqualTo(new PatientMatches(0, List.of()));
    }

    // ---- the FFI Histories -----------------------------------------------------------------

    private static Ffi ffi(String id, FfiType type, FfiOutcome outcome, String issueDate,
            String expiryDate, FfiHigherAuthority higherAuthority, FfiSubmittingMc submittingMc,
            FfiStatus status) {
        return new Ffi(id, type, outcome, LocalDate.parse(issueDate), LocalDate.parse(expiryDate),
                higherAuthority, submittingMc, status);
    }

    /** Tan Wei Ming's four FFIs, in the source's own order. */
    @Test
    void tan_wei_mings_ffi_history_is_his_four_ffis_in_the_sources_order() {
        assertThat(patients().findFfiHistory(new PatientId("eq081-VQEgP8drUUqCWzHfw3"), FfiFilter.NONE, HistorySort.SOURCE_ORDER,
                FIRST_FIVE)).contains(new FfiHistory(4, List.of(
                        ffi("ffi-tan-wei-ming-1", FfiType.OVERSEAS_EXERCISES, FfiOutcome.FIT,
                                "2023-10-14", "2024-10-13", FfiHigherAuthority.CAMO,
                                FfiSubmittingMc.NEE_SOON_CAMP, FfiStatus.INACTIVE),
                        ffi("ffi-tan-wei-ming-2", FfiType.IPPT, FfiOutcome.REQUEST_WAIVER,
                                "2024-01-10", "2025-01-09", FfiHigherAuthority.CNMO,
                                FfiSubmittingMc.KRANJI_CAMP, FfiStatus.ACTIVE),
                        ffi("ffi-tan-wei-ming-3", FfiType.DENTAL, FfiOutcome.FIT,
                                "2023-11-22", "2024-11-21", FfiHigherAuthority.CARMO,
                                FfiSubmittingMc.HENDON_CAMP, FfiStatus.ACTIVE),
                        ffi("ffi-tan-wei-ming-4", FfiType.VOCATIONAL, FfiOutcome.UNFIT,
                                "2023-08-05", "2024-08-04", FfiHigherAuthority.CAMO,
                                FfiSubmittingMc.MANDAI_HILL_CAMP, FfiStatus.INACTIVE))));
    }

    /** Tan Hui Min's seven FFIs over two pages of five: the first page. */
    @Test
    void tan_hui_mins_ffi_history_reads_its_first_page_of_five_of_seven() {
        assertThat(patients().findFfiHistory(new PatientId("dev-patient-05"), FfiFilter.NONE, HistorySort.SOURCE_ORDER, FIRST_FIVE))
                .contains(new FfiHistory(7, List.of(
                        ffi("ffi-tan-hui-min-1", FfiType.ORD_RELEASE_FROM_SERVICE,
                                FfiOutcome.FIT, "2025-03-03", "2026-03-02",
                                FfiHigherAuthority.DIS, FfiSubmittingMc.NEE_SOON_CAMP,
                                FfiStatus.ACTIVE),
                        ffi("ffi-tan-hui-min-2", FfiType.CAT_A_OCS_SCS, FfiOutcome.UNFIT,
                                "2024-11-18", "2025-11-17", FfiHigherAuthority.COMD_MMI,
                                FfiSubmittingMc.KRANJI_CAMP, FfiStatus.INACTIVE),
                        ffi("ffi-tan-hui-min-3", FfiType.IPPT, FfiOutcome.REQUEST_WAIVER,
                                "2024-06-10", "2025-06-09", FfiHigherAuthority.CNMO,
                                FfiSubmittingMc.HENDON_CAMP, FfiStatus.ACTIVE),
                        ffi("ffi-tan-hui-min-4", FfiType.OVERSEAS_EXERCISES, FfiOutcome.FIT,
                                "2024-02-26", "2025-02-25", FfiHigherAuthority.CARMO,
                                FfiSubmittingMc.MANDAI_HILL_CAMP, FfiStatus.ACTIVE),
                        ffi("ffi-tan-hui-min-5", FfiType.DENTAL, FfiOutcome.FIT, "2023-09-04",
                                "2024-09-03", FfiHigherAuthority.CAMO,
                                FfiSubmittingMc.NEE_SOON_CAMP, FfiStatus.INACTIVE))));
    }

    @Test
    void tan_hui_mins_ffi_history_reads_its_last_two_on_the_second_page() {
        assertThat(patients().findFfiHistory(new PatientId("dev-patient-05"), FfiFilter.NONE, HistorySort.SOURCE_ORDER, NEXT_FIVE))
                .contains(new FfiHistory(7, List.of(
                        ffi("ffi-tan-hui-min-6", FfiType.VOCATIONAL, FfiOutcome.REQUEST_WAIVER,
                                "2023-04-17", "2024-04-16", FfiHigherAuthority.CNMO,
                                FfiSubmittingMc.KRANJI_CAMP, FfiStatus.INACTIVE),
                        ffi("ffi-tan-hui-min-7", FfiType.IPPT, FfiOutcome.UNFIT, "2022-12-05",
                                "2023-12-04", FfiHigherAuthority.CAMO,
                                FfiSubmittingMc.HENDON_CAMP, FfiStatus.INACTIVE))));
    }

    /** Siti Nurhaliza binte Ahmad, as every fixture but those two, has no FFI. */
    @Test
    void a_patient_with_no_ffis_reads_an_empty_history_of_none() {
        assertThat(patients().findFfiHistory(new PatientId("erXuFYUfucBZaryVksYEcMg3"), FfiFilter.NONE, HistorySort.SOURCE_ORDER,
                FIRST_FIVE)).contains(new FfiHistory(0, List.of()));
    }

    /** {@code not-a-uuid} is a well-formed id the source holds no patient under. */
    @Test
    void a_well_formed_id_no_patient_is_held_under_has_no_ffi_history() {
        assertThat(patients().findFfiHistory(new PatientId("not-a-uuid"), FfiFilter.NONE, HistorySort.SOURCE_ORDER, FIRST_FIVE)).isEmpty();
    }

    // ---- the NUMC Histories ----------------------------------------------------------------

    private static NumcRecord numc(String id, NumcType type, NumcOutcome outcome,
            String issueDate, String expiryDate) {
        return new NumcRecord(id, type, outcome, LocalDate.parse(issueDate),
                LocalDate.parse(expiryDate));
    }

    /** The page {@code page} of {@code patient}'s NUMC History, unfiltered, in the source's order. */
    private Optional<NumcHistory> numcHistory(String patient, PageRequest page) {
        return patients().findNumcHistory(new PatientId(patient), NumcFilter.NONE,
                HistorySort.SOURCE_ORDER, page);
    }

    /** Tan Wei Ming's four NUMC Records, in the source's own order. */
    @Test
    void tan_wei_mings_numc_history_is_his_four_records_in_the_sources_order() {
        assertThat(numcHistory("eq081-VQEgP8drUUqCWzHfw3", FIRST_FIVE)).contains(
                new NumcHistory(4, List.of(
                        numc("numc-tan-wei-ming-1", NumcType.HYPERBARIC_CHAMBER, NumcOutcome.FIT,
                                "2023-10-14", "2024-10-13"),
                        numc("numc-tan-wei-ming-2", NumcType.SUBMARINER,
                                NumcOutcome.WAIVER_REJECTED, "2024-01-10", "2025-01-09"),
                        numc("numc-tan-wei-ming-3", NumcType.DIVING, NumcOutcome.WAIVER_GRANTED,
                                "2023-11-22", "2024-11-21"),
                        numc("numc-tan-wei-ming-4", NumcType.HYPERBARIC_CHAMBER,
                                NumcOutcome.UNFIT, "2023-08-05", "2024-08-04"))));
    }

    /** Tan Hui Min's seven NUMC Records over two pages of five: the first page. */
    @Test
    void tan_hui_mins_numc_history_reads_its_first_page_of_five_of_seven() {
        assertThat(numcHistory("dev-patient-05", FIRST_FIVE))
                .contains(new NumcHistory(7, List.of(
                        numc("numc-tan-hui-min-1", NumcType.DIVING, NumcOutcome.FIT,
                                "2025-03-03", "2026-03-02"),
                        numc("numc-tan-hui-min-2", NumcType.SUBMARINER, NumcOutcome.UNFIT,
                                "2024-11-18", "2025-11-17"),
                        numc("numc-tan-hui-min-3", NumcType.HYPERBARIC_CHAMBER,
                                NumcOutcome.WAIVER_GRANTED, "2024-06-10", "2025-06-09"),
                        numc("numc-tan-hui-min-4", NumcType.DIVING, NumcOutcome.WAIVER_REJECTED,
                                "2024-02-26", "2025-02-25"),
                        numc("numc-tan-hui-min-5", NumcType.SUBMARINER, NumcOutcome.FIT,
                                "2023-09-04", "2024-09-03"))));
    }

    @Test
    void tan_hui_mins_numc_history_reads_its_last_two_on_the_second_page() {
        assertThat(numcHistory("dev-patient-05", NEXT_FIVE))
                .contains(new NumcHistory(7, List.of(
                        numc("numc-tan-hui-min-6", NumcType.HYPERBARIC_CHAMBER,
                                NumcOutcome.UNFIT, "2023-04-17", "2024-04-16"),
                        numc("numc-tan-hui-min-7", NumcType.DIVING, NumcOutcome.WAIVER_GRANTED,
                                "2022-12-05", "2023-12-04"))));
    }

    /** The ids of a NUMC History's page, in its order, beside its total. */
    private static List<Object> idsAndTotal(Optional<NumcHistory> history) {
        NumcHistory read = history.orElseThrow();
        return List.of(read.totalResults(),
                read.records().stream().map(NumcRecord::id).toList());
    }

    /** Each canned sort of Tan Wei Ming's NUMC History: the sort, then the ids in its order. */
    static Stream<Arguments> cannedNumcSorts() {
        List<String> earliestFirst = List.of("numc-tan-wei-ming-4", "numc-tan-wei-ming-1",
                "numc-tan-wei-ming-3", "numc-tan-wei-ming-2");
        List<String> latestFirst = List.of("numc-tan-wei-ming-2", "numc-tan-wei-ming-3",
                "numc-tan-wei-ming-1", "numc-tan-wei-ming-4");
        return Stream.of(
                Arguments.of(HistorySort.Field.ISSUE_DATE, SortOrder.ASCENDING, earliestFirst),
                Arguments.of(HistorySort.Field.ISSUE_DATE, SortOrder.DESCENDING, latestFirst),
                Arguments.of(HistorySort.Field.EXPIRY_DATE, SortOrder.ASCENDING, earliestFirst),
                Arguments.of(HistorySort.Field.EXPIRY_DATE, SortOrder.DESCENDING, latestFirst));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("cannedNumcSorts")
    void tan_wei_mings_numc_history_reads_in_each_canned_sort(HistorySort.Field field,
            SortOrder order, List<String> ids) {
        assertThat(idsAndTotal(patients().findNumcHistory(
                new PatientId("eq081-VQEgP8drUUqCWzHfw3"), NumcFilter.NONE,
                new HistorySort(field, order), FIRST_FIVE)))
                .containsExactly(4, ids);
    }

    private static final LocalDate START_OF_2024 = LocalDate.of(2024, 1, 1);

    private static final LocalDate END_OF_2024 = LocalDate.of(2024, 12, 31);

    /** Each canned filter of Tan Hui Min's NUMC History: the case, the filter, then the ids it selects. */
    static Stream<Arguments> cannedNumcFilters() {
        return Stream.of(
                Arguments.of("hyperbaric-chamber or submariner", new NumcFilter(
                        Set.of(NumcType.SUBMARINER, NumcType.HYPERBARIC_CHAMBER), Set.of(),
                        DateRange.ANY, DateRange.ANY),
                        List.of("numc-tan-hui-min-2", "numc-tan-hui-min-3",
                                "numc-tan-hui-min-5", "numc-tan-hui-min-6")),
                Arguments.of("waiver granted or rejected", new NumcFilter(Set.of(),
                        Set.of(NumcOutcome.WAIVER_REJECTED, NumcOutcome.WAIVER_GRANTED),
                        DateRange.ANY, DateRange.ANY),
                        List.of("numc-tan-hui-min-3", "numc-tan-hui-min-4",
                                "numc-tan-hui-min-7")),
                Arguments.of("issued in 2024", new NumcFilter(Set.of(), Set.of(),
                        new DateRange(START_OF_2024, END_OF_2024), DateRange.ANY),
                        List.of("numc-tan-hui-min-2", "numc-tan-hui-min-3",
                                "numc-tan-hui-min-4")),
                Arguments.of("expiring to 2024", new NumcFilter(Set.of(), Set.of(),
                        DateRange.ANY, new DateRange(null, END_OF_2024)),
                        List.of("numc-tan-hui-min-5", "numc-tan-hui-min-6",
                                "numc-tan-hui-min-7")),
                Arguments.of("diving issued from 2024", new NumcFilter(Set.of(NumcType.DIVING),
                        Set.of(), new DateRange(START_OF_2024, null), DateRange.ANY),
                        List.of("numc-tan-hui-min-1", "numc-tan-hui-min-4")),
                Arguments.of("expiring to 2022", new NumcFilter(Set.of(), Set.of(),
                        DateRange.ANY, new DateRange(null, LocalDate.of(2022, 12, 31))),
                        List.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cannedNumcFilters")
    void tan_hui_mins_numc_history_reads_each_canned_filter(String filtered, NumcFilter filter,
            List<String> ids) {
        assertThat(idsAndTotal(patients().findNumcHistory(new PatientId("dev-patient-05"),
                filter, HistorySort.SOURCE_ORDER, FIRST_FIVE)))
                .containsExactly(ids.size(), ids);
    }

    /** Siti Nurhaliza binte Ahmad, as every fixture but those two, has no NUMC Record. */
    @Test
    void a_patient_with_no_numc_records_reads_an_empty_history_of_none() {
        assertThat(numcHistory("erXuFYUfucBZaryVksYEcMg3", FIRST_FIVE))
                .contains(new NumcHistory(0, List.of()));
    }

    /** {@code not-a-uuid} is a well-formed id the source holds no patient under. */
    @Test
    void a_well_formed_id_no_patient_is_held_under_has_no_numc_history() {
        assertThat(numcHistory("not-a-uuid", FIRST_FIVE)).isEmpty();
    }

    // ---- the ARMC Histories ----------------------------------------------------------------

    private static ArmcBoard armc(String id, ArmcOutcome outcome, String startDate,
            String endDate, String medicalOfficer1, String medicalOfficer2) {
        return new ArmcBoard(id, ArmcBoardType.ACES, outcome, LocalDate.parse(startDate),
                endDate == null ? null : LocalDate.parse(endDate), medicalOfficer1,
                medicalOfficer2);
    }

    /** Tan Wei Ming's two ARMC Boards, in the source's own order. */
    @Test
    void tan_wei_mings_armc_history_is_his_two_boards_in_the_sources_order() {
        assertThat(patients().findArmcHistory(new PatientId("eq081-VQEgP8drUUqCWzHfw3"),
                FIRST_FIVE)).contains(new ArmcHistory(2, List.of(
                        armc("armc-tan-wei-ming-1", ArmcOutcome.FIT, "2024-01-01", "2024-02-10",
                                "Medical Officer 1", "Medical Officer 2"),
                        armc("armc-tan-wei-ming-2", ArmcOutcome.UNFIT, "2020-11-21",
                                "2021-04-02", "Medical Officer 3", "Medical Officer 4"))));
    }

    /** Tan Hui Min's seven ARMC Boards over two pages of five: the first page, its first open. */
    @Test
    void tan_hui_mins_armc_history_reads_its_first_page_of_five_of_seven() {
        assertThat(patients().findArmcHistory(new PatientId("dev-patient-05"), FIRST_FIVE))
                .contains(new ArmcHistory(7, List.of(
                        armc("armc-tan-hui-min-1", ArmcOutcome.FIT, "2025-06-02", null,
                                "Dr Lim Wei Jie", "Dr Nur Aisyah binte Rahman"),
                        armc("armc-tan-hui-min-2", ArmcOutcome.UNFIT, "2024-09-16",
                                "2024-11-29", "Dr Rajesh Kumar", "Dr Lim Wei Jie"),
                        armc("armc-tan-hui-min-3", ArmcOutcome.FIT, "2023-12-04", "2023-12-04",
                                "Dr Chen Mei Ling", "Dr Rajesh Kumar"),
                        armc("armc-tan-hui-min-4", ArmcOutcome.FIT, "2023-03-13", "2023-05-08",
                                "Dr Nur Aisyah binte Rahman", "Dr Chen Mei Ling"),
                        armc("armc-tan-hui-min-5", ArmcOutcome.UNFIT, "2022-07-25",
                                "2022-10-03", "Dr Lim Wei Jie", "Dr Chen Mei Ling"))));
    }

    @Test
    void tan_hui_mins_armc_history_reads_its_last_two_on_the_second_page() {
        assertThat(patients().findArmcHistory(new PatientId("dev-patient-05"), NEXT_FIVE))
                .contains(new ArmcHistory(7, List.of(
                        armc("armc-tan-hui-min-6", ArmcOutcome.FIT, "2021-10-11", "2021-12-20",
                                "Dr Rajesh Kumar", "Dr Nur Aisyah binte Rahman"),
                        armc("armc-tan-hui-min-7", ArmcOutcome.UNFIT, "2020-05-18",
                                "2020-08-24", "Dr Chen Mei Ling", "Dr Lim Wei Jie"))));
    }

    /** Siti Nurhaliza binte Ahmad, as every fixture but those two, has no ARMC Board. */
    @Test
    void a_patient_with_no_armc_boards_reads_an_empty_history_of_none() {
        assertThat(patients().findArmcHistory(new PatientId("erXuFYUfucBZaryVksYEcMg3"),
                FIRST_FIVE)).contains(new ArmcHistory(0, List.of()));
    }

    /** {@code not-a-uuid} is a well-formed id the source holds no patient under. */
    @Test
    void a_well_formed_id_no_patient_is_held_under_has_no_armc_history() {
        assertThat(patients().findArmcHistory(new PatientId("not-a-uuid"), FIRST_FIVE)).isEmpty();
    }
}
