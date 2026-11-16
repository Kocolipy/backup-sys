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
import com.example.backend.patient.domain.DateRange;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.NumcFilter;
import com.example.backend.patient.domain.NumcHistory;
import com.example.backend.patient.domain.NumcOutcome;
import com.example.backend.patient.domain.NumcRecord;
import com.example.backend.patient.domain.NumcType;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.SortOrder;
import com.example.backend.patient.domain.SourceField;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

/**
 * A patient's NUMC History as the HTTP adapter reads it (ADR 0019, D5; ADR 0020), against the
 * patient source's mock in process: the one call it sends — the page, counted by the source, with
 * each filter and the sort as the source's own search parameters — and each NUMC Record read as
 * received, each element present, missing or invalid and none refused, a response holding invalid
 * elements reported once in the log and counted by element. What the mock's NUMC Histories hold
 * is {@code PatientStoreContract}'s, and how one field is classified {@code ReceivedFieldsTests}'.
 * What every history shares — the page's call, the statuses that are no page, the timeout, the
 * fault stubs they have in common, an entry that is another resource — is
 * {@code PatientSourceHistoriesAdapterTests}'.
 */
class PatientSourceNumcAdapterTests {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    private static final PageRequest SECOND_PAGE_OF_FIVE = new PageRequest(6, 5);

    /** A patient id no stub file names, for a case's own stub, and which no message may quote. */
    private static final String PROBE_PATIENT = "probe-patient-N3c7";

    private static final String PROBE_PATH = "/Patient/" + PROBE_PATIENT + "/numc";

    /** One NUMC Record as the source answers it, every field there and well-formed, as JSON. */
    private static final Map<String, String> WELL_FORMED = Map.of(
            "resourceType", "\"NUMC\"",
            "id", "\"probe-numc-W5q1\"",
            "type", "\"submariner\"",
            "outcome", "\"waiver-granted\"",
            "issueDate", "\"2017-02-13\"",
            "expiryDate", "\"2018-02-12\"");

    /** {@link #WELL_FORMED} as the domain reads it. */
    private static final NumcRecord READ = new NumcRecord(SourceField.present("probe-numc-W5q1"),
            SourceField.present("submariner"), SourceField.present("waiver-granted"),
            SourceField.present(LocalDate.of(2017, 2, 13)),
            SourceField.present(LocalDate.of(2018, 2, 12)));

