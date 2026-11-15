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
import com.example.backend.patient.PatientSourceStubs.FaultStub;
import com.example.backend.patient.domain.ArmcFilter;
import com.example.backend.patient.domain.FfiFilter;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.MedicationFilter;
import com.example.backend.patient.domain.NumcFilter;
import com.example.backend.patient.domain.PatientId;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.RestClient;

/**
 * Every patient history as the HTTP adapter reads it (ADR 0019, D5), held to the rules they share
 * from one table with a row for each history: the one call it sends — the page, counted by the
 * source, in the source's own order — and presenting no credential, never cached; and what a
 * failure of that call is — a status that is no page, a read that times out, each fault stub the
 * histories have in common, an entry that is another resource — saying nothing of the patient or
 * the answer. Each history's own adapter tests hold what that history declares: its search
 * parameters, its date columns and their precision, its row mapping, and its own faults and their
 * reasons. A new history joins by adding its row.
 */
class PatientSourceHistoriesAdapterTests {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    private static final PageRequest SECOND_PAGE_OF_FIVE = new PageRequest(6, 5);

    /** A patient id no stub file names, for a case's own stub. */
    private static final String PROBE_PATIENT = "probe-patient-H9d2";

    private static final String EMPTY_PAGE = """
            {"resourceType": "Bundle", "type": "searchset", "total": 0, "entry": []}""";

    /** The second page of five of a patient's history, unfiltered, in the source's order. */
    @FunctionalInterface
    private interface Reader {
        Optional<?> read(PatientSourceAdapter adapter, String patient);
    }

    /**
     * One history: its source path segment, the {@code resourceType} of its records, how its
     * history is read unfiltered and its fault stubs with what a record of their failure must not
     * quote.
     */
    private record History(String name, String segment, String resourceType, Reader reader,
            Supplier<List<FaultStub>> faults, Function<FaultStub, List<String>> values) {

        @Override
        public String toString() {
            return name;
        }

        String path(String patient) {
            return "/Patient/" + patient + "/" + segment;
        }

        String fault(String stub) {
            return "fault-" + segment + "-" + stub;
        }
    }

    private static final List<History> HISTORIES = List.of(
            new History("FFI", "ffi", "FFI",
                    (adapter, patient) -> adapter.findFfiHistory(new PatientId(patient),
                            FfiFilter.NONE, HistorySort.SOURCE_ORDER, SECOND_PAGE_OF_FIVE),
                    PatientSourceStubs::ffiFaults, FaultStub::ffiValues),
            new History("NUMC", "numc", "NUMC",
                    (adapter, patient) -> adapter.findNumcHistory(new PatientId(patient),
                            NumcFilter.NONE, HistorySort.SOURCE_ORDER, SECOND_PAGE_OF_FIVE),
                    PatientSourceStubs::numcFaults, FaultStub::numcValues),
            new History("ARMC", "armc", "ARMC",
                    (adapter, patient) -> adapter.findArmcHistory(new PatientId(patient),
                            ArmcFilter.NONE, HistorySort.SOURCE_ORDER, SECOND_PAGE_OF_FIVE),
                    PatientSourceStubs::armcFaults, FaultStub::armcValues),
            new History("Medication", "medication", "Medication",
                    (adapter, patient) -> adapter.findMedicationHistory(new PatientId(patient),
                            MedicationFilter.NONE, HistorySort.SOURCE_ORDER, SECOND_PAGE_OF_FIVE),
                    PatientSourceStubs::medicationFaults, FaultStub::medicationValues));

    /** A fault as its record reports it: its fixed message, its reason and its category. */
    private record Fault(String message, String reason, ErrorCategory category) {
    }

    private static Fault data(String message, String reason) {
        return new Fault(message, reason, ErrorCategory.DATA);
    }

    /** A fault stub every history serves, by its suffix, and the fault it is. */
    private record SharedFaultStub(String suffix, Fault fault) {
    }

