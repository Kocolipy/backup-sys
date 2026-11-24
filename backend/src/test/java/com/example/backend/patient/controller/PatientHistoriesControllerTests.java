package com.example.backend.patient.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.example.backend.patient.PatientSourceStubs;
import com.example.backend.patient.application.ArmcHistoryService;
import com.example.backend.patient.application.FfiHistoryService;
import com.example.backend.patient.application.HspHistoryService;
import com.example.backend.patient.application.MedicationHistoryService;
import com.example.backend.patient.application.NumcHistoryService;
import com.example.backend.patient.domain.PatientStore;
import com.example.backend.patient.infrastructure.http.PatientSourceAdapters;
import com.example.backend.web.ApiExceptionHandler;
import com.example.backend.web.ErrorNamespaces;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every patient history's {@code GET /api/patient-<history>/{patientId}} held to the rules they
 * share, from one table with a row for each history: the page handed to the patient source and
 * answered as asked for, the source's own order and filtering, the empty page, the {@code 404} of
 * a patient the source does not hold, the {@code 502} of a failing source, a patient id outside
 * the grammar, a paging value that is no integer, a blank code as no filter and a from-date after
 * its to-date. Each history's own controller tests hold what that history declares: its filters
 * and their search parameters, its date columns, its closed sets and its row. A new history
 * joins by adding its row.
 *
 * <p>The filter chain is not in this MockMvc: the Permission is {@code AuthorizationContractTests}',
 * the paging coercion {@code PagingContractTests}', and what is logged and audited over the real
 * chain {@code PatientHistoriesIntegrationTests}'.
 */
class PatientHistoriesControllerTests {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    private static final String TAN_WEI_MING = "eq081-VQEgP8drUUqCWzHfw3";

    private static final String DEV_PATIENT_05 = "dev-patient-05";

    /** A patient the source holds, with no record of any history. */
    private static final String NO_RECORDS = "erXuFYUfucBZaryVksYEcMg3";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A read of {@code patient} with {@code query}, and the page the source answers it with. */
    private record Read(String patient, String query, int total, int startIndex,
            List<String> ids) {
    }

    /** A from and a to request parameter, and values with the from after the to. */
    private record Range(String fromParameter, String toParameter, String from, String to) {
    }

    /**
     * One history: its API and source path segment, the page's list, the controller over a
     * patient port, and the canned reads of the patient source's fixtures that answer it.
     */
    private record History(String name, String segment, String list,
            Function<PatientStore, Object> controller, Read secondPage, Read sorted,
            Read filtered, String filterMatchingNothing, List<String> codeFilters,
            List<Range> invertedRanges) {

        @Override
        public String toString() {
            return name;
        }

        String fault(String stub) {
            return "fault-" + segment + "-" + stub;
        }
    }

