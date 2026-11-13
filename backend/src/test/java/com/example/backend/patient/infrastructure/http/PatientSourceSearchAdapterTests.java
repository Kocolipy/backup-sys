package com.example.backend.patient.infrastructure.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.backend.observability.LogEvent.ErrorCategory;
import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.PatientSourceStubs;
import com.example.backend.patient.domain.AdministrativeGender;
import com.example.backend.patient.domain.DateRange;
import com.example.backend.patient.domain.PatientFilter;
import com.example.backend.patient.domain.PatientQuery;
import com.example.backend.patient.domain.PatientSort;
import com.example.backend.patient.domain.SortOrder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.client.RestClient;

/**
 * A Patient search as the HTTP adapter makes it (ADR 0018), against the patient source's mock in
 * process: the one call it sends — the source's own search, sorted, paged and counted there — and
 * what a failure of that call is. What the canned searches answer is {@code PatientStoreContract}'s;
 * each Patient's own rules are the read by id's ({@code PatientSourceAdapterContractTests}), which
 * a search's Patient is held to through one wiring case here.
 */
class PatientSourceSearchAdapterTests {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    private static final PageRequest SECOND_PAGE_OF_FIVE = new PageRequest(6, 5);

    /** A name no stub file names, for a case's own stub, and which no message may quote. */
    private static final String PROBE_NAME = "Quah Seow Peng";

    private static final String PROBE_SEARCH = "/Patient";

    /** A searchset holding one Patient, its {@code id} and Last visit as the case writes them. */
    private static final String ONE_MATCH = """
            {"resourceType": "Bundle", "type": "searchset", %s
             "entry": [{"resource": {"resourceType": "Patient", %s
               "extension": [{"url": "https://fhir.synapxe.sg/StructureDefinition/last-visit",
                              "valueDate": "%s"}],
               "identifier": [{"system": "https://fhir.synapxe.sg/NamingSystem/nric",
                               "value": "T7391046Q"}],
               "name": [{"use": "official", "text": "Quah Seow Peng"}],
               "gender": "female", "birthDate": "1963-07-29"}}]}""";

    private static String oneMatch(String total, String id, String lastVisit) {
        return ONE_MATCH.formatted(total, id, lastVisit);
    }

    private static final String WELL_FORMED =
            oneMatch("\"total\": 1,", "\"id\": \"probe-Q7xKd2.v9\",", "2026-01-02");

    private static PatientSourceAdapter adapter() {
        return new PatientSourceAdapter(RestClient.builder(), SOURCE.baseUrl());
    }

    private static void answering(String body) {
        SOURCE.stubFor(get(urlPathEqualTo(PROBE_SEARCH)).atPriority(1).willReturn(okJson(body)));
    }

    /** A fault as its record reports it: its fixed message, its reason and its category. */
    private record Fault(String message, String reason, ErrorCategory category) {
    }

    private static Fault faultSearching(PatientSourceAdapter adapter) {
        PatientSourceException fault = (PatientSourceException) catchThrowable(() ->
                adapter.search(new PatientQuery.ByName(PROBE_NAME), PatientFilter.NONE, PatientSort.DEFAULT, SECOND_PAGE_OF_FIVE));
        return new Fault(fault.getMessage(), fault.reason(), fault.category());
    }

    /** The one search request the source received, its query parameters by name. */
    private static Map<String, String> sentQuery() {
        LoggedRequest sent = SOURCE.findAll(getRequestedFor(urlPathEqualTo(PROBE_SEARCH)))
                .getFirst();
        Map<String, String> query = new TreeMap<>();
        sent.getQueryParams().forEach((name, value) -> query.put(name, value.firstValue()));
        return query;
    }

    // ---- the call ------------------------------------------------------------------------

    /**
     * A name search is the source's {@code name:contains}, sorted newest Last visit first with ties
     * by id, from the page's zero-based offset, for the page's count, with an accurate total.
     */
    @Test
    void aNameSearchAsksTheSourceToMatchSortPageAndCountIt() {
        adapter().search(new PatientQuery.ByName("Tan Hui"), PatientFilter.NONE, PatientSort.DEFAULT, SECOND_PAGE_OF_FIVE);

        assertThat(sentQuery()).isEqualTo(Map.of(
                "name:contains", "Tan Hui",
                "_sort", "-last-visit,_id",
                "_offset", "5",
                "_count", "5",
                "_total", "accurate"));
    }