    private static final List<SharedFaultStub> SHARED_FAULT_STUBS = List.of(
            new SharedFaultStub("unreachable", new Fault(
                    "The patient source could not be reached", "unreachable",
                    ErrorCategory.NETWORK)),
            new SharedFaultStub("server-error", new Fault("The patient source answered 503",
                    "server-error", ErrorCategory.SERVER)),
            new SharedFaultStub("unexpected-status",
                    data("The patient source answered 400", "unexpected-status")),
            new SharedFaultStub("not-a-searchset",
                    data("The patient source's page is no searchset", "not-a-searchset")),
            new SharedFaultStub("no-total",
                    data("The patient source's page has no total", "no-total")));

    /**
     * The fault stubs every history serves and the slow one, for a history's own adapter tests to
     * set its own fault stubs beside.
     */
    static List<String> sharedFaultStubs(String segment) {
        return Stream.concat(Stream.of("slow"),
                        SHARED_FAULT_STUBS.stream().map(SharedFaultStub::suffix))
                .map(suffix -> "fault-" + segment + "-" + suffix).toList();
    }

    static Stream<History> histories() {
        return HISTORIES.stream();
    }

    static Stream<Arguments> otherStatuses() {
        return HISTORIES.stream().flatMap(history -> Stream.of(
                Arguments.of(history, 400, "unexpected-status", ErrorCategory.DATA),
                Arguments.of(history, 401, "unexpected-status", ErrorCategory.DATA),
                Arguments.of(history, 302, "unexpected-status", ErrorCategory.DATA),
                Arguments.of(history, 500, "server-error", ErrorCategory.SERVER),
                Arguments.of(history, 503, "server-error", ErrorCategory.SERVER)));
    }

    static Stream<Arguments> sharedFaults() {
        return HISTORIES.stream().flatMap(history -> SHARED_FAULT_STUBS.stream()
                .map(stub -> Arguments.of(history, history.fault(stub.suffix()), stub.fault())));
    }

    static Stream<Arguments> anotherResource() {
        return HISTORIES.stream().flatMap(history -> Stream.concat(
                Stream.of("{}", entryOf("Patient")),
                HISTORIES.stream().filter(other -> other != history)
                        .map(other -> entryOf(other.resourceType())))
                .map(entry -> Arguments.of(history, entry)));
    }

    static Stream<Arguments> failedReads() {
        return HISTORIES.stream().flatMap(history -> history.faults().get().stream()
                .filter(stub -> !stub.id().endsWith("-slow"))
                .map(stub -> Arguments.of(history, stub)));
    }

    private static String entryOf(String resourceType) {
        return "{\"resource\": {\"resourceType\": \"" + resourceType + "\", \"id\": \"x\"}}";
    }

    private static PatientSourceAdapter adapter() {
        return new PatientSourceAdapter(RestClient.builder(), SOURCE.baseUrl());
    }

    private static void answering(History history, String body) {
        SOURCE.stubFor(get(urlPathEqualTo(history.path(PROBE_PATIENT))).atPriority(1)
                .willReturn(okJson(body)));
    }

    private static Fault faultReading(History history, PatientSourceAdapter adapter,
            String patient) {
        PatientSourceException fault = (PatientSourceException) catchThrowable(() ->
                history.reader().read(adapter, patient));
        return new Fault(fault.getMessage(), fault.reason(), fault.category());
    }

    private static LoggedRequest sent(History history) {
        return SOURCE.findAll(getRequestedFor(urlPathEqualTo(history.path(PROBE_PATIENT))))
                .getFirst();
    }

