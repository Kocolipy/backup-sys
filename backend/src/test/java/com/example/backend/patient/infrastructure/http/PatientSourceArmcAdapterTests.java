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
import com.example.backend.patient.domain.ArmcBoard;
import com.example.backend.patient.domain.ArmcFilter;
import com.example.backend.patient.domain.ArmcBoardType;
import com.example.backend.patient.domain.ArmcHistory;
import com.example.backend.patient.domain.ArmcOutcome;
import com.example.backend.patient.domain.DateRange;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.PatientId;
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
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.RestClient;

/**
 * A patient's ARMC History as the HTTP adapter reads it (ADR 0019, D6), against the patient
 * source's mock in process: the one call it sends — the page, counted by the source, with each
 * filter and the sort as the source's own search parameters, the sort's ties the source's to
 * break — how an entry's fields become an ARMC Board, its End Date the one element
 * it may lack, and what a failure of that call is, each ARMC Board's under a reason naming ARMC.
 * What the mock's ARMC Histories hold is {@code PatientStoreContract}'s.
 */
class PatientSourceArmcAdapterTests {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    private static final PageRequest SECOND_PAGE_OF_FIVE = new PageRequest(6, 5);

    /** A patient id no stub file names, for a case's own stub, and which no message may quote. */
    private static final String PROBE_PATIENT = "probe-patient-A9r2";

    private static final String PROBE_PATH = "/Patient/" + PROBE_PATIENT + "/armc";

    /** One ARMC Board as the source answers it, every field there and well-formed. */
    private static final Map<String, String> WELL_FORMED = Map.of(
            "resourceType", "ARMC",
            "id", "probe-armc-K4t8",
            "boardType", "aces",
            "outcome", "unfit",
            "startDate", "2017-02-13",
            "endDate", "2017-03-20",
            "medicalOfficer1", "Dr Probe Lee",
            "medicalOfficer2", "Dr Probe Tan");

    /** {@link #WELL_FORMED} with {@code field} set to {@code value}, or left out for {@code null}. */
    private static String board(String field, String value) {
        Map<String, String> fields = new TreeMap<>(WELL_FORMED);
        if (value == null) {
            fields.remove(field);
        } else {
            fields.put(field, value);
        }
        StringBuilder json = new StringBuilder("{");
        fields.forEach((name, member) -> json.append(json.length() > 1 ? ", " : "")
                .append('"').append(name).append("\": \"").append(member).append('"'));
        return json.append('}').toString();
    }

    private static String page(String... boards) {
        return """
                {"resourceType": "Bundle", "type": "searchset", "total": %d,
                 "entry": [%s]}""".formatted(boards.length,
                String.join(", ", Stream.of(boards)
                        .map(board -> "{\"resource\": " + board + "}").toList()));
    }

    private static PatientSourceAdapter adapter() {
        return new PatientSourceAdapter(RestClient.builder(), SOURCE.baseUrl());
    }

    private static void answering(String body) {
        SOURCE.stubFor(get(urlPathEqualTo(PROBE_PATH)).atPriority(1).willReturn(okJson(body)));
    }

    /** The second page of five of {@code patient}'s ARMC History, unfiltered, in the source's order. */
    private static Optional<ArmcHistory> unfiltered(PatientSourceAdapter adapter, String patient) {
        return adapter.findArmcHistory(new PatientId(patient), ArmcFilter.NONE,
                HistorySort.SOURCE_ORDER, SECOND_PAGE_OF_FIVE);
    }

    private static ArmcHistory reading() {
        return unfiltered(adapter(), PROBE_PATIENT).orElseThrow();
    }

    /** A fault as its record reports it: its fixed message, its reason and its category. */
    private record Fault(String message, String reason, ErrorCategory category) {
    }

    private static Fault faultReading(PatientSourceAdapter adapter, String patient) {
        PatientSourceException fault = (PatientSourceException) catchThrowable(() ->
                unfiltered(adapter, patient));
        return new Fault(fault.getMessage(), fault.reason(), fault.category());
    }

    private static Fault faultReading(PatientSourceAdapter adapter) {
        return faultReading(adapter, PROBE_PATIENT);
    }

