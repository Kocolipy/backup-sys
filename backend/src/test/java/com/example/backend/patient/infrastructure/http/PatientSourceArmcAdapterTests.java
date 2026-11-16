package com.example.backend.patient.infrastructure.http;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.example.backend.audit.CapturedLog;
import com.example.backend.observability.LogEvent;
import com.example.backend.observability.LogEvent.ErrorCategory;
import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.PatientSourceStubs;
import com.example.backend.patient.domain.ArmcBoard;
import com.example.backend.patient.domain.ArmcFilter;
import com.example.backend.patient.domain.ArmcHistory;
import com.example.backend.patient.domain.ArmcOutcome;
import com.example.backend.patient.domain.DateRange;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.SortOrder;
import com.example.backend.patient.domain.SourceField;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

/**
 * A patient's ARMC History as the HTTP adapter reads it (ADR 0019, D6; ADR 0020), against the
 * patient source's mock in process: the one call it sends — the page, counted by the source, with
 * each filter and the sort as the source's own search parameters, the sort's ties the source's to
 * break — and each ARMC Board read as received, each element present, missing or invalid and
 * none refused, a response holding invalid elements reported once in the log and counted by
 * element. What the mock's ARMC Histories hold is {@code PatientStoreContract}'s, and how one
 * field is classified {@code ReceivedFieldsTests}'. What every history shares — the page's call,
 * the statuses that are no page, the timeout, the fault stubs they have in common, an entry that
 * is another resource or no object — is {@code PatientSourceHistoriesAdapterTests}'.
 */
class PatientSourceArmcAdapterTests {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    private static final PageRequest SECOND_PAGE_OF_FIVE = new PageRequest(6, 5);

    /** A patient id no stub file names, for a case's own stub, and which no message may quote. */
    private static final String PROBE_PATIENT = "probe-patient-A9r2";

    private static final String PROBE_PATH = "/Patient/" + PROBE_PATIENT + "/armc";

    /** One ARMC Board as the source answers it, every field there and well-formed, as JSON. */
    private static final Map<String, String> WELL_FORMED = Map.of(
            "resourceType", "\"ARMC\"",
            "id", "\"probe-armc-K4t8\"",
            "boardType", "\"aces\"",
            "outcome", "\"unfit\"",
            "startDate", "\"2017-02-13\"",
            "endDate", "\"2017-03-20\"",
            "medicalOfficer1", "\"Dr Probe Lee\"",
            "medicalOfficer2", "\"Dr Probe Tan\"");

    /** {@link #WELL_FORMED} as the domain reads it. */
    private static final ArmcBoard READ = new ArmcBoard(SourceField.present("probe-armc-K4t8"),
            SourceField.present("aces"), SourceField.present("unfit"),
            SourceField.present(LocalDate.of(2017, 2, 13)),
            SourceField.present(LocalDate.of(2017, 3, 20)),
            SourceField.present("Dr Probe Lee"), SourceField.present("Dr Probe Tan"));

    /**
     * {@link #WELL_FORMED} with {@code field} set to the JSON {@code value}, or left out for
     * {@code null}.
     */
    private static String board(String field, String value) {
        Map<String, String> fields = new TreeMap<>(WELL_FORMED);
        if (value == null) {
            fields.remove(field);
        } else {
            fields.put(field, value);
        }
        StringBuilder json = new StringBuilder("{");
        fields.forEach((name, member) -> json.append(json.length() > 1 ? ", " : "")
                .append('"').append(name).append("\": ").append(member));
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
        return adapter(new SimpleMeterRegistry());
    }

    private static PatientSourceAdapter adapter(MeterRegistry meters) {
        return new PatientSourceAdapter(RestClient.builder(), SOURCE.baseUrl(), meters);
    }

    private static void answering(String body) {
        SOURCE.stubFor(get(urlPathEqualTo(PROBE_PATH)).atPriority(1).willReturn(okJson(body)));
    }

    /** The second page of five of {@code patient}'s ARMC History, unfiltered, in the source's order. */
    private static Optional<ArmcHistory> unfiltered(PatientSourceAdapter adapter, String patient) {
        return adapter.findArmcHistory(new PatientId(patient), ArmcFilter.NONE,
                HistorySort.SOURCE_ORDER, SECOND_PAGE_OF_FIVE);
    }

