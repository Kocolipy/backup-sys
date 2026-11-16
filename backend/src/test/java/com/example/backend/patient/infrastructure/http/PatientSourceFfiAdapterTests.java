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
import com.example.backend.patient.domain.Ffi;
import com.example.backend.patient.domain.FfiFilter;
import com.example.backend.patient.domain.FfiHistory;
import com.example.backend.patient.domain.FfiHigherAuthority;
import com.example.backend.patient.domain.FfiOutcome;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.FfiStatus;
import com.example.backend.patient.domain.FfiType;
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
 * A patient's FFI History as the HTTP adapter reads it (ADR 0019; ADR 0020), against the patient
 * source's mock in process: the one call it sends — the page, counted by the source, with each
 * filter and the sort as the source's own search parameters — and each FFI read as received, each
 * field present, missing or invalid and none refused, a response holding invalid fields reported
 * once in the log and counted by field. What the mock's FFI Histories hold is
 * {@code PatientStoreContract}'s, and how one field is classified {@code ReceivedFieldsTests}'.
 * What every history shares — the page's call, the statuses that are no page, the timeout, the
 * fault stubs they have in common, an entry that is another resource — is
 * {@code PatientSourceHistoriesAdapterTests}'.
 */
class PatientSourceFfiAdapterTests {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    private static final PageRequest SECOND_PAGE_OF_FIVE = new PageRequest(6, 5);

    /** A patient id no stub file names, for a case's own stub, and which no message may quote. */
    private static final String PROBE_PATIENT = "probe-patient-R4w9";

    private static final String PROBE_PATH = "/Patient/" + PROBE_PATIENT + "/ffi";

    /** One FFI as the source answers it, every field there and well-formed, as JSON. */
    private static final Map<String, String> WELL_FORMED = Map.of(
            "resourceType", "\"FFI\"",
            "id", "\"probe-ffi-Z8k2\"",
            "type", "\"ippt\"",
            "outcome", "\"request-waiver\"",
            "issueDate", "\"2017-02-13\"",
            "expiryDate", "\"2018-02-12\"",
            "higherAuthority", "\"cnmo\"",
            "submittingMc", "\"hendon-camp\"",
            "status", "\"active\"");

    /** {@link #WELL_FORMED} as the domain reads it. */
    private static final Ffi READ = new Ffi(SourceField.present("probe-ffi-Z8k2"),
            SourceField.present("ippt"), SourceField.present("request-waiver"),
            SourceField.present(LocalDate.of(2017, 2, 13)),
            SourceField.present(LocalDate.of(2018, 2, 12)), SourceField.present("cnmo"),
            SourceField.present("hendon-camp"), SourceField.present("active"));

