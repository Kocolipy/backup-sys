package com.example.backend.patient.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.example.backend.patient.PatientSourceStubs;
import com.example.backend.patient.application.ArmcHistoryService;
import com.example.backend.patient.infrastructure.http.PatientSourceAdapters;
import com.example.backend.web.ApiExceptionHandler;
import com.example.backend.web.ErrorNamespaces;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code GET /api/patient-armc/{patientId}} as the web adapter answers it, through the HTTP
 * adapter over the patient source's mock — the stub files the local mock serves
 * ({@link PatientSourceStubs}) — with the app-wide exception handler its failures go to: one page
 * of the patient's ARMC History as plain JSON, its codes as the source sent them and no
 * {@code endDate} for a board still open, or the application API's {@code ApiError}. The filter
 * chain is not in this MockMvc: the Permission and its backstop are
 * {@code AuthorizationContractTests}', the paging coercion {@code PagingContractTests}', and what
 * is logged and audited over the real chain {@code ArmcHistoryIntegrationTests}'.
 */
class PatientArmcControllerTests {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    private static final String TAN_WEI_MING = "eq081-VQEgP8drUUqCWzHfw3";

    private final JsonMapper json = JsonMapper.builder().build();

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new PatientArmcController(new ArmcHistoryService(
                        PatientSourceAdapters.reading(SOURCE.baseUrl()))))
                .setControllerAdvice(new ApiExceptionHandler(new ErrorNamespaces(List.of())))
                .build();
    }

    private MockHttpServletResponse reading(MockHttpServletRequestBuilder read) throws Exception {
        return mvc.perform(read).andReturn().getResponse();
    }

    private static MockHttpServletRequestBuilder boards(String patientId) {
        return request(HttpMethod.GET, "/api/patient-armc/" + patientId).param("count", "5");
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString());
    }

    private JsonNode apiError(int status, String code, String detail) {
        return json.readTree("""
                {"status": %d, "code": "%s", "detail": "%s"}""".formatted(status, code, detail));
    }

    /**
     * Tan Wei Ming's two ARMC Boards, exactly: each its id, its two codes, its two dates and its
     * two Medical Officers, in the source's order, beside the total the source counted and where
     * the page starts.
     */
    @Test
    void anArmcHistoryIsAnsweredAsOnePageOfBoardsInTheSourcesOrder() throws Exception {
        MockHttpServletResponse response = reading(boards(TAN_WEI_MING));

        assertThat(List.of(response.getStatus(), response.getContentType()))
                .containsExactly(200, "application/json");
        assertThat(body(response)).isEqualTo(json.readTree("""
                {
                  "totalResults": 2,
                  "startIndex": 1,
                  "records": [
                    {"id": "armc-tan-wei-ming-1", "boardType": "aces", "outcome": "fit",
                     "startDate": "2024-01-01", "endDate": "2024-02-10",
                     "medicalOfficer1": "Medical Officer 1",
                     "medicalOfficer2": "Medical Officer 2"},
                    {"id": "armc-tan-wei-ming-2", "boardType": "aces", "outcome": "unfit",
                     "startDate": "2020-11-21", "endDate": "2021-04-02",
                     "medicalOfficer1": "Medical Officer 3",
                     "medicalOfficer2": "Medical Officer 4"}
                  ]
                }"""));
    }

    /** A board still open is answered with no {@code endDate} at all, not a {@code null} one. */
    @Test
    void aBoardStillOpenIsAnsweredWithNoEndDate() throws Exception {
        JsonNode open = body(reading(boards("dev-patient-05"))).at("/records/0");

        assertThat(open).isEqualTo(json.readTree("""
                {"id": "armc-tan-hui-min-1", "boardType": "aces", "outcome": "fit",
                 "startDate": "2025-06-02",
                 "medicalOfficer1": "Dr Lim Wei Jie",
                 "medicalOfficer2": "Dr Nur Aisyah binte Rahman"}"""));
    }

    /** The second page is the source's, sent from its zero-based offset: the last two of seven. */
    @Test
    void theSecondPageIsTheSourcesSecondPage() throws Exception {
        JsonNode page = body(reading(boards("dev-patient-05").param("startIndex", "6")));

        assertThat(List.of(page.at("/totalResults").asInt(), page.at("/startIndex").asInt(),
                page.at("/records").valueStream().map(board -> board.at("/id").asString())
                        .toList()))
                .containsExactly(7, 6, List.of("armc-tan-hui-min-6", "armc-tan-hui-min-7"));
    }

    /** The one ARMC History read the source was sent, every value of each query parameter. */
    private static Map<String, List<String>> sentToTheSource(String patientId) {
        LoggedRequest sent = SOURCE.findAll(
                getRequestedFor(urlPathEqualTo("/Patient/" + patientId + "/armc"))).getFirst();
        Map<String, List<String>> query = new TreeMap<>();
        sent.getQueryParams().forEach((name, value) -> query.put(name, value.values()));
        return query;
    }

    /**
     * Paging is coerced, never refused (ADR 0017), and the page it comes to is the source's to
     * read — {@code count=0} and a page past the end included, as ADR 0018's D3 has it.
     */
    @Test
    void aPageOfNoneIsStillTheSourcesToAnswer() throws Exception {
        MockHttpServletResponse response = reading(request(HttpMethod.GET,
                "/api/patient-armc/erXuFYUfucBZaryVksYEcMg3")
                .param("startIndex", "-4").param("count", "0"));

        assertThat(List.of(response.getStatus(), sentToTheSource("erXuFYUfucBZaryVksYEcMg3")))
                .containsExactly(200, Map.of(
                        "_offset", List.of("0"),
                        "_count", List.of("0"),
                        "_total", List.of("accurate")));
    }

    /** No sort and no filter reach the source: a parameter the read does not take is not passed on. */
    @Test
    void aParameterTheReadDoesNotTakeIsNotSentToTheSource() throws Exception {
        assertThat(reading(boards(TAN_WEI_MING).param("sortBy", "startDate")
                .param("outcome", "fit")).getStatus()).isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING)).isEqualTo(Map.of(
                "_offset", List.of("0"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    @Test
    void aPatientWithNoBoardsIsAnEmptyPageOfNone() throws Exception {
        assertThat(body(reading(boards("erXuFYUfucBZaryVksYEcMg3")))).isEqualTo(json.readTree("""
                {"totalResults": 0, "startIndex": 1, "records": []}"""));
    }

    /** The source holding no patient under the id is the application API's {@code 404}. */
    @Test
    void aPatientTheSourceDoesNotHoldIsNotFound() throws Exception {
        MockHttpServletResponse response = reading(boards("not-a-uuid"));

        assertThat(List.of(response.getStatus(), body(response))).containsExactly(404,
                apiError(404, "request-refused", "The request was refused."));
    }

    /**
     * A patient id outside FHIR's id grammar is the {@code 400} of a path that cannot be bound,
     * refused before the source is called; the answer quotes none of it.
     */
    @ParameterizedTest
    @ValueSource(strings = {"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
        "%3Cimg%20src=x%3E", "probe_patient"})
    void aPatientIdOutsideTheGrammarIsRefusedWithoutCallingTheSource(String patientId)
            throws Exception {
        MockHttpServletResponse response = reading(boards(patientId));

        assertThat(body(response)).isEqualTo(apiError(400, "invalid-request",
                "The request could not be read or did not pass validation."));
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void aPagingValueThatIsNoIntegerIsRefused() throws Exception {
        assertThat(reading(boards(TAN_WEI_MING).param("startIndex", "two")).getStatus())
                .isEqualTo(400);
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    /**
     * The source failing — here, a page whose board ends before it starts — is the application
     * API's {@code 502}, not a FHIR outcome, quoting nothing of the failure.
     */
    @Test
    void aFailingSourceIsABadGatewayApiError() throws Exception {
        MockHttpServletResponse response = reading(boards("fault-armc-end-before-start"));

        assertThat(List.of(response.getStatus(), response.getContentType(), body(response)))
                .containsExactly(502, "application/json", apiError(502, "server-error",
                        "The request could not be completed because of a server-side failure."));
    }

    @Test
    void aSourceAnsweringAnErrorIsABadGateway() throws Exception {
        assertThat(reading(boards("fault-armc-server-error")).getStatus()).isEqualTo(502);
    }

    /** A row is Restricted data: printed, it names the ARMC Board by id alone. */
    @Test
    void aRowPrintsItsBoardsIdAlone() {
        assertThat(new ArmcHistoryPage.Row("armc-tan-wei-ming-1", "aces", "fit", "2024-01-01",
                "2024-02-10", "Medical Officer 1", "Medical Officer 2"))
                .hasToString("Row[id=armc-tan-wei-ming-1]");
    }
}
