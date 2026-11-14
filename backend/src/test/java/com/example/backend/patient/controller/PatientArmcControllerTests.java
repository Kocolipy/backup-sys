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
import org.junit.jupiter.params.provider.CsvSource;
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
 * {@code endDate} for a board still open, with each filter and the sort the read takes passed to
 * the source, or the application API's {@code ApiError}. The filter
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

    /**
     * Each filter reaches the source as its own search parameter: one case for each request
     * parameter, beside the parameter the source is sent for it. The Outcome is one code.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        outcome        | fit        | outcome    | fit
        outcome        | unfit      | outcome    | unfit
        startDateFrom  | 2023-01-01 | start-date | ge2023-01-01
        startDateTo    | 2023-12-31 | start-date | le2023-12-31
        endDateFrom    | 2024-01-01 | end-date   | ge2024-01-01
        endDateTo      | 2024-12-31 | end-date   | le2024-12-31
        """)
    void eachFilterIsForwardedToTheSource(String parameter, String value, String forwarded,
            String sent) throws Exception {
        assertThat(reading(boards(TAN_WEI_MING).param(parameter, value)).getStatus())
                .isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING)).containsEntry(forwarded, List.of(sent));
    }

    /** Every filter at once reaches the source, combined, with the sort. */
    @Test
    void everyFilterAndTheSortAreForwardedTogether() throws Exception {
        assertThat(reading(boards(TAN_WEI_MING).param("outcome", "unfit")
                .param("startDateFrom", "2020-01-01").param("startDateTo", "2024-06-30")
                .param("endDateTo", "2024-12-31")
                .param("sortBy", "startDate").param("sortOrder", "descending")).getStatus())
                .isEqualTo(200);

        assertThat(sentToTheSource(TAN_WEI_MING)).isEqualTo(Map.ofEntries(
                Map.entry("outcome", List.of("unfit")),
                Map.entry("start-date", List.of("ge2020-01-01", "le2024-06-30")),
                Map.entry("end-date", List.of("le2024-12-31")),
                Map.entry("_sort", List.of("-start-date")),
                Map.entry("_offset", List.of("0")),
                Map.entry("_count", List.of("5")),
                Map.entry("_total", List.of("accurate"))));
    }

    /**
     * The sort reaches the source as its {@code _sort}, the column alone: a column with no
     * direction ascending. With no column — whatever the direction says — no {@code _sort} is
     * sent, and the order is the source's own.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
        "startDate, ascending, start-date",
        "startDate, descending, -start-date",
        "endDate, ascending, end-date",
        "endDate, descending, -end-date",
        "endDate, -, end-date",
        "-, descending, -",
        "-, -, -"})
    void theSortIsForwardedToTheSource(String sortBy, String sortOrder, String sent)
            throws Exception {
        MockHttpServletRequestBuilder sorted = boards(TAN_WEI_MING);
        if (sortBy != null) {
            sorted.param("sortBy", sortBy);
        }
        if (sortOrder != null) {
            sorted.param("sortOrder", sortOrder);
        }

        assertThat(reading(sorted).getStatus()).isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING).get("_sort"))
                .isEqualTo(sent == null ? null : List.of(sent));
    }

    /**
     * A filter or sort the read cannot take is the application API's {@code 400}, refused before
     * the source is called: a column but the two dates — an FFI's or a NUMC Record's among them
     * — a direction but ascending or descending, an Outcome outside its closed set — a label,
     * another case, a Java constant's name, another spelling or another set's code, a NUMC
     * Outcome's among them — and a date that is no calendar date {@code YYYY-MM-DD}.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        sortBy        | issueDate
        sortBy        | expiryDate
        sortBy        | outcome
        sortBy        | start-date
        sortBy        | EndDate
        sortBy        | START_DATE
        sortOrder     | asc
        sortOrder     | DESCENDING
        outcome       | FIT
        outcome       | Fit
        outcome       | UNFIT
        outcome       | all
        outcome       | waiver-granted
        outcome       | request-waiver
        startDateFrom | 2023-02-29
        startDateTo   | 01/02/2023
        endDateFrom   | 2024-1-1
        endDateTo     | 2024-12-31T00:00:00
        endDateTo     | yesterday
        """)
    void aFilterOrSortTheReadCannotTakeIsRefused(String parameter, String value)
            throws Exception {
        MockHttpServletResponse response = reading(boards(TAN_WEI_MING).param(parameter, value));

        assertThat(body(response)).isEqualTo(apiError(400, "invalid-request",
                "The request could not be read or did not pass validation."));
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    /** The Outcome is one code: given twice, even two good ones, it refuses the whole read. */
    @Test
    void anOutcomeGivenTwiceIsRefused() throws Exception {
        assertThat(reading(boards(TAN_WEI_MING).param("outcome", "fit", "unfit")).getStatus())
                .isEqualTo(400);
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @CsvSource({
        "startDateFrom, startDateTo, 2023-01-02, 2023-01-01",
        "endDateFrom, endDateTo, 2024-12-31, 2024-01-01"})
    void aFromDateAfterItsToDateIsRefused(String fromParameter, String toParameter, String from,
            String to) throws Exception {
        MockHttpServletResponse response = reading(boards(TAN_WEI_MING)
                .param(fromParameter, from).param(toParameter, to));

        assertThat(body(response)).isEqualTo(apiError(400, "invalid-request",
                "The request could not be read or did not pass validation."));
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    /** A range one day long is a from-date equal to its to-date, and is no refusal. */
    @Test
    void aRangeOfOneDayIsRead() throws Exception {
        assertThat(reading(boards(TAN_WEI_MING)
                .param("endDateFrom", "2024-02-10").param("endDateTo", "2024-02-10"))
                .getStatus()).isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING))
                .containsEntry("end-date", List.of("ge2024-02-10", "le2024-02-10"));
    }

    /** A blank Outcome, as an empty field sends one, is no filter on its column. */
    @Test
    void aBlankOutcomeIsNoFilter() throws Exception {
        assertThat(reading(boards(TAN_WEI_MING).param("outcome", "")).getStatus())
                .isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING)).isEqualTo(Map.of(
                "_offset", List.of("0"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /**
     * An ARMC Board has no Issue Date, Expiry Date, Status or Higher Authority, and its Board
     * Type takes no filter: a filter on one is no parameter the read takes, and nothing of it is
     * passed on.
     */
    @Test
    void aFilterOnAColumnArmcDoesNotFilterIsNotSentToTheSource() throws Exception {
        assertThat(reading(boards(TAN_WEI_MING).param("type", "aces")
                .param("boardType", "aces").param("issueDateFrom", "2024-01-01")
                .param("expiryDateTo", "2025-12-31").param("status", "active")
                .param("higherAuthority", "camo")).getStatus()).isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING)).isEqualTo(Map.of(
                "_offset", List.of("0"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /** The ids of a page's ARMC Boards, in its order, beside its total. */
    private List<Object> idsAndTotal(MockHttpServletResponse response) throws Exception {
        JsonNode page = body(response);
        return List.of(page.at("/totalResults").asInt(),
                page.at("/records").valueStream().map(board -> board.at("/id").asString())
                        .toList());
    }

    /** A canned sorted read, answered in the order the source sorted it: earliest Start Date first. */
    @Test
    void aSortedReadIsAnsweredInTheSourcesSortedOrder() throws Exception {
        assertThat(idsAndTotal(reading(boards(TAN_WEI_MING).param("sortBy", "startDate"))))
                .containsExactly(2, List.of("armc-tan-wei-ming-2", "armc-tan-wei-ming-1"));
    }

    /** A canned filtered read, answered as the source filtered it: the open board left out. */
    @Test
    void aFilteredReadIsAnsweredAsTheSourceFilteredIt() throws Exception {
        assertThat(idsAndTotal(reading(boards("dev-patient-05")
                .param("endDateFrom", "2023-01-01"))))
                .containsExactly(3, List.of("armc-tan-hui-min-2", "armc-tan-hui-min-3",
                        "armc-tan-hui-min-4"));
    }

    @Test
    void aFilterTheSourceMatchesNothingWithIsAnEmptyPage() throws Exception {
        assertThat(body(reading(boards("dev-patient-05").param("startDateTo", "2019-12-31"))))
                .isEqualTo(json.readTree("""
                        {"totalResults": 0, "startIndex": 1, "records": []}"""));
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
