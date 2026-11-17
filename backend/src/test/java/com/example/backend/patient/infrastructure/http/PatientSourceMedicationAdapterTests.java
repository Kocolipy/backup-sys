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
import com.example.backend.patient.domain.DispenseStatus;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.MedicationFilter;
import com.example.backend.patient.domain.MedicationHistory;
import com.example.backend.patient.domain.MedicationRecord;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.SortOrder;
import com.example.backend.patient.domain.SourceField;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

/**
 * A patient's Medication History as the HTTP adapter reads it (ADR 0019, D7; ADR 0020), against
 * the patient source's mock in process: the one call it sends — the page, counted by the source,
 * with each filter and the sort as the source's own search parameters, the sort's ties the
 * source's to break and each end of a date-time range the instant it names — and each Medication
 * Record read as received, each element present, missing or invalid and none refused, a response
 * holding invalid elements reported once in the log and counted by element. What the mock's
 * Medication Histories hold is {@code PatientStoreContract}'s, and how one field is classified
 * {@code ReceivedFieldsTests}'. What every history shares — the page's call, the statuses that are
 * no page, the timeout, the fault stubs they have in common, an entry that is another resource —
 * is {@code PatientSourceHistoriesAdapterTests}'.
 */
class PatientSourceMedicationAdapterTests {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    private static final PageRequest SECOND_PAGE_OF_FIVE = new PageRequest(6, 5);

    /** A patient id no stub file names, for a case's own stub, and which no message may quote. */
    private static final String PROBE_PATIENT = "probe-patient-M3q7";

    private static final String PROBE_PATH = "/Patient/" + PROBE_PATIENT + "/medication";

    private static final ZoneOffset SINGAPORE = ZoneOffset.ofHours(8);

    /** One Medication Record as the source answers it, every field there and well-formed, as JSON. */
    private static final Map<String, String> WELL_FORMED = Map.ofEntries(
            Map.entry("resourceType", "\"Medication\""),
            Map.entry("id", "\"probe-medication-K4t8\""),
            Map.entry("medication", "\"Probe Amoxicillin\""),
            Map.entry("dosage", "\"300 mg\""),
            Map.entry("route", "\"PO\""),
            Map.entry("frequency", "\"TDS\""),
            Map.entry("step", "\"Step 2\""),
            Map.entry("quantity", "\"20 tablets\""),
            Map.entry("dispenseStatus", "\"uncollected\""),
            Map.entry("duration", "\"3 days\""),
            Map.entry("startDate", "\"2017-02-13T10:00:00+08:00\""),
            Map.entry("endDate", "\"2017-02-15T10:00:00+08:00\""),
            Map.entry("dispensary", "\"khatib-camp\""),
            Map.entry("prescribingDoctor", "\"Dr Probe Lee\""));

    /** {@link #WELL_FORMED} as the domain reads it. */
    private static final MedicationRecord READ = new MedicationRecord(
            SourceField.present("probe-medication-K4t8"), SourceField.present("Probe Amoxicillin"),
            SourceField.present("300 mg"), SourceField.present("PO"), SourceField.present("TDS"),
            SourceField.present("Step 2"), SourceField.present("20 tablets"),
            SourceField.present("uncollected"), SourceField.present("3 days"),
            SourceField.present(OffsetDateTime.of(2017, 2, 13, 10, 0, 0, 0, SINGAPORE)),
            SourceField.present(OffsetDateTime.of(2017, 2, 15, 10, 0, 0, 0, SINGAPORE)),
            SourceField.present("khatib-camp"), SourceField.present("Dr Probe Lee"));

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

    /** The second page of five of {@code patient}'s Medication History, unfiltered, in the source's order. */
    private static Optional<MedicationHistory> unfiltered(PatientSourceAdapter adapter,
            String patient) {
        return adapter.findMedicationHistory(new PatientId(patient), MedicationFilter.NONE,
                HistorySort.SOURCE_ORDER, SECOND_PAGE_OF_FIVE);
    }

    /** The probe patient's one Medication Record, the source answering a page of {@code record} alone. */
    private static MedicationRecord readingOne(String record) {
        answering(page(record));
        return unfiltered(adapter(), PROBE_PATIENT).orElseThrow().records().getFirst();
    }

    /** A fault as its record reports it: its fixed message, its reason and its category. */
    private record Fault(String message, String reason, ErrorCategory category) {
    }

