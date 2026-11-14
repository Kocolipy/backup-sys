package com.example.backend.patient;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The in-house patient source's mock, in process: WireMock serving the very stub files the local
 * mock container serves ({@code patient-source-mock/mappings}, mounted by {@code compose.yaml}),
 * so the patient tests and the mock a local run and E2E read cannot drift apart
 * (/docs/domain/patient.md, "The patient source's fixtures").
 *
 * <p>A test class owning its server registers {@link #extension()}; the full-context tests
 * share {@link #shared()}, one per test JVM, which {@code ContainerTestConfiguration} points every
 * context's {@code app.patient-source.base-url} at.
 */
public final class PatientSourceStubs {

    /** The mock's root, with its {@code mappings}: the tests run from {@code backend/}. */
    public static final Path ROOT = Path.of("patient-source-mock");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static WireMockServer shared;

    private PatientSourceStubs() {
    }

    /** WireMock on a free loopback port, serving the stub files. */
    public static WireMockConfiguration options() {
        return WireMockConfiguration.options()
                .dynamicPort()
                .bindAddress("127.0.0.1")
                .usingFilesUnderDirectory(ROOT.toString());
    }

    /** A server of a test class's own, for its {@code @RegisterExtension}: the stub files. */
    public static WireMockExtension extension() {
        return WireMockExtension.newInstance().options(options()).build();
    }

    /** The one server every full-context test in this JVM reads through, started on first use. */
    public static synchronized WireMockServer shared() {
        if (shared == null) {
            WireMockServer server = new WireMockServer(options());
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            shared = server;
        }
        return shared;
    }

    /**
     * Every fixture the stub files serve: the R4 {@code Patient} body of each {@code 200} stub
     * that is no fault stub.
     */
    public static List<JsonNode> fixtures() {
        return stubs()
                .filter(stub -> stub.at(FAULT).isMissingNode())
                .filter(stub -> stub.at("/response/status").asInt() == 200)
                .map(stub -> stub.at("/response/jsonBody"))
                .filter(body -> "Patient".equals(body.at("/resourceType").asString("")))
                .toList();
    }

    /**
     * Every search stub: the R4 searchset {@code Bundle} each stub answering
     * {@code GET /Patient?…} serves — the canned searches and the fallback for any other.
     */
    public static List<JsonNode> searches() {
        return searchStubs().stream().map(SearchStub::bundle).toList();
    }

    /**
     * Every search stub, as the search it matches beside the Bundle it answers: the canned
     * searches and the fallback for any other, whose matcher names no query parameter.
     */
    public static List<SearchStub> searchStubs() {
        return stubs()
                .filter(stub -> "/Patient".equals(stub.at("/request/urlPath").asString("")))
                .map(stub -> new SearchStub(stub.at("/request/queryParameters"),
                        stub.at("/response/jsonBody")))
                .toList();
    }

    /**
     * One search stub.
     *
     * @param query  its query parameters' matchers, by parameter name — missing for the fallback
     * @param bundle the searchset it answers
     */
    public record SearchStub(JsonNode query, JsonNode bundle) {

        /** Whether it is the fallback, matching any search. */
        public boolean fallback() {
            return query.isMissingNode();
        }
    }

    /**
     * Every fault stub of a read by id: a stub whose {@code metadata.fault} names the failure it
     * serves — a delay
     * past the read timeout, a body dribbled out past it, a {@code 5xx}, or a Patient breaking
     * one domain rule — under a
     * well-formed id no fixture uses.
     */
    public static List<FaultStub> faults() {
        return faultStubs("").stream()
                .map(stub -> new FaultStub(stub.at(FAULT).asString(),
                        stub.at("/request/urlPath").asString().substring("/Patient/".length()),
                        stub.at("/response")))
                .toList();
    }

    /**
     * Every FFI fault stub: a stub whose {@code metadata.fault} names the failure an FFI History
     * read meets there — no answer, a status but a {@code 200} or a {@code 404}, or a page the
     * service cannot hold — under a well-formed patient id no fixture uses
     * (/docs/domain/ffi.md, "The patient source's FFI fixtures").
     */
    public static List<FaultStub> ffiFaults() {
        return historyFaults(FFI);
    }

    /**
     * Every NUMC fault stub: a stub whose {@code metadata.fault} names the failure a NUMC History
     * read meets there — no answer, a status but a {@code 200} or a {@code 404}, or a page the
     * service cannot hold — under a well-formed patient id no fixture uses
     * (/docs/domain/numc.md, "The patient source's NUMC fixtures").
     */
    public static List<FaultStub> numcFaults() {
        return historyFaults(NUMC);
    }

    /**
     * Every ARMC fault stub: a stub whose {@code metadata.fault} names the failure an ARMC History
     * read meets there — no answer, a status but a {@code 200} or a {@code 404}, or a page the
     * service cannot hold — under a well-formed patient id no fixture uses
     * (/docs/domain/armc.md, "The patient source's ARMC fixtures").
     */
    public static List<FaultStub> armcFaults() {
        return historyFaults(ARMC);
    }

    /** The fault stubs of the history under {@code segment}, each under its patient id. */
    private static List<FaultStub> historyFaults(String segment) {
        return faultStubs(segment).stream()
                .map(stub -> {
                    String path = stub.at("/request/urlPath").asString();
                    return new FaultStub(stub.at(FAULT).asString(),
                            path.substring("/Patient/".length(), path.length() - segment.length()),
                            stub.at("/response"));
                })
                .toList();
    }

    /** Every FFI History stub that is no fault stub ({@link #histories}). */
    public static List<HistoryStub> ffiHistories() {
        return histories(FFI);
    }

    /** Every NUMC History stub that is no fault stub ({@link #histories}). */
    public static List<HistoryStub> numcHistories() {
        return histories(NUMC);
    }

    /** Every ARMC History stub that is no fault stub ({@link #histories}). */
    public static List<HistoryStub> armcHistories() {
        return histories(ARMC);
    }

    /**
     * Every stub of the history under {@code segment} that is no fault stub: each development
     * fixture's page as the patient id it answers for — or, for a stub answering several, its
     * pattern — beside the searchset it serves.
     */
    private static List<HistoryStub> histories(String segment) {
        return stubs()
                .filter(stub -> stub.at(FAULT).isMissingNode())
                .filter(stub -> stub.at("/response/status").asInt() == 200)
                .filter(stub -> path(stub).endsWith(segment))
                .map(stub -> new HistoryStub(path(stub), stub.at("/request/queryParameters"),
                        stub.at("/response/jsonBody")))
                .toList();
    }

    /**
     * One stub of an FFI, NUMC or ARMC History.
     *
     * @param path   the path it answers, {@code /Patient/{id}/ffi}, {@code /numc} or {@code /armc},
     *               or the pattern of paths
     * @param query  its query parameters' matchers — missing for one answering any page
     * @param bundle the searchset it answers
     */
    public record HistoryStub(String path, JsonNode query, JsonNode bundle) {
    }

    /** The path a stub matches, exactly or by pattern. */
    private static String path(JsonNode stub) {
        JsonNode request = stub.at("/request");
        return request.optional("urlPath").or(() -> request.optional("urlPathPattern"))
                .map(JsonNode::asString).orElse("");
    }

    /**
     * The fault stubs of reads of the history under {@code segment}, or, for {@code ""}, of
     * Patient reads by id: the stubs whose path names no history segment.
     */
    private static List<JsonNode> faultStubs(String segment) {
        return stubs()
                .filter(stub -> !stub.at(FAULT).isMissingNode())
                .filter(stub -> segment.isEmpty()
                        ? HISTORIES.stream().noneMatch(path(stub)::endsWith)
                        : path(stub).endsWith(segment))
                .toList();
    }

    /** The FFI History's path segment, after the patient's id. */
    private static final String FFI = "/ffi";

    /** The NUMC History's path segment, after the patient's id. */
    private static final String NUMC = "/numc";

    /** The ARMC History's path segment, after the patient's id. */
    private static final String ARMC = "/armc";

    /** Every history's path segment. */
    private static final List<String> HISTORIES = List.of(FFI, NUMC, ARMC);

    /**
     * One fault stub.
     *
     * @param fault    the failure it serves, its {@code metadata.fault}
     * @param id       the patient id it answers under
     * @param response the answer it gives, body and all
     */
    public record FaultStub(String fault, String id, JsonNode response) {

        /**
         * What a record of its failure must not quote: every identifier's value, every name
         * part, the birth date, and an error body's diagnostics.
         */
        public List<String> patientValues() {
            JsonNode body = response.at("/jsonBody");
            List<String> values = new ArrayList<>();
            body.at("/identifier").forEach(identifier -> values.add(identifier.at("/value").asString()));
            body.at("/name").forEach(name -> {
                name.optional("text").ifPresent(text -> values.add(text.asString()));
                name.optional("family").ifPresent(family -> values.add(family.asString()));
                name.at("/given").forEach(given -> values.add(given.asString()));
            });
            body.optional("birthDate").ifPresent(birthDate -> values.add(birthDate.asString()));
            body.at("/issue").forEach(issue -> values.add(issue.at("/diagnostics").asString()));
            return List.copyOf(values);
        }

        /**
         * What a record of an FFI History's failure must not quote: each FFI's id and dates, and
         * an error body's diagnostics.
         */
        public List<String> ffiValues() {
            return recordValues(DATED_RECORD);
        }

        /**
         * What a record of a NUMC History's failure must not quote: each NUMC Record's id and
         * dates, and an error body's diagnostics.
         */
        public List<String> numcValues() {
            return recordValues(DATED_RECORD);
        }

        /**
         * What a record of an ARMC History's failure must not quote: each ARMC Board's id, dates
         * and Medical Officers, and an error body's diagnostics.
         */
        public List<String> armcValues() {
            return recordValues(List.of("id", "startDate", "endDate", "medicalOfficer1",
                    "medicalOfficer2"));
        }

        /** The fields of an FFI or a NUMC Record no record of a failure may quote. */
        private static final List<String> DATED_RECORD = List.of("id", "issueDate", "expiryDate");

        /**
         * Each of a history page's records' {@code fields}, and an error body's diagnostics.
         */
        private List<String> recordValues(List<String> fields) {
            JsonNode body = response.at("/jsonBody");
            List<String> values = new ArrayList<>();
            body.at("/entry").forEach(entry -> fields.forEach(
                    field -> entry.at("/resource").optional(field)
                            .ifPresent(value -> values.add(value.asString()))));
            body.at("/issue").forEach(issue -> values.add(issue.at("/diagnostics").asString()));
            return List.copyOf(values);
        }
    }

    private static final String FAULT = "/metadata/fault";

    private static Stream<JsonNode> stubs() {
        try (Stream<Path> files = Files.list(ROOT.resolve("mappings"))) {
            return files.filter(file -> file.toString().endsWith(".json"))
                    .sorted()
                    .map(PatientSourceStubs::read)
                    .toList()
                    .stream();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static JsonNode read(Path stub) {
        try {
            return JSON.readTree(Files.readString(stub));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