    /**
     * {@link #WELL_FORMED} with {@code field} set to the JSON {@code value}, or left out for
     * {@code null}.
     */
    private static String ffi(String field, String value) {
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

    private static String page(String... ffis) {
        return """
                {"resourceType": "Bundle", "type": "searchset", "total": %d,
                 "entry": [%s]}""".formatted(ffis.length,
                String.join(", ", Stream.of(ffis)
                        .map(ffi -> "{\"resource\": " + ffi + "}").toList()));
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

    /** The second page of five of the probe patient's FFI History, unfiltered, in the source's order. */
    private static Optional<FfiHistory> unfiltered(PatientSourceAdapter adapter) {
        return adapter.findFfiHistory(new PatientId(PROBE_PATIENT), FfiFilter.NONE,
                HistorySort.SOURCE_ORDER, SECOND_PAGE_OF_FIVE);
    }

    /** The probe patient's one FFI, the source answering a page of {@code ffi} alone. */
    private static Ffi readingOne(String ffi) {
        answering(page(ffi));
        return unfiltered(adapter()).orElseThrow().ffis().getFirst();
    }

    // ---- the call ------------------------------------------------------------------------

    /** The one FFI History read the source received. */
    private static LoggedRequest sent() {
        return SOURCE.findAll(getRequestedFor(urlPathEqualTo(PROBE_PATH))).getFirst();
    }

    /** The one FFI History read the source received, every value of each query parameter. */
    private static Map<String, List<String>> sentQuery() {
        Map<String, List<String>> query = new TreeMap<>();
        sent().getQueryParams().forEach((name, value) -> query.put(name, value.values()));
        return query;
    }

    /** The second page of five of the probe patient's FFI History, filtered and sorted so. */
    private static void reading(FfiFilter filter, HistorySort sort) {
        answering(page());
        adapter().findFfiHistory(new PatientId(PROBE_PATIENT), filter, sort, SECOND_PAGE_OF_FIVE);
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
        reading(FfiFilter.NONE,
                new HistorySort(HistorySort.column(FfiHistory.DATE_COLUMNS, field), order));

        assertThat(sentQuery()).containsEntry("_sort", List.of(sorted));
    }

    /**
     * A filter on a closed set is its search parameter once, its codes joined by commas — FHIR's
     * OR — in the set's own order, whatever order they were asked in.
     */
    @Test
    void eachSetOfCodesIsOneParameterItsCodesJoinedByCommas() {
        reading(new FfiFilter(Set.of(FfiType.DENTAL, FfiType.IPPT),
                Set.of(FfiOutcome.UNFIT, FfiOutcome.FIT), DateRange.any(), DateRange.any(),
                Set.of(FfiHigherAuthority.DIS, FfiHigherAuthority.COMD_MMI),
                Set.of(MedicalCentre.HENDON_CAMP), null), HistorySort.SOURCE_ORDER);

        assertThat(sentQuery()).isEqualTo(Map.of(
                "type", List.of("ippt,dental"),
                "outcome", List.of("fit,unfit"),
                "higher-authority", List.of("comd-mmi,dis"),
                "submitting-mc", List.of("hendon-camp"),
                "_offset", List.of("5"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /** The comma between two codes reaches the source as a comma, FHIR's OR, not escaped. */
    @Test
    void theCommaBetweenCodesIsSentAsItIs() {
        reading(new FfiFilter(Set.of(FfiType.DENTAL, FfiType.IPPT), Set.of(), DateRange.any(),
                DateRange.any(), Set.of(), Set.of(), null), HistorySort.SOURCE_ORDER);

        assertThat(sent().getUrl().split("[?&]")).contains("type=ippt,dental");
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
        reading(new FfiFilter(null, null, new DateRange<>(from, to), DateRange.any(), null, null, null),
                HistorySort.SOURCE_ORDER);
        Map<String, List<String>> issued = sentQuery();
        SOURCE.resetRequests();
        reading(new FfiFilter(null, null, DateRange.any(), new DateRange<>(from, to), null, null, null),
                HistorySort.SOURCE_ORDER);
        Map<String, List<String>> expiring = sentQuery();

        assertThat(Arrays.asList(issued.get("issue-date"), issued.containsKey("expiry-date"),
                expiring.get("expiry-date"), expiring.containsKey("issue-date")))
                .containsExactly(List.of(ends.split(",")), false, List.of(ends.split(",")),
                        false);
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE, active", "INACTIVE, inactive"})
    void aStatusIsTheSourcesStatusByItsCode(FfiStatus status, String code) {
        reading(new FfiFilter(null, null, DateRange.any(), DateRange.any(), null, null, status),
                HistorySort.SOURCE_ORDER);

        assertThat(sentQuery()).containsEntry("status", List.of(code));
    }

    /** Every filter and a sort at once, each sent as its own. */
    @Test
    void everyFilterNarrowsTheReadAtOnceInTheOrderAsked() {
        reading(new FfiFilter(Set.of(FfiType.VOCATIONAL), Set.of(FfiOutcome.REQUEST_WAIVER),
                        new DateRange<>(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31)),
                        new DateRange<>(LocalDate.of(2024, 1, 1), null),
                        Set.of(FfiHigherAuthority.CNMO), Set.of(MedicalCentre.KRANJI_CAMP),
                        FfiStatus.INACTIVE),
                new HistorySort(FfiHistory.EXPIRY_DATE, SortOrder.DESCENDING));

        assertThat(sentQuery()).isEqualTo(Map.ofEntries(
                Map.entry("type", List.of("vocational")),
                Map.entry("outcome", List.of("request-waiver")),
                Map.entry("issue-date", List.of("ge2023-01-01", "le2023-12-31")),
                Map.entry("expiry-date", List.of("ge2024-01-01")),
                Map.entry("higher-authority", List.of("cnmo")),
                Map.entry("submitting-mc", List.of("kranji-camp")),
                Map.entry("status", List.of("inactive")),
                Map.entry("_sort", List.of("-expiry-date,_id")),
                Map.entry("_offset", List.of("5")),
                Map.entry("_count", List.of("5")),
                Map.entry("_total", List.of("accurate"))));
    }

    // ---- each FFI as received -------------------------------------------------------------

    @Test
    void aWellFormedFfiIsReadWithEveryFieldPresent() {
        assertThat(readingOne(ffi("id", "\"probe-ffi-Z8k2\""))).isEqualTo(READ);
    }

    /**
     * A field the source left out, sent {@code null}, or sent empty or blank is missing, and the
     * rest of the FFI is read as it came.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "-", delimiter = '|', textBlock = """
        id              | -
        type            | null
        outcome         | '""'
        issueDate       | '"  "'
        expiryDate      | -
        higherAuthority | null
        submittingMc    | '" "'
        status          | -
        """)
    void aFieldLeftOutOrBlankIsMissing(String field, String value) {
        assertThat(readingOne(ffi(field, value))).isEqualTo(with(field, SourceField.missing()));
    }

    /**
     * A field of the wrong JSON type — a number is not coerced into a string — or a date that is
     * no calendar date is invalid, and the rest of the FFI is read as it came.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        id              | 7
        type            | {"code": "ippt"}
        outcome         | ["request-waiver"]
        issueDate       | 20170213
        issueDate       | "2017-02"
        expiryDate      | "2018-02-30"
        expiryDate      | "2018-02-12T00:00:00"
        higherAuthority | false
        submittingMc    | 3
        status          | true
        """)
    void aFieldOfTheWrongFormatIsInvalid(String field, String value) {
        assertThat(readingOne(ffi(field, value))).isEqualTo(with(field, SourceField.invalid()));
    }

    /**
     * A code outside its closed set is kept as the source sent it, in each of the five sets: one
     * in another case, a label in place of the code, or a code of no set at all.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        type            | IPPT FFI
        type            | swimming
        outcome         | FIT
        outcome         | waiver-granted
        higherAuthority | comd_mmi
        submittingMc    | nee-soon-camp-mc
        submittingMc    | Kranji Camp MC
        status          | expired
        status          | Active
        """)
    void aCodeOutsideItsClosedSetIsKeptAsItCame(String field, String code) {
        assertThat(readingOne(ffi(field, "\"" + code + "\"")))
                .isEqualTo(with(field, SourceField.present(code)));
    }

    /** An FFI's id is not checked: one outside FHIR's id grammar is kept as it came. */
    @ParameterizedTest
    @ValueSource(strings = {"ffi tan/1", "ffi_1",
        "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void anIdIsKeptAsItCame(String id) {
        assertThat(readingOne(ffi("id", "\"" + id + "\"")).id())
                .isEqualTo(SourceField.present(id));
    }

    /** Nothing of a date's meaning is checked: an Expiry Date before the Issue Date is kept. */
    @Test
    void anFfiExpiringBeforeItIsIssuedIsKept() {
        assertThat(readingOne(ffi("expiryDate", "\"2016-01-01\"")).expiryDate())
                .isEqualTo(SourceField.present(LocalDate.of(2016, 1, 1)));
    }

    /** {@link #READ} with its {@code field} read as {@code read}. */
    @SuppressWarnings("unchecked")
    private static Ffi with(String field, SourceField<?> read) {
        return new Ffi(
                field.equals("id") ? (SourceField<String>) read : READ.id(),
                field.equals("type") ? (SourceField<String>) read : READ.type(),
                field.equals("outcome") ? (SourceField<String>) read : READ.outcome(),
                field.equals("issueDate") ? (SourceField<LocalDate>) read : READ.issueDate(),
                field.equals("expiryDate") ? (SourceField<LocalDate>) read : READ.expiryDate(),
                field.equals("higherAuthority") ? (SourceField<String>) read
                        : READ.higherAuthority(),
                field.equals("submittingMc") ? (SourceField<String>) read : READ.submittingMc(),
                field.equals("status") ? (SourceField<String>) read : READ.status());
    }

    // ---- invalid fields, reported -------------------------------------------------------

    /**
     * Three FFIs, the second well-formed: the first's Submitting MC invalid, and the third's Issue
     * Date and Status.
     */
    private static final String TWO_FFIS_WITH_INVALID_FIELDS = page(
            ffi("submittingMc", "7"),
            ffi("id", "\"probe-ffi-Z8k3\""),
            ffi("issueDate", "\"2017-13-01\"").replace("\"active\"", "true"));

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
        answering(TWO_FFIS_WITH_INVALID_FIELDS);

        try (CapturedLog captured = CapturedLog.attach()) {
            unfiltered(adapter());

            assertThat(invalidFieldRecords(captured)).singleElement()
                    .satisfies(warning -> assertThat(CapturedLog.fields(warning))
                            .containsEntry("app.patient_source.read", "ffi")
                            .containsEntry("app.patient_source.invalid_fields", List.of(
                                    "entry[0].submittingMc", "entry[2].issueDate",
                                    "entry[2].status")));
        }
    }

    @Test
    void theWarnQuotesNoValueOfTheResponseNorThePatient() {
        answering(TWO_FFIS_WITH_INVALID_FIELDS);

        try (CapturedLog captured = CapturedLog.attach()) {
            unfiltered(adapter());

            ILoggingEvent warning = invalidFieldRecords(captured).getFirst();
            assertThat(warning.getFormattedMessage() + " " + CapturedLog.fields(warning))
                    .doesNotContain(PROBE_PATIENT, "probe-ffi", "2017", "2018", "ippt",
                            "request-waiver", "cnmo", "hendon-camp");
        }
    }

    /** A response whose fields are each present or missing reports nothing. */
    @Test
    void aResponseWithNoInvalidFieldIsNotReported() {
        answering(page(ffi("outcome", null), ffi("status", "\"\"")));
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
        answering(TWO_FFIS_WITH_INVALID_FIELDS);
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
                "ffi submittingMc", 2.0,
                "ffi issueDate", 2.0,
                "ffi status", 2.0));
    }

    // ---- a failure of the source ---------------------------------------------------------

    /**
     * A resource that is no object is no FFI the read can hold: the response is unreadable,
     * whatever its other entries hold.
     */
    @ParameterizedTest
    @ValueSource(strings = {"\"FFI\"", "7", "[]"})
    void aResourceThatIsNoObjectIsAFault(String resource) {
        answering("""
                {"resourceType": "Bundle", "type": "searchset", "total": 2,
                 "entry": [{"resource": %s}, {"resource": %s}]}"""
                .formatted(ffi("id", "\"probe-ffi-Z8k2\""), resource));

        assertThat(faultReading(adapter())).isEqualTo(new Fault(
                "The patient source could not be read", "unreadable", ErrorCategory.DATA));
    }

    /** The stubs every history serves and the slow one are every FFI fault stub the files hold. */
    @Test
    void everyFfiFaultStubIsOneEveryHistoryServes() {
        assertThat(PatientSourceStubs.ffiFaults()).extracting(PatientSourceStubs.FaultStub::id)
                .containsExactlyInAnyOrderElementsOf(
                        PatientSourceHistoriesAdapterTests.sharedFaultStubs("ffi"));
    }
}
