package com.example.backend.patient.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.example.backend.patient.PatientSourceStubs;
import com.example.backend.patient.application.FfiHistoryService;
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
 * {@code GET /api/patient-ffi/{patientId}} as the web adapter answers it, through the HTTP adapter
 * over the patient source's mock — the stub files the local mock serves
 * ({@link PatientSourceStubs}) — with the app-wide exception handler its failures go to: one page
 * of the patient's FFI History as plain JSON, its codes as the source sent them, or the
 * application API's {@code ApiError}. The filter chain is not in this MockMvc: the Permission and
 * its backstop are {@code AuthorizationContractTests}', the paging coercion
 * {@code PagingContractTests}', and what is logged and audited over the real chain
 * {@code FfiHistoryIntegrationTests}'.
 */
class PatientFfiControllerTests {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    private static final String TAN_WEI_MING = "eq081-VQEgP8drUUqCWzHfw3";

    private final JsonMapper json = JsonMapper.builder().build();

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new PatientFfiController(new FfiHistoryService(
                        PatientSourceAdapters.reading(SOURCE.baseUrl()))))
                .setControllerAdvice(new ApiExceptionHandler(new ErrorNamespaces(List.of())))
                .build();
    }

    private MockHttpServletResponse reading(MockHttpServletRequestBuilder read) throws Exception {
        return mvc.perform(read).andReturn().getResponse();
    }

    private static MockHttpServletRequestBuilder ffis(String patientId) {
        return request(HttpMethod.GET, "/api/patient-ffi/" + patientId).param("count", "5");
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString());
    }

    private JsonNode apiError(int status, String code, String detail) {
        return json.readTree("""
                {"status": %d, "code": "%s", "detail": "%s"}""".formatted(status, code, detail));
    }

    /**
     * Tan Wei Ming's four FFIs, exactly: each its id, its five codes and its two dates, in the
     * source's order, beside the total the source counted and where the page starts.
     */
    @Test
    void anFfiHistoryIsAnsweredAsOnePageOfFfisInTheSourcesOrder() throws Exception {
        MockHttpServletResponse response = reading(ffis(TAN_WEI_MING));

        assertThat(List.of(response.getStatus(), response.getContentType()))
                .containsExactly(200, "application/json");
        assertThat(body(response)).isEqualTo(json.readTree("""
                {
                  "totalResults": 4,
                  "startIndex": 1,
                  "ffis": [
                    {"id": "ffi-tan-wei-ming-1", "type": "overseas-exercises", "outcome": "fit",
                     "issueDate": "2023-10-14", "expiryDate": "2024-10-13",
                     "higherAuthority": "camo", "submittingMc": "nee-soon-camp",
                     "status": "inactive"},
                    {"id": "ffi-tan-wei-ming-2", "type": "ippt", "outcome": "request-waiver",
                     "issueDate": "2024-01-10", "expiryDate": "2025-01-09",
                     "higherAuthority": "cnmo", "submittingMc": "kranji-camp",
                     "status": "active"},
                    {"id": "ffi-tan-wei-ming-3", "type": "dental", "outcome": "fit",
                     "issueDate": "2023-11-22", "expiryDate": "2024-11-21",
                     "higherAuthority": "carmo", "submittingMc": "hendon-camp",
                     "status": "active"},
                    {"id": "ffi-tan-wei-ming-4", "type": "vocational", "outcome": "unfit",
                     "issueDate": "2023-08-05", "expiryDate": "2024-08-04",
                     "higherAuthority": "camo", "submittingMc": "mandai-hill-camp",
                     "status": "inactive"}
                  ]
                }"""));
    }

    /** The second page is the source's, sent from its zero-based offset: the last two of seven. */
    @Test
    void theSecondPageIsTheSourcesSecondPage() throws Exception {
        JsonNode page = body(reading(ffis("dev-patient-05").param("startIndex", "6")));

        assertThat(List.of(page.at("/totalResults").asInt(), page.at("/startIndex").asInt(),
                page.at("/ffis").valueStream().map(ffi -> ffi.at("/id").asString()).toList()))
                .containsExactly(7, 6, List.of("ffi-tan-hui-min-6", "ffi-tan-hui-min-7"));
    }

    /** The one FFI History read the source was sent, every value of each query parameter. */
    private static Map<String, List<String>> sentToTheSource(String patientId) {
        LoggedRequest sent = SOURCE.findAll(
                getRequestedFor(urlPathEqualTo("/Patient/" + patientId + "/ffi"))).getFirst();
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
                "/api/patient-ffi/erXuFYUfucBZaryVksYEcMg3")
                .param("startIndex", "-4").param("count", "0"));

        assertThat(List.of(response.getStatus(), sentToTheSource("erXuFYUfucBZaryVksYEcMg3")))
                .containsExactly(200, Map.of(
                        "_offset", List.of("0"),
                        "_count", List.of("0"),
                        "_total", List.of("accurate")));
    }

    @Test
    void aPatientWithNoFfisIsAnEmptyPageOfNone() throws Exception {
        assertThat(body(reading(ffis("erXuFYUfucBZaryVksYEcMg3")))).isEqualTo(json.readTree("""
                {"totalResults": 0, "startIndex": 1, "ffis": []}"""));
    }

    /** The source holding no patient under the id is the application API's {@code 404}. */
    @Test
    void aPatientTheSourceDoesNotHoldIsNotFound() throws Exception {
        MockHttpServletResponse response = reading(ffis("not-a-uuid"));

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
        MockHttpServletResponse response = reading(ffis(patientId));

        assertThat(body(response)).isEqualTo(apiError(400, "invalid-request",
                "The request could not be read or did not pass validation."));
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void aPagingValueThatIsNoIntegerIsRefused() throws Exception {
        assertThat(reading(ffis(TAN_WEI_MING).param("startIndex", "two")).getStatus())
                .isEqualTo(400);
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    /**
     * The source failing — here, a page whose FFI has an Outcome outside its set — is the
     * application API's {@code 502}, not a FHIR outcome, quoting nothing of the failure.
     */
    @Test
    void aFailingSourceIsABadGatewayApiError() throws Exception {
        MockHttpServletResponse response = reading(ffis("fault-ffi-unknown-higher-authority"));

        assertThat(List.of(response.getStatus(), response.getContentType(), body(response)))
                .containsExactly(502, "application/json", apiError(502, "server-error",
                        "The request could not be completed because of a server-side failure."));
    }

    @Test
    void aSourceAnsweringAnErrorIsABadGateway() throws Exception {
        assertThat(reading(ffis("fault-ffi-server-error")).getStatus()).isEqualTo(502);
    }

    /** A row is Restricted data: printed, it names the FFI by id alone. */
    @Test
    void aRowPrintsItsFfisIdAlone() {
        assertThat(new FfiHistoryPage.Row("ffi-tan-wei-ming-1", "overseas-exercises", "fit",
                "2023-10-14", "2024-10-13", "camo", "nee-soon-camp", "inactive"))
                .hasToString("Row[id=ffi-tan-wei-ming-1]");
    }
}
