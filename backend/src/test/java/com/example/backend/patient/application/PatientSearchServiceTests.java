package com.example.backend.patient.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.backend.audit.RecordingAuditTrail;
import com.example.backend.audit.RecordingAuditTrail.Recorded;
import com.example.backend.audit.domain.AuditOperation;
import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.domain.AdministrativeGender;
import com.example.backend.patient.domain.DateRange;
import com.example.backend.patient.domain.Patient;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.PatientMatch;
import com.example.backend.patient.domain.PatientMatches;
import com.example.backend.patient.domain.PatientName;
import com.example.backend.patient.domain.PatientFilter;
import com.example.backend.patient.domain.PatientQuery;
import com.example.backend.patient.domain.PatientSort;
import com.example.backend.patient.domain.PatientStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A Patient search as the use case runs it: the patient source's page, passed through as it
 * answered, and one {@code PATIENT_SEARCH} naming who searched, how many Patients the page held and
 * the query's kind — only once the source has answered.
 */
class PatientSearchServiceTests {

    private static final UUID SEARCHER = UUID.fromString("5d7c1e2a-3b4f-4a6d-8e9f-0a1b2c3d4e5f");

    private static PatientMatch match(String id) {
        return new PatientMatch(new Patient(new PatientId(id), "S9012345A",
                new PatientName("Tan Hui Min", "Tan", List.of("Hui", "Min")),
                AdministrativeGender.FEMALE, LocalDate.of(1990, 2, 4)), LocalDate.of(2026, 11, 2));
    }

    /** A page of two of the seven Patients the source says the search matched. */
    private static final PatientMatches TWO_OF_SEVEN =
            new PatientMatches(7, List.of(match("dev-patient-05"), match("dev-patient-06")));

    /** A source answering every search with {@code answer}, and keeping what it was asked. */
    private static final class AnsweringStore implements PatientStore {

        private final PatientMatches answer;

        private final RuntimeException failure;

        final List<List<Object>> asked = new ArrayList<>();

        AnsweringStore(PatientMatches answer, RuntimeException failure) {
            this.answer = answer;
            this.failure = failure;
        }

        @Override
        public Optional<Patient> findById(PatientId id) {
            throw new UnsupportedOperationException("a search reads no Patient by id");
        }

        @Override
        public PatientMatches search(PatientQuery query, PatientFilter filter, PatientSort sort,
                PageRequest page) {
            asked.add(Arrays.asList(query, filter, sort, page));
            if (failure != null) {
                throw failure;
            }
            return answer;
        }
    }

    private final RecordingAuditTrail trail = new RecordingAuditTrail();

    private PatientSearchService searching(PatientStore store) {
        return new PatientSearchService(store, trail, () -> Optional.of(SEARCHER));
    }

    @Test
    void aSearchAnswersTheSourcesPageAsTheSourceAnsweredIt() {
        AnsweringStore store = new AnsweringStore(TWO_OF_SEVEN, null);

        PatientMatches answered = searching(store)
                .search(new PatientQuery.ByName("tan"), PatientFilter.NONE, PatientSort.DEFAULT, new PageRequest(6, 5));

        assertThat(List.of(answered, store.asked)).containsExactly(
                TWO_OF_SEVEN,
                List.of(List.of(new PatientQuery.ByName("tan"), PatientFilter.NONE,
                        PatientSort.DEFAULT, new PageRequest(6, 5))));
    }

    /** The filters and the sort are the source's to apply: each reaches it as it was asked. */
    @Test
    void aFilteredSortedSearchIsTheSourcesAsAsked() {
        AnsweringStore store = new AnsweringStore(TWO_OF_SEVEN, null);
        PatientFilter filter = new PatientFilter(AdministrativeGender.MALE,
                new DateRange(LocalDate.of(1960, 1, 1), null), DateRange.ANY);
        PatientSort sort = new PatientSort(PatientSort.Field.BIRTH_DATE, PatientSort.Order.ASCENDING);

        searching(store).search(new PatientQuery.ByName("tan"), filter, sort, new PageRequest(1, 5));

        assertThat(store.asked).containsExactly(
                List.of(new PatientQuery.ByName("tan"), filter, sort, new PageRequest(1, 5)));
    }

