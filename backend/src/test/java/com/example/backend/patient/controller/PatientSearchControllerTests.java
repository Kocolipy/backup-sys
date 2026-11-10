package com.example.backend.patient.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.example.backend.audit.RecordingAuditTrail;
import com.example.backend.patient.PatientSourceStubs;
import com.example.backend.patient.application.PatientSearchService;
import com.example.backend.patient.infrastructure.http.PatientSourceAdapters;
import com.example.backend.web.ApiExceptionHandler;
import com.example.backend.web.ErrorNamespaces;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
 * {@code GET /api/patient-search} as the web adapter answers it, through the HTTP adapter over
 * the patient source's mock — the stub files the local mock serves ({@link PatientSourceStubs}) —
 * with the app-wide exception handler its failures go to: one page of matches as plain JSON, or
 * the application API's {@code ApiError} for a query refused before the source is called and for
 * the source failing. The filter chain is not in this MockMvc: the Permission and its backstop are
 * {@code AuthorizationContractTests}', the paging coercion {@code PagingContractTests}', and what
 * is logged and audited over the real chain {@code PatientSearchIntegrationTests}'.
 */
class PatientSearchControllerTests {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    private static final UUID SEARCHER = UUID.fromString("5d7c1e2a-3b4f-4a6d-8e9f-0a1b2c3d4e5f");

    private final JsonMapper json = JsonMapper.builder().build();

    private final RecordingAuditTrail trail = new RecordingAuditTrail();

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new PatientSearchController(new PatientSearchService(
                        PatientSourceAdapters.reading(SOURCE.baseUrl()), trail,
                        () -> Optional.of(SEARCHER))))
                .setControllerAdvice(new ApiExceptionHandler(new ErrorNamespaces(List.of())))
                .build();
    }

    private MockHttpServletResponse searching(MockHttpServletRequestBuilder search)
            throws Exception {
        return mvc.perform(search).andReturn().getResponse();
    }

    private static MockHttpServletRequestBuilder search(String q) {
        return request(HttpMethod.GET, "/api/patient-search")
                .param("q", q).param("count", "5");
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString());
    }

    private JsonNode apiError(int status, String code, String detail) {
        return json.readTree("""
                {"status": %d, "code": "%s", "detail": "%s"}""".formatted(status, code, detail));
    }

    /**
     * The name search's second page, exactly: the source's two matches in its order, each its id,
     * official name, NRIC, gender, birth date and Last visit — the Patient with none as
     * {@code null} — beside the total the source counted and where the page starts.
     */
    @Test
    void aSearchIsAnsweredAsOnePageOfMatchesInTheSourcesOrder() throws Exception {
        MockHttpServletResponse response = searching(search("tan").param("startIndex", "6"));

        assertThat(List.of(response.getStatus(), response.getContentType()))
                .containsExactly(200, "application/json");
        assertThat(body(response)).isEqualTo(json.readTree("""
                {
                  "totalResults": 7,
                  "startIndex": 6,
                  "patients": [
                    {"id": "dev-patient-09", "name": "Kavitha Tanabalan", "nric": "S9512345A",
                     "gender": "female", "birthDate": "1995-09-09", "lastVisit": "2026-05-07"},
                    {"id": "dev-patient-10", "name": "Tan Boon Huat", "nric": "S5512345A",
                     "gender": "male", "birthDate": "1955-01-23", "lastVisit": null}
                  ]
                }"""));
    }

    /** A row is Restricted data: printed, it names the Patient by id alone. */
    @Test
    void aRowPrintsItsPatientsIdAlone() {
        assertThat(new PatientSearchPage.Row("dev-patient-05", "Tan Hui Min", "S9012345A",
                "female", "1990-02-04", "2026-11-02")).hasToString("Row[id=dev-patient-05]");
    }

    /** A full NRIC in lower case is still an NRIC search, for the NRIC in upper case. */
    @Test
    void anNricInEitherCaseFindsThePatientHoldingIt() throws Exception {
        assertThat(body(searching(search("s9212345a"))).at("/patients"))
                .isEqualTo(json.readTree("""
                        [{"id": "erXuFYUfucBZaryVksYEcMg3", "name": "Siti Nurhaliza binte Ahmad",
                          "nric": "S9212345A", "gender": "female", "birthDate": "1992-08-21",
                          "lastVisit": "2026-07-11"}]"""));
    }

    @Test
    void aSearchNothingMatchesIsAnEmptyPageOfNone() throws Exception {
        assertThat(body(searching(search("nobody")))).isEqualTo(json.readTree("""
                {"totalResults": 0, "startIndex": 1, "patients": []}"""));
    }

    /** Each search the source answered is recorded once, by its kind and count. */
    @Test
    void anAnsweredSearchIsAudited() throws Exception {
        searching(search("tan"));

        assertThat(trail.recorded()).extracting(RecordingAuditTrail.Recorded::detail)
                .containsExactly("5 NAME");
    }

    /**
     * A query under three characters — none at all included — is the {@code 400} of a parameter
     * that cannot be bound, refused before the source is called and recording nothing; the
     * answer quotes none of it.
     */
    @ParameterizedTest
    @ValueSource(strings = {"ta", "  t  ", ""})
    void aQueryTooShortIsRefusedWithoutCallingTheSource(String q) throws Exception {
        MockHttpServletResponse response = searching(search(q));

        assertThat(body(response)).isEqualTo(apiError(400, "invalid-request",
                "The request could not be read or did not pass validation."));
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
        assertThat(trail.recorded()).isEmpty();
    }

    @Test
    void aSearchWithNoQueryIsRefused() throws Exception {
        MockHttpServletResponse response =
                searching(request(HttpMethod.GET, "/api/patient-search"));

        assertThat(response.getStatus()).isEqualTo(400);
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void aPagingValueThatIsNoIntegerIsRefused() throws Exception {
        assertThat(searching(search("tan").param("startIndex", "two")).getStatus())
                .isEqualTo(400);
    }

    /**
     * The source failing — here, a searchset with no total — is the application API's
     * {@code 502}, not a FHIR outcome, quoting nothing of the failure, and records nothing.
     */
    @Test
    void aFailingSourceIsABadGatewayApiError() throws Exception {
        SOURCE.stubFor(get(urlPathEqualTo("/Patient")).atPriority(1).willReturn(okJson("""
                {"resourceType": "Bundle", "type": "searchset"}""")));

        MockHttpServletResponse response = searching(search("tan"));

        assertThat(List.of(response.getStatus(), response.getContentType()))
                .containsExactly(502, "application/json");
        assertThat(body(response)).isEqualTo(apiError(502, "server-error",
                "The request could not be completed because of a server-side failure."));
        assertThat(trail.recorded()).isEmpty();
    }

    @Test
    void aSourceAnsweringAnErrorIsABadGateway() throws Exception {
        SOURCE.stubFor(get(urlPathEqualTo("/Patient")).atPriority(1)
                .willReturn(aResponse().withStatus(500)));

        assertThat(searching(search("tan")).getStatus()).isEqualTo(502);
    }
}
