package com.example.backend.patient;

import static com.example.backend.TestJson.JSON;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.ContainerTestConfiguration;
import com.example.backend.ServerSession;
import com.example.backend.observability.EcsLogCapture;
import com.example.backend.scim.domain.NormalizedUserName;
import com.example.backend.scim.domain.ScimUserRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A Patient search over a real socket and the whole application chain, against the patient
 * source's mock every context reads ({@code PatientSourceStubs}): the {@code PATIENT_SEARCH} row
 * each answered search appends, naming the signed-in User, the page's count and the query's kind
 * and nothing of the query; no row for a search refused before the source; no row for a read by
 * id; and a log stream that never names the query or a match.
 *
 * <p>The rows are read from the trail's table, as the other audit recording tests read them; the
 * searcher is the seeded {@code test-user}, whose Patient searches no other suite makes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ContainerTestConfiguration.class)
@AutoConfigureMetrics
class PatientSearchIntegrationTests {

    private static final String SEARCHES_BY = """
            SELECT operation, outcome, resource_type, resource_id, subject_id, result_count,
                   filter_shape, error_code, changed_paths, http_method, http_path
              FROM audit_events
             WHERE actor_id = ? AND operation = 'PATIENT_SEARCH'
             ORDER BY occurred_at""";

    @LocalServerPort
    private int port;

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ScimUserRepository users;

    private EcsLogCapture logs;

    private UUID searcher;

    @BeforeEach
    void setUp() {
        logs = EcsLogCapture.attach(environment);
        searcher = users.findByNormalizedUserName(NormalizedUserName.of("test-user"))
                .orElseThrow().id();
    }

    @AfterEach
    void tearDown() {
        logs.close();
    }

    private List<Map<String, Object>> searchesRecorded() {
        return jdbc.queryForList(SEARCHES_BY, searcher);
    }

    /**
     * A name search's row: the User as actor, the {@code Patient} resource type and no resource
     * or subject, the five Patients its page held, {@code NAME}, the route template — and no
     * column holding the query.
     */
    @Test
    void anAnsweredSearchAppendsOneRowNamingItsSearcherCountAndKind() throws Exception {
        HttpClient session = signedIn();
        int before = searchesRecorded().size();

        HttpResponse<String> response = send(session, search("tan", 5));

        List<Map<String, Object>> recorded = searchesRecorded();
        assertThat(List.of(response.statusCode(),
                JSON.readTree(response.body()).at("/patients").size(),
                recorded.size() - before)).containsExactly(200, 5, 1);
        Map<String, Object> row = new HashMap<>();
        row.put("operation", "PATIENT_SEARCH");
        row.put("outcome", "SUCCESS");
        row.put("resource_type", "Patient");
        row.put("resource_id", null);
        row.put("subject_id", null);
        row.put("result_count", 5);
        row.put("filter_shape", "NAME");
        row.put("error_code", null);
        row.put("changed_paths", null);
        row.put("http_method", "GET");
        row.put("http_path", "/api/patient-search");
        assertThat(recorded.getLast()).isEqualTo(row);
    }

    @Test
    void anNricSearchIsRecordedAsOne() throws Exception {
        HttpClient session = signedIn();

        send(session, search("S9212345A", 5));

        assertThat(searchesRecorded().getLast())
                .containsEntry("filter_shape", "NRIC").containsEntry("result_count", 1);
    }

    /** A search refused before the source, and a read by id, read nothing to record. */
    @Test
    void aRefusedSearchAndAReadByIdAppendNoRow() throws Exception {
        HttpClient session = signedIn();
        int before = searchesRecorded().size();
        long everyRow = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE actor_id = ?", Long.class, searcher);

        int refused = send(session, search("ta", 5)).statusCode();
        int read = send(session, request("/api/patient/eq081-VQEgP8drUUqCWzHfw3")).statusCode();

        assertThat(List.of(refused, read, searchesRecorded().size() - before,
                jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE actor_id = ?",
                        Long.class, searcher) - everyRow))
                .containsExactly(400, 200, 0, 0L);
    }

    /**
     * Neither a search nor its answer reaches the log: the request record names the route
     * template, never the query string, so no name or NRIC searched for, and no match's name,
     * NRIC or birth date, is in the stream.
     */
    @Test
    void noSearchPutsItsQueryOrAMatchInTheLog() throws Exception {
        HttpClient session = signedIn();
        logs.reset();

        assertThat(send(session, search("Kavitha", 5)).statusCode()).isEqualTo(200);
        assertThat(send(session, search("s9212345a", 5)).statusCode()).isEqualTo(200);
        logs.awaitRequestRecords(2);

        assertThat(logs.lines())
                .contains("/api/patient-search")
                .doesNotContain("Kavitha", "s9212345a", "S9212345A", "Siti Nurhaliza",
                        "1992-08-21");
    }

    // ---- harness -------------------------------------------------------------------------

    private HttpClient signedIn() throws Exception {
        return ServerSession.logIn(port, "test-user", "test-password");
    }

    private HttpRequest.Builder search(String q, int count) {
        return request("/api/patient-search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                + "&count=" + count);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest.Builder request)
            throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
