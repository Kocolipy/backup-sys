package com.example.backend.patient;

import static com.example.backend.TestJson.JSON;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.ContainerTestConfiguration;
import com.example.backend.ScimTestRows;
import com.example.backend.ServerSession;
import com.example.backend.TestMedicalOfficer;
import com.example.backend.observability.EcsLogCapture;
import com.example.backend.patient.PatientSourceStubs.FaultStub;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Every patient history over a real socket and the whole application chain, against the patient
 * source's mock every context reads ({@code PatientSourceStubs}), held to the rules they share
 * from one table with a row for each history: the {@code patient:read} it requires, no audit row
 * for a read, a log stream that names neither the patient nor a record of the history nor a
 * filter's value, and, for each way the source fails, the {@code 502} and exactly one
 * {@code ERROR} naming nothing of the patient or the answer (/docs/domain/ffi.md,
 * "Logging"). The reason each fault record names is each history's own integration tests'. A new
 * history joins by adding its row.
 *
 * <p>The reader is a Medical Officer of this suite's own, whose audit rows no other suite writes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ContainerTestConfiguration.class)
@AutoConfigureMetrics
class PatientHistoriesIntegrationTests {

    /** Tan Wei Ming, whose records of every history the mock serves. */
    private static final String TAN_WEI_MING = "eq081-VQEgP8drUUqCWzHfw3";

    /**
     * One history: its API path segment, the page's list, how many records of Tan Wei Ming's it
     * answers, what its fixtures hold that no log line may quote, a filtered read of
     * {@code dev-patient-05} with the total it answers and the filter's values, a sorted and
     * range-bounded read of Tan Wei Ming with its values, and its fault stubs with what a record
     * of their failure must not quote.
     */
    private record History(String name, String segment, String list, int records,
            List<String> fixtureValues, String filtered, int filteredTotal,
            List<String> filteredValues, String sorted, List<String> sortedValues,
            Supplier<List<FaultStub>> faults, Function<FaultStub, List<String>> values) {

        @Override
        public String toString() {
            return name;
        }

        String path() {
            return "/api/patient-" + segment;
        }
    }

    private static final List<History> HISTORIES = List.of(
            new History("FFI", "ffi", "ffis", 4,
                    List.of("ffi-tan-wei-ming", "ffi-tan-hui-min", "2023-10-14", "2024-10-13",
                            "2025-03-03"),
                    "higherAuthority=camo&higherAuthority=cnmo", 4, List.of("camo", "cnmo"),
                    "sortBy=issueDate&expiryDateTo=2099-01-01", List.of("2099-01-01"),
                    PatientSourceStubs::ffiFaults, FaultStub::ffiValues),
            new History("NUMC", "numc", "records", 4,
                    List.of("numc-tan-wei-ming", "numc-tan-hui-min", "2023-10-14", "2024-10-13",
                            "2025-03-03"),
                    "outcome=waiver-granted&outcome=waiver-rejected", 3,
                    List.of("waiver-granted", "waiver-rejected"),
                    "sortBy=expiryDate&issueDateTo=2099-01-01", List.of("2099-01-01"),
                    PatientSourceStubs::numcFaults, FaultStub::numcValues),
            new History("ARMC", "armc", "records", 2,
                    List.of("armc-tan-wei-ming", "armc-tan-hui-min", "2024-01-01", "2024-02-10",
                            "2025-06-02", "Medical Officer 1", "Dr Lim Wei Jie"),
                    "outcome=unfit", 3, List.of("unfit"),
                    "sortBy=endDate&endDateTo=2099-01-01", List.of("2099-01-01"),
                    PatientSourceStubs::armcFaults, FaultStub::armcValues),
            new History("Medication", "medication", "records", 5,
                    List.of("medication-tan-wei-ming", "medication-tan-hui-min", "Paracetamol",
                            "Oral rehydration salts", "2026-10-18T17:30", "2024-08-05T11:05",
                            "Dr Lim Wei Jie", "Dr Nur Aisyah binte Rahman"),
                    "dispenseStatus=uncollected&dispenseStatus=pending", 2,
                    List.of("uncollected", "pending"),
                    "sortBy=startDate&sortOrder=descending&endDateTo=2098-12-31T23:59",
                    List.of("2098-12-31"),
                    PatientSourceStubs::medicationFaults, FaultStub::medicationValues));

    static Stream<History> histories() {
        return HISTORIES.stream();
    }

    static Stream<Arguments> failingSources() {
        return HISTORIES.stream().flatMap(history -> history.faults().get().stream()
                .map(stub -> Arguments.of(history, stub)));
    }

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
        reader = TestMedicalOfficer.create(context, "histories-reader");
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
    @ParameterizedTest
    @MethodSource("histories")
    void aReadIsAnsweredAndAppendsNoAuditRow(History history) throws Exception {
        HttpClient session = signedIn();
        long before = rowsBy(reader.userId());

        HttpResponse<String> response = send(session, records(history, TAN_WEI_MING));

        assertThat(List.of(response.statusCode(),
                JSON.readTree(response.body()).at("/" + history.list()).size(),
                rowsBy(reader.userId()) - before)).containsExactly(200, history.records(), 0L);
    }