    /**
     * {@link #WELL_FORMED} with {@code field} set to the JSON {@code value}, or left out for
     * {@code null}.
     */
    private static String record(String field, String value) {
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

    private static String page(String... records) {
        return """
                {"resourceType": "Bundle", "type": "searchset", "total": %d,
                 "entry": [%s]}""".formatted(records.length,
                String.join(", ", Stream.of(records)
                        .map(record -> "{\"resource\": " + record + "}").toList()));
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

    /** A fault as its record reports it: its fixed message, its reason and its category. */
    private record Fault(String message, String reason, ErrorCategory category) {
    }

    private static Fault faultReading(PatientSourceAdapter adapter) {
        PatientSourceException fault = (PatientSourceException) catchThrowable(() ->
                unfiltered(adapter, PROBE_PATIENT));
        return new Fault(fault.getMessage(), fault.reason(), fault.category());
    }

    /** The second page of five of {@code patient}'s NUMC History, unfiltered, in the source's order. */
    private static Optional<NumcHistory> unfiltered(PatientSourceAdapter adapter, String patient) {
        return adapter.findNumcHistory(new PatientId(patient), NumcFilter.NONE,
                HistorySort.SOURCE_ORDER, SECOND_PAGE_OF_FIVE);
    }

    /** The probe patient's one NUMC Record, the source answering a page of {@code record} alone. */
    private static NumcRecord readingOne(String record) {
        answering(page(record));
        return unfiltered(adapter(), PROBE_PATIENT).orElseThrow().records().getFirst();
    }

    // ---- the call ------------------------------------------------------------------------

    /** The one NUMC History read the source received. */
    private static LoggedRequest sent() {
        return SOURCE.findAll(getRequestedFor(urlPathEqualTo(PROBE_PATH))).getFirst();
    }

    /** The one NUMC History read the source received, every value of each query parameter. */
    private static Map<String, List<String>> sentQuery() {
        Map<String, List<String>> query = new TreeMap<>();
        sent().getQueryParams().forEach((name, value) -> query.put(name, value.values()));
        return query;
    }

    /** The second page of five of the probe patient's NUMC History, filtered and sorted so. */
    private static void reading(NumcFilter filter, HistorySort sort) {
        answering(page());
        adapter().findNumcHistory(new PatientId(PROBE_PATIENT), filter, sort, SECOND_PAGE_OF_FIVE);
    }

    /**
     * Each sort is the source's {@code _sort} on its own search parameter — {@code issue-date}
     * or {@code expiry-date}, descending with a leading {@code -} — its ties broken by id.
     */
    @ParameterizedTest
    @CsvSource({
        "issueDate, ASCENDING, 'issue-date,_id'",
        "issueDate, DESCENDING, '-issue-date,_id'",
        "expiryDate, ASCENDING, 'expiry-date,_id'",
        "expiryDate, DESCENDING, '-expiry-date,_id'"})
    void aSortIsTheSourcesOwnOnItsSearchParameter(String field, SortOrder order,
            String sorted) {
        reading(NumcFilter.NONE,
                new HistorySort(HistorySort.column(NumcHistory.DATE_COLUMNS, field), order));

        assertThat(sentQuery()).containsEntry("_sort", List.of(sorted));
    }

    /**
     * A filter on a closed set is its search parameter once, its codes joined by commas — FHIR's
     * OR — in the set's own order, whatever order they were asked in.
     */
    @Test
    void eachSetOfCodesIsOneParameterItsCodesJoinedByCommas() {
        reading(new NumcFilter(Set.of(NumcType.DIVING, NumcType.SUBMARINER),
                Set.of(NumcOutcome.WAIVER_REJECTED, NumcOutcome.FIT, NumcOutcome.UNFIT),
                DateRange.any(), DateRange.any()), HistorySort.SOURCE_ORDER);

        assertThat(sentQuery()).isEqualTo(Map.of(
                "type", List.of("submariner,diving"),
                "outcome", List.of("fit,unfit,waiver-rejected"),
                "_offset", List.of("5"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /** The comma between two codes reaches the source as a comma, FHIR's OR, not escaped. */
    @Test
    void theCommaBetweenCodesIsSentAsItIs() {
        reading(new NumcFilter(Set.of(), Set.of(NumcOutcome.WAIVER_GRANTED, NumcOutcome.FIT),
                DateRange.any(), DateRange.any()), HistorySort.SOURCE_ORDER);

        assertThat(sent().getUrl().split("[?&]")).contains("outcome=fit,waiver-granted");
    }

    /**
     * A date range is its search parameter once for each end it has — {@code ge} the from-date and
     * {@code le} the to-date, both inclusive — the Issue Date's {@code issue-date} and the Expiry
     * Date's {@code expiry-date}.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "-", delimiter = '|', value = {
        "2024-01-01 | 2024-12-31 | ge2024-01-01,le2024-12-31",
        "2024-01-01 | -          | ge2024-01-01",
        "-          | 2024-12-31 | le2024-12-31"})
    void aDateRangeIsItsSearchParameterForEachEnd(LocalDate from, LocalDate to, String ends) {
        reading(new NumcFilter(null, null, new DateRange<>(from, to), DateRange.any()),
                HistorySort.SOURCE_ORDER);
        Map<String, List<String>> issued = sentQuery();
        SOURCE.resetRequests();
        reading(new NumcFilter(null, null, DateRange.any(), new DateRange<>(from, to)),
                HistorySort.SOURCE_ORDER);
        Map<String, List<String>> expiring = sentQuery();

        assertThat(Arrays.asList(issued.get("issue-date"), issued.containsKey("expiry-date"),
                expiring.get("expiry-date"), expiring.containsKey("issue-date")))
                .containsExactly(List.of(ends.split(",")), false, List.of(ends.split(",")),
                        false);
    }

    /** Every filter and a sort at once, each sent as its own. */
    @Test
    void everyFilterNarrowsTheReadAtOnceWithTheSort() {
        reading(new NumcFilter(Set.of(NumcType.HYPERBARIC_CHAMBER),
                        Set.of(NumcOutcome.WAIVER_GRANTED),
                        new DateRange<>(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31)),
                        new DateRange<>(LocalDate.of(2024, 1, 1), null)),
                new HistorySort(NumcHistory.ISSUE_DATE, SortOrder.DESCENDING));

        assertThat(sentQuery()).isEqualTo(Map.ofEntries(
                Map.entry("type", List.of("hyperbaric-chamber")),
                Map.entry("outcome", List.of("waiver-granted")),
                Map.entry("issue-date", List.of("ge2023-01-01", "le2023-12-31")),
                Map.entry("expiry-date", List.of("ge2024-01-01")),
                Map.entry("_sort", List.of("-issue-date,_id")),
                Map.entry("_offset", List.of("5")),
                Map.entry("_count", List.of("5")),
                Map.entry("_total", List.of("accurate"))));
    }

    // ---- each record as received --------------------------------------------------------

    @Test
    void aWellFormedRecordIsReadWithEveryElementPresent() {
        assertThat(readingOne(record("id", "\"probe-numc-W5q1\""))).isEqualTo(READ);
    }

    /**
     * An element the source left out, sent {@code null}, or sent empty or blank is missing, and
     * the rest of the record is read as it came.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "-", delimiter = '|', textBlock = """
        id         | -
        type       | null
        outcome    | '""'
        issueDate  | '"  "'
        expiryDate | -
        """)
    void anElementLeftOutOrBlankIsMissing(String field, String value) {
        assertThat(readingOne(record(field, value))).isEqualTo(with(field,
                SourceField.missing()));
    }

    /**
     * An element of the wrong JSON type — a number is not coerced into a string — or a date that
     * is no calendar date is invalid, and the rest of the record is read as it came.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        id         | 7
        type       | {"code": "submariner"}
        outcome    | ["waiver-granted"]
        issueDate  | 20170213
        issueDate  | "2017-02"
        expiryDate | "2018-02-30"
        expiryDate | true
        """)
    void anElementOfTheWrongFormatIsInvalid(String field, String value) {
        assertThat(readingOne(record(field, value))).isEqualTo(with(field,
                SourceField.invalid()));
    }

    /**
     * A code outside its closed set is kept as the source sent it: one in another case, a label in
     * place of the code, or another set's code — an FFI Outcome's.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        type    | Submariner Duties
        type    | SUBMARINER
        type    | ippt
        outcome | WAIVER GRANTED
        outcome | request-waiver
        """)
    void aCodeOutsideItsClosedSetIsKeptAsItCame(String field, String code) {
        assertThat(readingOne(record(field, "\"" + code + "\"")))
                .isEqualTo(with(field, SourceField.present(code)));
    }

    /** A record's id is not checked: one outside FHIR's id grammar is kept as it came. */
    @ParameterizedTest
    @ValueSource(strings = {"numc tan/1", "numc_1",
        "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void anIdIsKeptAsItCame(String id) {
        assertThat(readingOne(record("id", "\"" + id + "\"")).id())
                .isEqualTo(SourceField.present(id));
    }

    /** Nothing of a date's meaning is checked: an Expiry Date before the Issue Date is kept. */
    @Test
    void aRecordExpiringBeforeItIsIssuedIsKept() {
        assertThat(readingOne(record("expiryDate", "\"2016-01-01\"")).expiryDate())
                .isEqualTo(SourceField.present(LocalDate.of(2016, 1, 1)));
    }

    /** {@link #READ} with its {@code field} read as {@code read}. */
    @SuppressWarnings("unchecked")
    private static NumcRecord with(String field, SourceField<?> read) {
        return new NumcRecord(
                field.equals("id") ? (SourceField<String>) read : READ.id(),
                field.equals("type") ? (SourceField<String>) read : READ.type(),
                field.equals("outcome") ? (SourceField<String>) read : READ.outcome(),
                field.equals("issueDate") ? (SourceField<LocalDate>) read : READ.issueDate(),
                field.equals("expiryDate") ? (SourceField<LocalDate>) read : READ.expiryDate());
    }

    // ---- invalid elements, reported -----------------------------------------------------

    /** Three records, the second well-formed: the first's outcome and the third's dates invalid. */
    private static final String TWO_RECORDS_WITH_INVALID_ELEMENTS = page(
            record("outcome", "7"),
            record("id", "\"probe-numc-W5q2\""),
            record("issueDate", "\"2017-13-01\"").replace("\"2018-02-12\"", "20180212"));

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
        answering(TWO_RECORDS_WITH_INVALID_ELEMENTS);

        try (CapturedLog captured = CapturedLog.attach()) {
            unfiltered(adapter(), PROBE_PATIENT);

            assertThat(invalidFieldRecords(captured)).singleElement()
                    .satisfies(warning -> assertThat(CapturedLog.fields(warning))
                            .containsEntry("app.patient_source.read", "numc")
                            .containsEntry("app.patient_source.invalid_fields", List.of(
                                    "entry[0].outcome", "entry[2].issueDate",
                                    "entry[2].expiryDate")));
        }
    }

    @Test
    void theWarnQuotesNoValueOfTheResponseNorThePatient() {
        answering(TWO_RECORDS_WITH_INVALID_ELEMENTS);

        try (CapturedLog captured = CapturedLog.attach()) {
            unfiltered(adapter(), PROBE_PATIENT);

            ILoggingEvent warning = invalidFieldRecords(captured).getFirst();
            assertThat(warning.getFormattedMessage() + " " + CapturedLog.fields(warning))
                    .doesNotContain(PROBE_PATIENT, "probe-numc", "2017", "2018", "20180212",
                            "submariner", "waiver-granted");
        }
    }

    /** A response whose elements are each present or missing reports nothing. */
    @Test
    void aResponseWithNoInvalidElementIsNotReported() {
        answering(page(record("outcome", null), record("type", "\"\"")));
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
        answering(TWO_RECORDS_WITH_INVALID_ELEMENTS);
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
                "numc outcome", 2.0,
                "numc issueDate", 2.0,
                "numc expiryDate", 2.0));
    }

    // ---- a failure of the source ---------------------------------------------------------

    /**
     * A resource that is no object is no record the read can hold: the response is unreadable,
     * whatever its other entries hold.
     */
    @ParameterizedTest
    @ValueSource(strings = {"\"NUMC\"", "7", "[]"})
    void aResourceThatIsNoObjectIsAFault(String resource) {
        answering("""
                {"resourceType": "Bundle", "type": "searchset", "total": 2,
                 "entry": [{"resource": %s}, {"resource": %s}]}"""
                .formatted(record("id", "\"probe-numc-W5q1\""), resource));

        assertThat(faultReading(adapter())).isEqualTo(new Fault(
                "The patient source could not be read", "unreadable", ErrorCategory.DATA));
    }

    /** The stubs every history serves and the slow one are every NUMC fault stub the files hold. */
    @Test
    void everyNumcFaultStubIsOneEveryHistoryServes() {
        assertThat(PatientSourceStubs.numcFaults()).extracting(PatientSourceStubs.FaultStub::id)
                .containsExactlyInAnyOrderElementsOf(
                        PatientSourceHistoriesAdapterTests.sharedFaultStubs("numc"));
    }
}
