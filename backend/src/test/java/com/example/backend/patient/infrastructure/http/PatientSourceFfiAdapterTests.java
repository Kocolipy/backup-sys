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
import com.example.backend.patient.domain.PatientId;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
 * A patient's FFI History as the HTTP adapter reads it (ADR 0019), against the patient source's
 * mock in process: the one call it sends — the page, counted by the source, in the source's own
 * order — and what a failure of that call is. What the mock's FFI Histories hold is
 * {@code PatientStoreContract}'s.
 */
class PatientSourceFfiAdapterTests {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    private static final PageRequest SECOND_PAGE_OF_FIVE = new PageRequest(6, 5);

    /** A patient id no stub file names, for a case's own stub, and which no message may quote. */
    private static final String PROBE_PATIENT = "probe-patient-R4w9";

    private static final String PROBE_PATH = "/Patient/" + PROBE_PATIENT + "/ffi";

    /** One FFI as the source answers it, every field there and well-formed. */
    private static final Map<String, String> WELL_FORMED = Map.of(
            "resourceType", "FFI",
            "id", "probe-ffi-Z8k2",
            "type", "ippt",
            "outcome", "request-waiver",
            "issueDate", "2017-02-13",
            "expiryDate", "2018-02-12",
            "higherAuthority", "cnmo",
            "submittingMc", "hendon-camp",
            "status", "active");

