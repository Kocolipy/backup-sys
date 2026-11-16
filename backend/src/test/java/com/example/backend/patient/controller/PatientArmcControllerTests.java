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
 * of the patient's ARMC History as plain JSON, as received (ADR 0020) — its codes as the source
 * sent them, a field missing or invalid {@code null} and each invalid one named — with each filter and the sort the read takes passed to
 * the source, or the application API's {@code ApiError}. The filter
 * chain is not in this MockMvc: the Permission and its backstop are
 * {@code AuthorizationContractTests}', the paging coercion {@code PagingContractTests}', and what
 * is logged and audited over the real chain {@code PatientHistoriesIntegrationTests}'. The rules every history shares are
 * {@code PatientHistoriesControllerTests}'; this class holds what the ARMC History declares.
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
                     "medicalOfficer2": "Medical Officer 2", "invalid": []},
                    {"id": "armc-tan-wei-ming-2", "boardType": "aces", "outcome": "unfit",
                     "startDate": "2020-11-21", "endDate": "2021-04-02",
                     "medicalOfficer1": "Medical Officer 3",
                     "medicalOfficer2": "Medical Officer 4", "invalid": []}
                  ]
                }"""));
    }

    /** A board still open is answered with its {@code endDate} {@code null}: missing, not invalid. */
    @Test
    void aBoardStillOpenIsAnsweredWithANullEndDate() throws Exception {
        JsonNode open = body(reading(boards("dev-patient-05"))).at("/records/0");

        assertThat(open).isEqualTo(json.readTree("""
                {"id": "armc-tan-hui-min-1", "boardType": "aces", "outcome": "fit",
                 "startDate": "2025-06-02", "endDate": null,
                 "medicalOfficer1": "Dr Lim Wei Jie",
                 "medicalOfficer2": "Dr Nur Aisyah binte Rahman", "invalid": []}"""));
    }

    /**
     * Tan Boon Huat's four ARMC Boards, as received (ADR 0020): a field missing, blank or invalid
     * is {@code null}, each invalid one named in its row's {@code invalid}; a code outside its
     * closed set, an id outside FHIR's grammar and an End Date before the Start Date as the
     * source sent them.
     */
    @Test
    void aBoardsMissingAndInvalidFieldsAreNullTheInvalidOnesNamed() throws Exception {
        MockHttpServletResponse response = reading(boards("dev-patient-10"));

        assertThat(body(response)).isEqualTo(json.readTree("""
                {
                  "totalResults": 4,
                  "startIndex": 1,
                  "records": [
                    {"id": "armc-tan-boon-huat-1", "boardType": "aces", "outcome": null,
                     "startDate": "2022-03-16", "endDate": "2022-03-01",
                     "medicalOfficer1": "Dr Lim Wei Jie", "medicalOfficer2": null,
                     "invalid": []},
                    {"id": "armc-tan-boon-huat-2", "boardType": "aces", "outcome": "fit",
                     "startDate": null, "endDate": null,
                     "medicalOfficer1": "Dr Rajesh Kumar", "medicalOfficer2": "Dr Chen Mei Ling",
                     "invalid": ["startDate", "endDate"]},
                    {"id": "armc tan boon huat 3", "boardType": "aircrew",
                     "outcome": "waiver-granted", "startDate": "2020-03-16",
                     "endDate": "2020-05-11", "medicalOfficer1": "Dr Chen Mei Ling",
                     "medicalOfficer2": "Dr Lim Wei Jie", "invalid": []},
                    {"id": null, "boardType": "aces", "outcome": null, "startDate": null,
                     "endDate": null, "medicalOfficer1": null, "medicalOfficer2": null,
                     "invalid": []}
                  ]
                }"""));
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

    /** A range one day long is a from-date equal to its to-date, and is no refusal. */
    @Test
    void aRangeOfOneDayIsRead() throws Exception {
        assertThat(reading(boards(TAN_WEI_MING)
                .param("endDateFrom", "2024-02-10").param("endDateTo", "2024-02-10"))
                .getStatus()).isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING))
                .containsEntry("end-date", List.of("ge2024-02-10", "le2024-02-10"));
    }

    /** A date given twice is read as one value, its first, as any one-valued parameter is. */
    @Test
    void aDateGivenTwiceIsReadAsItsFirst() throws Exception {
        assertThat(reading(boards(TAN_WEI_MING)
                .param("startDateFrom", "2023-01-01", "2024-01-01")).getStatus()).isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING))
                .containsEntry("start-date", List.of("ge2023-01-01"));
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

    /** A row is Restricted data: printed, it names the ARMC Board by id alone. */
    @Test
    void aRowPrintsItsBoardsIdAlone() {
        assertThat(new ArmcHistoryPage.Row("armc-tan-wei-ming-1", "aces", "fit", "2024-01-01",
                "2024-02-10", "Medical Officer 1", "Medical Officer 2", List.of("outcome")))
                .hasToString("Row[id=armc-tan-wei-ming-1]");
    }
}
