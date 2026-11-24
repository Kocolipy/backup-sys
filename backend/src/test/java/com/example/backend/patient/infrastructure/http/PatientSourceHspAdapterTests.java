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
import com.example.backend.patient.domain.HspFilter;
import com.example.backend.patient.domain.HspHistory;
import com.example.backend.patient.domain.HspOutcome;
import com.example.backend.patient.domain.HspRecord;
import com.example.backend.patient.domain.HspStatus;
import com.example.backend.patient.domain.MedicalCentre;
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
 * A patient's HSP History as the HTTP adapter reads it (ADR 0019; ADR 0020), against the patient
 * source's mock in process: the one call it sends — the page, counted by the source, with each
 * filter and the sort as the source's own search parameters — and each HSP Record read as
 * received, each field present, missing or invalid and none refused, a response holding invalid
 * fields reported once in the log and counted by field. What the mock's HSP Histories hold is
 * {@code PatientStoreContract}'s, and how one field is classified {@code ReceivedFieldsTests}'.
 * What every history shares — the page's call, the statuses that are no page, the timeout, the
 * fault stubs they have in common, an entry that is another resource — is
 * {@code PatientSourceHistoriesAdapterTests}'.
 */
class PatientSourceHspAdapterTests {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    private static final PageRequest SECOND_PAGE_OF_FIVE = new PageRequest(6, 5);

    /** A patient id no stub file names, for a case's own stub, and which no message may quote. */
    private static final String PROBE_PATIENT = "probe-patient-K3h7";

    private static final String PROBE_PATH = "/Patient/" + PROBE_PATIENT + "/hsp";

    /** One HSP Record as the source answers it, every field there and well-formed, as JSON. */
    private static final Map<String, String> WELL_FORMED = Map.of(
            "resourceType", "\"HSP\"",
            "id", "\"probe-hsp-Q5m1\"",
            "outcome", "\"moderate\"",
            "startDate", "\"2017-02-13\"",
            "endDate", "\"2018-02-12\"",
            "submittingMc", "\"hendon-camp\"",
            "status", "\"active\"");

    /** {@link #WELL_FORMED} as the domain reads it. */
    private static final HspRecord READ = new HspRecord(SourceField.present("probe-hsp-Q5m1"),
            SourceField.present("moderate"), SourceField.present(LocalDate.of(2017, 2, 13)),
            SourceField.present(LocalDate.of(2018, 2, 12)), SourceField.present("hendon-camp"),
            SourceField.present("active"));

    /**
     * {@link #WELL_FORMED} with {@code field} set to the JSON {@code value}, or left out for
     * {@code null}.
     */
    private static String hsp(String field, String value) {
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

    private static String page(String... hsps) {
        return """
                {"resourceType": "Bundle", "type": "searchset", "total": %d,
                 "entry": [%s]}""".formatted(hsps.length,
                String.join(", ", Stream.of(hsps)
                        .map(hsp -> "{\"resource\": " + hsp + "}").toList()));
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
                unfiltered(adapter));
        return new Fault(fault.getMessage(), fault.reason(), fault.category());
    }

    /** The second page of five of the probe patient's HSP History, unfiltered, in the source's order. */
    private static Optional<HspHistory> unfiltered(PatientSourceAdapter adapter) {
        return adapter.findHspHistory(new PatientId(PROBE_PATIENT), HspFilter.NONE,
                HistorySort.SOURCE_ORDER, SECOND_PAGE_OF_FIVE);
    }

    /** The probe patient's one HSP Record, the source answering a page of {@code hsp} alone. */
    private static HspRecord readingOne(String hsp) {
        answering(page(hsp));
        return unfiltered(adapter()).orElseThrow().records().getFirst();
    }

    // ---- the call ------------------------------------------------------------------------

    /** The one HSP History read the source received. */
    private static LoggedRequest sent() {
        return SOURCE.findAll(getRequestedFor(urlPathEqualTo(PROBE_PATH))).getFirst();
    }

    /** The one HSP History read the source received, every value of each query parameter. */
    private static Map<String, List<String>> sentQuery() {
        Map<String, List<String>> query = new TreeMap<>();
        sent().getQueryParams().forEach((name, value) -> query.put(name, value.values()));
        return query;
    }