    private static Fault faultReading(PatientSourceAdapter adapter) {
        PatientSourceException fault = (PatientSourceException) catchThrowable(() ->
                unfiltered(adapter, PROBE_PATIENT));
        return new Fault(fault.getMessage(), fault.reason(), fault.category());
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

    // ---- each record as received --------------------------------------------------------

    /** Each field of the entry is the record's element of that name; the source's total is the history's count. */
    @Test
    void aWellFormedEntryIsTheRecordItsFieldsSpellBesideTheSourcesTotal() {
        answering(page(record("id", "\"probe-medication-K4t8\""))
                .replace("\"total\": 1", "\"total\": 9"));

        assertThat(unfiltered(adapter(), PROBE_PATIENT))
                .contains(new MedicationHistory(9, List.of(READ)));
    }

    /**
     * An element the source left out, sent {@code null}, or sent empty or blank is missing — the
     * six optional free-text elements as any other — and the rest of the record is read as it
     * came.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "-", delimiter = '|', textBlock = """
        id                | -
        medication        | '""'
        dosage            | null
        route             | -
        frequency         | '"   "'
        step              | null
        quantity          | -
        dispenseStatus    | null
        duration          | '""'
        startDate         | -
        endDate           | '"  "'
        dispensary        | '""'
        prescribingDoctor | null
        """)
    void anElementLeftOutOrBlankIsMissing(String field, String value) {
        assertThat(readingOne(record(field, value))).isEqualTo(with(field,
                SourceField.missing()));
    }

    /**
     * An element of the wrong JSON type — a number in free text is not coerced into a string — or
     * a date that is no date-time with an offset is invalid, and the rest of the record is read as
     * it came.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        id                | 7
        medication        | true
        dosage            | 300
        route             | ["PO"]
        frequency         | {"code": "TDS"}
        step              | 2
        quantity          | 20
        dispenseStatus    | {"code": "uncollected"}
        duration          | 3
        startDate         | "2017-02-13"
        startDate         | "2017-02-13T10:00"
        endDate           | "2017-02-15T10:00:00"
        endDate           | "2017-02-30T10:00:00+08:00"
        endDate           | 20170215
        dispensary        | ["khatib-camp"]
        prescribingDoctor | false
        """)
    void anElementOfTheWrongFormatIsInvalid(String field, String value) {
        assertThat(readingOne(record(field, value))).isEqualTo(with(field,
                SourceField.invalid()));
    }

    /**
     * A code outside its closed set is kept as the source sent it: one in another case, a label in
     * place of the code, or a code of no set.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        dispenseStatus | DISPENSED
        dispenseStatus | collected
        dispenseStatus | active
        dispensary     | Khatib Camp MC
        dispensary     | sembawang-camp
        """)
    void aCodeOutsideItsClosedSetIsKeptAsItCame(String field, String code) {
        assertThat(readingOne(record(field, "\"" + code + "\"")))
                .isEqualTo(with(field, SourceField.present(code)));
    }

    /** A record's id is not checked: one outside FHIR's id grammar is kept as it came. */
    @ParameterizedTest
    @ValueSource(strings = {"medication tan/1", "medication_1",
        "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void anIdIsKeptAsItCame(String id) {
        assertThat(readingOne(record("id", "\"" + id + "\"")).id())
                .isEqualTo(SourceField.present(id));
    }

    /** Free text is the source's, read as it is: no Duration is worked out, no Dosage parsed. */
    @Test
    void freeTextIsReadExactlyAsTheSourceGivesIt() {
        assertThat(readingOne(record("duration", "\"  about a week <b>\"")).duration())
                .isEqualTo(SourceField.present("  about a week <b>"));
    }

    /** A date-time may be given in any offset, UTC's {@code Z} among them, and keeps it. */
    @Test
    void aDateTimeInAnotherOffsetIsReadWithItsOffset() {
        assertThat(readingOne(record("endDate", "\"2017-02-15T02:00:00Z\"")).endDate())
                .isEqualTo(SourceField.present(
                        OffsetDateTime.of(2017, 2, 15, 2, 0, 0, 0, ZoneOffset.UTC)));
    }

    /**
     * Nothing of a date-time's meaning is checked: an End Date before the Start Date, as instants
     * too where the End Date's own clock reads later, is kept as it came.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        2017-02-13T09:59:00+08:00 | 2017-02-13T09:59+08:00
        2017-02-13T10:30:00+09:00 | 2017-02-13T10:30+09:00
        """)
    void aRecordEndingBeforeItStartsIsKept(String endDate, OffsetDateTime read) {
        assertThat(readingOne(record("endDate", "\"" + endDate + "\"")).endDate())
                .isEqualTo(SourceField.present(read));
    }

    /** {@link #READ} with its {@code field} read as {@code read}. */
    @SuppressWarnings("unchecked")
    private static MedicationRecord with(String field, SourceField<?> read) {
        return new MedicationRecord(
                field.equals("id") ? (SourceField<String>) read : READ.id(),
                field.equals("medication") ? (SourceField<String>) read : READ.medication(),
                field.equals("dosage") ? (SourceField<String>) read : READ.dosage(),
                field.equals("route") ? (SourceField<String>) read : READ.route(),
                field.equals("frequency") ? (SourceField<String>) read : READ.frequency(),
                field.equals("step") ? (SourceField<String>) read : READ.step(),
                field.equals("quantity") ? (SourceField<String>) read : READ.quantity(),
                field.equals("dispenseStatus") ? (SourceField<String>) read
                        : READ.dispenseStatus(),
                field.equals("duration") ? (SourceField<String>) read : READ.duration(),
                field.equals("startDate") ? (SourceField<OffsetDateTime>) read
                        : READ.startDate(),
                field.equals("endDate") ? (SourceField<OffsetDateTime>) read : READ.endDate(),
                field.equals("dispensary") ? (SourceField<String>) read : READ.dispensary(),
                field.equals("prescribingDoctor") ? (SourceField<String>) read
                        : READ.prescribingDoctor());
    }

    // ---- invalid elements, reported -----------------------------------------------------

    /** Three records, the second well-formed: the first's quantity and the third's dates invalid. */
    private static final String TWO_RECORDS_WITH_INVALID_ELEMENTS = page(
            record("quantity", "20"),
            record("id", "\"probe-medication-K4t9\""),
            record("startDate", "\"2017-02-13T10:00:00\"")
                    .replace("\"2017-02-15T10:00:00+08:00\"", "20170215"));

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
                            .containsEntry("app.patient_source.read", "medication")
                            .containsEntry("app.patient_source.invalid_fields", List.of(
                                    "entry[0].quantity", "entry[2].startDate",
                                    "entry[2].endDate")));
        }
    }

    @Test
    void theWarnQuotesNoValueOfTheResponseNorThePatient() {
        answering(TWO_RECORDS_WITH_INVALID_ELEMENTS);

        try (CapturedLog captured = CapturedLog.attach()) {
            unfiltered(adapter(), PROBE_PATIENT);

            ILoggingEvent warning = invalidFieldRecords(captured).getFirst();
            assertThat(warning.getFormattedMessage() + " " + CapturedLog.fields(warning))
                    .doesNotContain(PROBE_PATIENT, "probe-medication", "2017", "20170215",
                            "Amoxicillin", "uncollected", "khatib-camp", "Dr Probe Lee");
        }
    }

    /** A response whose elements are each present or missing reports nothing. */
    @Test
    void aResponseWithNoInvalidElementIsNotReported() {
        answering(page(record("dosage", null), record("dispenseStatus", "\"\"")));
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
                "medication quantity", 2.0,
                "medication startDate", 2.0,
                "medication endDate", 2.0));
    }

    // ---- a failure of the source ---------------------------------------------------------

    /**
     * A resource that is no object is no record the read can hold: the response is unreadable,
     * whatever its other entries hold.
     */
    @ParameterizedTest
    @ValueSource(strings = {"\"Medication\"", "7", "[]"})
    void aResourceThatIsNoObjectIsAFault(String resource) {
        answering("""
                {"resourceType": "Bundle", "type": "searchset", "total": 2,
                 "entry": [{"resource": %s}, {"resource": %s}]}"""
                .formatted(record("id", "\"probe-medication-K4t8\""), resource));

        assertThat(faultReading(adapter())).isEqualTo(new Fault(
                "The patient source could not be read", "unreadable", ErrorCategory.DATA));
    }

    /** The stubs every history serves and the slow one are every Medication fault stub the files hold. */
    @Test
    void everyMedicationFaultStubIsOneEveryHistoryServes() {
        assertThat(PatientSourceStubs.medicationFaults())
                .extracting(PatientSourceStubs.FaultStub::id)
                .containsExactlyInAnyOrderElementsOf(
                        PatientSourceHistoriesAdapterTests.sharedFaultStubs("medication"));
    }
}