    private static final List<History> HISTORIES = List.of(
            new History("FFI", "ffi", "ffis",
                    store -> new PatientFfiController(new FfiHistoryService(store)),
                    new Read(DEV_PATIENT_05, "startIndex=6", 7, 6,
                            List.of("ffi-tan-hui-min-6", "ffi-tan-hui-min-7")),
                    new Read(TAN_WEI_MING, "sortBy=issueDate", 4, 1,
                            List.of("ffi-tan-wei-ming-4", "ffi-tan-wei-ming-1",
                                    "ffi-tan-wei-ming-3", "ffi-tan-wei-ming-2")),
                    new Read(DEV_PATIENT_05, "issueDateFrom=2023-01-01&startIndex=6", 6, 6,
                            List.of("ffi-tan-hui-min-6")),
                    "expiryDateTo=2022-12-31",
                    List.of("type", "outcome", "higherAuthority", "submittingMc", "status"),
                    List.of(new Range("issueDateFrom", "issueDateTo", "2024-01-02", "2024-01-01"),
                            new Range("expiryDateFrom", "expiryDateTo", "2025-12-31",
                                    "2025-01-01"))),
            new History("NUMC", "numc", "records",
                    store -> new PatientNumcController(new NumcHistoryService(store)),
                    new Read(DEV_PATIENT_05, "startIndex=6", 7, 6,
                            List.of("numc-tan-hui-min-6", "numc-tan-hui-min-7")),
                    new Read(TAN_WEI_MING, "sortBy=expiryDate&sortOrder=descending", 4, 1,
                            List.of("numc-tan-wei-ming-2", "numc-tan-wei-ming-3",
                                    "numc-tan-wei-ming-1", "numc-tan-wei-ming-4")),
                    new Read(DEV_PATIENT_05, "outcome=waiver-granted&outcome=waiver-rejected", 3,
                            1, List.of("numc-tan-hui-min-3", "numc-tan-hui-min-4",
                                    "numc-tan-hui-min-7")),
                    "expiryDateTo=2022-12-31",
                    List.of("type", "outcome"),
                    List.of(new Range("issueDateFrom", "issueDateTo", "2024-01-02", "2024-01-01"),
                            new Range("expiryDateFrom", "expiryDateTo", "2025-12-31",
                                    "2025-01-01"))),
            new History("ARMC", "armc", "records",
                    store -> new PatientArmcController(new ArmcHistoryService(store)),
                    new Read(DEV_PATIENT_05, "startIndex=6", 7, 6,
                            List.of("armc-tan-hui-min-6", "armc-tan-hui-min-7")),
                    new Read(TAN_WEI_MING, "sortBy=startDate", 2, 1,
                            List.of("armc-tan-wei-ming-2", "armc-tan-wei-ming-1")),
                    new Read(DEV_PATIENT_05, "endDateFrom=2023-01-01", 3, 1,
                            List.of("armc-tan-hui-min-2", "armc-tan-hui-min-3",
                                    "armc-tan-hui-min-4")),
                    "startDateTo=2019-12-31",
                    List.of("outcome"),
                    List.of(new Range("startDateFrom", "startDateTo", "2023-01-02", "2023-01-01"),
                            new Range("endDateFrom", "endDateTo", "2024-12-31", "2024-01-01"))),
            // Every HSP History is one page: the second is the fallback's empty page.
            new History("HSP", "hsp", "records",
                    store -> new PatientHspController(new HspHistoryService(store)),
                    new Read(TAN_WEI_MING, "startIndex=6", 0, 6, List.of()),
                    new Read(TAN_WEI_MING, "sortBy=startDate", 3, 1,
                            List.of("hsp-tan-wei-ming-3", "hsp-tan-wei-ming-2",
                                    "hsp-tan-wei-ming-1")),
                    new Read(TAN_WEI_MING, "outcome=high&outcome=low", 2, 1,
                            List.of("hsp-tan-wei-ming-1", "hsp-tan-wei-ming-3")),
                    "startDateTo=2019-12-31",
                    List.of("outcome", "submittingMc", "status"),
                    List.of(new Range("startDateFrom", "startDateTo", "2024-03-12", "2024-03-11"),
                            new Range("endDateFrom", "endDateTo", "2025-12-31", "2025-01-01"))),
            new History("Medication", "medication", "records",
                    store -> new PatientMedicationController(new MedicationHistoryService(store)),
                    new Read(TAN_WEI_MING, "startIndex=6", 6, 6,
                            List.of("medication-tan-wei-ming-6")),
                    new Read(TAN_WEI_MING, "sortBy=startDate&sortOrder=descending", 6, 1,
                            List.of("medication-tan-wei-ming-2", "medication-tan-wei-ming-1",
                                    "medication-tan-wei-ming-3", "medication-tan-wei-ming-4",
                                    "medication-tan-wei-ming-5")),
                    new Read(DEV_PATIENT_05, "startDateTo=2025-07-14T16:20", 3, 1,
                            List.of("medication-tan-hui-min-2", "medication-tan-hui-min-3",
                                    "medication-tan-hui-min-4")),
                    "endDateTo=2023-12-31T23:59",
                    List.of("dispenseStatus"),
                    List.of(new Range("startDateFrom", "startDateTo", "2026-10-18T17:31",
                                    "2026-10-18T17:30"),
                            new Range("endDateFrom", "endDateTo", "2026-01-01T00:00",
                                    "2025-12-31T23:59"))));

    static Stream<History> histories() {
        return HISTORIES.stream();
    }

    static Stream<Arguments> blankCodes() {
        return HISTORIES.stream().flatMap(history -> history.codeFilters().stream()
                .map(code -> Arguments.of(history, code)));
    }

