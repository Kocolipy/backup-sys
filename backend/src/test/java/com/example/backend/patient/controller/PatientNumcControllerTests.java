package com.example.backend.patient.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.example.backend.patient.PatientSourceStubs;
import com.example.backend.patient.application.NumcHistoryService;
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
 * {@code GET /api/patient-numc/{patientId}} as the web adapter answers it, through the HTTP
 * adapter over the patient source's mock — the stub files the local mock serves
 * ({@link PatientSourceStubs}) — with the app-wide exception handler its failures go to: one page
 * of the patient's NUMC History as plain JSON, its codes as the source sent them, or the
 * application API's {@code ApiError}. The filter chain is not in this MockMvc: the Permission and
 * its backstop are {@code AuthorizationContractTests}', the paging coercion
 * {@code PagingContractTests}', and what is logged and audited over the real chain
 * {@code NumcHistoryIntegrationTests}'.
 */
class PatientNumcControllerTests {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    private static final String TAN_WEI_MING = "eq081-VQEgP8drUUqCWzHfw3";

    private final JsonMapper json = JsonMapper.builder().build();

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new PatientNumcController(new NumcHistoryService(
                        PatientSourceAdapters.reading(SOURCE.baseUrl()))))
                .setControllerAdvice(new ApiExceptionHandler(new ErrorNamespaces(List.of())))
                .build();
    }

    private MockHttpServletResponse reading(MockHttpServletRequestBuilder read) throws Exception {
        return mvc.perform(read).andReturn().getResponse();
    }

    private static MockHttpServletRequestBuilder records(String patientId) {
        return request(HttpMethod.GET, "/api/patient-numc/" + patientId).param("count", "5");
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString());
    }

    private JsonNode apiError(int status, String code, String detail) {
        return json.readTree("""
                {"status": %d, "code": "%s", "detail": "%s"}""".formatted(status, code, detail));
    }

    /**
     * Tan Wei Ming's four NUMC Records, exactly: each its id, its two codes and its two dates, in
     * the source's order, beside the total the source counted and where the page starts.
     */
    @Test
    void aNumcHistoryIsAnsweredAsOnePageOfRecordsInTheSourcesOrder() throws Exception {
        MockHttpServletResponse response = reading(records(TAN_WEI_MING));

        assertThat(List.of(response.getStatus(), response.getContentType()))
                .containsExactly(200, "application/json");
        assertThat(body(response)).isEqualTo(json.readTree("""
                {
                  "totalResults": 4,
                  "startIndex": 1,
                  "records": [
                    {"id": "numc-tan-wei-ming-1", "type": "hyperbaric-chamber", "outcome": "fit",
                     "issueDate": "2023-10-14", "expiryDate": "2024-10-13"},
                    {"id": "numc-tan-wei-ming-2", "type": "submariner",
                     "outcome": "waiver-rejected",
                     "issueDate": "2024-01-10", "expiryDate": "2025-01-09"},
                    {"id": "numc-tan-wei-ming-3", "type": "diving", "outcome": "waiver-granted",
                     "issueDate": "2023-11-22", "expiryDate": "2024-11-21"},
                    {"id": "numc-tan-wei-ming-4", "type": "hyperbaric-chamber",
                     "outcome": "unfit",
                     "issueDate": "2023-08-05", "expiryDate": "2024-08-04"}
                  ]
                }"""));
    }

    /** The second page is the source's, sent from its zero-based offset: the last two of seven. */
    @Test
    void theSecondPageIsTheSourcesSecondPage() throws Exception {
        JsonNode page = body(reading(records("dev-patient-05").param("startIndex", "6")));

        assertThat(List.of(page.at("/totalResults").asInt(), page.at("/startIndex").asInt(),
                page.at("/records").valueStream().map(record -> record.at("/id").asString())
                        .toList()))
                .containsExactly(7, 6, List.of("numc-tan-hui-min-6", "numc-tan-hui-min-7"));
    }

    /** The one NUMC History read the source was sent, every value of each query parameter. */
    private static Map<String, List<String>> sentToTheSource(String patientId) {
        LoggedRequest sent = SOURCE.findAll(
                getRequestedFor(urlPathEqualTo("/Patient/" + patientId + "/numc"))).getFirst();
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
                "/api/patient-numc/erXuFYUfucBZaryVksYEcMg3")
                .param("startIndex", "-4").param("count", "0"));

        assertThat(List.of(response.getStatus(), sentToTheSource("erXuFYUfucBZaryVksYEcMg3")))
                .containsExactly(200, Map.of(
                        "_offset", List.of("0"),
                        "_count", List.of("0"),
                        "_total", List.of("accurate")));
    }

    /**
     * Each filter reaches the source as its own search parameter: one case for each request
     * parameter, beside the parameter the source is sent for it. A repeated code is one parameter,
     * its codes joined by commas — FHIR's OR — in its closed set's order.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        type            | diving                    | type        | diving
        type            | diving,hyperbaric-chamber | type        | hyperbaric-chamber,diving
        outcome         | waiver-granted            | outcome     | waiver-granted
        outcome         | waiver-rejected,unfit     | outcome     | unfit,waiver-rejected
        issueDateFrom   | 2024-01-01                | issue-date  | ge2024-01-01
        issueDateTo     | 2024-12-31                | issue-date  | le2024-12-31
        expiryDateFrom  | 2025-01-01                | expiry-date | ge2025-01-01
        expiryDateTo    | 2025-12-31                | expiry-date | le2025-12-31
        """)
    void eachFilterIsForwardedToTheSource(String parameter, String values, String forwarded,
            String sent) throws Exception {
        MockHttpServletRequestBuilder read = records(TAN_WEI_MING);
        for (String value : values.split(",")) {
            read.param(parameter, value);
        }

        assertThat(reading(read).getStatus()).isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING)).containsEntry(forwarded, List.of(sent));
    }

    /** Every filter at once reaches the source, combined, with the sort. */
    @Test
    void everyFilterAndTheSortAreForwardedTogether() throws Exception {
        assertThat(reading(records(TAN_WEI_MING).param("type", "submariner")
                .param("outcome", "fit").param("outcome", "unfit")
                .param("issueDateFrom", "2023-01-01").param("issueDateTo", "2024-06-30")
                .param("expiryDateTo", "2025-12-31")
                .param("sortBy", "expiryDate").param("sortOrder", "descending")).getStatus())
                .isEqualTo(200);

        assertThat(sentToTheSource(TAN_WEI_MING)).isEqualTo(Map.ofEntries(
                Map.entry("type", List.of("submariner")),
                Map.entry("outcome", List.of("fit,unfit")),
                Map.entry("issue-date", List.of("ge2023-01-01", "le2024-06-30")),
                Map.entry("expiry-date", List.of("le2025-12-31")),
                Map.entry("_sort", List.of("-expiry-date,_id")),
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
        "issueDate, ascending, 'issue-date,_id'",
        "issueDate, descending, '-issue-date,_id'",
        "expiryDate, ascending, 'expiry-date,_id'",
        "expiryDate, descending, '-expiry-date,_id'",
        "issueDate, -, 'issue-date,_id'",
        "-, ascending, -",
        "-, -, -"})
    void theSortIsForwardedToTheSource(String sortBy, String sortOrder, String sent)
            throws Exception {
        MockHttpServletRequestBuilder sorted = records(TAN_WEI_MING);
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
     * the source is called: a column but the two dates, a direction but ascending or descending,
     * a code outside its closed set — a label, another case, a Java constant's name, another
     * spelling or another set's code, an FFI Outcome's among them — and a date that is no
     * calendar date {@code YYYY-MM-DD}. NUMC has no Status, Higher Authority or Submitting MC,
     * so a filter on one is no parameter the read takes, and is not passed on (below).
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        sortBy         | type
        sortBy         | outcome
        sortBy         | issue-date
        sortBy         | ExpiryDate
        sortOrder      | asc
        sortOrder      | DESCENDING
        type           | Diving Duties
        type           | DIVING
        type           | HYPERBARIC_CHAMBER
        type           | ippt
        outcome        | WAIVER GRANTED
        outcome        | waiver_granted
        outcome        | WAIVER_REJECTED
        outcome        | request-waiver
        issueDateFrom  | 2024-02-30
        issueDateTo    | 01/02/2024
        expiryDateFrom | 2025-1-1
        expiryDateTo   | 2025-12-31T00:00:00
        expiryDateTo   | yesterday
        """)
    void aFilterOrSortTheReadCannotTakeIsRefused(String parameter, String value)
            throws Exception {
        MockHttpServletResponse response =
                reading(records(TAN_WEI_MING).param(parameter, value));

        assertThat(body(response)).isEqualTo(apiError(400, "invalid-request",
                "The request could not be read or did not pass validation."));
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    /** One code outside its set among good ones refuses the whole read. */
    @Test
    void oneCodeOutsideItsSetAmongOthersIsRefused() throws Exception {
        assertThat(reading(records(TAN_WEI_MING).param("outcome", "fit")
                .param("outcome", "request-waiver")).getStatus()).isEqualTo(400);
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @CsvSource({
        "issueDateFrom, issueDateTo, 2024-01-02, 2024-01-01",
        "expiryDateFrom, expiryDateTo, 2025-12-31, 2025-01-01"})
    void aFromDateAfterItsToDateIsRefused(String fromParameter, String toParameter, String from,
            String to) throws Exception {
        MockHttpServletResponse response = reading(records(TAN_WEI_MING)
                .param(fromParameter, from).param(toParameter, to));

        assertThat(body(response)).isEqualTo(apiError(400, "invalid-request",
                "The request could not be read or did not pass validation."));
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    /** A range one day long is a from-date equal to its to-date, and is no refusal. */
    @Test
    void aRangeOfOneDayIsRead() throws Exception {
        assertThat(reading(records(TAN_WEI_MING)
                .param("issueDateFrom", "2024-01-10").param("issueDateTo", "2024-01-10"))
                .getStatus()).isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING))
                .containsEntry("issue-date", List.of("ge2024-01-10", "le2024-01-10"));
    }

    /** A blank code, as an empty field sends one, is no filter on its column. */
    @ParameterizedTest
    @ValueSource(strings = {"type", "outcome"})
    void aBlankCodeIsNoFilter(String parameter) throws Exception {
        assertThat(reading(records(TAN_WEI_MING).param(parameter, "")).getStatus())
                .isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING)).isEqualTo(Map.of(
                "_offset", List.of("0"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /**
     * A NUMC Record has no Status, Higher Authority or Submitting MC: a filter on one is no
     * parameter the read takes, and nothing of it is passed on.
     */
    @Test
    void aFilterOnAColumnNumcDoesNotHaveIsNotSentToTheSource() throws Exception {
        assertThat(reading(records(TAN_WEI_MING).param("status", "active")
                .param("higherAuthority", "camo").param("submittingMc", "kranji-camp"))
                .getStatus()).isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING)).isEqualTo(Map.of(
                "_offset", List.of("0"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /** The ids of a page's NUMC Records, in its order, beside its total. */
    private List<Object> idsAndTotal(MockHttpServletResponse response) throws Exception {
        JsonNode page = body(response);
        return List.of(page.at("/totalResults").asInt(),
                page.at("/records").valueStream().map(record -> record.at("/id").asString())
                        .toList());
    }

    /** A canned sorted read, answered in the order the source sorted it: latest Expiry Date first. */
    @Test
    void aSortedReadIsAnsweredInTheSourcesSortedOrder() throws Exception {
        assertThat(idsAndTotal(reading(records(TAN_WEI_MING).param("sortBy", "expiryDate")
                .param("sortOrder", "descending"))))
                .containsExactly(4, List.of("numc-tan-wei-ming-2", "numc-tan-wei-ming-3",
                        "numc-tan-wei-ming-1", "numc-tan-wei-ming-4"));
    }

    /** A canned filtered read, answered as the source filtered it. */
    @Test
    void aFilteredReadIsAnsweredAsTheSourceFilteredIt() throws Exception {
        assertThat(idsAndTotal(reading(records("dev-patient-05")
                .param("outcome", "waiver-granted").param("outcome", "waiver-rejected"))))
                .containsExactly(3, List.of("numc-tan-hui-min-3", "numc-tan-hui-min-4",
                        "numc-tan-hui-min-7"));
    }

    @Test
    void aFilterTheSourceMatchesNothingWithIsAnEmptyPage() throws Exception {
        assertThat(body(reading(records("dev-patient-05").param("expiryDateTo", "2022-12-31"))))
                .isEqualTo(json.readTree("""
                        {"totalResults": 0, "startIndex": 1, "records": []}"""));
    }

    @Test
    void aPatientWithNoRecordsIsAnEmptyPageOfNone() throws Exception {
        assertThat(body(reading(records("erXuFYUfucBZaryVksYEcMg3")))).isEqualTo(json.readTree("""
                {"totalResults": 0, "startIndex": 1, "records": []}"""));
    }

    /** The source holding no patient under the id is the application API's {@code 404}. */
    @Test
    void aPatientTheSourceDoesNotHoldIsNotFound() throws Exception {
        MockHttpServletResponse response = reading(records("not-a-uuid"));

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
        MockHttpServletResponse response = reading(records(patientId));

        assertThat(body(response)).isEqualTo(apiError(400, "invalid-request",
                "The request could not be read or did not pass validation."));
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void aPagingValueThatIsNoIntegerIsRefused() throws Exception {
        assertThat(reading(records(TAN_WEI_MING).param("startIndex", "two")).getStatus())
                .isEqualTo(400);
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    /**
     * The source failing — here, a page whose record has a NUMC Outcome outside its set — is the
     * application API's {@code 502}, not a FHIR outcome, quoting nothing of the failure.
     */
    @Test
    void aFailingSourceIsABadGatewayApiError() throws Exception {
        MockHttpServletResponse response = reading(records("fault-numc-unknown-outcome"));

        assertThat(List.of(response.getStatus(), response.getContentType(), body(response)))
                .containsExactly(502, "application/json", apiError(502, "server-error",
                        "The request could not be completed because of a server-side failure."));
    }

    @Test
    void aSourceAnsweringAnErrorIsABadGateway() throws Exception {
        assertThat(reading(records("fault-numc-server-error")).getStatus()).isEqualTo(502);
    }

    /** A row is Restricted data: printed, it names the NUMC Record by id alone. */
    @Test
    void aRowPrintsItsRecordsIdAlone() {
        assertThat(new NumcHistoryPage.Row("numc-tan-wei-ming-1", "hyperbaric-chamber", "fit",
                "2023-10-14", "2024-10-13"))
                .hasToString("Row[id=numc-tan-wei-ming-1]");
    }
}