    /**
     * The row names each filter the search used, after the query's kind, and none of their
     * values; one case per filter, so each is shown reaching the row.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "-", delimiter = '|', textBlock = """
        MALE   | -          | -          | -          | -          | 2 NAME and gender eq ?
        -      | 1980-01-01 | -          | -          | -          | 2 NAME and birthDate ge ?
        -      | -          | 1989-12-31 | -          | -          | 2 NAME and birthDate le ?
        -      | -          | -          | 2026-09-01 | -          | 2 NAME and lastVisit ge ?
        -      | -          | -          | -          | 2026-10-31 | 2 NAME and lastVisit le ?
        FEMALE | 1980-01-01 | 1989-12-31 | 2026-09-01 | 2026-10-31 | 2 NAME and gender eq ? and birthDate ge ? and birthDate le ? and lastVisit ge ? and lastVisit le ?
        """)
    void aFilteredSearchRecordsWhichFiltersItUsed(AdministrativeGender gender,
            LocalDate bornFrom, LocalDate bornTo, LocalDate visitedFrom, LocalDate visitedTo,
            String recorded) {
        searching(new AnsweringStore(TWO_OF_SEVEN, null)).search(new PatientQuery.ByName("tan"),
                new PatientFilter(gender, new DateRange(bornFrom, bornTo),
                        new DateRange(visitedFrom, visitedTo)),
                PatientSort.DEFAULT, new PageRequest(1, 5));

        assertThat(trail.recorded()).extracting(Recorded::detail).containsExactly(recorded);
    }

    /** The sort is no filter: a sorted search records its kind alone. */
    @Test
    void aSortedSearchRecordsNoFilter() {
        searching(new AnsweringStore(TWO_OF_SEVEN, null)).search(new PatientQuery.ByName("tan"),
                PatientFilter.NONE,
                new PatientSort(PatientSort.Field.BIRTH_DATE, PatientSort.Order.DESCENDING),
                new PageRequest(1, 5));

        assertThat(trail.recorded()).extracting(Recorded::detail).containsExactly("2 NAME");
    }

    /** The count is the Patients the answered page held, never the total the source counted. */
    @Test
    void aNameSearchRecordsItsSearcherThePagesCountAndItsKind() {
        searching(new AnsweringStore(TWO_OF_SEVEN, null))
                .search(new PatientQuery.ByName("tan"), PatientFilter.NONE, PatientSort.DEFAULT, new PageRequest(1, 5));

        assertThat(trail.recorded()).containsExactly(
                new Recorded(AuditOperation.PATIENT_SEARCH, SEARCHER, null, "2 NAME"));
    }

    @Test
    void anNricSearchRecordsItsKind() {
        searching(new AnsweringStore(new PatientMatches(1, List.of(match("dev-patient-05"))),
                null))
                .search(new PatientQuery.ByNric("S9012345A"), PatientFilter.NONE, PatientSort.DEFAULT, new PageRequest(1, 5));

        assertThat(trail.recorded()).containsExactly(
                new Recorded(AuditOperation.PATIENT_SEARCH, SEARCHER, null, "1 NRIC"));
    }

    /** A search the source failed read nothing: it records nothing, and fails as the source did. */
    @Test
    void aSearchTheSourceFailedRecordsNothing() {
        IllegalStateException failed = new IllegalStateException("the source failed");

        Throwable thrown = catchThrowable(() -> searching(new AnsweringStore(null, failed))
                .search(new PatientQuery.ByName("tan"), PatientFilter.NONE, PatientSort.DEFAULT, new PageRequest(1, 5)));

        assertThat(List.of(thrown, trail.recorded())).containsExactly(failed, List.of());
    }

    /** A search with no query has no kind to record: it fails, and records nothing. */
    @Test
    void aSearchWithNoQueryRecordsNothing() {
        Throwable thrown = catchThrowable(() -> searching(new AnsweringStore(TWO_OF_SEVEN, null))
                .search(null, PatientFilter.NONE, PatientSort.DEFAULT, new PageRequest(1, 5)));

        assertThat(List.of(thrown.getClass(), trail.recorded()))
                .containsExactly(NullPointerException.class, List.of());
    }
}