    /**
     * Reading a history is a Group's to confer, as reading a Patient is: a User in no Group is
     * refused with the generic {@code 403}, audited as an authorization refusal.
     */
    @ParameterizedTest
    @MethodSource("histories")
    void aSessionWithoutPatientReadIsRefused(History history) throws Exception {
        UUID actor = UUID.randomUUID();
        String userName = ScimTestRows.unique("history-no-group");
        ScimTestRows.createUser(context, actor, userName, TestMedicalOfficer.PASSWORD);
        provisioned.add(actor);
        HttpClient outsider = ServerSession.logIn(port, userName, TestMedicalOfficer.PASSWORD);

        HttpResponse<String> response = send(outsider, records(history, TAN_WEI_MING));

        assertThat(List.of(response.statusCode(), JSON.readTree(response.body()).at("/code")
                        .asString(), jdbc.queryForList("""
                        SELECT http_path FROM audit_events
                         WHERE operation = 'ACCESS_DENIED' AND actor_id = ?""", String.class,
                actor))).containsExactly(403, "request-refused",
                List.of(history.path() + "/{patientId}"));
    }

    /**
     * A read that succeeds, one of a patient the source holds none under and one of a second
     * patient: the stream names neither patient id asked for, nor any record's id or values the
     * fixtures hold — the request record names the route template,
     * never the path.
     */
    @ParameterizedTest
    @MethodSource("histories")
    void noReadPutsThePatientOrARecordInTheLog(History history) throws Exception {
        HttpClient session = signedIn();
        String unknown = history.segment() + "-unknown-" + UUID.randomUUID();
        logs.reset();

        assertThat(List.of(send(session, records(history, TAN_WEI_MING)).statusCode(),
                send(session, records(history, unknown)).statusCode(),
                send(session, records(history, "dev-patient-05")).statusCode()))
                .containsExactly(200, 404, 200);
        logs.awaitRequestRecords(3);

        List<String> quoted = new ArrayList<>(List.of(TAN_WEI_MING, unknown, "dev-patient-05"));
        quoted.addAll(history.fixtureValues());
        assertThat(logs.lines())
                .contains(history.path() + "/{patientId}")
                .doesNotContain(quoted.toArray(String[]::new));
    }

    /**
     * A filtered read and a sorted one are answered as an unfiltered read is: the source's page,
     * no audit row, and a stream naming none of the filters' values.
     */
    @ParameterizedTest
    @MethodSource("histories")
    void aFilteredOrSortedReadAppendsNoAuditRowAndLogsNoFilter(History history) throws Exception {
        HttpClient session = signedIn();
        long before = rowsBy(reader.userId());
        logs.reset();

        HttpResponse<String> filtered = send(session, HttpRequest.newBuilder(URI.create(
                "http://localhost:" + port + history.path() + "/dev-patient-05?count=5&"
                        + history.filtered())));
        HttpResponse<String> sorted = send(session, HttpRequest.newBuilder(URI.create(
                "http://localhost:" + port + history.path() + "/" + TAN_WEI_MING + "?count=5&"
                        + history.sorted())));
        logs.awaitRequestRecords(2);

        assertThat(List.of(filtered.statusCode(),
                JSON.readTree(filtered.body()).at("/totalResults").asInt(), sorted.statusCode(),
                rowsBy(reader.userId()) - before))
                .containsExactly(200, history.filteredTotal(), 200, 0L);
        List<String> quoted = new ArrayList<>(history.filteredValues());
        quoted.addAll(history.sortedValues());
        assertThat(logs.lines()).doesNotContain(quoted.toArray(String[]::new));
    }

    /**
     * A source failure is the application API's {@code 502}, and writes exactly one
     * {@code ERROR}, the fault's record; nothing in the stream names the patient id asked for, or
     * anything the source's answer carried — a record's id or values, its error body. The slow
     * stub waits past the production read timeout; the tests' is shorter
     * (ContainerTestConfiguration).
     */
    @ParameterizedTest
    @MethodSource("failingSources")
    void aSourceFailureIsOneFaultRecordNamingNothingOfThePatient(History history,
            FaultStub stub) throws Exception {
        HttpClient session = signedIn();
        logs.reset();

        HttpResponse<String> response = send(session, records(history, stub.id()));
        logs.awaitRequestRecords(1);

        assertThat(List.of(response.statusCode(),
                JSON.readTree(response.body()).at("/code").asString()))
                .containsExactly(502, "server-error");
        assertThat(logs.records())
                .filteredOn(record -> "ERROR".equals(record.at("/log/level").asText()))
                .singleElement()
                .satisfies(fault -> assertThat(fault.at("/app/event/action").asText())
                        .isEqualTo("http.request.fault"));
        List<String> quoted = new ArrayList<>(history.values().apply(stub));
        quoted.add(stub.id());
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

    private HttpRequest.Builder records(History history, String patientId) {
        return HttpRequest.newBuilder(URI.create(
                "http://localhost:" + port + history.path() + "/" + patientId + "?count=5"));
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest.Builder request)
            throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