    /** The probe patient's one ARMC Board, the source answering a page of {@code board} alone. */
    private static ArmcBoard readingOne(String board) {
        answering(page(board));
        return unfiltered(adapter(), PROBE_PATIENT).orElseThrow().boards().getFirst();
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

    // ---- each board as received ---------------------------------------------------------

    /** Each field of the entry is the board's element of that name, the source's total its count. */
    @Test
    void aWellFormedBoardIsReadWithEveryElementPresentAndTheSourcesTotal() {
        answering(page(board("id", "\"probe-armc-K4t8\"")).replace("\"total\": 1",
                "\"total\": 9"));

        assertThat(unfiltered(adapter(), PROBE_PATIENT))
                .contains(new ArmcHistory(9, List.of(READ)));
    }

    /**
     * An element the source left out, sent {@code null}, or sent empty or blank is missing — a
     * board with no End Date is still open, and a blank Medical Officer names no one — and the
     * rest of the board is read as it came.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "-", delimiter = '|', textBlock = """
        id              | -
        boardType       | null
        outcome         | '""'
        startDate       | '"  "'
        endDate         | -
        medicalOfficer1 | '""'
        medicalOfficer2 | '"   "'
        """)
    void anElementLeftOutOrBlankIsMissing(String field, String value) {
        assertThat(readingOne(board(field, value))).isEqualTo(with(field,
                SourceField.missing()));
    }

    /**
     * An element of the wrong JSON type — a number is not coerced into a string — or a date that
     * is no calendar date is invalid, and the rest of the board is read as it came.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        id              | 7
        boardType       | {"code": "aces"}
        outcome         | ["fit"]
        startDate       | 20170213
        startDate       | "2017-02"
        startDate       | "2017-02-13T00:00:00"
        endDate         | "2018"
        endDate         | "2017-02-30"
        medicalOfficer1 | true
        medicalOfficer2 | 42
        """)
    void anElementOfTheWrongFormatIsInvalid(String field, String value) {
        assertThat(readingOne(board(field, value))).isEqualTo(with(field,
                SourceField.invalid()));
    }

    /**
     * A code outside its closed set is kept as the source sent it: one in another case, or
     * another set's code — a NUMC Outcome's or an FFI Outcome's.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        boardType | ACES
        boardType | diving
        outcome   | FIT
        outcome   | waiver-granted
        outcome   | request-waiver
        """)
    void aCodeOutsideItsClosedSetIsKeptAsItCame(String field, String code) {
        assertThat(readingOne(board(field, "\"" + code + "\"")))
                .isEqualTo(with(field, SourceField.present(code)));
    }

    /** A board's id is not checked: one outside FHIR's id grammar is kept as it came. */
    @ParameterizedTest
    @ValueSource(strings = {"armc tan/1", "armc_1",
        "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void anIdIsKeptAsItCame(String id) {
        assertThat(readingOne(board("id", "\"" + id + "\"")).id())
                .isEqualTo(SourceField.present(id));
    }

    /** Nothing of a date's meaning is checked: an End Date before the Start Date is kept. */
    @Test
    void aBoardEndingBeforeItStartsIsKept() {
        assertThat(readingOne(board("endDate", "\"2017-02-12\"")).endDate())
                .isEqualTo(SourceField.present(LocalDate.of(2017, 2, 12)));
    }

    /** {@link #READ} with its {@code field} read as {@code read}. */
    @SuppressWarnings("unchecked")
    private static ArmcBoard with(String field, SourceField<?> read) {
        return new ArmcBoard(
                field.equals("id") ? (SourceField<String>) read : READ.id(),
                field.equals("boardType") ? (SourceField<String>) read : READ.boardType(),
                field.equals("outcome") ? (SourceField<String>) read : READ.outcome(),
                field.equals("startDate") ? (SourceField<LocalDate>) read : READ.startDate(),
                field.equals("endDate") ? (SourceField<LocalDate>) read : READ.endDate(),
                field.equals("medicalOfficer1")
                        ? (SourceField<String>) read : READ.medicalOfficer1(),
                field.equals("medicalOfficer2")
                        ? (SourceField<String>) read : READ.medicalOfficer2());
    }

    // ---- invalid elements, reported -----------------------------------------------------

    /** Three boards, the second well-formed: the first's outcome and the third's dates invalid. */
    private static final String TWO_BOARDS_WITH_INVALID_ELEMENTS = page(
            board("outcome", "7"),
            board("id", "\"probe-armc-K4t9\""),
            board("startDate", "\"2017-13-01\"").replace("\"2017-03-20\"", "20170320"));

    /** The WARN records reporting a response's invalid fields. */
    private static List<ILoggingEvent> invalidFieldRecords(CapturedLog captured) {
        return captured.withAction(Level.WARN, LogEvent.LOCAL_ACTION,
                "patient_source.invalid_fields");
    }

    /**
     * A response holding invalid elements is one WARN, naming the read and each invalid field by
     * its path in the response — never its value, nor the patient.
     */
    @Test
    void aResponseHoldingInvalidElementsIsOneWarnNamingTheirPaths() {
        answering(TWO_BOARDS_WITH_INVALID_ELEMENTS);

        try (CapturedLog captured = CapturedLog.attach()) {
            unfiltered(adapter(), PROBE_PATIENT);

            assertThat(invalidFieldRecords(captured)).singleElement()
                    .satisfies(warning -> assertThat(CapturedLog.fields(warning))
                            .containsEntry("app.patient_source.read", "armc")
                            .containsEntry("app.patient_source.invalid_fields", List.of(
                                    "entry[0].outcome", "entry[2].startDate",
                                    "entry[2].endDate")));
        }
    }

    @Test
    void theWarnQuotesNoValueOfTheResponseNorThePatient() {
        answering(TWO_BOARDS_WITH_INVALID_ELEMENTS);

        try (CapturedLog captured = CapturedLog.attach()) {
            unfiltered(adapter(), PROBE_PATIENT);

            ILoggingEvent warning = invalidFieldRecords(captured).getFirst();
            assertThat(warning.getFormattedMessage() + " " + CapturedLog.fields(warning))
                    .doesNotContain(PROBE_PATIENT, "probe-armc", "2017", "20170320", "aces",
                            "unfit", "Dr Probe");
        }
    }

    /** A response whose elements are each present or missing reports nothing. */
    @Test
    void aResponseWithNoInvalidElementIsNotReported() {
        answering(page(board("outcome", null), board("medicalOfficer2", "\"\"")));
        MeterRegistry meters = new SimpleMeterRegistry();

        try (CapturedLog captured = CapturedLog.attach()) {
            unfiltered(adapter(meters), PROBE_PATIENT);

            assertThat(List.of(invalidFieldRecords(captured),
                    meters.find("patient_source.invalid_fields").counters()))
                    .containsExactly(List.of(), List.of());
        }
    }

    /** Each invalid element counts once, tagged by the read and the element. */
    @Test
    void eachInvalidElementCountsOnceByTheReadAndTheElement() {
        answering(TWO_BOARDS_WITH_INVALID_ELEMENTS);
        MeterRegistry meters = new SimpleMeterRegistry();
        PatientSourceAdapter adapter = adapter(meters);

        unfiltered(adapter, PROBE_PATIENT);
        unfiltered(adapter, PROBE_PATIENT);

        Map<String, Double> counted = new TreeMap<>();
        for (Counter counter : meters.find("patient_source.invalid_fields").counters()) {
            counted.put(counter.getId().getTag("read") + " " + counter.getId().getTag("field"),
                    counter.count());
        }
        assertThat(counted).isEqualTo(Map.of(
                "armc outcome", 2.0,
                "armc startDate", 2.0,
                "armc endDate", 2.0));
    }

    // ---- a failure of the source ---------------------------------------------------------

    /** A fault as its record reports it: its fixed message, its reason and its category. */
    private record Fault(String message, String reason, ErrorCategory category) {
    }

    /**
     * A resource that is no object is no board the read can hold: the response is unreadable,
     * whatever its other entries hold.
     */
    @ParameterizedTest
    @ValueSource(strings = {"\"ARMC\"", "7", "[]"})
    void aResourceThatIsNoObjectIsAFault(String resource) {
        answering("""
                {"resourceType": "Bundle", "type": "searchset", "total": 2,
                 "entry": [{"resource": %s}, {"resource": %s}]}"""
                .formatted(board("id", "\"probe-armc-K4t8\""), resource));

        PatientSourceException fault = (PatientSourceException) catchThrowable(() ->
                unfiltered(adapter(), PROBE_PATIENT));
        assertThat(new Fault(fault.getMessage(), fault.reason(), fault.category()))
                .isEqualTo(new Fault("The patient source could not be read", "unreadable",
                        ErrorCategory.DATA));
    }

    /**
     * An entry of another resource — a NUMC Record in an ARMC History — is no board: the
     * response is a fault naming another resource, whatever its other entries hold.
     */
    @Test
    void anEntryOfAnotherResourceIsAFault() {
        answering(page(board("id", "\"probe-armc-K4t8\""),
                board("resourceType", "\"NUMC\"")));

        PatientSourceException fault = (PatientSourceException) catchThrowable(() ->
                unfiltered(adapter(), PROBE_PATIENT));
        assertThat(new Fault(fault.getMessage(), fault.reason(), fault.category()))
                .isEqualTo(new Fault("The patient source answered with another resource",
                        "another-resource", ErrorCategory.DATA));
    }

    /** The stubs every history serves and the slow one are every ARMC fault stub the files hold. */
    @Test
    void everyArmcFaultStubIsOneEveryHistoryServes() {
        assertThat(PatientSourceStubs.armcFaults()).extracting(PatientSourceStubs.FaultStub::id)
                .containsExactlyInAnyOrderElementsOf(
                        PatientSourceHistoriesAdapterTests.sharedFaultStubs("armc"));
    }
}