    static Stream<Arguments> invertedRanges() {
        return HISTORIES.stream().flatMap(history -> history.invertedRanges().stream()
                .map(range -> Arguments.of(history, range)));
    }

    static Stream<Arguments> patientIdsOutsideTheGrammar() {
        return HISTORIES.stream().flatMap(history -> Stream.of(
                        "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                        "%3Cimg%20src=x%3E", "probe_patient")
                .map(patientId -> Arguments.of(history, patientId)));
    }

    static Stream<Arguments> failingSources() {
        return HISTORIES.stream().flatMap(history -> Stream.of("server-error", "not-a-searchset")
                .map(stub -> Arguments.of(history, history.fault(stub))));
    }

    private static MockHttpServletResponse reading(History history,
            MockHttpServletRequestBuilder read) throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(history.controller()
                        .apply(PatientSourceAdapters.reading(SOURCE.baseUrl())))
                .setControllerAdvice(new ApiExceptionHandler(new ErrorNamespaces(List.of())))
                .build();
        return mvc.perform(read).andReturn().getResponse();
    }

    private static MockHttpServletRequestBuilder records(History history, String patientId) {
        return request(HttpMethod.GET, "/api/patient-" + history.segment() + "/" + patientId)
                .param("count", "5");
    }

    private static MockHttpServletRequestBuilder records(History history, Read read) {
        MockHttpServletRequestBuilder builder = records(history, read.patient());
        for (String pair : read.query().split("&")) {
            String[] parameter = pair.split("=", 2);
            builder.param(parameter[0], parameter[1]);
        }
        return builder;
    }

    private static JsonNode body(MockHttpServletResponse response) throws Exception {
        return JSON.readTree(response.getContentAsString());
    }

    private static JsonNode apiError(int status, String code, String detail) {
        return JSON.readTree("""
                {"status": %d, "code": "%s", "detail": "%s"}""".formatted(status, code, detail));
    }

    private static JsonNode refused() {
        return apiError(400, "invalid-request",
                "The request could not be read or did not pass validation.");
    }

    /** The one history read the source was sent, every value of each query parameter. */
    private static Map<String, List<String>> sentToTheSource(History history, String patientId) {
        Map<String, List<String>> query = new TreeMap<>();
        SOURCE.findAll(getRequestedFor(urlPathEqualTo(
                "/Patient/" + patientId + "/" + history.segment()))).getFirst().getQueryParams()
                .forEach((name, value) -> query.put(name, value.values()));
        return query;
    }

    /** A page's total, where it starts and the ids of its records, in its order. */
    private static List<Object> pageOf(History history, MockHttpServletResponse response)
            throws Exception {
        JsonNode page = body(response);
        return List.of(page.at("/totalResults").asInt(), page.at("/startIndex").asInt(),
                page.at("/" + history.list()).valueStream()
                        .map(record -> record.at("/id").asString()).toList());
    }

    private static List<Object> answered(Read read) {
        return List.of(read.total(), read.startIndex(), read.ids());
    }

    private static JsonNode emptyPage(History history) {
        return JSON.readTree("""
                {"totalResults": 0, "startIndex": 1, "%s": []}""".formatted(history.list()));
    }

    /** The second page is the source's, sent from its zero-based offset. */
    @ParameterizedTest
    @MethodSource("histories")
    void theSecondPageIsTheSourcesSecondPage(History history) throws Exception {
        assertThat(pageOf(history, reading(history, records(history, history.secondPage()))))
                .isEqualTo(answered(history.secondPage()));
    }

    /**
     * Paging is coerced, never refused (ADR 0017), and the page it comes to is the source's to
     * read — {@code count=0} and a page past the end included, as ADR 0018's D3 has it.
     */
    @ParameterizedTest
    @MethodSource("histories")
    void aPageOfNoneIsStillTheSourcesToAnswer(History history) throws Exception {
        MockHttpServletResponse response = reading(history, request(HttpMethod.GET,
                "/api/patient-" + history.segment() + "/" + NO_RECORDS)
                .param("startIndex", "-4").param("count", "0"));

        assertThat(List.of(response.getStatus(), sentToTheSource(history, NO_RECORDS)))
                .containsExactly(200, Map.of(
                        "_offset", List.of("0"),
                        "_count", List.of("0"),
                        "_total", List.of("accurate")));
    }

