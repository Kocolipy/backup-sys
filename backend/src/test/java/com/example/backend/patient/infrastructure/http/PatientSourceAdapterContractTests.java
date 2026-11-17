package com.example.backend.patient.infrastructure.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.example.backend.audit.CapturedLog;
import com.example.backend.observability.LogEvent;
import com.example.backend.observability.LogEvent.ErrorCategory;
import com.example.backend.observability.RedactedFaultException;
import com.example.backend.patient.PatientSourceStubs;
import com.example.backend.patient.PatientStoreContract;
import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.domain.AdministrativeGender;
import com.example.backend.patient.domain.Demographics;
import com.example.backend.patient.domain.MilitaryHistory;
import com.example.backend.patient.domain.NextOfKin;
import com.example.backend.patient.domain.Patient;
import com.example.backend.patient.domain.PatientFilter;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.PatientMatch;
import com.example.backend.patient.domain.PatientName;
import com.example.backend.patient.domain.PatientQuery;
import com.example.backend.patient.domain.PatientStore;
import com.example.backend.patient.domain.SourceField;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * The production patient port — the HTTP adapter over the in-house patient source — read against
 * the patient source's mock, in process, serving the stub files the local mock serves; and what
 * the adapter alone decides: the call it makes, that nothing is cached, what a failure of the
 * source is, and that it says nothing of the patient when it is one.
 */
class PatientSourceAdapterContractTests extends PatientStoreContract {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    /** Tan Wei Ming's id: a fixture the stub files serve. */
    private static final PatientId TAN = new PatientId("eq081-VQEgP8drUUqCWzHfw3");

    /** An id no stub file names, for a case's own stub. */
    private static final PatientId PROBE = new PatientId("probe-Q7xKd2.v9");

    /** A Patient body whose every value is one no message could hold by chance. */
    private static final String PROBE_BODY = """
            {"resourceType": "Patient", "id": "probe-Q7xKd2.v9",
             "identifier": [{"system": "https://fhir.synapxe.sg/NamingSystem/nric",
                             "value": "T7391046Q"}],
             "name": [{"use": "official", "text": "Quah Seow Peng"}],
             "gender": "female", "birthDate": "%s"}""";

    @Override
    protected PatientStore patients() {
        return PatientSourceAdapters.at(SOURCE.baseUrl());
    }

    private static PatientSourceAdapter at(String baseUrl) {
        return PatientSourceAdapters.at(baseUrl);
    }

    private static void answering(PatientId id, String body) {
        SOURCE.stubFor(get(urlPathEqualTo("/Patient/" + id.value())).willReturn(okJson(body)));
    }

    /**
     * A fault as its record reports it: its fixed message, the failure it names and the
     * {@code error.category} it is reported under.
     */
    private record Fault(String message, String reason, ErrorCategory category) {

        static Fault of(Throwable thrown) {
            PatientSourceException fault = (PatientSourceException) thrown;
            return new Fault(fault.getMessage(), fault.reason(), fault.category());
        }
    }

    private static Fault faultReading(PatientStore patients, PatientId id) {
        return Fault.of(catchThrowable(() -> patients.findById(id)));
    }

    /** What a log record attaching {@code failure} would print: every message in its chain. */
    private static String printed(Throwable failure) {
        StringWriter out = new StringWriter();
        failure.printStackTrace(new PrintWriter(out));
        return out.toString();
    }

    // ---- the call ------------------------------------------------------------------------

    /** The source is assumed to need no authorisation: a read presents nothing to it. */
    @Test
    void aReadPresentsNoCredentialToTheSource() {
        patients().findById(TAN);

        LoggedRequest read = SOURCE.findAll(getRequestedFor(urlPathEqualTo("/Patient/" + TAN)))
                .getFirst();
        assertThat(List.of(read.containsHeader("Authorization"), read.containsHeader("Cookie")))
                .containsExactly(false, false);
    }

    @Test
    void aReadAsksForFhirJson() {
        patients().findById(TAN);

        SOURCE.verify(getRequestedFor(urlPathEqualTo("/Patient/" + TAN))
                .withHeader("Accept", equalTo("application/fhir+json")));
    }

    /** No Patient is cached: every read, of the same patient too, is a call to the source. */
    @Test
    void everyReadIsACallToTheSource() {
        PatientStore patients = patients();

        patients.findById(TAN);
        patients.findById(TAN);

        SOURCE.verify(2, getRequestedFor(urlPathEqualTo("/Patient/" + TAN)));
    }

    /** A base with a path of its own, written with its trailing slash, keeps its path. */
    @Test
    void aBaseUrlWithAPathIsReadBeneathItsPath() {
        SOURCE.stubFor(get(urlPathEqualTo("/fhir/r4/Patient/" + PROBE))
                .willReturn(okJson(PROBE_BODY.formatted("1963-07-29"))));

        assertThat(at(SOURCE.baseUrl() + "/fhir/r4/").findById(PROBE)).isPresent();
    }