    /** An NRIC search is the identifier under the NRIC NamingSystem, exactly. */
    @Test
    void anNricSearchAsksTheSourceForTheIdentifierUnderTheNricSystem() {
        adapter().search(new PatientQuery.ByNric("S9212345A"), PatientFilter.NONE, PatientSort.DEFAULT, new PageRequest(1, 5));

        assertThat(sentQuery()).isEqualTo(Map.of(
                "identifier", "https://fhir.synapxe.sg/NamingSystem/nric|S9212345A",
                "_sort", "-last-visit,_id",
                "_offset", "0",
                "_count", "5",
                "_total", "accurate"));
    }

    /** The one search request the source received, every value of each query parameter. */
    private static Map<String, List<String>> sentQueryValues() {
        LoggedRequest sent = SOURCE.findAll(getRequestedFor(urlPathEqualTo(PROBE_SEARCH)))
                .getFirst();
        Map<String, List<String>> query = new TreeMap<>();
        sent.getQueryParams().forEach((name, value) -> query.put(name, value.values()));
        return query;
    }

    private static void searchingTan(PatientFilter filter, PatientSort sort) {
        adapter().search(new PatientQuery.ByName("tan"), filter, sort, new PageRequest(1, 5));
    }

    /**
     * Each sort is the source's {@code _sort} on its own search parameter — {@code birthdate} or
     * {@code last-visit}, descending with a leading {@code -} — its ties still broken by id.
     */
    @ParameterizedTest
    @CsvSource({
        "BIRTH_DATE, ASCENDING, 'birthdate,_id'",
        "BIRTH_DATE, DESCENDING, '-birthdate,_id'",
        "LAST_VISIT, ASCENDING, 'last-visit,_id'",
        "LAST_VISIT, DESCENDING, '-last-visit,_id'"})
    void aSortIsTheSourcesOwnOnItsSearchParameter(PatientSort.Field field,
            SortOrder order, String sorted) {
        searchingTan(PatientFilter.NONE, new PatientSort(field, order));

        assertThat(sentQuery()).containsEntry("_sort", sorted);
    }

