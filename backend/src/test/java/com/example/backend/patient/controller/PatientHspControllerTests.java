package com.example.backend.patient.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.example.backend.patient.PatientSourceStubs;
import com.example.backend.patient.application.HspHistoryService;
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
 * {@code GET /api/patient-hsp/{patientId}} as the web adapter answers it, through the HTTP adapter
 * over the patient source's mock — the stub files the local mock serves
 * ({@link PatientSourceStubs}) — with the app-wide exception handler its failures go to: one page
 * of the patient's HSP History as plain JSON, its codes as the source sent them, or the
 * application API's {@code ApiError}. The filter chain is not in this MockMvc: the Permission and
 * its backstop are {@code AuthorizationContractTests}', the paging coercion
 * {@code PagingContractTests}', and what is logged and audited over the real chain
 * {@code PatientHistoriesIntegrationTests}'. The rules every history shares are
 * {@code PatientHistoriesControllerTests}'; this class holds what the HSP History declares.
 */
class PatientHspControllerTests {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    private static final String TAN_WEI_MING = "eq081-VQEgP8drUUqCWzHfw3";

    private final JsonMapper json = JsonMapper.builder().build();

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new PatientHspController(new HspHistoryService(
                        PatientSourceAdapters.reading(SOURCE.baseUrl()))))
                .setControllerAdvice(new ApiExceptionHandler(new ErrorNamespaces(List.of())))
                .build();
    }

    private MockHttpServletResponse reading(MockHttpServletRequestBuilder read) throws Exception {
        return mvc.perform(read).andReturn().getResponse();
    }

    private static MockHttpServletRequestBuilder hsps(String patientId) {
        return request(HttpMethod.GET, "/api/patient-hsp/" + patientId).param("count", "5");
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString());
    }

    private JsonNode apiError(int status, String code, String detail) {
        return json.readTree("""
                {"status": %d, "code": "%s", "detail": "%s"}""".formatted(status, code, detail));
    }

    /**
     * Tan Wei Ming's three HSP Records, exactly: each its id, its three codes and its two dates,
     * in the source's order, beside the total the source counted and where the page starts.
     */
    @Test
    void anHspHistoryIsAnsweredAsOnePageOfRecordsInTheSourcesOrder() throws Exception {
        MockHttpServletResponse response = reading(hsps(TAN_WEI_MING));

        assertThat(List.of(response.getStatus(), response.getContentType()))
                .containsExactly(200, "application/json");
        assertThat(body(response)).isEqualTo(json.readTree("""
                {
                  "totalResults": 3,
                  "startIndex": 1,
                  "records": [
                    {"id": "hsp-tan-wei-ming-2", "outcome": "moderate", "startDate": "2023-10-14",
                     "endDate": "2024-10-13", "submittingMc": "mandai-hill-camp",
                     "status": "inactive", "invalid": []},
                    {"id": "hsp-tan-wei-ming-1", "outcome": "low", "startDate": "2024-03-11",
                     "endDate": "2025-03-10", "submittingMc": "nee-soon-camp",
                     "status": "active", "invalid": []},
                    {"id": "hsp-tan-wei-ming-3", "outcome": "high", "startDate": "2022-11-22",
                     "endDate": "2023-10-10", "submittingMc": "hendon-camp",
                     "status": "inactive", "invalid": []}
                  ]
                }"""));
    }

    /**
     * Tan Boon Huat's three HSP Records, as received (ADR 0020): a field missing or invalid is
     * {@code null}, each invalid one named in its row's {@code invalid}, and a code outside its
     * closed set as the source sent it.
     */
    @Test
    void anHspRecordsMissingAndInvalidFieldsAreNullTheInvalidOnesNamed() throws Exception {
        MockHttpServletResponse response = reading(hsps("dev-patient-10"));

        assertThat(body(response)).isEqualTo(json.readTree("""
                {
                  "totalResults": 3,
                  "startIndex": 1,
                  "records": [
                    {"id": "hsp-tan-boon-huat-1", "outcome": null, "startDate": "2022-03-16",
                     "endDate": "2023-03-15", "submittingMc": null, "status": "active",
                     "invalid": []},
                    {"id": "hsp-tan-boon-huat-2", "outcome": "low", "startDate": null,
                     "endDate": null, "submittingMc": "kranji-camp", "status": null,
                     "invalid": ["startDate", "endDate", "status"]},
                    {"id": null, "outcome": "critical", "startDate": "2020-03-16",
                     "endDate": "2021-03-15", "submittingMc": "tengah-air-base",
                     "status": "expired", "invalid": []}
                  ]
                }"""));
    }

    /** The one HSP History read the source was sent, every value of each query parameter. */
    private static Map<String, List<String>> sentToTheSource(String patientId) {
        LoggedRequest sent = SOURCE.findAll(
                getRequestedFor(urlPathEqualTo("/Patient/" + patientId + "/hsp"))).getFirst();
        Map<String, List<String>> query = new TreeMap<>();
        sent.getQueryParams().forEach((name, value) -> query.put(name, value.values()));
        return query;
    }

    /**
     * Each filter reaches the source as its own search parameter: one case for each request
     * parameter, beside the parameter the source is sent for it. A repeated code is one parameter,
     * its codes joined by commas — FHIR's OR.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        outcome       | moderate                  | outcome       | moderate
        outcome       | high,low                  | outcome       | low,high
        startDateFrom | 2024-01-01                | start-date    | ge2024-01-01
        startDateTo   | 2024-12-31                | start-date    | le2024-12-31
        endDateFrom   | 2025-01-01                | end-date      | ge2025-01-01
        endDateTo     | 2025-12-31                | end-date      | le2025-12-31
        submittingMc  | mandai-hill-camp          | submitting-mc | mandai-hill-camp
        submittingMc  | hendon-camp,nee-soon-camp | submitting-mc | nee-soon-camp,hendon-camp
        status        | active                    | status        | active
        status        | inactive                  | status        | inactive
        """)
    void eachFilterIsForwardedToTheSource(String parameter, String values, String forwarded,
            String sent) throws Exception {
        MockHttpServletRequestBuilder read = hsps(TAN_WEI_MING);
        for (String value : values.split(",")) {
            read.param(parameter, value);
        }

        assertThat(reading(read).getStatus()).isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING)).containsEntry(forwarded, List.of(sent));
    }

    /** Every filter at once reaches the source, combined, with the sort. */
    @Test
    void everyFilterAndTheSortAreForwardedTogether() throws Exception {
        assertThat(reading(hsps(TAN_WEI_MING).param("outcome", "high").param("outcome", "low")
                .param("startDateFrom", "2022-01-01").param("endDateTo", "2025-12-31")
                .param("submittingMc", "hendon-camp").param("status", "inactive")
                .param("sortBy", "startDate").param("sortOrder", "descending")).getStatus())
                .isEqualTo(200);

        assertThat(sentToTheSource(TAN_WEI_MING)).isEqualTo(Map.ofEntries(
                Map.entry("outcome", List.of("low,high")),
                Map.entry("start-date", List.of("ge2022-01-01")),
                Map.entry("end-date", List.of("le2025-12-31")),
                Map.entry("submitting-mc", List.of("hendon-camp")),
                Map.entry("status", List.of("inactive")),
                Map.entry("_sort", List.of("-start-date,_id")),
                Map.entry("_offset", List.of("0")),
                Map.entry("_count", List.of("5")),
                Map.entry("_total", List.of("accurate"))));
    }

    /**
     * The sort reaches the source as its {@code _sort}: a column with no direction ascending.
     * With no column — whatever the direction says — no {@code _sort} is sent, and the order is
     * the source's own.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
        "startDate, ascending, 'start-date,_id'",
        "startDate, descending, '-start-date,_id'",
        "endDate, ascending, 'end-date,_id'",
        "endDate, descending, '-end-date,_id'",
        "endDate, -, 'end-date,_id'",
        "-, descending, -",
        "-, -, -"})
    void theSortIsForwardedToTheSource(String sortBy, String sortOrder, String sent)
            throws Exception {
        MockHttpServletRequestBuilder sorted = hsps(TAN_WEI_MING);
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
     * the source is called: a column but the two dates — another history's dates among them — a
     * direction but ascending or descending, a code outside its closed set — a label, another
     * case, a Java constant's name, another set's code or another spelling — and a date that is
     * no calendar date {@code YYYY-MM-DD}.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        sortBy        | outcome
        sortBy        | issueDate
        sortBy        | start-date
        sortBy        | StartDate
        sortOrder     | desc
        sortOrder     | Ascending
        outcome       | LOW
        outcome       | Moderate
        outcome       | critical
        outcome       | fit
        submittingMc  | nee-soon-camp-mc
        submittingMc  | HENDON_CAMP
        status        | Active
        status        | expired
        status        | INACTIVE
        startDateFrom | 2024-02-30
        startDateTo   | 11/03/2024
        endDateFrom   | 2025-3-10
        endDateTo     | 2025-03-10T00:00:00
        endDateTo     | tomorrow
        """)
    void aFilterOrSortTheReadCannotTakeIsRefused(String parameter, String value)
            throws Exception {
        MockHttpServletResponse response = reading(hsps(TAN_WEI_MING).param(parameter, value));

        assertThat(body(response)).isEqualTo(apiError(400, "invalid-request",
                "The request could not be read or did not pass validation."));
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    /** A blank code beside a good one leaves the good one alone. */
    @Test
    void aBlankCodeBesideAnotherIsThatOneAlone() throws Exception {
        assertThat(reading(hsps(TAN_WEI_MING).param("submittingMc", "", "kranji-camp"))
                .getStatus()).isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING))
                .containsEntry("submitting-mc", List.of("kranji-camp"));
    }

    /** One code outside its set among good ones refuses the whole read. */
    @Test
    void oneCodeOutsideItsSetAmongOthersIsRefused() throws Exception {
        assertThat(reading(hsps(TAN_WEI_MING).param("outcome", "low").param("outcome", "HIGH"))
                .getStatus()).isEqualTo(400);
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    /** A range one day long is a from-date equal to its to-date, and is no refusal. */
    @Test
    void aRangeOfOneDayIsRead() throws Exception {
        assertThat(reading(hsps(TAN_WEI_MING)
                .param("startDateFrom", "2024-03-11").param("startDateTo", "2024-03-11"))
                .getStatus()).isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING))
                .containsEntry("start-date", List.of("ge2024-03-11", "le2024-03-11"));
    }

    /** A row is Restricted data: printed, it names the HSP Record by id alone. */
    @Test
    void aRowPrintsItsRecordsIdAlone() {
        assertThat(new HspHistoryPage.Row("hsp-tan-wei-ming-1", "low", "2024-03-11",
                "2025-03-10", "nee-soon-camp", "active", List.of("outcome")))
                .hasToString("Row[id=hsp-tan-wei-ming-1]");
    }
}
