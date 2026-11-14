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
import com.example.backend.patient.domain.DateRange;
import com.example.backend.patient.domain.DispenseStatus;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.MedicalCentre;
import com.example.backend.patient.domain.MedicationFilter;
import com.example.backend.patient.domain.MedicationHistory;
import com.example.backend.patient.domain.MedicationRecord;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.SortOrder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

/**
 * A patient's Medication History as the HTTP adapter reads it (ADR 0019, D7), against the
 * patient source's mock in process: the one call it sends — the page, counted by the source, with
 * each filter and the sort as the source's own search parameters, the sort's ties the source's to
 * break and each end of a date-time range the instant it names — how an entry's fields become a
 * Medication Record, its six optional
 * elements the ones it may lack, and what a failure of that call is, each Medication Record's
 * under a reason naming Medication. What the mock's Medication Histories hold is
 * {@code PatientStoreContract}'s.
 */
class PatientSourceMedicationAdapterTests {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    private static final PageRequest SECOND_PAGE_OF_FIVE = new PageRequest(6, 5);

    /** A patient id no stub file names, for a case's own stub, and which no message may quote. */
    private static final String PROBE_PATIENT = "probe-patient-M3q7";

    private static final String PROBE_PATH = "/Patient/" + PROBE_PATIENT + "/medication";

    /** One Medication Record as the source answers it, every field there and well-formed. */
    private static final Map<String, String> WELL_FORMED = Map.ofEntries(
            Map.entry("resourceType", "Medication"),
            Map.entry("id", "probe-medication-K4t8"),
            Map.entry("medication", "Probe Amoxicillin"),
            Map.entry("dosage", "300 mg"),
            Map.entry("route", "PO"),
            Map.entry("frequency", "TDS"),
            Map.entry("step", "Step 2"),
            Map.entry("quantity", "20 tablets"),
            Map.entry("dispenseStatus", "uncollected"),
            Map.entry("duration", "3 days"),
            Map.entry("startDate", "2017-02-13T10:00:00+08:00"),
            Map.entry("endDate", "2017-02-15T10:00:00+08:00"),
            Map.entry("dispensary", "khatib-camp"),
            Map.entry("prescribingDoctor", "Dr Probe Lee"));