    /** The timeouts the patient source's calls take, as the configuration docs state them. */
    @Test
    void theSourceIsGivenTwoSecondsToConnectAndFiveToAnswer() {
        assertThat(List.of(PatientSourceAdapter.CONNECT_TIMEOUT,
                PatientSourceAdapter.DEFAULT_READ_TIMEOUT))
                .containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(5));
    }

    /** {@code APP_PATIENT_SOURCE_READ_TIMEOUT} unset, or set to nothing, is the five seconds. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void anUnsetReadTimeoutIsTheDefault(String setting) {
        assertThat(PatientSourceAdapter.readTimeout(setting))
                .isEqualTo(PatientSourceAdapter.DEFAULT_READ_TIMEOUT);
    }

    /** Either style, and with the space an {@code .env} line can leave around it. */
    @ParameterizedTest
    @CsvSource({"1500ms, PT1.5S", "2s, PT2S", "PT3S, PT3S", "' 2s ', PT2S"})
    void aReadTimeoutIsReadInEitherDurationStyle(String setting, Duration expected) {
        assertThat(PatientSourceAdapter.readTimeout(setting)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1s", "soon"})
    void aReadTimeoutThatIsNotAPositiveDurationIsRefused(String setting) {
        assertThatThrownBy(() -> PatientSourceAdapter.readTimeout(setting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("APP_PATIENT_SOURCE_READ_TIMEOUT must be a positive duration");
    }

    /**
     * The service's adapter reads its read timeout from the setting: a Patient the source answers
     * in a second, read under a tenth of one, is not waited for. Under the default five seconds
     * the read would wait the second out and get the Patient.
     */
    @Test
    void theServicesAdapterIsGivenTheConfiguredReadTimeout() {
        SOURCE.stubFor(get(urlPathEqualTo("/Patient/" + PROBE)).willReturn(
                okJson(PROBE_BODY.formatted("1963-07-29")).withFixedDelay(1000)));

        new ApplicationContextRunner()
                .withBean(RestClient.Builder.class, RestClient::builder)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withUserConfiguration(PatientSourceAdapter.class)
                .withPropertyValues("app.patient-source.base-url=" + SOURCE.baseUrl(),
                        "app.patient-source.read-timeout=100ms")
                .run(context -> assertThat(faultReading(
                        context.getBean(PatientStore.class), PROBE))
                        .isEqualTo(new Fault("The patient source did not answer in time",
                                "timed-out", ErrorCategory.NETWORK)));
    }

    // ---- the base URL --------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "patients.internal/fhir", "ftp://patients.internal/fhir",
        "http:///fhir", "http://patients internal/fhir"})
    void aBaseUrlThatIsNotAnAbsoluteHttpUrlIsRefused(String baseUrl) {
        assertThatThrownBy(() -> at(baseUrl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("APP_PATIENT_SOURCE_BASE_URL must be an absolute http or https URL");
    }

    @Test
    void anUnsetBaseUrlIsRefused() {
        assertThatThrownBy(() -> at(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("APP_PATIENT_SOURCE_BASE_URL must be an absolute http or https URL");
    }

    /** The setting has no default: a start without it fails, and a start with it reads it. */
    @Test
    void theServiceDoesNotStartWithoutTheBaseUrl() {
        ApplicationContextRunner contexts = new ApplicationContextRunner()
                .withBean(RestClient.Builder.class, RestClient::builder)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withUserConfiguration(PatientSourceAdapter.class);

        contexts.run(context -> assertThat(context).hasFailed());
        contexts.withPropertyValues("app.patient-source.base-url=" + SOURCE.baseUrl())
                .run(context -> assertThat(context.getBean(PatientStore.class).findById(TAN))
                        .isPresent());
    }

    // ---- a failure of the source ---------------------------------------------------------

    /**
     * Only a {@code 404} is no patient: any other answer is a fault, not an absent patient — a
     * {@code 5xx} the source's own failure, reported as {@code server}, and any other status an
     * answer the service cannot use, reported as {@code data}.
     */
    @ParameterizedTest
    @CsvSource({
        "201, unexpected-status, DATA",
        "204, unexpected-status, DATA",
        "400, unexpected-status, DATA",
        "401, unexpected-status, DATA",
        "403, unexpected-status, DATA",
        "410, unexpected-status, DATA",
        "500, server-error, SERVER",
        "502, server-error, SERVER",
        "503, server-error, SERVER",
        "599, server-error, SERVER"})
    void anAnswerOtherThanAPatientOrA404IsAFault(int status, String reason,
            ErrorCategory category) {
        SOURCE.stubFor(get(urlPathEqualTo("/Patient/" + PROBE))
                .willReturn(aResponse().withStatus(status)));

        assertThat(faultReading(patients(), PROBE))
                .isEqualTo(new Fault("The patient source answered " + status, reason, category));
    }

    /**
     * A redirect is never followed, even to a Patient on the source itself: the read is the
     * {@code 302} it got, a fault, and the place it points at is never asked.
     */
    @Test
    void aRedirectIsAFaultAndIsNotFollowed() {
        SOURCE.stubFor(get(urlPathEqualTo("/Patient/" + PROBE)).willReturn(aResponse()
                .withStatus(302).withHeader("Location", SOURCE.baseUrl() + "/elsewhere")));
        SOURCE.stubFor(get(urlPathEqualTo("/elsewhere"))
                .willReturn(okJson(PROBE_BODY.formatted("1963-07-29"))));

        assertThat(faultReading(patients(), PROBE)).isEqualTo(new Fault(
                "The patient source answered 302", "unexpected-status", ErrorCategory.DATA));
    }

    @Test
    void aSourceNothingListensOnIsUnreachable() {
        assertThat(faultReading(at(closedPort()), TAN)).isEqualTo(new Fault(
                "The patient source could not be reached", "unreachable", ErrorCategory.NETWORK));
    }

    /**
     * The fault stub that answers only after the read timeout: the read gives up and times out.
     * The stub waits past the production timeout; this adapter is given a shorter one, so the
     * case need not wait the five seconds.
     */
    @Test
    void theSlowFaultStubTimesOut() {
        PatientSourceAdapter impatient = PatientSourceAdapters.at(SOURCE.baseUrl(), Duration.ofSeconds(2), Duration.ofMillis(300));

        assertThat(faultReading(impatient, new PatientId("fault-slow"))).isEqualTo(new Fault(
                "The patient source did not answer in time", "timed-out", ErrorCategory.NETWORK));
    }

    /**
     * The read timeout is what gives up: a Patient the source answers in a second, read with a
     * tenth of one, is not waited for. Without the timeout the read would wait the second out and
     * get the Patient, so the case fails in that second rather than hanging on the slow stub.
     */
    @Test
    void anAnswerSlowerThanTheReadTimeoutIsNotWaitedFor() {
        SOURCE.stubFor(get(urlPathEqualTo("/Patient/" + PROBE)).willReturn(
                okJson(PROBE_BODY.formatted("1963-07-29")).withFixedDelay(1000)));
        PatientSourceAdapter impatient = PatientSourceAdapters.at(SOURCE.baseUrl(), Duration.ofSeconds(2), Duration.ofMillis(100));

        assertThat(faultReading(impatient, PROBE)).isEqualTo(new Fault(
                "The patient source did not answer in time", "timed-out", ErrorCategory.NETWORK));
    }

    /** The slow fault stub waits past the production read timeout, not merely the test's. */
    @Test
    void theSlowFaultStubWaitsPastTheReadTimeout() {
        PatientSourceStubs.FaultStub slow = PatientSourceStubs.faults().stream()
                .filter(fault -> fault.id().equals("fault-slow")).findFirst().orElseThrow();

        assertThat(Duration.ofMillis(slow.response().at("/fixedDelayMilliseconds").asLong()))
                .isGreaterThan(PatientSourceAdapter.DEFAULT_READ_TIMEOUT);
    }

    /**
     * The fault stub whose headers come at once and whose body is dribbled out past the read
     * timeout: the read gives up on the body and times out, as it does on headers that never
     * come, rather than taking the cut-off body for an unreadable one.
     */
    @Test
    void theDribbledBodyFaultStubTimesOut() {
        PatientSourceAdapter impatient = PatientSourceAdapters.at(SOURCE.baseUrl(), Duration.ofSeconds(2), Duration.ofMillis(300));

        assertThat(faultReading(impatient, new PatientId("fault-slow-body"))).isEqualTo(new Fault(
                "The patient source did not answer in time", "timed-out", ErrorCategory.NETWORK));
    }

    /**
     * The dribbled stub spreads its body past the production read timeout, but sends its headers
     * with its first chunk, within a tenth of a second and with no delay before it: it is the
     * body, not the headers, that a read waits on, the impatient one above included.
     */
    @Test
    void theDribbledBodyFaultStubDribblesPastTheReadTimeout() {
        JsonNode response = PatientSourceStubs.faults().stream()
                .filter(fault -> fault.id().equals("fault-slow-body")).findFirst().orElseThrow()
                .response();
        Duration total = Duration.ofMillis(response.at("/chunkedDribbleDelay/totalDuration")
                .asLong());
        Duration eachChunk = total.dividedBy(response.at("/chunkedDribbleDelay/numberOfChunks")
                .asLong());

        assertThat(List.of(response.at("/fixedDelayMilliseconds").isMissingNode(),
                total.compareTo(PatientSourceAdapter.DEFAULT_READ_TIMEOUT) > 0,
                eachChunk.compareTo(Duration.ofMillis(100)) <= 0))
                .containsExactly(true, true, true);
    }

    /**
     * A source whose connection is reset once the headers and part of the body are in gave no
     * answer: unreachable, as a source nothing listens on is, not an unreadable body.
     */
    @Test
    void aConnectionResetMidBodyIsUnreachable() throws IOException {
        // Test-only: a plain-HTTP source on loopback, as the patient source's mock is; a raw
        // socket because only one can reset a connection mid-body. Nothing it carries leaves the
        // host, and the adapter under test speaks plain HTTP to its mock, so TLS has nothing to
        // protect here.
        // nosemgrep: java.lang.security.audit.crypto.unencrypted-socket.unencrypted-socket
        try (ServerSocket source = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread.ofVirtual().start(() -> resettingMidBody(source));

            assertThat(faultReading(at("http://127.0.0.1:" + source.getLocalPort()), PROBE))
                    .isEqualTo(new Fault("The patient source could not be reached",
                            "unreachable", ErrorCategory.NETWORK));
        }
    }

    /**
     * A source that never takes the connection gives up at the connect timeout, long before the
     * read timeout: timed out, not unreachable. The source's accept queue is full — it listens
     * but takes nothing, and two connections already wait in its queue of one — so the
     * adapter's connection is never completed.
     */
    @Test
    void aConnectionNotTakenWithinTheConnectTimeoutTimesOut() throws IOException {
        // Test-only: a plain-TCP listener on loopback that never accepts; nothing is sent over
        // it, so TLS has nothing to protect.
        // nosemgrep: java.lang.security.audit.crypto.unencrypted-socket.unencrypted-socket
        try (ServerSocket source = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                Socket first = queued(source);
                Socket second = queued(source)) {
            PatientSourceAdapter adapter = PatientSourceAdapters.at("http://127.0.0.1:" + source.getLocalPort(), Duration.ofMillis(200), Duration.ofSeconds(30));

            assertThat(faultReading(adapter, PROBE)).isEqualTo(new Fault(
                    "The patient source did not answer in time", "timed-out",
                    ErrorCategory.NETWORK));
        }
    }

    /** A connection left waiting in {@code source}'s accept queue. */
    private static Socket queued(ServerSocket source) throws IOException {
        // Test-only: a loopback client socket that sends nothing, only fills the accept queue,
        // so TLS has nothing to protect.
        // nosemgrep: java.lang.security.audit.crypto.unencrypted-socket.unencrypted-socket
        return new Socket(InetAddress.getLoopbackAddress(), source.getLocalPort());
    }

    /**
     * Answers one request with a {@code 200}'s headers and the start of its body, then resets
     * the connection: a close with no linger sends a TCP reset, not an orderly end.
     */
    private static void resettingMidBody(ServerSocket source) {
        try (Socket call = source.accept()) {
            InputStream request = call.getInputStream();
            String endOfHeaders = "\r\n\r\n";
            int matched = 0;
            while (matched < endOfHeaders.length()) {
                int next = request.read();
                if (next < 0) {
                    return;
                }
                matched = next == endOfHeaders.charAt(matched) ? matched + 1
                        : next == '\r' ? 1 : 0;
            }
            OutputStream answer = call.getOutputStream();
            answer.write(("HTTP/1.1 200 OK\r\nContent-Type: application/fhir+json\r\n"
                    + "Content-Length: 400\r\n\r\n{\"resourceType\": \"Patient\", ")
                    .getBytes(StandardCharsets.US_ASCII));
            answer.flush();
            Thread.sleep(100);
            call.setSoLinger(true, 0);
        } catch (IOException | InterruptedException ended) {
            // The case reads the failure on the client's side; the server's own is no part of it.
        }
    }

    /**
     * Each fault stub that answers at once, as the fault it is: its message, the failure it
     * names, and its category — a {@code 5xx} the source's failure.
     */
    static Stream<Arguments> faultStubs() {
        return Stream.of(
                arguments("fault-server-error", new Fault(
                        "The patient source answered 503", "server-error", ErrorCategory.SERVER)));
    }

    @ParameterizedTest
    @MethodSource("faultStubs")
    void eachFaultStubIsTheFaultItServes(String id, Fault expected) {
        assertThat(faultReading(patients(), new PatientId(id))).isEqualTo(expected);
    }

    /** The cases above and the two slow stubs' are every fault stub the stub files hold. */
    @Test
    void everyFaultStubIsReadAbove() {
        Stream<String> covered = Stream.concat(Stream.of("fault-slow", "fault-slow-body"),
                faultStubs().map(arguments -> (String) arguments.get()[0]));

        assertThat(PatientSourceStubs.faults()).extracting(PatientSourceStubs.FaultStub::id)
                .containsExactlyInAnyOrderElementsOf(covered.toList());
    }

    /** A Patient body under the probe's id, its elements given as JSON members. */
    private static String probe(String members) {
        return "{\"id\": \"probe-Q7xKd2.v9\", " + members + "}";
    }

    private static final String NRIC_IDENTIFIER = """
            "identifier": [{"system": "https://fhir.synapxe.sg/NamingSystem/nric",
                            "value": "T7391046Q"}]""";

    private static final String OFFICIAL_NAME =
            "\"name\": [{\"use\": \"official\", \"text\": \"Quah Seow Peng\"}]";

    private static final String GENDER = "\"gender\": \"female\"";

    private static final String BIRTH_DATE = "\"birthDate\": \"1963-07-29\"";

    private static String joined(String... members) {
        return String.join(", ", members);
    }

    /**
     * A {@code 200} whose body is not a Patient the service can hold, each as its fault reports
     * it: another patient's — every read of this patient is made by the id asked for, so an id
     * differing only by case is another — no Patient at all, and no JSON at all, each reported as
     * {@code data}.
     */
    static Stream<Arguments> bodiesTheServiceCannotHold() {
        Fault anotherResource = rule("The patient source answered with another resource",
                "another-resource");
        return Stream.of(
                arguments("{\"id\": \"another-patient\", " + joined(NRIC_IDENTIFIER,
                        OFFICIAL_NAME, GENDER, BIRTH_DATE) + "}", anotherResource),
                arguments("{\"id\": \"PROBE-q7xkd2.v9\", " + joined(NRIC_IDENTIFIER,
                        OFFICIAL_NAME, GENDER, BIRTH_DATE) + "}", anotherResource),
                arguments("{\"resourceType\": \"OperationOutcome\"}", anotherResource),
                arguments("not JSON: T7391046Q", rule("The patient source could not be read",
                        "unreadable")));
    }

    private static Fault rule(String message, String reason) {
        return new Fault(message, reason, ErrorCategory.DATA);
    }

    @ParameterizedTest
    @MethodSource("bodiesTheServiceCannotHold")
    void aBodyThatIsNotAPatientTheServiceCanHoldIsAFault(String body, Fault expected) {
        answering(PROBE, body);

        assertThat(faultReading(patients(), PROBE)).isEqualTo(expected);
    }

    // ---- a Patient as received -------------------------------------------------------------

    /** The probe as its body with every element is read: each element present. */
    private static final Patient READ = new Patient(PROBE, "T7391046Q",
            new PatientName("Quah Seow Peng", null, List.of()), AdministrativeGender.FEMALE,
            LocalDate.of(1963, 7, 29));

    /** The probe's body with these members, an empty one left out. */
    private static String probeWith(String nric, String name, String gender, String birthDate) {
        return probe(String.join(", ", Stream.of(nric, name, gender, birthDate)
                .filter(member -> !member.isEmpty()).toList()));
    }

    private Patient reading(String body) {
        answering(PROBE, body);
        return patients().findById(PROBE).orElseThrow();
    }

    /** The body above with every element is the Patient, each element present. */
    @Test
    void aBodyWithEveryElementIsReadWithEachPresent() {
        assertThat(reading(probeWith(NRIC_IDENTIFIER, OFFICIAL_NAME, GENDER, BIRTH_DATE)))
                .isEqualTo(READ);
    }

    /**
     * An NRIC that is not there — no identifier at all, one under another system only, the
     * NRIC's with no value, or a blank one — is missing, one of the wrong JSON type invalid, and
     * the rest of the Patient is read as it came.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        ''                                                                                               | MISSING
        '"identifier": [{"system": "urn:oid:1.2.3", "value": "T7391046Q"}]'                               | MISSING
        '"identifier": [{"system": "https://fhir.synapxe.sg/NamingSystem/nric"}]'                         | MISSING
        '"identifier": [{"system": "https://fhir.synapxe.sg/NamingSystem/nric", "value": " "}]'           | MISSING
        '"identifier": [{"system": "https://fhir.synapxe.sg/NamingSystem/nric", "value": 7391046}]'       | INVALID
        '"identifier": [{"system": "https://fhir.synapxe.sg/NamingSystem/nric", "value": ["T7391046Q"]}]' | INVALID
        """)
    void anNricIsReadAsReceived(String nric, SourceField.Received received) {
        assertThat(reading(probeWith(nric, OFFICIAL_NAME, GENDER, BIRTH_DATE)))
                .isEqualTo(new Patient(PROBE, new SourceField<>(received, null), READ.name(),
                        READ.gender(), READ.birthDate(), Demographics.NONE));
    }

    /** No official name — none at all, or another use only — is no name. */
    @ParameterizedTest
    @ValueSource(strings = {"", "\"name\": [{\"use\": \"usual\", \"text\": \"Quah Seow Peng\"}]"})
    void aPatientWithNoOfficialNameHasNone(String name) {
        assertThat(reading(probeWith(NRIC_IDENTIFIER, name, GENDER, BIRTH_DATE)).name())
                .isNull();
    }

    /**
     * An official name's text that is not there or blank is missing, one of the wrong JSON type
     * invalid, and its family and given names are read as they came: the text is never rebuilt
     * from them.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        '"name": [{"use": "official", "family": "Quah", "given": ["Seow Peng"]}]'                                                 | MISSING
        '"name": [{"use": "official", "text": " ", "family": "Quah", "given": ["Seow Peng"]}]'                                    | MISSING
        '"name": [{"use": "official", "text": 7, "family": "Quah", "given": ["Seow Peng"]}]'                                      | INVALID
        '"name": [{"use": "usual", "text": "Q"}, {"use": "official", "text": {"a": 1}, "family": "Quah", "given": ["Seow Peng"]}]' | INVALID
        """)
    void anOfficialNamesTextIsReadAsReceived(String name, SourceField.Received received) {
        assertThat(reading(probeWith(NRIC_IDENTIFIER, name, GENDER, BIRTH_DATE)).name())
                .isEqualTo(new PatientName(new SourceField<>(received, null), "Quah",
                        List.of("Seow Peng")));
    }

    /**
     * A gender that is not there is missing, one of the wrong JSON type invalid, and a code
     * outside the four is kept as it came.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "-", delimiter = '|', textBlock = """
        ''                 | MISSING | -
        '"gender": null'   | MISSING | -
        '"gender": 1'      | INVALID | -
        '"gender": "F"'    | PRESENT | F
        '"gender": "Male"' | PRESENT | Male
        """)
    void aGenderIsReadAsReceived(String gender, SourceField.Received received, String code) {
        assertThat(reading(probeWith(NRIC_IDENTIFIER, OFFICIAL_NAME, gender, BIRTH_DATE))
                .gender()).isEqualTo(new SourceField<>(received, code));
    }

    /**
     * A birth date that is not there or blank is missing, and one of the wrong JSON type or that
     * is no calendar date invalid. Nothing of its meaning is checked: one in the future is kept.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "-", delimiter = '|', textBlock = """
        ''                          | MISSING | -
        '"birthDate": ""'           | MISSING | -
        '"birthDate": "1963-07"'    | INVALID | -
        '"birthDate": "1963-02-30"' | INVALID | -
        '"birthDate": "29/07/1963"' | INVALID | -
        '"birthDate": 19630729'     | INVALID | -
        '"birthDate": "2999-01-01"' | PRESENT | 2999-01-01
        """)
    void aBirthDateIsReadAsReceived(String birthDate, SourceField.Received received,
            LocalDate day) {
        assertThat(reading(probeWith(NRIC_IDENTIFIER, OFFICIAL_NAME, GENDER, birthDate))
                .birthDate()).isEqualTo(new SourceField<>(received, day));
    }

    // ---- invalid elements, reported -----------------------------------------------------

    /**
     * A Patient with each of its four elements in the wrong format, the NRIC and the official
     * name each second in their lists.
     */
    private static final String EVERY_ELEMENT_INVALID = probe(joined(
            "\"identifier\": [{\"system\": \"urn:oid:1.2.3\", \"value\": \"MRN-559\"}, "
                    + "{\"system\": \"https://fhir.synapxe.sg/NamingSystem/nric\", "
                    + "\"value\": 7391046}]",
            "\"name\": [{\"use\": \"usual\", \"text\": \"Quah Seow Peng\"}, "
                    + "{\"use\": \"official\", \"text\": 7, \"family\": \"Quah\"}]",
            "\"gender\": true", "\"birthDate\": \"1963-07\""));

    private static PatientSourceAdapter adapter(MeterRegistry meters) {
        return new PatientSourceAdapter(RestClient.builder(), SOURCE.baseUrl(), meters);
    }

    /** The WARN records reporting an answer's invalid fields. */
    private static List<ILoggingEvent> invalidFieldRecords(CapturedLog captured) {
        return captured.withAction(Level.WARN, LogEvent.LOCAL_ACTION,
                "patient_source.invalid_fields");
    }

    /**
     * A Patient holding invalid elements is one WARN, naming the read and each element by its
     * path in the source's answer — never its value, nor the patient.
     */
    @Test
    void aPatientHoldingInvalidElementsIsOneWarnNamingTheirPaths() {
        answering(PROBE, EVERY_ELEMENT_INVALID);

        try (CapturedLog captured = CapturedLog.attach()) {
            patients().findById(PROBE);

            assertThat(invalidFieldRecords(captured)).singleElement()
                    .satisfies(warning -> assertThat(CapturedLog.fields(warning))
                            .containsEntry("app.patient_source.read", "patient")
                            .containsEntry("app.patient_source.invalid_fields", List.of(
                                    "identifier[1].value", "name[1].text", "gender",
                                    "birthDate")));
        }
    }

    @Test
    void theWarnQuotesNoValueOfTheAnswerNorThePatient() {
        answering(PROBE, EVERY_ELEMENT_INVALID);

        try (CapturedLog captured = CapturedLog.attach()) {
            patients().findById(PROBE);

            ILoggingEvent warning = invalidFieldRecords(captured).getFirst();
            assertThat(warning.getFormattedMessage() + " " + CapturedLog.fields(warning))
                    .doesNotContain(PROBE.value(), "7391046", "MRN-559", "Quah", "1963");
        }
    }

    /** A Patient whose elements are each present or missing reports nothing. */
    @Test
    void aPatientWithNoInvalidElementIsNotReported() {
        answering(PROBE, probeWith("", OFFICIAL_NAME, "", BIRTH_DATE));
        MeterRegistry meters = new SimpleMeterRegistry();

        try (CapturedLog captured = CapturedLog.attach()) {
            adapter(meters).findById(PROBE);

            assertThat(List.of(invalidFieldRecords(captured),
                    meters.find("patient_source.invalid_fields").counters()))
                    .containsExactly(List.of(), List.of());
        }
    }

    /** Each invalid element counts once a read, tagged by the read and the served element. */
    @Test
    void eachInvalidElementCountsOnceByTheReadAndTheElement() {
        answering(PROBE, EVERY_ELEMENT_INVALID);
        MeterRegistry meters = new SimpleMeterRegistry();
        PatientSourceAdapter adapter = adapter(meters);

        adapter.findById(PROBE);
        adapter.findById(PROBE);

        Map<String, Double> counted = new TreeMap<>();
        for (Counter counter : meters.find("patient_source.invalid_fields").counters()) {
            counted.put(counter.getId().getTag("read") + " " + counter.getId().getTag("field"),
                    counter.count());
        }
        assertThat(counted).isEqualTo(Map.of(
                "patient identifier[0].value", 2.0,
                "patient name[0].text", 2.0,
                "patient gender", 2.0,
                "patient birthDate", 2.0));
    }

    /** The Demographic details the probe's body with {@code members} besides its own is read with. */
    private static Demographics demographicsOf(String... members) {
        answering(PROBE, probe(joined(NRIC_IDENTIFIER, OFFICIAL_NAME, GENDER, BIRTH_DATE,
                joined(members))));
        return at(SOURCE.baseUrl()).findById(PROBE)
                .orElseThrow().demographics();
    }

    /** A body with none of the Demographic elements has no Demographic details. */
    @Test
    void aBodyWithNoDemographicElementHasNoDemographicDetails() {
        answering(PROBE, probe(joined(NRIC_IDENTIFIER, OFFICIAL_NAME, GENDER, BIRTH_DATE)));

        assertThat(patients().findById(PROBE).orElseThrow().demographics())
                .isEqualTo(Demographics.NONE);
    }

    /** The first phone and the first email are read, past any other system and a later one. */
    @Test
    void theFirstPhoneAndTheFirstEmailAreRead() {
        Demographics read = demographicsOf("""
                "telecom": [{"system": "fax", "value": "+65 6000 0000"},
                            {"system": "email", "value": "first@example.org"},
                            {"system": "phone", "value": "+65 6000 0001"},
                            {"system": "phone", "value": "+65 6000 0002"},
                            {"system": "email", "value": "second@example.org"}]""");

        assertThat(List.of(read.phone(), read.email())).containsExactly(
                SourceField.present("+65 6000 0001"), SourceField.present("first@example.org"));
    }

    /**
     * The first phone's value is read as received: missing when it has none or a blank one — a
     * later phone's value is never read in its place — and invalid when it is the wrong JSON
     * type. The email's is read by the same rule.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        '{"system": "phone"}, {"system": "phone", "value": "+65 6000 0002"}' | MISSING
        '{"system": "phone", "value": " "}'                                  | MISSING
        '{"system": "phone", "value": 6560000001}'                           | INVALID
        '{"system": "phone", "value": ["+65 6000 0001"]}'                    | INVALID
        """)
    void aPhoneIsReadAsReceived(String points, SourceField.Received received) {
        assertThat(demographicsOf("\"telecom\": [" + points + "]").phone())
                .isEqualTo(new SourceField<>(received, null));
    }

    /**
     * The residential address is the first home address's text, as received, past another use:
     * never rebuilt from its lines, missing when it has none or a blank one — a later home
     * address is never read in its place — and invalid when it is the wrong JSON type.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
        [{"use": "work", "text": "1 Office Road"}, {"use": "home", "text": "2 Home Road"}] | PRESENT | 2 Home Road
        [{"use": "home", "line": ["Blk 1"]}, {"use": "home", "text": "2 Home Road"}]      | MISSING | -
        [{"use": "work", "text": "1 Office Road"}]                                       | MISSING | -
        [{"use": "home", "text": " ", "line": ["Blk 1"]}]                                | MISSING | -
        [{"use": "home", "text": ["Blk 1", "2 Home Road"]}]                              | INVALID | -
        """)
    void theResidentialAddressIsTheFirstHomeAddressesText(String addresses,
            SourceField.Received received, String text) {
        assertThat(demographicsOf("\"address\": " + addresses).residentialAddress())
                .isEqualTo(new SourceField<>(received, text));
    }

    /**
     * The next of kin is the first contact coded v2-0131 {@code N} — past a contact coded
     * otherwise — their relationship the first relationship text, past a blank one, their phone
     * their first phone.
     */
    @Test
    void theNextOfKinIsTheFirstContactCodedAsOne() {
        Demographics read = demographicsOf("""
                "contact": [
                  {"relationship": [{"text": "Employer"}], "name": {"text": "Acme Pte Ltd"}},
                  {"relationship": [{"coding": [{"system": "http://terminology.hl7.org/CodeSystem/v2-0131",
                                                 "code": "N"}]}, {"text": " "}, {"text": "Mother"},
                                    {"text": "Guardian"}],
                   "name": {"text": "Mdm Lee Siew Kuen"},
                   "telecom": [{"system": "email", "value": "lee@example.org"},
                               {"system": "phone", "value": "+65 8234 5678"}]},
                  {"relationship": [{"coding": [{"system": "http://terminology.hl7.org/CodeSystem/v2-0131",
                                                 "code": "N"}]}], "name": {"text": "Mr Lee"}}]""");

        assertThat(read.nextOfKin()).isEqualTo(new NextOfKin(
                SourceField.present("Mdm Lee Siew Kuen"), SourceField.present("Mother"),
                SourceField.present("+65 8234 5678")));
    }

    /**
     * A next of kin with no name at all has their name missing, and a relationship text on the
     * concept that codes them is theirs.
     */
    @Test
    void aNextOfKinWithNoNameIsReadWithTheirNameMissing() {
        Demographics read = demographicsOf("""
                "contact": [{"relationship": [{"coding": [{"system": "http://terminology.hl7.org/CodeSystem/v2-0131",
                                                           "code": "N"}], "text": "Mother"}]}]""");

        assertThat(read.nextOfKin()).isEqualTo(new NextOfKin(SourceField.missing(),
                SourceField.present("Mother"), SourceField.missing()));
    }

    /** A next of kin with no relationship text and no phone is read by their name alone. */
    @Test
    void aNextOfKinWithNoRelationshipTextOrPhoneIsReadByTheirNameAlone() {
        Demographics read = demographicsOf("""
                "contact": [{"relationship": [{"coding": [{"system": "http://terminology.hl7.org/CodeSystem/v2-0131",
                                                           "code": "N"}]}],
                             "name": {"text": "Mdm Lee Siew Kuen"}}]""");

        assertThat(read.nextOfKin()).isEqualTo(new NextOfKin(
                SourceField.present("Mdm Lee Siew Kuen"), SourceField.missing(),
                SourceField.missing()));
    }

    /**
     * A next of kin is read whatever their details hold, each as received: a name with no text is
     * missing, and a relationship text and a phone of the wrong JSON type invalid.
     */
    @Test
    void aNextOfKinsDetailsAreEachReadAsReceived() {
        Demographics read = demographicsOf("""
                "contact": [{"relationship": [{"coding": [{"system": "http://terminology.hl7.org/CodeSystem/v2-0131",
                                                           "code": "N"}]}, {"text": ["Mother"]}],
                             "name": {"family": "Lee"},
                             "telecom": [{"system": "phone", "value": 82345678}]}]""");

        assertThat(read.nextOfKin()).isEqualTo(new NextOfKin(SourceField.missing(),
                SourceField.invalid(), SourceField.invalid()));
    }

    /**
     * A contact coded otherwise — another code of v2-0131, or {@code N} under another system —
     * or one with no relationship at all, is no next of kin.
     */
    @Test
    void aContactCodedOtherwiseIsNoNextOfKin() {
        Demographics read = demographicsOf("""
                "contact": [{"relationship": [{"coding": [{"system": "http://terminology.hl7.org/CodeSystem/v2-0131",
                                                           "code": "E"}]}],
                             "name": {"text": "Acme Pte Ltd"}},
                            {"relationship": [{"coding": [{"system": "urn:example:roles",
                                                           "code": "N"}]}],
                             "name": {"text": "Ms Tan"}},
                            {"name": {"text": "Mr Lee"}}]""");

        assertThat(read.nextOfKin()).isNull();
    }

    /**
     * The military service is read past another extension: its records in order, each field and
     * each date the first sub-extension under its url, as received — missing when there is none,
     * invalid when of the wrong JSON type or, for a date, no date to the day — and a
     * sub-extension it does not name left unread.
     */
    @Test
    void theMilitaryHistoryIsReadWithItsRecordsInOrderAndItsDates() {
        Demographics read = demographicsOf("""
                "extension": [
                  {"url": "urn:example:other", "valueString": "x"},
                  {"url": "http://example.org/fhir/StructureDefinition/military-service",
                   "extension": [
                     {"url": "serviceRecord", "extension": [
                       {"url": "rank", "valueString": "CPT"},
                       {"url": "rank", "valueString": "MAJ"},
                       {"url": "a-later-field", "valueString": "x"}]},
                     {"url": "serviceRecord", "extension": [
                       {"url": "unit", "valueString": "3 SIR"},
                       {"url": "vocation", "valueString": 3}]},
                     {"url": "mrDate", "valueDate": "2030-04-01"},
                     {"url": "ordDate", "valueDate": "2020-01"},
                     {"url": "enlistmentDate", "valueDate": 20180115},
                     {"url": "enlistmentDate", "valueDate": "2018-01-15"}]}]""");

        SourceField<String> missing = SourceField.missing();
        assertThat(read.militaryHistory()).isEqualTo(new MilitaryHistory(
                List.of(new MilitaryHistory.ServiceRecord(missing, SourceField.present("CPT"),
                                missing, missing, missing),
                        new MilitaryHistory.ServiceRecord(missing, missing,
                                SourceField.present("3 SIR"), missing, SourceField.invalid())),
                SourceField.invalid(), SourceField.invalid(),
                SourceField.present(LocalDate.of(2030, 4, 1)), SourceField.missing()));
    }

    /** The military service is read when it is the first extension too. */
    @Test
    void aMilitaryServiceFirstAmongTheExtensionsIsRead() {
        Demographics read = demographicsOf(
                "\"extension\": [{\"url\": \"http://example.org/fhir/StructureDefinition/"
                        + "military-service\", \"extension\": [{\"url\": \"ordDate\", "
                        + "\"valueDate\": \"2020-01-14\"}]}]");

        assertThat(read.militaryHistory().ordDate())
                .isEqualTo(SourceField.present(LocalDate.of(2020, 1, 14)));
    }

    /**
     * A Patient each of whose Demographic details is in the wrong format, none first in its list:
     * the phone and the email past a fax, the home address past a work one, the next of kin past
     * another contact — their relationship text past a blank one, their phone past an email — and
     * the military service past another extension, a service record's field past another field
     * and a date before the records.
     */
    private static final String EVERY_DETAIL_INVALID = probe(joined(NRIC_IDENTIFIER,
            OFFICIAL_NAME, GENDER, BIRTH_DATE, """
            "telecom": [{"system": "fax", "value": "+65 6000 0000"},
                        {"system": "phone", "value": 6591234567},
                        {"system": "email", "value": true}],
            "address": [{"use": "work", "text": "1 Office Road"},
                        {"use": "home", "text": ["Blk 123 Bishan Street 12"]}],
            "contact": [{"relationship": [{"text": "Employer"}], "name": {"text": "Acme Pte Ltd"}},
                        {"relationship": [{"coding": [{"system": "http://terminology.hl7.org/CodeSystem/v2-0131",
                                                       "code": "N"}]}, {"text": " "}, {"text": 1}],
                         "name": {"text": 7},
                         "telecom": [{"system": "email", "value": "lee@example.org"},
                                     {"system": "phone", "value": 82345678}]}],
            "extension": [
              {"url": "urn:example:other", "valueString": "x"},
              {"url": "http://example.org/fhir/StructureDefinition/military-service",
               "extension": [
                 {"url": "enlistmentDate", "valueDate": "2018"},
                 {"url": "serviceRecord", "extension": [{"url": "rank", "valueString": "CPT"}]},
                 {"url": "serviceRecord", "extension": [
                   {"url": "serviceType", "valueString": "NSF"},
                   {"url": "unit", "valueString": 5}]}]}]"""));

    /**
     * A Patient holding invalid Demographic details is one WARN, naming each detail by its path
     * in the source's answer, nested as deep as it was found.
     */
    @Test
    void aPatientHoldingInvalidDemographicDetailsIsOneWarnNamingTheirPaths() {
        answering(PROBE, EVERY_DETAIL_INVALID);

        try (CapturedLog captured = CapturedLog.attach()) {
            patients().findById(PROBE);

            assertThat(invalidFieldRecords(captured)).singleElement()
                    .satisfies(warning -> assertThat(CapturedLog.fields(warning))
                            .containsEntry("app.patient_source.read", "patient")
                            .containsEntry("app.patient_source.invalid_fields", List.of(
                                    "telecom[1].value", "telecom[2].value", "address[1].text",
                                    "contact[1].name.text", "contact[1].relationship[2].text",
                                    "contact[1].telecom[1].value",
                                    "extension[1].extension[2].extension[1].valueString",
                                    "extension[1].extension[0].valueDate")));
        }
    }

    @Test
    void theDemographicWarnQuotesNoValueOfTheAnswer() {
        answering(PROBE, EVERY_DETAIL_INVALID);

        try (CapturedLog captured = CapturedLog.attach()) {
            patients().findById(PROBE);

            ILoggingEvent warning = invalidFieldRecords(captured).getFirst();
            assertThat(warning.getFormattedMessage() + " " + CapturedLog.fields(warning))
                    .doesNotContain("6591234567", "Bishan", "82345678", "2018", "NSF", "CPT");
        }
    }

    /** Each invalid Demographic detail counts once a read, tagged by the read and its name. */
    @Test
    void eachInvalidDemographicDetailCountsOnceByTheReadAndItsName() {
        answering(PROBE, EVERY_DETAIL_INVALID);
        MeterRegistry meters = new SimpleMeterRegistry();

        adapter(meters).findById(PROBE);

        Map<String, Double> counted = new TreeMap<>();
        for (Counter counter : meters.find("patient_source.invalid_fields").counters()) {
            counted.put(counter.getId().getTag("read") + " " + counter.getId().getTag("field"),
                    counter.count());
        }
        assertThat(counted).isEqualTo(Map.of(
                "patient phone", 1.0,
                "patient email", 1.0,
                "patient residentialAddress", 1.0,
                "patient nextOfKin.name", 1.0,
                "patient nextOfKin.relationship", 1.0,
                "patient nextOfKin.phone", 1.0,
                "patient serviceRecord.unit", 1.0,
                "patient enlistmentDate", 1.0));
    }

    /** A search's match carries no Demographic details: only a read by id copies them. */
    @Test
    void aSearchMatchCarriesNoDemographicDetails() {
        Patient tan = patients().search(new PatientQuery.ByName("tan"), PatientFilter.NONE,
                new PageRequest(1, 5)).patients().stream()
                .map(PatientMatch.OfPatient.class::cast)
                .map(PatientMatch.OfPatient::patient)
                .filter(patient -> patient.id().equals(TAN))
                .findFirst().orElseThrow();

        assertThat(tan.demographics()).isEqualTo(Demographics.NONE);
    }

    /**
     * Nothing of a failed read reaches its exception, whose messages a fault record would print:
     * not the id, which a client's message quotes in the URL, and nothing of the body, which a
     * parser's message quotes — here another patient's Patient, and a body that is not JSON.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "{\"id\": \"another-patient\", \"name\": [{\"use\": \"official\", \"text\": \"Quah"
                + " Seow Peng\"}], \"birthDate\": \"12/03/1985 T7391046Q\"}",
        "{\"id\": \"probe-Q7xKd2.v9\", \"birthDate\": \"\"} T7391046Q Quah Seow Peng\"}"})
    void aFaultsExceptionSaysNothingOfThePatientOrTheBody(String body) {
        answering(PROBE, body);

        Throwable fault = catchThrowable(() -> patients().findById(PROBE));

        assertThat(printed(fault)).contains(PatientSourceException.class.getName())
                .doesNotContain("T7391046Q", "Quah", "12/03/1985", PROBE.value());
    }

    @Test
    void aSourceThatDoesNotAnswerSaysNothingOfTheIdOrWhereItWent() {
        String base = closedPort();

        Throwable fault = catchThrowable(() -> at(base).findById(PROBE));

        assertThat(printed(fault)).contains(PatientSourceException.class.getName())
                .doesNotContain(PROBE.value(), base);
    }

    @Test
    void anErrorAnswersBodyIsNeverRead() {
        SOURCE.stubFor(get(urlPathEqualTo("/Patient/" + PROBE))
                .willReturn(aResponse().withStatus(500).withBody("T7391046Q Quah Seow Peng")));

        Throwable fault = catchThrowable(() -> patients().findById(PROBE));

        assertThat(printed(fault)).doesNotContain("T7391046Q", "Quah", PROBE.value());
    }

    /**
     * The fault keeps what failed, for the record, as its type alone: the client's own exception,
     * whose message names the URL or quotes the body, redacted to its class name — whether the
     * source was unreachable, timed out, or answered something that is not JSON.
     */
    @Test
    void anUnreachableFaultKeepsTheFailuresTypeAsItsRedactedCause() {
        Throwable fault = catchThrowable(() -> at(closedPort()).findById(PROBE));

        assertThat(fault.getCause()).isInstanceOf(RedactedFaultException.class)
                .hasMessage(ResourceAccessException.class.getName());
    }

    @Test
    void aTimedOutFaultKeepsTheFailuresTypeAsItsRedactedCause() {
        PatientSourceAdapter impatient = PatientSourceAdapters.at(SOURCE.baseUrl(), Duration.ofSeconds(2), Duration.ofMillis(300));

        Throwable fault = catchThrowable(() -> impatient.findById(new PatientId("fault-slow")));

        assertThat(fault.getCause()).isInstanceOf(RedactedFaultException.class)
                .hasMessage(ResourceAccessException.class.getName());
    }

    @Test
    void anUnreadableFaultKeepsTheFailuresTypeAsItsRedactedCause() {
        answering(PROBE, "not JSON: T7391046Q");

        Throwable fault = catchThrowable(() -> patients().findById(PROBE));

        assertThat(fault.getCause()).isInstanceOf(RedactedFaultException.class)
                .hasMessage(RestClientException.class.getName());
    }

    /** The client connects within the connect timeout and follows no redirect. */
    @Test
    void theClientConnectsWithinTheConnectTimeoutAndFollowsNoRedirect() {
        HttpClient client = PatientSourceAdapter.httpClient(Duration.ofMillis(1_500));

        assertThat(List.of(client.connectTimeout(), client.followRedirects()))
                .containsExactly(Optional.of(Duration.ofMillis(1_500)), HttpClient.Redirect.NEVER);
    }

    /** A base URL nothing listens on: a mock's, once it has stopped. */
    private static String closedPort() {
        WireMockServer stopped = new WireMockServer(PatientSourceStubs.options());
        stopped.start();
        String base = stopped.baseUrl();
        stopped.stop();
        return base;
    }
}