    private static Fault data(String message, String reason) {
        return new Fault(message, reason, ErrorCategory.DATA);
    }

    // ---- the call ------------------------------------------------------------------------

    /** The one ARMC History read the source received, every value of each query parameter. */
    private static Map<String, List<String>> sentQuery() {
        Map<String, List<String>> query = new TreeMap<>();
        SOURCE.findAll(getRequestedFor(urlPathEqualTo(PROBE_PATH))).getFirst().getQueryParams()
                .forEach((name, value) -> query.put(name, value.values()));
        return query;
    }

    /** The second page of five of the probe patient's ARMC History, filtered and sorted so. */
    private static void reading(ArmcFilter filter, HistorySort sort) {
        answering(page());
        adapter().findArmcHistory(new PatientId(PROBE_PATIENT), filter, sort, SECOND_PAGE_OF_FIVE);
    }

    /**
     * The read asks the source for the patient's ARMC History from the page's zero-based offset,
     * for the page's count, with an accurate total — and, with no filter and no column to sort
     * by, nothing else: no {@code _sort}, so the order is the source's own.
     */
    @Test
    void aReadAsksTheSourceForThePageAndItsTotalInTheSourcesOrder() {
        reading(ArmcFilter.NONE, HistorySort.SOURCE_ORDER);

        assertThat(sentQuery()).isEqualTo(Map.of(
                "_offset", List.of("5"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /**
     * Each sort is the source's {@code _sort} on its own search parameter — {@code start-date}
     * or {@code end-date}, descending with a leading {@code -} — and the column alone: the source
     * breaks ties, so no {@code _id} follows it.
     */
    @ParameterizedTest
    @CsvSource({
        "startDate, ASCENDING, start-date",
        "startDate, DESCENDING, -start-date",
        "endDate, ASCENDING, end-date",
        "endDate, DESCENDING, -end-date"})
    void aSortIsTheSourcesOwnOnItsSearchParameterAlone(String field,
            SortOrder order, String sorted) {
        reading(ArmcFilter.NONE,
                new HistorySort(HistorySort.column(ArmcHistory.DATE_COLUMNS, field), order));

        assertThat(sentQuery()).containsEntry("_sort", List.of(sorted));
    }

    /** An Outcome is the source's {@code outcome}, once, its one code. */
    @ParameterizedTest
    @CsvSource({"FIT, fit", "UNFIT, unfit"})
    void anOutcomeIsTheSourcesOutcomeParameter(ArmcOutcome outcome, String code) {
        reading(new ArmcFilter(outcome, DateRange.any(), DateRange.any()), HistorySort.SOURCE_ORDER);

        assertThat(sentQuery()).isEqualTo(Map.of(
                "outcome", List.of(code),
                "_offset", List.of("5"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /**
     * A date range is its search parameter once for each end it has — {@code ge} the from-date and
     * {@code le} the to-date, both inclusive — the Start Date's {@code start-date} and the End
     * Date's {@code end-date}.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "-", delimiter = '|', value = {
        "2023-01-01 | 2023-12-31 | ge2023-01-01,le2023-12-31",
        "2023-01-01 | -          | ge2023-01-01",
        "-          | 2023-12-31 | le2023-12-31"})
    void aDateRangeIsItsSearchParameterForEachEnd(LocalDate from, LocalDate to, String ends) {
        reading(new ArmcFilter(null, new DateRange<>(from, to), DateRange.any()),
                HistorySort.SOURCE_ORDER);
        Map<String, List<String>> started = sentQuery();
        SOURCE.resetRequests();
        reading(new ArmcFilter(null, DateRange.any(), new DateRange<>(from, to)),
                HistorySort.SOURCE_ORDER);
        Map<String, List<String>> ended = sentQuery();

        assertThat(Arrays.asList(started.get("start-date"), started.containsKey("end-date"),
                ended.get("end-date"), ended.containsKey("start-date")))
                .containsExactly(List.of(ends.split(",")), false, List.of(ends.split(",")),
                        false);
    }

    /** Every filter and a sort at once, each sent as its own. */
    @Test
    void everyFilterNarrowsTheReadAtOnceWithTheSort() {
        reading(new ArmcFilter(ArmcOutcome.FIT,
                        new DateRange<>(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31)),
                        new DateRange<>(null, LocalDate.of(2024, 6, 30))),
                new HistorySort(ArmcHistory.END_DATE, SortOrder.DESCENDING));

        assertThat(sentQuery()).isEqualTo(Map.ofEntries(
                Map.entry("outcome", List.of("fit")),
                Map.entry("start-date", List.of("ge2023-01-01", "le2023-12-31")),
                Map.entry("end-date", List.of("le2024-06-30")),
                Map.entry("_sort", List.of("-end-date")),
                Map.entry("_offset", List.of("5")),
                Map.entry("_count", List.of("5")),
                Map.entry("_total", List.of("accurate"))));
    }

    /** As every read of the source, it presents nothing, and asks for FHIR JSON. */
    @Test
    void aReadPresentsNoCredentialAndAsksForFhirJson() {
        answering(page());

        reading();

        LoggedRequest sent = SOURCE.findAll(getRequestedFor(urlPathEqualTo(PROBE_PATH)))
                .getFirst();
        assertThat(List.of(sent.containsHeader("Authorization"), sent.containsHeader("Cookie"),
                sent.getHeader("Accept")))
                .containsExactly(false, false, "application/fhir+json");
    }

    /** Nothing is cached: the same read twice is two calls to the source. */
    @Test
    void everyReadIsACallToTheSource() {
        answering(page());

        reading();
        reading();

        SOURCE.verify(2, getRequestedFor(urlPathEqualTo(PROBE_PATH)));
    }

    // ---- the answer ----------------------------------------------------------------------

    /** Each field of the entry is the board's element of that name, the source's total its count. */
    @Test
    void anEntryIsTheArmcBoardItsFieldsSpell() {
        answering(page(board("outcome", "unfit")).replace("\"total\": 1", "\"total\": 9"));

        assertThat(reading()).isEqualTo(new ArmcHistory(9, List.of(new ArmcBoard(
                "probe-armc-K4t8", ArmcBoardType.ACES, ArmcOutcome.UNFIT,
                LocalDate.of(2017, 2, 13), LocalDate.of(2017, 3, 20), "Dr Probe Lee",
                "Dr Probe Tan"))));
    }

    /** The End Date is the one element a board may lack: one with none is still open. */
    @Test
    void aBoardWithNoEndDateIsReadAsStillOpen() {
        answering(page(board("endDate", null)));

        assertThat(reading().boards()).singleElement()
                .extracting(ArmcBoard::endDate).isNull();
    }

    /** A board may end on the day it starts. */
    @Test
    void aBoardEndingOnTheDayItStartsIsRead() {
        answering(page(board("endDate", "2017-02-13")));

        assertThat(reading().boards()).singleElement()
                .extracting(ArmcBoard::endDate).isEqualTo(LocalDate.of(2017, 2, 13));
    }

    // ---- a failure of the source ---------------------------------------------------------

    /** Only a {@code 200} is a page and only a {@code 404} no patient: any other status is a fault. */
    @ParameterizedTest
    @CsvSource({
        "400, unexpected-status, DATA",
        "401, unexpected-status, DATA",
        "302, unexpected-status, DATA",
        "500, server-error, SERVER",
        "503, server-error, SERVER"})
    void aStatusButAPageOrNoPatientIsAFault(int status, String reason, ErrorCategory category) {
        SOURCE.stubFor(get(urlPathEqualTo(PROBE_PATH)).atPriority(1)
                .willReturn(aResponse().withStatus(status)));

        assertThat(faultReading(adapter()))
                .isEqualTo(new Fault("The patient source answered " + status, reason, category));
    }

    @Test
    void aReadUnansweredWithinTheReadTimeoutTimesOut() {
        SOURCE.stubFor(get(urlPathEqualTo(PROBE_PATH)).atPriority(1)
                .willReturn(okJson(page()).withFixedDelay(1000)));
        PatientSourceAdapter impatient = new PatientSourceAdapter(RestClient.builder(),
                SOURCE.baseUrl(), Duration.ofSeconds(2), Duration.ofMillis(100));

        assertThat(faultReading(impatient)).isEqualTo(new Fault(
                "The patient source did not answer in time", "timed-out", ErrorCategory.NETWORK));
    }

    /**
     * Each fault stub of an ARMC History, by the failure it serves, but the slow one, whose wait
     * is the production read timeout's (the case above times out the same way, sooner).
     */
    static Stream<Arguments> faultStubs() {
        return Stream.of(
                Arguments.of("fault-armc-unreachable", new Fault(
                        "The patient source could not be reached", "unreachable",
                        ErrorCategory.NETWORK)),
                Arguments.of("fault-armc-server-error", new Fault(
                        "The patient source answered 503", "server-error", ErrorCategory.SERVER)),
                Arguments.of("fault-armc-unexpected-status",
                        data("The patient source answered 400", "unexpected-status")),
                Arguments.of("fault-armc-not-a-searchset", data(
                        "The patient source's page is no searchset", "not-a-searchset")),
                Arguments.of("fault-armc-no-total",
                        data("The patient source's page has no total", "no-total")),
                Arguments.of("fault-armc-no-id",
                        data("The patient source's ARMC Board has no id", "armc-no-id")),
                Arguments.of("fault-armc-invalid-id", data(
                        "The patient source's ARMC Board's id is not a FHIR id",
                        "armc-invalid-id")),
                Arguments.of("fault-armc-no-outcome", data(
                        "The patient source's ARMC Board has no outcome", "no-armc-outcome")),
                Arguments.of("fault-armc-no-medical-officer-2", data(
                        "The patient source's ARMC Board has no Medical Officer #2",
                        "no-armc-medical-officer-2")),
                Arguments.of("fault-armc-unknown-board-type", data(
                        "The patient source's ARMC Board's board type is not an ARMC Board Type",
                        "unknown-armc-board-type")),
                Arguments.of("fault-armc-unknown-outcome", data(
                        "The patient source's ARMC Board's outcome is not an ARMC Outcome",
                        "unknown-armc-outcome")),
                Arguments.of("fault-armc-end-before-start", data(
                        "The patient source's ARMC Board ends before it starts",
                        "armc-end-before-start")),
                Arguments.of("fault-armc-end-date-not-to-the-day", data(
                        "The patient source's ARMC Board's end date is not to the day",
                        "armc-end-date-not-to-the-day")));
    }

    @ParameterizedTest
    @MethodSource("faultStubs")
    void eachFaultStubIsTheFaultItServes(String patient, Fault expected) {
        assertThat(faultReading(adapter(), patient)).isEqualTo(expected);
    }

    /** The cases above and the slow stub are every ARMC fault stub the stub files hold. */
    @Test
    void everyArmcFaultStubIsReadAbove() {
        Stream<String> covered = Stream.concat(Stream.of("fault-armc-slow"),
                faultStubs().map(arguments -> (String) arguments.get()[0]));

        assertThat(PatientSourceStubs.armcFaults()).extracting(PatientSourceStubs.FaultStub::id)
                .containsExactlyInAnyOrderElementsOf(covered.toList());
    }

    /**
     * Every element but the End Date is required: a board missing any one is a fault naming it,
     * and ARMC.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        id              | armc-no-id                | The patient source's ARMC Board has no id
        boardType       | no-armc-board-type        | The patient source's ARMC Board has no board type
        outcome         | no-armc-outcome           | The patient source's ARMC Board has no outcome
        startDate       | no-armc-start-date        | The patient source's ARMC Board has no start date
        medicalOfficer1 | no-armc-medical-officer-1 | The patient source's ARMC Board has no Medical Officer #1
        medicalOfficer2 | no-armc-medical-officer-2 | The patient source's ARMC Board has no Medical Officer #2
        """)
    void aBoardMissingARequiredElementIsAFault(String field, String reason, String message) {
        answering(page(board(field, null)));

        assertThat(faultReading(adapter())).isEqualTo(data(message, reason));
    }

    /** A Medical Officer is a name: a blank one is no Medical Officer, as a missing one is not. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        medicalOfficer1 | ''    | no-armc-medical-officer-1 | The patient source's ARMC Board has no Medical Officer #1
        medicalOfficer2 | '   ' | no-armc-medical-officer-2 | The patient source's ARMC Board has no Medical Officer #2
        """)
    void aBlankMedicalOfficerIsAFault(String field, String name, String reason, String message) {
        answering(page(board(field, name)));

        assertThat(faultReading(adapter())).isEqualTo(data(message, reason));
    }

    /**
     * A code outside its closed set is a fault naming the set: a code is matched exactly, so one
     * in another case, a label in place of the code, or another set's code — a NUMC Outcome's —
     * is outside it too.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        boardType | ACES           | unknown-armc-board-type | The patient source's ARMC Board's board type is not an ARMC Board Type
        boardType | diving         | unknown-armc-board-type | The patient source's ARMC Board's board type is not an ARMC Board Type
        outcome   | FIT            | unknown-armc-outcome    | The patient source's ARMC Board's outcome is not an ARMC Outcome
        outcome   | waiver-granted | unknown-armc-outcome    | The patient source's ARMC Board's outcome is not an ARMC Outcome
        outcome   | request-waiver | unknown-armc-outcome    | The patient source's ARMC Board's outcome is not an ARMC Outcome
        """)
    void aCodeOutsideItsClosedSetIsAFault(String field, String code, String reason,
            String message) {
        answering(page(board(field, code)));

        assertThat(faultReading(adapter())).isEqualTo(data(message, reason));
    }

    /** Each date must be a calendar date to the day: a month, a time or no day the calendar has is a fault. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        startDate | 2017-02             | armc-start-date-not-to-the-day | The patient source's ARMC Board's start date is not to the day
        startDate | 2017-02-13T00:00:00 | armc-start-date-not-to-the-day | The patient source's ARMC Board's start date is not to the day
        endDate   | 2018                | armc-end-date-not-to-the-day   | The patient source's ARMC Board's end date is not to the day
        endDate   | 2017-02-30          | armc-end-date-not-to-the-day   | The patient source's ARMC Board's end date is not to the day
        """)
    void aDateNotToTheDayIsAFault(String field, String date, String reason, String message) {
        answering(page(board(field, date)));

        assertThat(faultReading(adapter())).isEqualTo(data(message, reason));
    }

    /** An End Date before the Start Date breaks the board's own rule: a fault naming ARMC. */
    @Test
    void anEndDateBeforeTheStartDateIsAFault() {
        answering(page(board("endDate", "2017-02-12")));

        assertThat(faultReading(adapter())).isEqualTo(data(
                "The patient source's ARMC Board ends before it starts", "armc-end-before-start"));
    }

    /** An entry holding no ARMC Board — nothing at all, a NUMC Record or a Patient — is another resource. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        {"resourceType": "Bundle", "type": "searchset", "total": 1, "entry": [{}]}
        {"resourceType": "Bundle", "type": "searchset", "total": 1, "entry": [{"resource": {"resourceType": "NUMC", "id": "x"}}]}
        {"resourceType": "Bundle", "type": "searchset", "total": 1, "entry": [{"resource": {"resourceType": "Patient", "id": "x"}}]}
        """)
    void anEntryHoldingNoBoardIsAFault(String body) {
        answering(body);

        assertThat(faultReading(adapter())).isEqualTo(
                data("The patient source answered with another resource", "another-resource"));
    }

    /**
     * A failed read names nothing it asked for or got: not the patient's id, nor anything of the
     * answer, in its message, its cause or anything a record of it would print.
     */
    @Test
    void aFailedReadSaysNothingOfThePatientOrTheAnswer() {
        answering(page(board("outcome", "lapsed")));

        Throwable fault = catchThrowable(() ->
                unfiltered(adapter(), PROBE_PATIENT));

        StringWriter printed = new StringWriter();
        fault.printStackTrace(new PrintWriter(printed));
        assertThat(printed.toString())
                .doesNotContain(PROBE_PATIENT, "probe-armc-K4t8", "2017-02-13", "2017-03-20",
                        "Dr Probe Lee", "Dr Probe Tan", "lapsed");
    }
}