    /**
     * The read asks the source for the patient's history from the page's zero-based offset, for
     * the page's count, with an accurate total — and, with no filter and no column to sort by,
     * nothing else: no {@code _sort}, so the order is the source's own.
     */
    @ParameterizedTest
    @MethodSource("histories")
    void aReadAsksTheSourceForThePageAndItsTotalInTheSourcesOrder(History history) {
        answering(history, EMPTY_PAGE);

        history.reader().read(adapter(), PROBE_PATIENT);

        Map<String, List<String>> query = new TreeMap<>();
        sent(history).getQueryParams().forEach((name, value) -> query.put(name, value.values()));
        assertThat(query).isEqualTo(Map.of(
                "_offset", List.of("5"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /** As every read of the source, it presents nothing, and asks for FHIR JSON. */
    @ParameterizedTest
    @MethodSource("histories")
    void aReadPresentsNoCredentialAndAsksForFhirJson(History history) {
        answering(history, EMPTY_PAGE);

        history.reader().read(adapter(), PROBE_PATIENT);

        LoggedRequest sent = sent(history);
        assertThat(List.of(sent.containsHeader("Authorization"), sent.containsHeader("Cookie"),
                sent.getHeader("Accept")))
                .containsExactly(false, false, "application/fhir+json");
    }

    /** Nothing is cached: the same read twice is two calls to the source. */
    @ParameterizedTest
    @MethodSource("histories")
    void everyReadIsACallToTheSource(History history) {
        answering(history, EMPTY_PAGE);
        PatientSourceAdapter adapter = adapter();

        history.reader().read(adapter, PROBE_PATIENT);
        history.reader().read(adapter, PROBE_PATIENT);

        SOURCE.verify(2, getRequestedFor(urlPathEqualTo(history.path(PROBE_PATIENT))));
    }

    /** Only a {@code 200} is a page and only a {@code 404} no patient: any other status is a fault. */
    @ParameterizedTest
    @MethodSource("otherStatuses")
    void aStatusButAPageOrNoPatientIsAFault(History history, int status, String reason,
            ErrorCategory category) {
        SOURCE.stubFor(get(urlPathEqualTo(history.path(PROBE_PATIENT))).atPriority(1)
                .willReturn(aResponse().withStatus(status)));

        assertThat(faultReading(history, adapter(), PROBE_PATIENT))
                .isEqualTo(new Fault("The patient source answered " + status, reason, category));
    }

    @ParameterizedTest
    @MethodSource("histories")
    void aReadUnansweredWithinTheReadTimeoutTimesOut(History history) {
        SOURCE.stubFor(get(urlPathEqualTo(history.path(PROBE_PATIENT))).atPriority(1)
                .willReturn(okJson(EMPTY_PAGE).withFixedDelay(1000)));
        PatientSourceAdapter impatient = new PatientSourceAdapter(RestClient.builder(),
                SOURCE.baseUrl(), Duration.ofSeconds(2), Duration.ofMillis(100));

        assertThat(faultReading(history, impatient, PROBE_PATIENT)).isEqualTo(new Fault(
                "The patient source did not answer in time", "timed-out", ErrorCategory.NETWORK));
    }

    /**
     * Each fault stub every history serves, by the failure it serves, but the slow one, whose
     * wait is the production read timeout's (the case above times out the same way, sooner).
     */
    @ParameterizedTest
    @MethodSource("sharedFaults")
    void eachSharedFaultStubIsTheFaultItServes(History history, String patient,
            Fault expected) {
        assertThat(faultReading(history, adapter(), patient)).isEqualTo(expected);
    }

    /** An entry holding no record of the history — nothing at all, a Patient or another history's — is another resource. */
    @ParameterizedTest
    @MethodSource("anotherResource")
    void anEntryHoldingNoRecordIsAFault(History history, String entry) {
        answering(history, """
                {"resourceType": "Bundle", "type": "searchset", "total": 1, "entry": [%s]}"""
                .formatted(entry));

        assertThat(faultReading(history, adapter(), PROBE_PATIENT)).isEqualTo(
                data("The patient source answered with another resource", "another-resource"));
    }

    /**
     * A failed read names nothing it asked for or got: not the patient's id, nor anything of the
     * answer, in its message, its cause or anything a record of it would print.
     */
    @ParameterizedTest
    @MethodSource("failedReads")
    void aFailedReadSaysNothingOfThePatientOrTheAnswer(History history, FaultStub stub) {
        Throwable fault = catchThrowable(() -> history.reader().read(adapter(), stub.id()));

        StringWriter printed = new StringWriter();
        fault.printStackTrace(new PrintWriter(printed));
        List<String> quoted = new ArrayList<>(history.values().apply(stub));
        quoted.add(stub.id());
        assertThat(printed.toString()).doesNotContain(quoted.toArray(String[]::new));
    }
}
