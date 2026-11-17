package com.example.backend.patient.infrastructure.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.example.backend.audit.CapturedLog;
import com.example.backend.observability.LogEvent;
import com.example.backend.observability.LogEvent.ErrorCategory;
import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.PatientSourceStubs;
import com.example.backend.patient.domain.AdministrativeGender;
import com.example.backend.patient.domain.Patient;
import com.example.backend.patient.domain.PatientFilter;
import com.example.backend.patient.domain.PatientMatch;
import com.example.backend.patient.domain.PatientMatches;
import com.example.backend.patient.domain.PatientQuery;
import com.example.backend.patient.domain.SourceField;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

/**
 * A Patient search as the HTTP adapter makes it (ADR 0018), against the patient source's mock in
 * process: the one call it sends — the source's own search, paged and counted there, in its own
 * order — how it reads its answer, as received (ADR 0020) but for a match's id, which is
 * validated and a match without a good one dropped, and what a failure of that call is. What
 * the canned searches answer is {@code PatientStoreContract}'s; how each of a Patient's fields is
 * classified is {@code ReceivedFieldsTests}', which a search's Patient is held to through one
 * wiring case per field here.
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
        return PatientSourceAdapters.at(SOURCE.baseUrl());
    }

    private static PatientSourceAdapter adapter(MeterRegistry meters) {
        return new PatientSourceAdapter(RestClient.builder(), SOURCE.baseUrl(), meters);
    }

    private static void answering(String body) {
        SOURCE.stubFor(get(urlPathEqualTo(PROBE_SEARCH)).atPriority(1).willReturn(okJson(body)));
    }

    /** A fault as its record reports it: its fixed message, its reason and its category. */
    private record Fault(String message, String reason, ErrorCategory category) {
    }

    private static Fault faultSearching(PatientSourceAdapter adapter) {
        PatientSourceException fault = (PatientSourceException) catchThrowable(() -> adapter.search(
                new PatientQuery.ByName(PROBE_NAME), PatientFilter.NONE, SECOND_PAGE_OF_FIVE));
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
     * A name search is the source's {@code name:contains}, from the page's zero-based offset, for
     * the page's count, with an accurate total, and no {@code _sort}: the source's own order.
     */
    @Test
    void aNameSearchAsksTheSourceToMatchPageAndCountIt() {
        adapter().search(
                new PatientQuery.ByName("Tan Hui"), PatientFilter.NONE, SECOND_PAGE_OF_FIVE);

        assertThat(sentQuery()).isEqualTo(Map.of(
                "name:contains", "Tan Hui",
                "_offset", "5",
                "_count", "5",
                "_total", "accurate"));
    }

    /** An NRIC search is the identifier under the NRIC NamingSystem, exactly. */
    @Test
    void anNricSearchAsksTheSourceForTheIdentifierUnderTheNricSystem() {
        adapter().search(
                new PatientQuery.ByNric("S9212345A"), PatientFilter.NONE, new PageRequest(1, 5));

        assertThat(sentQuery()).isEqualTo(Map.of(
                "identifier", "https://fhir.synapxe.sg/NamingSystem/nric|S9212345A",
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

    private static void searchingTan(PatientFilter filter) {
        adapter().search(new PatientQuery.ByName("tan"), filter, new PageRequest(1, 5));
    }

    /** A gender filter is the source's {@code gender}, by its FHIR code. */
    @ParameterizedTest
    @CsvSource({"MALE, male", "FEMALE, female"})
    void aGenderFilterIsTheSourcesGender(AdministrativeGender gender, String code) {
        searchingTan(new PatientFilter(gender));

        assertThat(sentQueryValues()).isEqualTo(Map.of(
                "name:contains", List.of("tan"),
                "gender", List.of(code),
                "_offset", List.of("0"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /** A gender filter narrows an NRIC search as it does a name search. */
    @Test
    void aGenderFilterNarrowsAnNricSearch() {
        adapter().search(new PatientQuery.ByNric("S9212345A"),
                new PatientFilter(AdministrativeGender.FEMALE), new PageRequest(1, 5));

        assertThat(sentQueryValues()).isEqualTo(Map.of(
                "identifier", List.of("https://fhir.synapxe.sg/NamingSystem/nric|S9212345A"),
                "gender", List.of("female"),
                "_offset", List.of("0"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /** As a read by id, a search presents nothing to the source, and asks for FHIR JSON. */
    @Test
    void aSearchPresentsNoCredentialAndAsksForFhirJson() {
        adapter().search(new PatientQuery.ByName("tan"), PatientFilter.NONE, new PageRequest(1, 5));

        LoggedRequest sent = SOURCE.findAll(getRequestedFor(urlPathEqualTo(PROBE_SEARCH)))
                .getFirst();
        assertThat(List.of(sent.containsHeader("Authorization"), sent.containsHeader("Cookie"),
                sent.getHeader("Accept")))
                .containsExactly(false, false, "application/fhir+json");
    }

    /** A search needs a query: none is refused before anything is sent. */
    @Test
    void aSearchWithNoQueryIsRefusedBeforeTheSourceIsCalled() {
        Throwable refused = catchThrowable(() -> adapter().search(
                null, PatientFilter.NONE, new PageRequest(1, 5)));

        assertThat(refused).isInstanceOf(NullPointerException.class);
        SOURCE.verify(0, getRequestedFor(urlPathEqualTo(PROBE_SEARCH)));
    }

    /** Nothing is cached: the same search twice is two calls to the source. */
    @Test
    void everySearchIsACallToTheSource() {
        PatientSourceAdapter adapter = adapter();

        adapter.search(new PatientQuery.ByName("tan"), PatientFilter.NONE, new PageRequest(1, 5));
        adapter.search(new PatientQuery.ByName("tan"), PatientFilter.NONE, new PageRequest(1, 5));

        SOURCE.verify(2, getRequestedFor(urlPathEqualTo(PROBE_SEARCH)));
    }

    /** The Last visit of the one match {@code adapter} reads from the probe search. */
    private static SourceField<LocalDate> lastVisitSearching(PatientSourceAdapter adapter) {
        return ofPatient(adapter.search(
                new PatientQuery.ByName(PROBE_NAME), PatientFilter.NONE, SECOND_PAGE_OF_FIVE)
                .patients().getFirst()).lastVisit();
    }

    /** A Patient with a Last visit is read with it. */
    @Test
    void aMatchIsReadWithItsLastVisit() {
        answering(WELL_FORMED);

        assertThat(lastVisitSearching(adapter()))
                .isEqualTo(SourceField.present(LocalDate.of(2026, 1, 2)));
    }

    /** Only the Last visit extension is read: another ahead of it is passed over, and alone is none. */
    @Test
    void anotherExtensionIsNoLastVisit() {
        String another = """
                {"url": "https://fhir.synapxe.sg/StructureDefinition/other", "valueDate": "1999-09-09"},""";
        answering(WELL_FORMED.replace("\"extension\": [", "\"extension\": [" + another));
        PatientSourceAdapter adapter = adapter();
        SourceField<LocalDate> behindAnother = lastVisitSearching(adapter);
        answering(WELL_FORMED.replace(
                "https://fhir.synapxe.sg/StructureDefinition/last-visit",
                "https://fhir.synapxe.sg/StructureDefinition/other"));

        assertThat(List.of(behindAnother, lastVisitSearching(adapter))).containsExactly(
                SourceField.present(LocalDate.of(2026, 1, 2)), SourceField.missing());
    }

    /**
     * A Last visit is shown as received (ADR 0020): one in the wrong format — no date to the day,
     * a day the calendar lacks, the wrong JSON type — is invalid, and one sent {@code null} or
     * blank is missing, and neither fails the search.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        "2026-01" | INVALID
        "2026-02-30" | INVALID
        "02 Jan 2026" | INVALID
        20260102 | INVALID
        null | MISSING
        " " | MISSING
        """)
    void aLastVisitIsReadAsReceived(String valueDate, SourceField.Received received) {
        answering(WELL_FORMED.replace("\"2026-01-02\"", valueDate));

        assertThat(lastVisitSearching(adapter()).received()).isEqualTo(received);
    }

    // ---- an invalid Last visit, reported -------------------------------------------------

    /** One match of {@link #WELL_FORMED}'s searchset, as its entry. */
    private static final String WELL_FORMED_ENTRY = WELL_FORMED.substring(
            WELL_FORMED.indexOf("{\"resource\""), WELL_FORMED.lastIndexOf(']'));

    /** Two matches, the second's Last visit, behind another extension, not to the day. */
    private static final String SECOND_MATCH_WITH_AN_INVALID_LAST_VISIT = """
            {"resourceType": "Bundle", "type": "searchset", "total": 2,
             "entry": [%s, %s]}""".formatted(WELL_FORMED_ENTRY, WELL_FORMED_ENTRY
            .replace("probe-Q7xKd2.v9", "probe-Q7xKd3.v9")
            .replace("\"extension\": [", "\"extension\": [{\"url\": \"urn:other\"},")
            .replace("\"2026-01-02\"", "\"2026-01\""));

    /** The WARN records reporting a response's invalid fields. */
    private static List<ILoggingEvent> invalidFieldRecords(CapturedLog captured) {
        return captured.withAction(Level.WARN, LogEvent.LOCAL_ACTION,
                "patient_source.invalid_fields");
    }

    private static void searchingProbe(PatientSourceAdapter adapter) {
        adapter.search(new PatientQuery.ByName(PROBE_NAME), PatientFilter.NONE, SECOND_PAGE_OF_FIVE);
    }

    /**
     * A search whose matches hold an invalid Last visit is one WARN, naming the read and each by
     * its path in the answer — never its value, nor the Patient.
     */
    @Test
    void anInvalidLastVisitIsOneWarnNamingItsPath() {
        answering(SECOND_MATCH_WITH_AN_INVALID_LAST_VISIT);

        try (CapturedLog captured = CapturedLog.attach()) {
            searchingProbe(adapter());

            assertThat(invalidFieldRecords(captured)).singleElement()
                    .satisfies(warning -> assertThat(CapturedLog.fields(warning))
                            .containsEntry("app.patient_source.read", "search")
                            .containsEntry("app.patient_source.invalid_fields",
                                    List.of("entry[1].extension[1].valueDate")));
        }
    }

    @Test
    void theWarnQuotesNoValueOfTheAnswerNorThePatient() {
        answering(SECOND_MATCH_WITH_AN_INVALID_LAST_VISIT);

        try (CapturedLog captured = CapturedLog.attach()) {
            searchingProbe(adapter());

            ILoggingEvent warning = invalidFieldRecords(captured).getFirst();
            assertThat(warning.getFormattedMessage() + " " + CapturedLog.fields(warning))
                    .doesNotContain("2026", "probe-Q7xKd", PROBE_NAME, "T7391046Q", "1963");
        }
    }

    /** A search whose every Last visit is present or missing reports nothing. */
    @Test
    void aSearchWithNoInvalidLastVisitIsNotReported() {
        answering(WELL_FORMED.replace("\"2026-01-02\"", "null"));
        MeterRegistry meters = new SimpleMeterRegistry();

        try (CapturedLog captured = CapturedLog.attach()) {
            searchingProbe(adapter(meters));

            assertThat(List.of(invalidFieldRecords(captured),
                    meters.find("patient_source.invalid_fields").counters()))
                    .containsExactly(List.of(), List.of());
        }
    }

    /** Each invalid Last visit counts once, tagged by the read and the row's field. */
    @Test
    void eachInvalidLastVisitCountsOnceByTheReadAndTheField() {
        answering(SECOND_MATCH_WITH_AN_INVALID_LAST_VISIT);
        MeterRegistry meters = new SimpleMeterRegistry();
        PatientSourceAdapter adapter = adapter(meters);

        searchingProbe(adapter);
        searchingProbe(adapter);

        Map<String, Double> counted = new TreeMap<>();
        for (Counter counter : meters.find("patient_source.invalid_fields").counters()) {
            counted.put(counter.getId().getTag("read") + " " + counter.getId().getTag("field"),
                    counter.count());
        }
        assertThat(counted).isEqualTo(Map.of("search lastVisit", 2.0));
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
        PatientSourceAdapter impatient = PatientSourceAdapters.at(SOURCE.baseUrl(), Duration.ofSeconds(2), Duration.ofMillis(100));

        assertThat(faultSearching(impatient)).isEqualTo(new Fault(
                "The patient source did not answer in time", "timed-out", ErrorCategory.NETWORK));
    }

    /**
     * A {@code 200} the service cannot take as a page of matches, each naming what it lacks: no
     * searchset, no total — so no telling how many pages there are — or an entry with no
     * resource at all. What a match's fields hold, its id included, is none of these.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        {"resourceType": "Bundle", "type": "collection", "total": 0} | not-a-searchset | The patient source's page is no searchset
        {"resourceType": "OperationOutcome", "type": "searchset", "total": 0} | not-a-searchset | The patient source's page is no searchset
        {"resourceType": "Bundle", "type": "searchset"} | no-total | The patient source's page has no total
        {"resourceType": "Bundle", "type": "searchset", "total": -1} | no-total | The patient source's page has no total
        {"resourceType": "Bundle", "type": "searchset", "total": 1, "entry": [{}]} | another-resource | The patient source answered with another resource
        {"resourceType": "Bundle", "type": "searchset", "total": 1, "entry": [{"resource": null}]} | another-resource | The patient source answered with another resource
        """)
    void aSearchsetTheServiceCannotHoldIsAFault(String body, String reason, String message) {
        answering(body);

        assertThat(faultSearching(adapter()))
                .isEqualTo(new Fault(message, reason, ErrorCategory.DATA));
    }

    // ---- the id, validated ---------------------------------------------------------------

    private static PatientMatches searchingTheProbe(PatientSourceAdapter adapter) {
        return adapter.search(
                new PatientQuery.ByName(PROBE_NAME), PatientFilter.NONE, SECOND_PAGE_OF_FIVE);
    }

    /**
     * A match's id is the one field validated: a match with none, or one outside FHIR's id
     * grammar — a blank, a space, 65 characters, the wrong JSON type — is dropped from the page,
     * which counts it, and the search is answered with the source's total unchanged.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "\"id\": null,",
        "\"id\": \"\",",
        "\"id\": \"probe Q7xKd2\",",
        "\"id\": \"probe-Q7xKd2-v9-0123456789012345678901234567890123456789012345678\",",
        "\"id\": 7391046,"})
    void aMatchWithAMissingOrMalformedIdIsDroppedAndCounted(String id) {
        answering(oneMatch("\"total\": 3,", id, "2026-01-02"));

        PatientMatches page = searchingTheProbe(adapter());

        assertThat(List.of(page.totalResults(), page.patients().size(), page.droppedResults()))
                .containsExactly(3, 0, 1);
    }

    /** An id of exactly 64 characters is in FHIR's grammar: the match is shown. */
    @Test
    void aMatchWithAnIdOfSixtyFourCharactersIsShown() {
        String longest = "probe-Q7xKd2-v9-012345678901234567890123456789012345678901234567";
        answering(oneMatch("\"total\": 1,", "\"id\": \"" + longest + "\",", "2026-01-02"));

        PatientMatches page = searchingTheProbe(adapter());

        assertThat(List.of(ofPatient(page.patients().getFirst()).patient().id().value(),
                page.droppedResults())).containsExactly(longest, 0);
    }

    /** One entry of {@link #WELL_FORMED}'s searchset, its id {@code probe-<n>}. */
    private static String entry(int n) {
        return WELL_FORMED_ENTRY.replace("probe-Q7xKd2.v9", "probe-" + n);
    }

    /** Only the matches with a bad id are dropped: the rest are shown, in the source's order. */
    @Test
    void theOtherMatchesAreShownInTheSourcesOrder() {
        answering("""
                {"resourceType": "Bundle", "type": "searchset", "total": 9,
                 "entry": [%s, %s, %s]}""".formatted(entry(1),
                WELL_FORMED_ENTRY.replace("\"id\": \"probe-Q7xKd2.v9\",", ""), entry(3)));

        PatientMatches page = searchingTheProbe(adapter());

        assertThat(List.of(page.patients().stream()
                        .map(match -> ofPatient(match).patient().id().value()).toList(),
                page.droppedResults(), page.totalResults()))
                .containsExactly(List.of("probe-1", "probe-3"), 1, 9);
    }

    // ---- the other fields, shown as received ---------------------------------------------

    /** The Patient of the one match the probe search reads. */
    private static Patient patientSearching(PatientSourceAdapter adapter) {
        return ofPatient(searchingTheProbe(adapter).patients().getFirst()).patient();
    }

    /** {@code match} as the Patient's match it is expected to be. */
    private static PatientMatch.OfPatient ofPatient(PatientMatch match) {
        return (PatientMatch.OfPatient) match;
    }

    /**
     * A match's Patient is read as a Patient read by id is, as received (ADR 0020): each of its
     * fields missing or in the wrong format is held so — no NRIC under the NRIC system, one of the
     * wrong JSON type, no official name, a name text blank or of the wrong JSON type, no gender,
     * a birth date missing or not to the day — and none fails the search.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        https://fhir.synapxe.sg/NamingSystem/nric | urn:other | NRIC | MISSING
        "value": "T7391046Q" | "value": 7391046 | NRIC | INVALID
        "use": "official" | "use": "usual" | NAME_TEXT | MISSING
        "text": "Quah Seow Peng" | "text": " " | NAME_TEXT | MISSING
        "text": "Quah Seow Peng" | "text": 7 | NAME_TEXT | INVALID
        "gender": "female", | '' | GENDER | MISSING
        "gender": "female" | "gender": true | GENDER | INVALID
        , "birthDate": "1963-07-29" | '' | BIRTH_DATE | MISSING
        "birthDate": "1963-07-29" | "birthDate": "1963-07" | BIRTH_DATE | INVALID
        """)
    void aMatchsPatientIsReadAsReceived(String element, String replaced,
            Patient.Element field, SourceField.Received received) {
        answering(WELL_FORMED.replace(element, replaced));

        Patient patient = patientSearching(adapter());

        SourceField<?> read = switch (field) {
            case NRIC -> patient.nric();
            case NAME_TEXT -> patient.name() == null ? SourceField.missing() : patient.name().text();
            case GENDER -> patient.gender();
            case BIRTH_DATE -> patient.birthDate();
        };
        assertThat(read.received()).isEqualTo(received);
    }

    /** A gender outside the four codes is present, as the raw code the source sent. */
    @Test
    void aGenderOutsideTheFourIsReadAsItsRawCode() {
        answering(WELL_FORMED.replace("\"gender\": \"female\"", "\"gender\": \"F\""));

        assertThat(patientSearching(adapter()).gender()).isEqualTo(SourceField.present("F"));
    }

    /** A well-formed match is read with each of its fields present, as it came. */
    @Test
    void aWellFormedMatchIsReadWithEachFieldPresent() {
        answering(WELL_FORMED);

        Patient patient = patientSearching(adapter());

        assertThat(List.of(patient.id().value(), patient.nric(), patient.name().text(),
                patient.gender(), patient.birthDate())).containsExactly("probe-Q7xKd2.v9",
                SourceField.present("T7391046Q"), SourceField.present("Quah Seow Peng"),
                SourceField.present("female"), SourceField.present(LocalDate.of(1963, 7, 29)));
    }

    /**
     * An entry whose resource is no Patient — another resource, or one naming no type — is a
     * match with no Patient, its every field invalid, and the search is answered.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "{\"resourceType\": \"OperationOutcome\", \"id\": \"x\"}",
        "{\"id\": \"probe-Q7xKd2.v9\"}"})
    void anEntryThatIsNoPatientIsAMatchWithNoPatient(String resource) {
        answering("""
                {"resourceType": "Bundle", "type": "searchset", "total": 2,
                 "entry": [{"resource": %s}, %s]}""".formatted(resource, entry(2)));

        PatientMatches page = searchingTheProbe(adapter());

        assertThat(page.patients()).extracting(PatientMatch.OfPatient.class::isInstance, PatientMatch::invalid)
                .containsExactly(
                        tuple(false, List.of("name", "nric", "gender", "birthDate", "lastVisit")),
                        tuple(true, List.of()));
    }

    // ---- a page's dropped matches and invalid fields, reported ---------------------------

    /**
     * A page dropping a match, holding an entry that is no Patient and fields in the wrong
     * format: a name text and an NRIC of the wrong JSON type, each second in its list, no id,
     * another resource, a birth date and a Last visit not to the day.
     */
    private static final String A_PAGE_OF_EVERY_FAULT = """
            {"resourceType": "Bundle", "type": "searchset", "total": 4,
             "entry": [%s, %s, {"resource": {"resourceType": "OperationOutcome"}}, %s]}"""
            .formatted(
                    entry(1)
                            .replace("\"identifier\": [",
                                    "\"identifier\": [{\"system\": \"urn:mrn\", \"value\": \"9\"},")
                            .replace("\"value\": \"T7391046Q\"", "\"value\": 7391046")
                            .replace("\"name\": [", "\"name\": [{\"use\": \"usual\", \"text\": \"Q\"},")
                            .replace("\"text\": \"Quah Seow Peng\"", "\"text\": 7"),
                    WELL_FORMED_ENTRY.replace("\"id\": \"probe-Q7xKd2.v9\",", ""),
                    entry(4).replace("\"1963-07-29\"", "\"1963-07\"")
                            .replace("\"extension\": [", "\"extension\": [{\"url\": \"urn:other\"},")
                            .replace("\"2026-01-02\"", "\"2026-01\""));

    /**
     * A page's dropped matches, its entries that are no Patient and its invalid fields are one
     * WARN, each by its path in the answer, in the answer's order — the match's fields in the
     * row's — never a value, nor the Patient.
     */
    @Test
    void aPagesDroppedMatchesAndInvalidFieldsAreOneWarnNamingTheirPaths() {
        answering(A_PAGE_OF_EVERY_FAULT);

        try (CapturedLog captured = CapturedLog.attach()) {
            searchingProbe(adapter());

            assertThat(invalidFieldRecords(captured)).singleElement()
                    .satisfies(warning -> assertThat(CapturedLog.fields(warning))
                            .containsEntry("app.patient_source.read", "search")
                            .containsEntry("app.patient_source.invalid_fields", List.of(
                                    "entry[0].name[1].text",
                                    "entry[0].identifier[1].value",
                                    "entry[1].id",
                                    "entry[2].resourceType",
                                    "entry[3].birthDate",
                                    "entry[3].extension[1].valueDate")));
        }
    }

    /** Each path reported counts once, tagged by the read and the field it is. */
    @Test
    void eachDroppedMatchAndInvalidFieldCountsOnceByTheReadAndTheField() {
        answering(A_PAGE_OF_EVERY_FAULT);
        MeterRegistry meters = new SimpleMeterRegistry();

        searchingProbe(adapter(meters));

        Map<String, Double> counted = new TreeMap<>();
        for (Counter counter : meters.find("patient_source.invalid_fields").counters()) {
            counted.put(counter.getId().getTag("read") + " " + counter.getId().getTag("field"),
                    counter.count());
        }
        assertThat(counted).isEqualTo(Map.of(
                "search name", 1.0,
                "search nric", 1.0,
                "search id", 1.0,
                "search resourceType", 1.0,
                "search birthDate", 1.0,
                "search lastVisit", 1.0));
    }

    /** A dropped match's WARN quotes neither its id nor anything else of it. */
    @Test
    void theWarnOfADroppedMatchQuotesNoValueOfIt() {
        answering(oneMatch("\"total\": 1,", "\"id\": \"probe Q7xKd2\",", "2026-01-02"));

        try (CapturedLog captured = CapturedLog.attach()) {
            searchingProbe(adapter());

            ILoggingEvent warning = invalidFieldRecords(captured).getFirst();
            assertThat(warning.getFormattedMessage() + " " + CapturedLog.fields(warning))
                    .doesNotContain("probe", "Q7xKd2", PROBE_NAME, "T7391046Q", "1963", "2026");
        }
    }

    /**
     * A failed search names nothing it searched for or got: not the name asked, nor anything of
     * the answer, in its message, its cause or anything a record of it would print.
     */
    @Test
    void aFailedSearchSaysNothingOfTheQueryOrTheAnswer() {
        answering(oneMatch("", "\"id\": \"probe-Q7xKd2.v9\",", "2026-01-02"));

        Throwable fault = catchThrowable(() -> adapter().search(
                new PatientQuery.ByName(PROBE_NAME), PatientFilter.NONE, SECOND_PAGE_OF_FIVE));

        StringWriter printed = new StringWriter();
        fault.printStackTrace(new PrintWriter(printed));
        assertThat(printed.toString())
                .doesNotContain(PROBE_NAME, "Quah", "T7391046Q", "1963-07-29", "probe-Q7xKd2");
    }
}
