package com.example.backend.patient;

import static com.example.backend.TestJson.JSON;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.ContainerTestConfiguration;
import com.example.backend.ScimTestRows;
import com.example.backend.ServerSession;
import com.example.backend.TestMedicalOfficer;
import com.example.backend.observability.EcsLogCapture;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A patient's ARMC History over a real socket and the whole application chain, against the
 * patient source's mock every context reads ({@code PatientSourceStubs}): the {@code patient:read}
 * it requires, no audit row for a read, and a log stream that names neither the patient nor an
 * ARMC Board — and, for each way the source fails, exactly one {@code ERROR}, the fault record
 * naming the failure under an ARMC reason where the board broke a rule
 * (/docs/domain/armc.md, "Logging").
 *
 * <p>The reader is a Medical Officer of this suite's own, whose audit rows no other suite writes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ContainerTestConfiguration.class)
@AutoConfigureMetrics
class ArmcHistoryIntegrationTests {

    /** Tan Wei Ming, whose two ARMC Boards the mock serves. */
    private static final String TAN_WEI_MING = "eq081-VQEgP8drUUqCWzHfw3";

    private final List<UUID> provisioned = new ArrayList<>();

    @LocalServerPort
    private int port;

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationContext context;

    private EcsLogCapture logs;

    private TestMedicalOfficer reader;

    @BeforeEach
    void setUp() {
        reader = TestMedicalOfficer.create(context, "armc-reader");
        logs = EcsLogCapture.attach(environment);
    }

    @AfterEach
    void tearDown() {
        logs.close();
        reader.delete(jdbc);
        provisioned.forEach(user -> jdbc.update(
                "DELETE FROM scim_resources WHERE id = ? AND reserved_name IS NULL", user));
    }

    /** A read answers the page, and appends no row of any kind to the trail. */
    @Test
    void aReadIsAnsweredAndAppendsNoAuditRow() throws Exception {
        HttpClient session = signedIn();
        long before = rowsBy(reader.userId());

        HttpResponse<String> response = send(session, boards(TAN_WEI_MING));

        assertThat(List.of(response.statusCode(),
                JSON.readTree(response.body()).at("/records").size(),
                rowsBy(reader.userId()) - before)).containsExactly(200, 2, 0L);
    }

    /**
     * Reading an ARMC History is a Group's to confer, as reading a Patient is: a User in no Group
     * is refused with the generic {@code 403}, audited as an authorization refusal.
     */
    @Test
    void aSessionWithoutPatientReadIsRefused() throws Exception {
        UUID actor = UUID.randomUUID();
        String userName = ScimTestRows.unique("armc-no-group");
        ScimTestRows.createUser(context, actor, userName, TestMedicalOfficer.PASSWORD);
        provisioned.add(actor);
        HttpClient outsider = ServerSession.logIn(port, userName, TestMedicalOfficer.PASSWORD);

        HttpResponse<String> response = send(outsider, boards(TAN_WEI_MING));

        assertThat(List.of(response.statusCode(), JSON.readTree(response.body()).at("/code")
                        .asString(), jdbc.queryForList("""
                        SELECT http_path FROM audit_events
                         WHERE operation = 'ACCESS_DENIED' AND actor_id = ?""", String.class,
                actor))).containsExactly(403, "request-refused",
                List.of("/api/patient-armc/{patientId}"));
    }

    /**
     * A read that succeeds, one of a patient the source holds none under and one of a patient
     * with a board still open: the stream names neither patient id asked for, nor any ARMC
     * Board's id, dates or Medical Officers — the request record names the route template, never
     * the path.
     */
    @Test
    void noReadPutsThePatientOrABoardInTheLog() throws Exception {
        HttpClient session = signedIn();
        String unknown = "armc-unknown-" + UUID.randomUUID();
        logs.reset();

        assertThat(List.of(send(session, boards(TAN_WEI_MING)).statusCode(),
                send(session, boards(unknown)).statusCode(),
                send(session, boards("dev-patient-05")).statusCode()))
                .containsExactly(200, 404, 200);
        logs.awaitRequestRecords(3);

        assertThat(logs.lines())
                .contains("/api/patient-armc/{patientId}")
                .doesNotContain(TAN_WEI_MING, unknown, "dev-patient-05", "armc-tan-wei-ming",
                        "armc-tan-hui-min", "2024-01-01", "2024-02-10", "2025-06-02",
                        "Medical Officer 1", "Dr Lim Wei Jie");
    }