    /** A canned sorted read, answered in the order the source sorted it. */
    @ParameterizedTest
    @MethodSource("histories")
    void aSortedReadIsAnsweredInTheSourcesSortedOrder(History history) throws Exception {
        assertThat(pageOf(history, reading(history, records(history, history.sorted()))))
                .isEqualTo(answered(history.sorted()));
    }

    /** A canned filtered read, answered as the source filtered it. */
    @ParameterizedTest
    @MethodSource("histories")
    void aFilteredReadIsAnsweredAsTheSourceFilteredIt(History history) throws Exception {
        assertThat(pageOf(history, reading(history, records(history, history.filtered()))))
                .isEqualTo(answered(history.filtered()));
    }

    @ParameterizedTest
    @MethodSource("histories")
    void aFilterTheSourceMatchesNothingWithIsAnEmptyPage(History history) throws Exception {
        MockHttpServletRequestBuilder read = records(history, DEV_PATIENT_05);
        String[] parameter = history.filterMatchingNothing().split("=", 2);
        read.param(parameter[0], parameter[1]);

        assertThat(body(reading(history, read))).isEqualTo(emptyPage(history));
    }

    @ParameterizedTest
    @MethodSource("histories")
    void aPatientWithNoRecordsIsAnEmptyPageOfNone(History history) throws Exception {
        assertThat(body(reading(history, records(history, NO_RECORDS))))
                .isEqualTo(emptyPage(history));
    }

    /** The source holding no patient under the id is the application API's {@code 404}. */
    @ParameterizedTest
    @MethodSource("histories")
    void aPatientTheSourceDoesNotHoldIsNotFound(History history) throws Exception {
        MockHttpServletResponse response = reading(history, records(history, "not-a-uuid"));

        assertThat(List.of(response.getStatus(), body(response))).containsExactly(404,
                apiError(404, "request-refused", "The request was refused."));
    }

    /**
     * A patient id outside FHIR's id grammar is the {@code 400} of a path that cannot be bound,
     * refused before the source is called; the answer quotes none of it.
     */
    @ParameterizedTest
    @MethodSource("patientIdsOutsideTheGrammar")
    void aPatientIdOutsideTheGrammarIsRefusedWithoutCallingTheSource(History history,
            String patientId) throws Exception {
        MockHttpServletResponse response = reading(history, records(history, patientId));

        assertThat(body(response)).isEqualTo(refused());
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @MethodSource("histories")
    void aPagingValueThatIsNoIntegerIsRefused(History history) throws Exception {
        MockHttpServletResponse response = reading(history,
                records(history, TAN_WEI_MING).param("startIndex", "two"));

        assertThat(response.getStatus()).isEqualTo(400);
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    /**
     * The source failing — answering an error, or a page that is no searchset — is the
     * application API's {@code 502}, not a FHIR outcome, quoting nothing of the failure.
     */
    @ParameterizedTest
    @MethodSource("failingSources")
    void aFailingSourceIsABadGatewayApiError(History history, String failing) throws Exception {
        MockHttpServletResponse response = reading(history, records(history, failing));

        assertThat(List.of(response.getStatus(), response.getContentType(), body(response)))
                .containsExactly(502, "application/json", apiError(502, "server-error",
                        "The request could not be completed because of a server-side failure."));
    }

    /** A blank code, as an empty field sends one, is no filter on its column. */
    @ParameterizedTest
    @MethodSource("blankCodes")
    void aBlankCodeIsNoFilter(History history, String parameter) throws Exception {
        MockHttpServletResponse response =
                reading(history, records(history, TAN_WEI_MING).param(parameter, ""));

        assertThat(List.of(response.getStatus(), sentToTheSource(history, TAN_WEI_MING)))
                .containsExactly(200, Map.of(
                        "_offset", List.of("0"),
                        "_count", List.of("5"),
                        "_total", List.of("accurate")));
    }

    /** A from-date after its to-date is refused before the source is called. */
    @ParameterizedTest
    @MethodSource("invertedRanges")
    void aFromAfterItsToIsRefused(History history, Range range) throws Exception {
        MockHttpServletResponse response = reading(history, records(history, TAN_WEI_MING)
                .param(range.fromParameter(), range.from())
                .param(range.toParameter(), range.to()));

        assertThat(body(response)).isEqualTo(refused());
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }
}
