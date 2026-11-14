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
 * A patient's NUMC History over a real socket and the whole application chain, against the
 * patient source's mock every context reads ({@code PatientSourceStubs}): the {@code patient:read}
 * it requires, no audit row for a read, and a log stream that names neither the patient nor a
 * NUMC Record — and, for each way the source fails, exactly one {@code ERROR}, the fault record
 * naming the failure under a NUMC reason where the record broke a rule
 * (/docs/domain/numc.md, "Logging").
 *
 * <p>The reader is a Medical Officer of this suite's own, whose audit rows no other suite writes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ContainerTestConfiguration.class)
@AutoConfigureMetrics
class NumcHistoryIntegrationTests {

    /** Tan Wei Ming, whose four NUMC Records the mock serves. */
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
        reader = TestMedicalOfficer.create(context, "numc-reader");
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

        HttpResponse<String> response = send(session, records(TAN_WEI_MING));

        assertThat(List.of(response.statusCode(),
                JSON.readTree(response.body()).at("/records").size(),
                rowsBy(reader.userId()) - before)).containsExactly(200, 4, 0L);
    }

    /**
     * Reading a NUMC History is a Group's to confer, as reading a Patient is: a User in no Group
     * is refused with the generic {@code 403}, audited as an authorization refusal.
     */
    @Test
    void aSessionWithoutPatientReadIsRefused() throws Exception {
        UUID actor = UUID.randomUUID();
        String userName = ScimTestRows.unique("numc-no-group");
        ScimTestRows.createUser(context, actor, userName, TestMedicalOfficer.PASSWORD);
        provisioned.add(actor);
        HttpClient outsider = ServerSession.logIn(port, userName, TestMedicalOfficer.PASSWORD);

        HttpResponse<String> response = send(outsider, records(TAN_WEI_MING));

        assertThat(List.of(response.statusCode(), JSON.readTree(response.body()).at("/code")
                        .asString(), jdbc.queryForList("""
                        SELECT http_path FROM audit_events
                         WHERE operation = 'ACCESS_DENIED' AND actor_id = ?""", String.class,
                actor))).containsExactly(403, "request-refused",
                List.of("/api/patient-numc/{patientId}"));
    }

    /**
     * A read that succeeds, one of a patient the source holds none under and one of a patient
     * with none: the stream names neither patient id asked for, nor any NUMC Record's id or dates
     * — the request record names the route template, never the path.
     */
    @Test
    void noReadPutsThePatientOrARecordInTheLog() throws Exception {
        HttpClient session = signedIn();
        String unknown = "numc-unknown-" + UUID.randomUUID();
        logs.reset();

        assertThat(List.of(send(session, records(TAN_WEI_MING)).statusCode(),
                send(session, records(unknown)).statusCode(),
                send(session, records("dev-patient-05")).statusCode()))
                .containsExactly(200, 404, 200);
        logs.awaitRequestRecords(3);

        assertThat(logs.lines())
                .contains("/api/patient-numc/{patientId}")
                .doesNotContain(TAN_WEI_MING, unknown, "dev-patient-05", "numc-tan-wei-ming",
                        "numc-tan-hui-min", "2023-10-14", "2024-10-13", "2025-03-03");
    }

    /**
     * Each NUMC fault stub the stub files serve, by the failure its record names and the
     * {@code error.category} it is reported under. The slow stub waits past the production read
     * timeout; the tests' is shorter (ContainerTestConfiguration).
     */
    private static final String FAULT_STUBS = """
            fault-numc-unreachable,                unreachable,                     network
            fault-numc-slow,                       timed-out,                       network
            fault-numc-server-error,               server-error,                    server
            fault-numc-unexpected-status,          unexpected-status,               data
            fault-numc-not-a-searchset,            not-a-searchset,                 data
            fault-numc-no-total,                   no-total,                        data
            fault-numc-no-id,                      numc-no-id,                      data
            fault-numc-invalid-id,                 numc-invalid-id,                 data
            fault-numc-no-outcome,                 no-numc-outcome,                 data
            fault-numc-unknown-type,               unknown-numc-type,               data
            fault-numc-unknown-outcome,            unknown-numc-outcome,            data
            fault-numc-expiry-date-not-to-the-day, numc-expiry-date-not-to-the-day, data
            """;

    /** The table above holds every NUMC fault stub the stub files serve. */
    @Test
    void theFaultTableHoldsEveryNumcFaultStub() {
        assertThat(FAULT_STUBS.lines().map(row -> row.split(",")[0].strip()))
                .containsExactlyInAnyOrderElementsOf(PatientSourceStubs.numcFaults().stream()
                        .map(PatientSourceStubs.FaultStub::id).toList());
    }

    /**
     * A source failure is the application API's {@code 502}, and writes exactly one
     * {@code ERROR}, the fault's record, naming the failure or the broken rule under its
     * category; nothing in the stream names the patient id asked for, or anything the source's
     * answer carried — a NUMC Record's id or dates, its error body.
     */
    @ParameterizedTest
    @CsvSource(textBlock = FAULT_STUBS)
    void aSourceFailureIsOneFaultRecordNamingItAndNothingOfThePatient(String failing,
            String reason, String category) throws Exception {
        PatientSourceStubs.FaultStub stub = PatientSourceStubs.numcFaults().stream()
                .filter(fault -> fault.id().equals(failing)).findFirst().orElseThrow();
        HttpClient session = signedIn();
        logs.reset();

        HttpResponse<String> response = send(session, records(failing));
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
        List<String> quoted = new ArrayList<>(stub.numcValues());
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

    private HttpRequest.Builder records(String patientId) {
        return HttpRequest.newBuilder(URI.create(
                "http://localhost:" + port + "/api/patient-numc/" + patientId + "?count=5"));
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest.Builder request)
            throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