    /** {@link #WELL_FORMED} with {@code field} set to {@code value}, or left out for {@code null}. */
    private static String record(String field, String value) {
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

    private static String page(String... records) {
        return """
                {"resourceType": "Bundle", "type": "searchset", "total": %d,
                 "entry": [%s]}""".formatted(records.length,
                String.join(", ", Stream.of(records)
                        .map(record -> "{\"resource\": " + record + "}").toList()));
    }

    private static PatientSourceAdapter adapter() {
        return new PatientSourceAdapter(RestClient.builder(), SOURCE.baseUrl());
    }

    private static void answering(String body) {
        SOURCE.stubFor(get(urlPathEqualTo(PROBE_PATH)).atPriority(1).willReturn(okJson(body)));
    }

    /** The second page of five of {@code patient}'s Medication History, unfiltered, in the source's order. */
    private static Optional<MedicationHistory> unfiltered(PatientSourceAdapter adapter,
            String patient) {
        return adapter.findMedicationHistory(new PatientId(patient), MedicationFilter.NONE,
                HistorySort.SOURCE_ORDER, SECOND_PAGE_OF_FIVE);
    }

    private static MedicationHistory reading() {
        return unfiltered(adapter(), PROBE_PATIENT).orElseThrow();
    }

    private static MedicationRecord readingOne(String field, String value) {
        answering(page(record(field, value)));
        return reading().records().getFirst();
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

    /** The one Medication History read the source received, every value of each query parameter. */
    private static Map<String, List<String>> sentQuery() {
        Map<String, List<String>> query = new TreeMap<>();
        SOURCE.findAll(getRequestedFor(urlPathEqualTo(PROBE_PATH))).getFirst().getQueryParams()
                .forEach((name, value) -> query.put(name, value.values()));
        return query;
    }

    /** The second page of five of the probe patient's Medication History, filtered and sorted so. */
    private static void reading(MedicationFilter filter, HistorySort sort) {
        answering(page());
        adapter().findMedicationHistory(new PatientId(PROBE_PATIENT), filter, sort,
                SECOND_PAGE_OF_FIVE);
    }

    /**
     * The read asks the source for the patient's Medication History from the page's zero-based
     * offset, for the page's count, with an accurate total — and, with no filter and no column to
     * sort by, nothing else: no {@code _sort}, so the order is the source's own.
     */
    @Test
    void aReadAsksTheSourceForThePageAndItsTotalInTheSourcesOrder() {
        reading(MedicationFilter.NONE, HistorySort.SOURCE_ORDER);

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
    void aSortIsTheSourcesOwnOnItsSearchParameterAlone(String field, SortOrder order,
            String sorted) {
        reading(MedicationFilter.NONE,
                new HistorySort(HistorySort.column(MedicationHistory.DATE_COLUMNS, field), order));

        assertThat(sentQuery()).containsEntry("_sort", List.of(sorted));
    }

    /**
     * The Dispense Statuses are the source's {@code dispense-status}, once, their codes joined by
     * commas — FHIR's OR — in their closed set's order, however they were chosen.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        PENDING                       | pending
        UNCOLLECTED,DISPENSED         | dispensed,uncollected
        UNCOLLECTED,PENDING,DISPENSED | pending,dispensed,uncollected
        """)
    void theDispenseStatusesAreTheSourcesDispenseStatusParameter(String chosen, String sent) {
        Set<DispenseStatus> statuses = Arrays.stream(chosen.split(","))
                .map(DispenseStatus::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        reading(new MedicationFilter(statuses, DateRange.any(), DateRange.any()),
                HistorySort.SOURCE_ORDER);

        assertThat(sentQuery()).isEqualTo(Map.of(
                "dispense-status", List.of(sent),
                "_offset", List.of("5"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /**
     * A date-time range is its search parameter once for each end it has, each the instant its
     * Singapore minute names, in UTC: {@code ge} the from-minute's first instant and {@code lt}
     * the first instant after the to-minute, so both whole minutes are in — the Start Date's
     * {@code start-date} and the End Date's {@code end-date}.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "-", delimiter = '|', value = {
        "2025-07-14T16:20 | 2025-07-14T16:20 | ge2025-07-14T08:20:00Z,lt2025-07-14T08:21:00Z",
        "2026-03-01T10:00 | -                | ge2026-03-01T02:00:00Z",
        "-                | 2025-12-31T23:59 | lt2025-12-31T16:00:00Z"})
    void aDateTimeRangeIsItsSearchParameterForEachEndAsAnInstant(LocalDateTime from,
            LocalDateTime to, String ends) {
        reading(new MedicationFilter(Set.of(), new DateRange<>(from, to), DateRange.any()),
                HistorySort.SOURCE_ORDER);
        Map<String, List<String>> started = sentQuery();
        SOURCE.resetRequests();
        reading(new MedicationFilter(Set.of(), DateRange.any(), new DateRange<>(from, to)),
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
        reading(new MedicationFilter(Set.of(DispenseStatus.DISPENSED),
                        new DateRange<>(LocalDateTime.of(2025, 1, 1, 0, 0), null),
                        new DateRange<>(LocalDateTime.of(2025, 1, 1, 0, 0),
                                LocalDateTime.of(2025, 12, 31, 23, 59))),
                new HistorySort(MedicationHistory.START_DATE, SortOrder.DESCENDING));

        assertThat(sentQuery()).isEqualTo(Map.ofEntries(
                Map.entry("dispense-status", List.of("dispensed")),
                Map.entry("start-date", List.of("ge2024-12-31T16:00:00Z")),
                Map.entry("end-date", List.of("ge2024-12-31T16:00:00Z", "lt2025-12-31T16:00:00Z")),
                Map.entry("_sort", List.of("-start-date")),
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

    /**
     * Each field of the entry is the record's element of that name, the free text exactly as
     * sent and each date-time with its offset; the source's total is the history's count.
     */
    @Test
    void anEntryIsTheMedicationRecordItsFieldsSpell() {
        answering(page(record("dispenseStatus", "uncollected"))
                .replace("\"total\": 1", "\"total\": 9"));

        ZoneOffset singapore = ZoneOffset.ofHours(8);
        assertThat(reading()).isEqualTo(new MedicationHistory(9, List.of(new MedicationRecord(
                "probe-medication-K4t8", "Probe Amoxicillin", "300 mg", "PO", "TDS", "Step 2",
                "20 tablets", DispenseStatus.UNCOLLECTED, "3 days",
                OffsetDateTime.of(2017, 2, 13, 10, 0, 0, 0, singapore),
                OffsetDateTime.of(2017, 2, 15, 10, 0, 0, 0, singapore),
                MedicalCentre.KHATIB_CAMP, "Dr Probe Lee"))));
    }

    /** Each optional element may be left out: the record reads without it. */
    @ParameterizedTest
    @ValueSource(strings = {"dosage", "route", "frequency", "step", "quantity", "duration"})
    void aRecordLackingAnOptionalElementIsRead(String field) {
        MedicationRecord record = readingOne(field, null);

        assertThat(Arrays.asList(record.dosage(), record.route(), record.frequency(),
                record.step(), record.quantity(), record.duration()))
                .containsOnlyOnce((String) null);
    }

    /** Free text is the source's, read as it is: no Duration is worked out, no Dosage parsed. */
    @Test
    void freeTextIsReadExactlyAsTheSourceGivesIt() {
        MedicationRecord record = readingOne("duration", "  about a week <b>");

        assertThat(record.duration()).isEqualTo("  about a week <b>");
    }

    /** A date-time may be given in any offset, UTC's {@code Z} among them, and keeps it. */
    @Test
    void aDateTimeInAnotherOffsetIsReadWithItsOffset() {
        MedicationRecord record = readingOne("endDate", "2017-02-15T02:00:00Z");

        assertThat(record.endDate())
                .isEqualTo(OffsetDateTime.of(2017, 2, 15, 2, 0, 0, 0, ZoneOffset.UTC));
    }

    /** A record may end the minute it starts. */
    @Test
    void aRecordEndingWhenItStartsIsRead() {
        MedicationRecord record = readingOne("endDate", "2017-02-13T02:00:00Z");

        assertThat(record.endDate()).isEqualTo(record.startDate());
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
     * Each fault stub of a Medication History, by the failure it serves, but the slow one, whose
     * wait is the production read timeout's (the case above times out the same way, sooner).
     */
    static Stream<Arguments> faultStubs() {
        return Stream.of(
                Arguments.of("fault-medication-unreachable", new Fault(
                        "The patient source could not be reached", "unreachable",
                        ErrorCategory.NETWORK)),
                Arguments.of("fault-medication-server-error", new Fault(
                        "The patient source answered 503", "server-error", ErrorCategory.SERVER)),
                Arguments.of("fault-medication-unexpected-status",
                        data("The patient source answered 400", "unexpected-status")),
                Arguments.of("fault-medication-not-a-searchset", data(
                        "The patient source's page is no searchset", "not-a-searchset")),
                Arguments.of("fault-medication-no-total",
                        data("The patient source's page has no total", "no-total")),
                Arguments.of("fault-medication-no-id", data(
                        "The patient source's Medication Record has no id",
                        "medication-no-id")),
                Arguments.of("fault-medication-invalid-id", data(
                        "The patient source's Medication Record's id is not a FHIR id",
                        "medication-invalid-id")),
                Arguments.of("fault-medication-no-dispense-status", data(
                        "The patient source's Medication Record has no dispense status",
                        "no-dispense-status")),
                Arguments.of("fault-medication-no-prescribing-doctor", data(
                        "The patient source's Medication Record has no prescribing doctor",
                        "no-prescribing-doctor")),
                Arguments.of("fault-medication-unknown-dispense-status", data(
                        "The patient source's Medication Record's dispense status is not a"
                                + " Dispense Status",
                        "unknown-dispense-status")),
                Arguments.of("fault-medication-unknown-dispensary", data(
                        "The patient source's Medication Record's dispensary is not a Medical"
                                + " Centre",
                        "unknown-dispensary")),
                Arguments.of("fault-medication-start-date-not-a-date-time", data(
                        "The patient source's Medication Record's start date is not a date-time"
                                + " with an offset",
                        "medication-start-date-not-a-date-time")),
                Arguments.of("fault-medication-end-before-start", data(
                        "The patient source's Medication Record ends before it starts",
                        "medication-end-before-start")));
    }

    @ParameterizedTest
    @MethodSource("faultStubs")
    void eachFaultStubIsTheFaultItServes(String patient, Fault expected) {
        assertThat(faultReading(adapter(), patient)).isEqualTo(expected);
    }

    /** The cases above and the slow stub are every Medication fault stub the stub files hold. */
    @Test
    void everyMedicationFaultStubIsReadAbove() {
        Stream<String> covered = Stream.concat(Stream.of("fault-medication-slow"),
                faultStubs().map(arguments -> (String) arguments.get()[0]));

        assertThat(PatientSourceStubs.medicationFaults())
                .extracting(PatientSourceStubs.FaultStub::id)
                .containsExactlyInAnyOrderElementsOf(covered.toList());
    }

    /** Each required element is required: a record missing any one is a fault naming it. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        id                | medication-no-id         | The patient source's Medication Record has no id
        medication        | no-medication            | The patient source's Medication Record has no medication
        dispenseStatus    | no-dispense-status       | The patient source's Medication Record has no dispense status
        startDate         | no-medication-start-date | The patient source's Medication Record has no start date
        endDate           | no-medication-end-date   | The patient source's Medication Record has no end date
        dispensary        | no-dispensary            | The patient source's Medication Record has no dispensary
        prescribingDoctor | no-prescribing-doctor    | The patient source's Medication Record has no prescribing doctor
        """)
    void aRecordMissingARequiredElementIsAFault(String field, String reason, String message) {
        answering(page(record(field, null)));

        assertThat(faultReading(adapter())).isEqualTo(data(message, reason));
    }

    /** The Medication and the Prescribing Doctor are required text: blank is as missing. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        medication        | ''    | no-medication         | The patient source's Medication Record has no medication
        prescribingDoctor | '   ' | no-prescribing-doctor | The patient source's Medication Record has no prescribing doctor
        """)
    void aBlankRequiredTextIsAFault(String field, String text, String reason, String message) {
        answering(page(record(field, text)));

        assertThat(faultReading(adapter())).isEqualTo(data(message, reason));
    }

    /**
     * A code outside its closed set is a fault naming the set: a code is matched exactly, so one
     * in another case, a label in place of the code, or another set's code is outside it too.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        dispenseStatus | DISPENSED      | unknown-dispense-status | The patient source's Medication Record's dispense status is not a Dispense Status
        dispenseStatus | collected      | unknown-dispense-status | The patient source's Medication Record's dispense status is not a Dispense Status
        dispenseStatus | active         | unknown-dispense-status | The patient source's Medication Record's dispense status is not a Dispense Status
        dispensary     | Khatib Camp MC | unknown-dispensary      | The patient source's Medication Record's dispensary is not a Medical Centre
        dispensary     | sembawang-camp | unknown-dispensary      | The patient source's Medication Record's dispensary is not a Medical Centre
        """)
    void aCodeOutsideItsClosedSetIsAFault(String field, String code, String reason,
            String message) {
        answering(page(record(field, code)));

        assertThat(faultReading(adapter())).isEqualTo(data(message, reason));
    }

    /**
     * Each date must be a date-time with an offset: a calendar date, one with no offset, or no
     * time the calendar has is a fault naming the date.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        startDate | 2017-02-13                | medication-start-date-not-a-date-time | The patient source's Medication Record's start date is not a date-time with an offset
        startDate | 2017-02-13T10:00          | medication-start-date-not-a-date-time | The patient source's Medication Record's start date is not a date-time with an offset
        endDate   | 2017-02-15T10:00:00       | medication-end-date-not-a-date-time   | The patient source's Medication Record's end date is not a date-time with an offset
        endDate   | 2017-02-30T10:00:00+08:00 | medication-end-date-not-a-date-time   | The patient source's Medication Record's end date is not a date-time with an offset
        """)
    void aDateThatIsNoDateTimeWithAnOffsetIsAFault(String field, String date, String reason,
            String message) {
        answering(page(record(field, date)));

        assertThat(faultReading(adapter())).isEqualTo(data(message, reason));
    }

    /**
     * An End Date before the Start Date breaks the record's own rule, as instants: a fault
     * naming Medication, even where the End Date's own clock reads later.
     */
    @ParameterizedTest
    @ValueSource(strings = {"2017-02-13T09:59:00+08:00", "2017-02-13T10:30:00+09:00"})
    void anEndDateBeforeTheStartDateIsAFault(String endDate) {
        answering(page(record("endDate", endDate)));

        assertThat(faultReading(adapter())).isEqualTo(data(
                "The patient source's Medication Record ends before it starts",
                "medication-end-before-start"));
    }

    /**
     * An entry holding no Medication Record — nothing at all, an ARMC Board or a Patient — is
     * another resource.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        {"resourceType": "Bundle", "type": "searchset", "total": 1, "entry": [{}]}
        {"resourceType": "Bundle", "type": "searchset", "total": 1, "entry": [{"resource": {"resourceType": "ARMC", "id": "x"}}]}
        {"resourceType": "Bundle", "type": "searchset", "total": 1, "entry": [{"resource": {"resourceType": "Patient", "id": "x"}}]}
        """)
    void anEntryHoldingNoRecordIsAFault(String body) {
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
        answering(page(record("dispensary", "lapsed-camp")));

        Throwable fault = catchThrowable(() ->
                unfiltered(adapter(), PROBE_PATIENT));

        StringWriter printed = new StringWriter();
        fault.printStackTrace(new PrintWriter(printed));
        assertThat(printed.toString())
                .doesNotContain(PROBE_PATIENT, "probe-medication-K4t8", "Probe Amoxicillin",
                        "2017-02-13", "2017-02-15", "Dr Probe Lee", "lapsed-camp");
    }
}