    /**
     * A filtered read and a sorted one are answered as an unfiltered read is: the source's page,
     * no audit row, and a stream naming none of the filters' values — not a code, not a date.
     */
    @Test
    void aFilteredOrSortedReadAppendsNoAuditRowAndLogsNoFilter() throws Exception {
        HttpClient session = signedIn();
        long before = rowsBy(reader.userId());
        logs.reset();

        HttpResponse<String> filtered = send(session, HttpRequest.newBuilder(URI.create(
                "http://localhost:" + port + "/api/patient-armc/dev-patient-05?count=5"
                        + "&outcome=unfit")));
        HttpResponse<String> sorted = send(session, HttpRequest.newBuilder(URI.create(
                "http://localhost:" + port + "/api/patient-armc/" + TAN_WEI_MING
                        + "?count=5&sortBy=endDate&endDateTo=2099-01-01")));
        logs.awaitRequestRecords(2);

        assertThat(List.of(filtered.statusCode(),
                JSON.readTree(filtered.body()).at("/totalResults").asInt(), sorted.statusCode(),
                rowsBy(reader.userId()) - before)).containsExactly(200, 3, 200, 0L);
        assertThat(logs.lines()).doesNotContain("unfit", "2099-01-01");
    }

    /**
     * Each ARMC fault stub the stub files serve, by the failure its record names and the
     * {@code error.category} it is reported under. The slow stub waits past the production read
     * timeout; the tests' is shorter (ContainerTestConfiguration).
     */
    private static final String FAULT_STUBS = """
            fault-armc-unreachable,             unreachable,                  network
            fault-armc-slow,                    timed-out,                    network
            fault-armc-server-error,            server-error,                 server
            fault-armc-unexpected-status,       unexpected-status,            data
            fault-armc-not-a-searchset,         not-a-searchset,              data
            fault-armc-no-total,                no-total,                     data
            fault-armc-no-id,                   armc-no-id,                   data
            fault-armc-invalid-id,              armc-invalid-id,              data
            fault-armc-no-outcome,              no-armc-outcome,              data
            fault-armc-no-medical-officer-2,    no-armc-medical-officer-2,    data
            fault-armc-unknown-board-type,      unknown-armc-board-type,      data
            fault-armc-unknown-outcome,         unknown-armc-outcome,         data
            fault-armc-end-before-start,        armc-end-before-start,        data
            fault-armc-end-date-not-to-the-day, armc-end-date-not-to-the-day, data
            """;

    /** The table above holds every ARMC fault stub the stub files serve. */
    @Test
    void theFaultTableHoldsEveryArmcFaultStub() {
        assertThat(FAULT_STUBS.lines().map(row -> row.split(",")[0].strip()))
                .containsExactlyInAnyOrderElementsOf(PatientSourceStubs.armcFaults().stream()
                        .map(PatientSourceStubs.FaultStub::id).toList());
    }

    /**
     * A source failure is the application API's {@code 502}, and writes exactly one
     * {@code ERROR}, the fault's record, naming the failure or the broken rule under its
     * category; nothing in the stream names the patient id asked for, or anything the source's
     * answer carried — an ARMC Board's id, dates or Medical Officers, its error body.
     */
    @ParameterizedTest
    @CsvSource(textBlock = FAULT_STUBS)
    void aSourceFailureIsOneFaultRecordNamingItAndNothingOfThePatient(String failing,
            String reason, String category) throws Exception {
        PatientSourceStubs.FaultStub stub = PatientSourceStubs.armcFaults().stream()
                .filter(fault -> fault.id().equals(failing)).findFirst().orElseThrow();
        HttpClient session = signedIn();
        logs.reset();

        HttpResponse<String> response = send(session, boards(failing));
        logs.awaitRequestRecords(1);

        assertThat(List.of(response.statusCode(),
                JSON.readTree(response.body()).at("/code").asString()))
                .containsExactly(502, "server-error");
        assertThat(logs.records())
                .filteredOn(record -> "ERROR".equals(record.at("/log/level").asText()))
                .singleElement()
                .satisfies(fault -> assertThat(List.of(
                        fault.at("/app/event/action").asText(),
                        fault.at("/event/reason").asText(),
                        fault.at("/error/category").asText()))
                        .containsExactly("http.request.fault", reason, category));
        List<String> quoted = new ArrayList<>(stub.armcValues());
        quoted.add(failing);
        assertThat(logs.lines()).contains("http.request.fault")
                .doesNotContain(quoted.toArray(String[]::new));
    }

    // ---- harness -------------------------------------------------------------------------

    private long rowsBy(UUID actor) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE actor_id = ?",
                Long.class, actor);
    }

    private HttpClient signedIn() throws Exception {
        return ServerSession.logIn(port, reader.userName(), TestMedicalOfficer.PASSWORD);
    }

    private HttpRequest.Builder boards(String patientId) {
        return HttpRequest.newBuilder(URI.create(
                "http://localhost:" + port + "/api/patient-armc/" + patientId + "?count=5"));
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest.Builder request)
            throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