    /** {@link #WELL_FORMED} with {@code field} set to {@code value}, or left out for {@code null}. */
    private static String ffi(String field, String value) {
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

    private static String page(String... ffis) {
        return """
                {"resourceType": "Bundle", "type": "searchset", "total": %d,
                 "entry": [%s]}""".formatted(ffis.length,
                String.join(", ", Stream.of(ffis)
                        .map(ffi -> "{\"resource\": " + ffi + "}").toList()));
    }

    private static PatientSourceAdapter adapter() {
        return new PatientSourceAdapter(RestClient.builder(), SOURCE.baseUrl());
    }

    private static void answering(String body) {
        SOURCE.stubFor(get(urlPathEqualTo(PROBE_PATH)).atPriority(1).willReturn(okJson(body)));
    }

    /** A fault as its record reports it: its fixed message, its reason and its category. */
    private record Fault(String message, String reason, ErrorCategory category) {
    }

    private static Fault faultReading(PatientSourceAdapter adapter, String patient) {
        PatientSourceException fault = (PatientSourceException) catchThrowable(() ->
                adapter.findFfiHistory(new PatientId(patient), SECOND_PAGE_OF_FIVE));
        return new Fault(fault.getMessage(), fault.reason(), fault.category());
    }

    private static Fault faultReading(PatientSourceAdapter adapter) {
        return faultReading(adapter, PROBE_PATIENT);
    }

    private static Fault data(String message, String reason) {
        return new Fault(message, reason, ErrorCategory.DATA);
    }

    // ---- the call ------------------------------------------------------------------------

    /**
     * The read asks the source for the patient's FFI History from the page's zero-based offset,
     * for the page's count, with an accurate total — and no sort, so the order is the source's.
     */
    @Test
    void aReadAsksTheSourceForThePageAndItsTotalInTheSourcesOrder() {
        answering(page());

        adapter().findFfiHistory(new PatientId(PROBE_PATIENT), SECOND_PAGE_OF_FIVE);

        LoggedRequest sent = SOURCE.findAll(getRequestedFor(urlPathEqualTo(PROBE_PATH)))
                .getFirst();
        Map<String, List<String>> query = new TreeMap<>();
        sent.getQueryParams().forEach((name, value) -> query.put(name, value.values()));
        assertThat(query).isEqualTo(Map.of(
                "_offset", List.of("5"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /** As every read of the source, it presents nothing, and asks for FHIR JSON. */
    @Test
    void aReadPresentsNoCredentialAndAsksForFhirJson() {
        answering(page());

        adapter().findFfiHistory(new PatientId(PROBE_PATIENT), SECOND_PAGE_OF_FIVE);

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
        PatientSourceAdapter adapter = adapter();

        adapter.findFfiHistory(new PatientId(PROBE_PATIENT), SECOND_PAGE_OF_FIVE);
        adapter.findFfiHistory(new PatientId(PROBE_PATIENT), SECOND_PAGE_OF_FIVE);

        SOURCE.verify(2, getRequestedFor(urlPathEqualTo(PROBE_PATH)));
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
     * Each fault stub of an FFI History, by the failure it serves, but the slow one, whose wait is
     * the production read timeout's (the case above times out the same way, sooner).
     */
    static Stream<Arguments> faultStubs() {
        return Stream.of(
                Arguments.of("fault-ffi-unreachable", new Fault(
                        "The patient source could not be reached", "unreachable",
                        ErrorCategory.NETWORK)),
                Arguments.of("fault-ffi-server-error", new Fault(
                        "The patient source answered 503", "server-error", ErrorCategory.SERVER)),
                Arguments.of("fault-ffi-unexpected-status",
                        data("The patient source answered 400", "unexpected-status")),
                Arguments.of("fault-ffi-not-a-searchset", data(
                        "The patient source's page is no searchset",
                        "not-a-searchset")),
                Arguments.of("fault-ffi-no-total",
                        data("The patient source's page has no total", "no-total")),
                Arguments.of("fault-ffi-no-id",
                        data("The patient source's FFI has no id", "no-id")),
                Arguments.of("fault-ffi-invalid-id",
                        data("The patient source's FFI's id is not a FHIR id", "invalid-id")),
                Arguments.of("fault-ffi-no-outcome",
                        data("The patient source's FFI has no outcome", "no-outcome")),
                Arguments.of("fault-ffi-unknown-higher-authority", data(
                        "The patient source's FFI's higher authority is not a Higher Authority",
                        "unknown-higher-authority")),
                Arguments.of("fault-ffi-expiry-date-not-to-the-day", data(
                        "The patient source's FFI's expiry date is not to the day",
                        "expiry-date-not-to-the-day")));
    }

    @ParameterizedTest
    @MethodSource("faultStubs")
    void eachFaultStubIsTheFaultItServes(String patient, Fault expected) {
        assertThat(faultReading(adapter(), patient)).isEqualTo(expected);
    }

    /** The cases above and the slow stub are every FFI fault stub the stub files hold. */
    @Test
    void everyFfiFaultStubIsReadAbove() {
        Stream<String> covered = Stream.concat(Stream.of("fault-ffi-slow"),
                faultStubs().map(arguments -> (String) arguments.get()[0]));

        assertThat(PatientSourceStubs.ffiFaults()).extracting(PatientSourceStubs.FaultStub::id)
                .containsExactlyInAnyOrderElementsOf(covered.toList());
    }

    /** Every field is required: an FFI missing any one is a fault naming the field. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        id              | no-id               | The patient source's FFI has no id
        type            | no-type             | The patient source's FFI has no type
        outcome         | no-outcome          | The patient source's FFI has no outcome
        issueDate       | no-issue-date       | The patient source's FFI has no issue date
        expiryDate      | no-expiry-date      | The patient source's FFI has no expiry date
        higherAuthority | no-higher-authority | The patient source's FFI has no higher authority
        submittingMc    | no-submitting-mc    | The patient source's FFI has no submitting MC
        status          | no-status           | The patient source's FFI has no status
        """)
    void anFfiMissingAFieldIsAFault(String field, String reason, String message) {
        answering(page(ffi(field, null)));

        assertThat(faultReading(adapter())).isEqualTo(data(message, reason));
    }

    /**
     * A code outside its closed set is a fault naming the set: a code is matched exactly, so
     * one in another case, or a label in place of the code, is outside it too.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        type            | IPPT FFI              | unknown-type             | The patient source's FFI's type is not an FFI Type
        type            | swimming              | unknown-type             | The patient source's FFI's type is not an FFI Type
        outcome         | FIT                   | unknown-outcome          | The patient source's FFI's outcome is not an Outcome
        outcome         | expired               | unknown-outcome          | The patient source's FFI's outcome is not an Outcome
        higherAuthority | comd_mmi              | unknown-higher-authority | The patient source's FFI's higher authority is not a Higher Authority
        submittingMc    | nee-soon-camp-mc      | unknown-submitting-mc    | The patient source's FFI's submitting MC is not a Submitting MC
        status          | expired               | unknown-status           | The patient source's FFI's status is not a Status
        status          | Active                | unknown-status           | The patient source's FFI's status is not a Status
        """)
    void aCodeOutsideItsClosedSetIsAFault(String field, String code, String reason,
            String message) {
        answering(page(ffi(field, code)));

        assertThat(faultReading(adapter())).isEqualTo(data(message, reason));
    }

    /** Each date must be a calendar date to the day: a month, a time or no day the calendar has is a fault. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        issueDate  | 2017-02             | issue-date-not-to-the-day  | The patient source's FFI's issue date is not to the day
        issueDate  | 2017-02-13T00:00:00 | issue-date-not-to-the-day  | The patient source's FFI's issue date is not to the day
        expiryDate | 2018                | expiry-date-not-to-the-day | The patient source's FFI's expiry date is not to the day
        expiryDate | 2018-02-30          | expiry-date-not-to-the-day | The patient source's FFI's expiry date is not to the day
        """)
    void aDateNotToTheDayIsAFault(String field, String date, String reason, String message) {
        answering(page(ffi(field, date)));

        assertThat(faultReading(adapter())).isEqualTo(data(message, reason));
    }

    /** An entry holding no FFI — nothing at all, or another resource — is another resource. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        {"resourceType": "Bundle", "type": "searchset", "total": 1, "entry": [{}]}
        {"resourceType": "Bundle", "type": "searchset", "total": 1, "entry": [{"resource": {"resourceType": "Patient", "id": "x"}}]}
        """)
    void anEntryHoldingNoFfiIsAFault(String body) {
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
        answering(page(ffi("status", "lapsed")));

        Throwable fault = catchThrowable(() ->
                adapter().findFfiHistory(new PatientId(PROBE_PATIENT), SECOND_PAGE_OF_FIVE));

        StringWriter printed = new StringWriter();
        fault.printStackTrace(new PrintWriter(printed));
        assertThat(printed.toString())
                .doesNotContain(PROBE_PATIENT, "probe-ffi-Z8k2", "2017-02-13", "2018-02-12",
                        "lapsed");
    }
}
