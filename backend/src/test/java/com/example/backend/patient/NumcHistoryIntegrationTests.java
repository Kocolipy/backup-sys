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
 * The reason each fault stub of a patient's NUMC History is reported under, over a real socket and
 * the whole application chain, against the patient source's mock every context reads
 * ({@code PatientSourceStubs}): the failure its record names, under a NUMC reason where the record
 * broke a rule (/docs/domain/numc.md, "Logging"). What every history shares is
 * {@code PatientHistoriesIntegrationTests}'.
 *
 * <p>The reader is a Medical Officer of this suite's own, whose audit rows no other suite writes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ContainerTestConfiguration.class)
@AutoConfigureMetrics
class NumcHistoryIntegrationTests {

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
     * A source failure writes exactly one {@code ERROR}, the fault's record, naming the failure
     * or the broken rule of the NUMC History under its category. The answer, and what the
     * stream does not quote, are {@code PatientHistoriesIntegrationTests}'.
     */
    @ParameterizedTest
    @CsvSource(textBlock = FAULT_STUBS)
    void aSourceFailureIsOneFaultRecordNamingItsReason(String failing, String reason,
            String category) throws Exception {
        HttpClient session = signedIn();
        logs.reset();

        send(session, records(failing));
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

    private HttpRequest.Builder records(String patientId) {
        return HttpRequest.newBuilder(URI.create(
                "http://localhost:" + port + "/api/patient-numc/" + patientId + "?count=5"));
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest.Builder request)
            throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