    /** A gender filter is the source's {@code gender}, by its FHIR code. */
    @ParameterizedTest
    @CsvSource({"MALE, male", "FEMALE, female"})
    void aGenderFilterIsTheSourcesGender(AdministrativeGender gender, String code) {
        searchingTan(new PatientFilter(gender, DateRange.ANY, DateRange.ANY), PatientSort.DEFAULT);

        assertThat(sentQueryValues()).isEqualTo(Map.of(
                "name:contains", List.of("tan"),
                "gender", List.of(code),
                "_sort", List.of("-last-visit,_id"),
                "_offset", List.of("0"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /**
     * A date range is its search parameter once for each end it has — {@code ge} the from-date and
     * {@code le} the to-date, both inclusive — for the date of birth {@code birthdate} and for the
     * Last visit {@code last-visit}.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "-", delimiter = '|', value = {
        "1980-01-01 | 1989-12-31 | ge1980-01-01,le1989-12-31",
        "1980-01-01 | -          | ge1980-01-01",
        "-          | 1989-12-31 | le1989-12-31"})
    void aDateRangeIsItsSearchParameterForEachEnd(LocalDate from, LocalDate to, String sent) {
        List<String> ends = List.of(sent.split(","));
        searchingTan(new PatientFilter(null, new DateRange(from, to), DateRange.ANY),
                PatientSort.DEFAULT);
        Map<String, List<String>> births = sentQueryValues();
        SOURCE.resetRequests();
        searchingTan(new PatientFilter(null, DateRange.ANY, new DateRange(from, to)),
                PatientSort.DEFAULT);
        Map<String, List<String>> visits = sentQueryValues();

        assertThat(Arrays.asList(births.get("birthdate"), births.containsKey("last-visit"),
                visits.get("last-visit"), visits.containsKey("birthdate")))
                .containsExactly(ends, false, ends, false);
    }

    /** Every filter at once, on an NRIC search as on a name search, each sent as its own. */
    @Test
    void everyFilterNarrowsTheSearchAtOnce() {
        adapter().search(new PatientQuery.ByNric("S9212345A"),
                new PatientFilter(AdministrativeGender.FEMALE,
                        new DateRange(LocalDate.of(1990, 1, 1), LocalDate.of(1999, 12, 31)),
                        new DateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))),
                new PatientSort(PatientSort.Field.BIRTH_DATE, SortOrder.DESCENDING),
                new PageRequest(1, 5));

        assertThat(sentQueryValues()).isEqualTo(Map.of(
                "identifier", List.of("https://fhir.synapxe.sg/NamingSystem/nric|S9212345A"),
                "gender", List.of("female"),
                "birthdate", List.of("ge1990-01-01", "le1999-12-31"),
                "last-visit", List.of("ge2026-07-01", "le2026-07-31"),
                "_sort", List.of("-birthdate,_id"),
                "_offset", List.of("0"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /** As a read by id, a search presents nothing to the source, and asks for FHIR JSON. */
    @Test
    void aSearchPresentsNoCredentialAndAsksForFhirJson() {
        adapter().search(new PatientQuery.ByName("tan"), PatientFilter.NONE, PatientSort.DEFAULT, new PageRequest(1, 5));

        LoggedRequest sent = SOURCE.findAll(getRequestedFor(urlPathEqualTo(PROBE_SEARCH)))
                .getFirst();
        assertThat(List.of(sent.containsHeader("Authorization"), sent.containsHeader("Cookie"),
                sent.getHeader("Accept")))
                .containsExactly(false, false, "application/fhir+json");
    }

    /** A search needs a query: none is refused before anything is sent. */
    @Test
    void aSearchWithNoQueryIsRefusedBeforeTheSourceIsCalled() {
        Throwable refused = catchThrowable(() -> adapter().search(null, PatientFilter.NONE, PatientSort.DEFAULT, new PageRequest(1, 5)));

        assertThat(refused).isInstanceOf(NullPointerException.class);
        SOURCE.verify(0, getRequestedFor(urlPathEqualTo(PROBE_SEARCH)));
    }

    /** Nothing is cached: the same search twice is two calls to the source. */
    @Test
    void everySearchIsACallToTheSource() {
        PatientSourceAdapter adapter = adapter();

        adapter.search(new PatientQuery.ByName("tan"), PatientFilter.NONE, PatientSort.DEFAULT, new PageRequest(1, 5));
        adapter.search(new PatientQuery.ByName("tan"), PatientFilter.NONE, PatientSort.DEFAULT, new PageRequest(1, 5));

        SOURCE.verify(2, getRequestedFor(urlPathEqualTo(PROBE_SEARCH)));
    }

    /** A Patient with a Last visit is read with it, and one with none with none. */
    @Test
    void aMatchIsReadWithItsLastVisit() {
        answering(WELL_FORMED);

        assertThat(adapter().search(new PatientQuery.ByName(PROBE_NAME), PatientFilter.NONE, PatientSort.DEFAULT, SECOND_PAGE_OF_FIVE)
                .patients().getFirst().lastVisit()).hasToString("2026-01-02");
    }

    /** Only the Last visit extension is read: another ahead of it, or alone, is not. */
    @Test
    void anotherExtensionIsNoLastVisit() {
        String another = """
                {"url": "https://fhir.synapxe.sg/StructureDefinition/other", "valueDate": "1999-09-09"},""";
        answering(WELL_FORMED.replace("\"extension\": [", "\"extension\": [" + another));
        PatientSourceAdapter adapter = adapter();
        String firstLastVisit = String.valueOf(adapter.search(
                new PatientQuery.ByName(PROBE_NAME), PatientFilter.NONE, PatientSort.DEFAULT, SECOND_PAGE_OF_FIVE)
                .patients().getFirst().lastVisit());
        answering(WELL_FORMED.replace(
                "https://fhir.synapxe.sg/StructureDefinition/last-visit",
                "https://fhir.synapxe.sg/StructureDefinition/other"));

        assertThat(List.of(firstLastVisit, String.valueOf(adapter.search(
                new PatientQuery.ByName(PROBE_NAME), PatientFilter.NONE, PatientSort.DEFAULT, SECOND_PAGE_OF_FIVE)
                .patients().getFirst().lastVisit())))
                .containsExactly("2026-01-02", "null");
    }

    // ---- a failure of the source ---------------------------------------------------------

    /**
     * A search has one good answer, a searchset: a {@code 404} is no "nothing found" here, but a
     * fault like any other status.
     */
    @ParameterizedTest
    @CsvSource({
        "404, unexpected-status, DATA",
        "400, unexpected-status, DATA",
        "302, unexpected-status, DATA",
        "503, server-error, SERVER"})
    void anAnswerOtherThanASearchsetIsAFault(int status, String reason, ErrorCategory category) {
        SOURCE.stubFor(get(urlPathEqualTo(PROBE_SEARCH)).atPriority(1)
                .willReturn(aResponse().withStatus(status)));

        assertThat(faultSearching(adapter()))
                .isEqualTo(new Fault("The patient source answered " + status, reason, category));
    }

    @Test
    void aSearchUnansweredWithinTheReadTimeoutTimesOut() {
        SOURCE.stubFor(get(urlPathEqualTo(PROBE_SEARCH)).atPriority(1)
                .willReturn(okJson(WELL_FORMED).withFixedDelay(1000)));
        PatientSourceAdapter impatient = new PatientSourceAdapter(RestClient.builder(),
                SOURCE.baseUrl(), Duration.ofSeconds(2), Duration.ofMillis(100));

        assertThat(faultSearching(impatient)).isEqualTo(new Fault(
                "The patient source did not answer in time", "timed-out", ErrorCategory.NETWORK));
    }

    /**
     * A {@code 200} the service cannot take as a page of matches, each naming what it lacks: no
     * searchset, no total — so no telling how many pages there are — or a match with no id, an id
     * outside FHIR's grammar, no Patient at all, or a Last visit not to the day.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        {"resourceType": "Bundle", "type": "collection", "total": 0} | not-a-searchset | The patient source's page is no searchset
        {"resourceType": "OperationOutcome", "type": "searchset", "total": 0} | not-a-searchset | The patient source's page is no searchset
        {"resourceType": "Bundle", "type": "searchset"} | no-total | The patient source's page has no total
        {"resourceType": "Bundle", "type": "searchset", "total": -1} | no-total | The patient source's page has no total
        {"resourceType": "Bundle", "type": "searchset", "total": 1, "entry": [{}]} | another-resource | The patient source answered with another resource
        {"resourceType": "Bundle", "type": "searchset", "total": 1, "entry": [{"resource": {"resourceType": "OperationOutcome", "id": "x"}}]} | another-resource | The patient source answered with another resource
        """)
    void aSearchsetTheServiceCannotHoldIsAFault(String body, String reason, String message) {
        answering(body);

        assertThat(faultSearching(adapter()))
                .isEqualTo(new Fault(message, reason, ErrorCategory.DATA));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        '' | 2026-01-02 | no-id | The patient source's Patient has no id
        "id": "probe Q7xKd2", | 2026-01-02 | invalid-id | The patient source's Patient's id is not a FHIR id
        "id": "probe-Q7xKd2.v9", | 2026-01 | last-visit-not-to-the-day | The patient source's Patient's last visit is not to the day
        """)
    void aMatchTheServiceCannotHoldIsAFault(String id, String lastVisit, String reason,
            String message) {
        answering(oneMatch("\"total\": 1,", id, lastVisit));

        assertThat(faultSearching(adapter()))
                .isEqualTo(new Fault(message, reason, ErrorCategory.DATA));
    }

    /** A match is held to every rule a Patient read by id is: one with no NRIC is a fault. */
    @Test
    void aMatchBreakingAPatientsRuleIsAFault() {
        answering(WELL_FORMED.replace("https://fhir.synapxe.sg/NamingSystem/nric", "urn:other"));

        assertThat(faultSearching(adapter())).isEqualTo(new Fault(
                "The patient source's Patient has no NRIC", "no-nric", ErrorCategory.DATA));
    }

    /**
     * A failed search names nothing it searched for or got: not the name asked, nor anything of
     * the answer, in its message, its cause or anything a record of it would print.
     */
    @Test
    void aFailedSearchSaysNothingOfTheQueryOrTheAnswer() {
        answering(oneMatch("", "\"id\": \"probe-Q7xKd2.v9\",", "2026-01-02"));

        Throwable fault = catchThrowable(() ->
                adapter().search(new PatientQuery.ByName(PROBE_NAME), PatientFilter.NONE, PatientSort.DEFAULT, SECOND_PAGE_OF_FIVE));

        StringWriter printed = new StringWriter();
        fault.printStackTrace(new PrintWriter(printed));
        assertThat(printed.toString())
                .doesNotContain(PROBE_NAME, "Quah", "T7391046Q", "1963-07-29", "probe-Q7xKd2");
    }
}
