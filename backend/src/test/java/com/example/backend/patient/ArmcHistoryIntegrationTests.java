package com.example.backend.patient;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.ContainerTestConfiguration;
import com.example.backend.ServerSession;
import com.example.backend.TestMedicalOfficer;
import com.example.backend.observability.EcsLogCapture;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
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
 * The reason each fault stub of a patient's ARMC History is reported under, over a real socket and
 * the whole application chain, against the patient source's mock every context reads
 * ({@code PatientSourceStubs}): the failure its record names, under a ARMC reason where the record
 * broke a rule (/docs/domain/armc.md, "Logging"). What every history shares is
 * {@code PatientHistoriesIntegrationTests}'.
 *
 * <p>The reader is a Medical Officer of this suite's own, whose audit rows no other suite writes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ContainerTestConfiguration.class)
@AutoConfigureMetrics
class ArmcHistoryIntegrationTests {

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
     * A source failure writes exactly one {@code ERROR}, the fault's record, naming the failure
     * or the broken rule of the ARMC History under its category. The answer, and what the
     * stream does not quote, are {@code PatientHistoriesIntegrationTests}'.
     */
    @ParameterizedTest
    @CsvSource(textBlock = FAULT_STUBS)
    void aSourceFailureIsOneFaultRecordNamingItsReason(String failing, String reason,
            String category) throws Exception {
        HttpClient session = signedIn();
        logs.reset();

        send(session, boards(failing));
        logs.awaitRequestRecords(1);

        assertThat(logs.records())
                .filteredOn(record -> "ERROR".equals(record.at("/log/level").asText()))
                .singleElement()
                .satisfies(fault -> assertThat(List.of(
                        fault.at("/app/event/action").asText(),
                        fault.at("/event/reason").asText(),
                        fault.at("/error/category").asText()))
                        .containsExactly("http.request.fault", reason, category));
    }

    // ---- harness -------------------------------------------------------------------------

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