    /** The second page of five of the probe patient's HSP History, filtered and sorted so. */
    private static void reading(HspFilter filter, HistorySort sort) {
        answering(page());
        adapter().findHspHistory(new PatientId(PROBE_PATIENT), filter, sort, SECOND_PAGE_OF_FIVE);
    }

    /**
     * Each sort is the source's {@code _sort} on its own search parameter — {@code start-date}
     * or {@code end-date}, descending with a leading {@code -} — its ties broken by id.
     */
    @ParameterizedTest
    @CsvSource({
        "startDate, ASCENDING, 'start-date,_id'",
        "startDate, DESCENDING, '-start-date,_id'",
        "endDate, ASCENDING, 'end-date,_id'",
        "endDate, DESCENDING, '-end-date,_id'"})
    void aSortIsTheSourcesOwnOnItsSearchParameter(String field, SortOrder order,
            String sorted) {
        reading(HspFilter.NONE,
                new HistorySort(HistorySort.column(HspHistory.DATE_COLUMNS, field), order));

        assertThat(sentQuery()).containsEntry("_sort", List.of(sorted));
    }

    /**
     * A filter on a closed set is its search parameter once, its codes joined by commas — FHIR's
     * OR — in the set's own order, whatever order they were asked in.
     */
    @Test
    void eachSetOfCodesIsOneParameterItsCodesJoinedByCommas() {
        reading(new HspFilter(Set.of(HspOutcome.HIGH, HspOutcome.LOW), DateRange.any(),
                DateRange.any(), Set.of(MedicalCentre.MANDAI_HILL_CAMP, MedicalCentre.KRANJI_CAMP),
                null), HistorySort.SOURCE_ORDER);

        assertThat(sentQuery()).isEqualTo(Map.of(
                "outcome", List.of("low,high"),
                "submitting-mc", List.of("kranji-camp,mandai-hill-camp"),
                "_offset", List.of("5"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /** The comma between two codes reaches the source as a comma, FHIR's OR, not escaped. */
    @Test
    void theCommaBetweenCodesIsSentAsItIs() {
        reading(new HspFilter(Set.of(HspOutcome.MODERATE, HspOutcome.LOW), DateRange.any(),
                DateRange.any(), Set.of(), null), HistorySort.SOURCE_ORDER);

        assertThat(sent().getUrl().split("[?&]")).contains("outcome=low,moderate");
    }

    /**
     * A date range is its search parameter once for each end it has — {@code ge} the from-date and
     * {@code le} the to-date, both inclusive — the Start Date's {@code start-date} and the End
     * Date's {@code end-date}.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "-", delimiter = '|', value = {
        "2024-01-01 | 2024-12-31 | ge2024-01-01,le2024-12-31",
        "2024-01-01 | -          | ge2024-01-01",
        "-          | 2024-12-31 | le2024-12-31"})
    void aDateRangeIsItsSearchParameterForEachEnd(LocalDate from, LocalDate to, String ends) {
        reading(new HspFilter(null, new DateRange<>(from, to), DateRange.any(), null, null),
                HistorySort.SOURCE_ORDER);
        Map<String, List<String>> started = sentQuery();
        SOURCE.resetRequests();
        reading(new HspFilter(null, DateRange.any(), new DateRange<>(from, to), null, null),
                HistorySort.SOURCE_ORDER);
        Map<String, List<String>> ended = sentQuery();

        assertThat(Arrays.asList(started.get("start-date"), started.containsKey("end-date"),
                ended.get("end-date"), ended.containsKey("start-date")))
                .containsExactly(List.of(ends.split(",")), false, List.of(ends.split(",")),
                        false);
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE, active", "INACTIVE, inactive"})
    void aStatusIsTheSourcesStatusByItsCode(HspStatus status, String code) {
        reading(new HspFilter(null, DateRange.any(), DateRange.any(), null, status),
                HistorySort.SOURCE_ORDER);

        assertThat(sentQuery()).containsEntry("status", List.of(code));
    }

    /** Every filter and a sort at once, each sent as its own. */
    @Test
    void everyFilterNarrowsTheReadAtOnceInTheOrderAsked() {
        reading(new HspFilter(Set.of(HspOutcome.MODERATE),
                        new DateRange<>(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31)),
                        new DateRange<>(LocalDate.of(2024, 1, 1), null),
                        Set.of(MedicalCentre.KHATIB_CAMP), HspStatus.INACTIVE),
                new HistorySort(HspHistory.END_DATE, SortOrder.DESCENDING));

        assertThat(sentQuery()).isEqualTo(Map.ofEntries(
                Map.entry("outcome", List.of("moderate")),
                Map.entry("start-date", List.of("ge2023-01-01", "le2023-12-31")),
                Map.entry("end-date", List.of("ge2024-01-01")),
                Map.entry("submitting-mc", List.of("khatib-camp")),
                Map.entry("status", List.of("inactive")),
                Map.entry("_sort", List.of("-end-date,_id")),
                Map.entry("_offset", List.of("5")),
                Map.entry("_count", List.of("5")),
                Map.entry("_total", List.of("accurate"))));
    }

    // ---- each HSP Record as received ----------------------------------------------------

    @Test
    void aWellFormedHspRecordIsReadWithEveryFieldPresent() {
        assertThat(readingOne(hsp("id", "\"probe-hsp-Q5m1\""))).isEqualTo(READ);
    }

    /**
     * A field the source left out, sent {@code null}, or sent empty or blank is missing, and the
     * rest of the record is read as it came.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "-", delimiter = '|', textBlock = """
        id           | -
        outcome      | null
        startDate    | '""'
        endDate      | '"  "'
        submittingMc | -
        status       | '" "'
        """)
    void aFieldLeftOutOrBlankIsMissing(String field, String value) {
        assertThat(readingOne(hsp(field, value))).isEqualTo(with(field, SourceField.missing()));
    }

    /**
     * A field of the wrong JSON type — a number is not coerced into a string — or a date that is
     * no calendar date is invalid, and the rest of the record is read as it came.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        id           | 7
        outcome      | {"code": "low"}
        startDate    | 20170213
        startDate    | "2017-02"
        endDate      | "2018-02-30"
        endDate      | "2018-02-12T00:00:00"
        submittingMc | ["hendon-camp"]
        status       | true
        """)
    void aFieldOfTheWrongFormatIsInvalid(String field, String value) {
        assertThat(readingOne(hsp(field, value))).isEqualTo(with(field, SourceField.invalid()));
    }

    /**
     * A code outside its closed set is kept as the source sent it, in each of the three sets: one
     * in another case, a label in place of the code, or a code of no set at all.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        outcome      | LOW
        outcome      | critical
        submittingMc | tengah-air-base
        submittingMc | Hendon Camp MC
        status       | expired
        status       | Active
        """)
    void aCodeOutsideItsClosedSetIsKeptAsItCame(String field, String code) {
        assertThat(readingOne(hsp(field, "\"" + code + "\"")))
                .isEqualTo(with(field, SourceField.present(code)));
    }

    /** An HSP Record's id is not checked: one outside FHIR's id grammar is kept as it came. */
    @ParameterizedTest
    @ValueSource(strings = {"hsp tan/1", "hsp_1",
        "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void anIdIsKeptAsItCame(String id) {
        assertThat(readingOne(hsp("id", "\"" + id + "\"")).id())
                .isEqualTo(SourceField.present(id));
    }

    /** Nothing of a date's meaning is checked: an End Date before the Start Date is kept. */
    @Test
    void anHspRecordEndingBeforeItStartsIsKept() {
        assertThat(readingOne(hsp("endDate", "\"2016-01-01\"")).endDate())
                .isEqualTo(SourceField.present(LocalDate.of(2016, 1, 1)));
    }

    /** {@link #READ} with its {@code field} read as {@code read}. */
    @SuppressWarnings("unchecked")
    private static HspRecord with(String field, SourceField<?> read) {
        return new HspRecord(
                field.equals("id") ? (SourceField<String>) read : READ.id(),
                field.equals("outcome") ? (SourceField<String>) read : READ.outcome(),
                field.equals("startDate") ? (SourceField<LocalDate>) read : READ.startDate(),
                field.equals("endDate") ? (SourceField<LocalDate>) read : READ.endDate(),
                field.equals("submittingMc") ? (SourceField<String>) read : READ.submittingMc(),
                field.equals("status") ? (SourceField<String>) read : READ.status());
    }

    // ---- invalid fields, reported -------------------------------------------------------

    /**
     * Three HSP Records, the second well-formed: the first's Submitting MC invalid, and the
     * third's Start Date and Status.
     */
    private static final String TWO_RECORDS_WITH_INVALID_FIELDS = page(
            hsp("submittingMc", "7"),
            hsp("id", "\"probe-hsp-Q5m2\""),
            hsp("startDate", "\"2017-13-01\"").replace("\"active\"", "true"));

    /** The WARN records reporting a response's invalid fields. */
    private static List<ILoggingEvent> invalidFieldRecords(CapturedLog captured) {
        return captured.withAction(Level.WARN, LogEvent.LOCAL_ACTION,
                "patient_source.invalid_fields");
    }

    /**
     * A response holding invalid fields is one WARN, naming the read and each invalid field by its
     * path in the response — never its value, nor the patient.
     */
    @Test
    void aResponseHoldingInvalidFieldsIsOneWarnNamingTheirPaths() {
        answering(TWO_RECORDS_WITH_INVALID_FIELDS);

        try (CapturedLog captured = CapturedLog.attach()) {
            unfiltered(adapter());

            assertThat(invalidFieldRecords(captured)).singleElement()
                    .satisfies(warning -> assertThat(CapturedLog.fields(warning))
                            .containsEntry("app.patient_source.read", "hsp")
                            .containsEntry("app.patient_source.invalid_fields", List.of(
                                    "entry[0].submittingMc", "entry[2].startDate",
                                    "entry[2].status")));
        }
    }

    @Test
    void theWarnQuotesNoValueOfTheResponseNorThePatient() {
        answering(TWO_RECORDS_WITH_INVALID_FIELDS);

        try (CapturedLog captured = CapturedLog.attach()) {
            unfiltered(adapter());

            ILoggingEvent warning = invalidFieldRecords(captured).getFirst();
            assertThat(warning.getFormattedMessage() + " " + CapturedLog.fields(warning))
                    .doesNotContain(PROBE_PATIENT, "probe-hsp", "2017", "2018", "moderate",
                            "hendon-camp");
        }
    }

    /** A response whose fields are each present or missing reports nothing. */
    @Test
    void aResponseWithNoInvalidFieldIsNotReported() {
        answering(page(hsp("outcome", null), hsp("status", "\"\"")));
        MeterRegistry meters = new SimpleMeterRegistry();

        try (CapturedLog captured = CapturedLog.attach()) {
            unfiltered(adapter(meters));

            assertThat(List.of(invalidFieldRecords(captured),
                    meters.find("patient_source.invalid_fields").counters()))
                    .containsExactly(List.of(), List.of());
        }
    }

    /** Each invalid field counts once, tagged by the read and the field. */
    @Test
    void eachInvalidFieldCountsOnceByTheReadAndTheField() {
        answering(TWO_RECORDS_WITH_INVALID_FIELDS);
        MeterRegistry meters = new SimpleMeterRegistry();
        PatientSourceAdapter adapter = adapter(meters);

        unfiltered(adapter);
        unfiltered(adapter);

        Map<String, Double> counted = new TreeMap<>();
        for (Counter counter : meters.find("patient_source.invalid_fields").counters()) {
            counted.put(counter.getId().getTag("read") + " " + counter.getId().getTag("field"),
                    counter.count());
        }
        assertThat(counted).isEqualTo(Map.of(
                "hsp submittingMc", 2.0,
                "hsp startDate", 2.0,
                "hsp status", 2.0));
    }

    // ---- a failure of the source ---------------------------------------------------------

    /**
     * A resource that is no object is no HSP Record the read can hold: the response is
     * unreadable, whatever its other entries hold.
     */
    @ParameterizedTest
    @ValueSource(strings = {"\"HSP\"", "7", "[]"})
    void aResourceThatIsNoObjectIsAFault(String resource) {
        answering("""
                {"resourceType": "Bundle", "type": "searchset", "total": 2,
                 "entry": [{"resource": %s}, {"resource": %s}]}"""
                .formatted(hsp("id", "\"probe-hsp-Q5m1\""), resource));

        assertThat(faultReading(adapter())).isEqualTo(new Fault(
                "The patient source could not be read", "unreadable", ErrorCategory.DATA));
    }

    /** The stubs every history serves and the slow one are every HSP fault stub the files hold. */
    @Test
    void everyHspFaultStubIsOneEveryHistoryServes() {
        assertThat(PatientSourceStubs.hspFaults()).extracting(PatientSourceStubs.FaultStub::id)
                .containsExactlyInAnyOrderElementsOf(
                        PatientSourceHistoriesAdapterTests.sharedFaultStubs("hsp"));
    }
}
