package com.example.backend.patient.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.example.backend.patient.PatientSourceStubs;
import com.example.backend.patient.application.MedicationHistoryService;
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
 * {@code GET /api/patient-medication/{patientId}} as the web adapter answers it, through the HTTP
 * adapter over the patient source's mock — the stub files the local mock serves
 * ({@link PatientSourceStubs}) — with the app-wide exception handler its failures go to: one page
 * of the patient's Medication History as plain JSON, its codes as the source sent them, its
 * date-times ISO-8601 with their offsets, and no field at all for an optional element the
 * source lacks, with each filter and the sort the read takes passed to the source, or the
 * application API's {@code ApiError}. The filter chain is not in this
 * MockMvc: the Permission and its backstop are {@code AuthorizationContractTests}', the paging
 * coercion {@code PagingContractTests}', and what is logged and audited over the real chain
 * {@code MedicationHistoryIntegrationTests}'.
 */
class PatientMedicationControllerTests {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    private static final String TAN_WEI_MING = "eq081-VQEgP8drUUqCWzHfw3";

    private final JsonMapper json = JsonMapper.builder().build();

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new PatientMedicationController(new MedicationHistoryService(
                        PatientSourceAdapters.reading(SOURCE.baseUrl()))))
                .setControllerAdvice(new ApiExceptionHandler(new ErrorNamespaces(List.of())))
                .build();
    }

    private MockHttpServletResponse reading(MockHttpServletRequestBuilder read) throws Exception {
        return mvc.perform(read).andReturn().getResponse();
    }

    private static MockHttpServletRequestBuilder records(String patientId) {
        return request(HttpMethod.GET, "/api/patient-medication/" + patientId)
                .param("count", "5");
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString());
    }

    private JsonNode apiError(int status, String code, String detail) {
        return json.readTree("""
                {"status": %d, "code": "%s", "detail": "%s"}""".formatted(status, code, detail));
    }

    /**
     * Tan Wei Ming's first two Medication Records exactly, of the page of five: each its id, its
     * free text as the source gave it, its two codes and its two date-times with their offset,
     * in the source's order, beside the total the source counted and where the page starts.
     */
    @Test
    void aMedicationHistoryIsAnsweredAsOnePageOfRecordsInTheSourcesOrder() throws Exception {
        MockHttpServletResponse response = reading(records(TAN_WEI_MING));
        JsonNode page = body(response);

        assertThat(List.of(response.getStatus(), response.getContentType(),
                page.at("/totalResults").asInt(), page.at("/startIndex").asInt(),
                page.at("/records").size()))
                .containsExactly(200, "application/json", 6, 1, 5);
        assertThat(List.of(page.at("/records/0"), page.at("/records/1")))
                .containsExactly(json.readTree("""
                        {"id": "medication-tan-wei-ming-1", "medication": "Paracetamol",
                         "dosage": "750 mg", "route": "PO", "frequency": "QDS",
                         "quantity": "30 tablets", "dispenseStatus": "pending",
                         "duration": "5 days", "startDate": "2026-10-18T17:30:00+08:00",
                         "endDate": "2026-10-22T17:30:00+08:00", "dispensary": "nee-soon-camp",
                         "prescribingDoctor": "Dr Lim Wei Jie"}"""),
                        json.readTree("""
                        {"id": "medication-tan-wei-ming-2", "medication": "Amoxicillin",
                         "dosage": "300 mg", "route": "PO", "frequency": "TDS",
                         "quantity": "20 tablets", "dispenseStatus": "pending",
                         "duration": "5 days", "startDate": "2026-10-18T17:31:00+08:00",
                         "endDate": "2026-10-22T17:31:00+08:00", "dispensary": "khatib-camp",
                         "prescribingDoctor": "Dr Lim Wei Jie"}"""));
    }

    /**
     * A record lacking every optional element is answered with none of those fields at all, not
     * {@code null} ones; a date-time the source gave in UTC is answered in UTC.
     */
    @Test
    void anOptionalElementTheSourceLacksIsLeftOut() throws Exception {
        JsonNode records = body(reading(records("dev-patient-05"))).at("/records");

        assertThat(List.of(records.at("/3"), records.at("/0/startDate").asString()))
                .containsExactly(json.readTree("""
                        {"id": "medication-tan-hui-min-4",
                         "medication": "Oral rehydration salts", "dispenseStatus": "dispensed",
                         "startDate": "2024-08-05T11:05:00+08:00",
                         "endDate": "2024-08-05T11:05:00+08:00", "dispensary": "kranji-camp",
                         "prescribingDoctor": "Dr Lim Wei Jie"}"""),
                        "2026-03-01T02:00:00Z");
    }

    /** The second page is the source's, sent from its zero-based offset: the last one of six. */
    @Test
    void theSecondPageIsTheSourcesSecondPage() throws Exception {
        JsonNode page = body(reading(records(TAN_WEI_MING).param("startIndex", "6")));

        assertThat(List.of(page.at("/totalResults").asInt(), page.at("/startIndex").asInt(),
                page.at("/records").valueStream().map(record -> record.at("/id").asString())
                        .toList(), page.at("/records/0/step").asString()))
                .containsExactly(6, 6, List.of("medication-tan-wei-ming-6"), "1");
    }

    /** The one Medication History read the source was sent, every value of each query parameter. */
    private static Map<String, List<String>> sentToTheSource(String patientId) {
        LoggedRequest sent = SOURCE.findAll(
                getRequestedFor(urlPathEqualTo("/Patient/" + patientId + "/medication")))
                .getFirst();
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
                "/api/patient-medication/erXuFYUfucBZaryVksYEcMg3")
                .param("startIndex", "-4").param("count", "0"));

        assertThat(List.of(response.getStatus(), sentToTheSource("erXuFYUfucBZaryVksYEcMg3")))
                .containsExactly(200, Map.of(
                        "_offset", List.of("0"),
                        "_count", List.of("0"),
                        "_total", List.of("accurate")));
    }

    /**
     * Each filter reaches the source as its own search parameter: one case for each request
     * parameter, beside the parameter the source is sent for it. A repeated Dispense Status is one
     * parameter, its codes joined by commas — FHIR's OR — in its closed set's order; each end of a
     * date-time range, a Singapore minute, is the instant it names in UTC, the to-end the first
     * instant after its minute.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        dispenseStatus | pending                  | dispense-status | pending
        dispenseStatus | uncollected,dispensed    | dispense-status | dispensed,uncollected
        startDateFrom  | 2026-10-18T17:31         | start-date      | ge2026-10-18T09:31:00Z
        startDateTo    | 2026-10-18T17:30         | start-date      | lt2026-10-18T09:31:00Z
        endDateFrom    | 2025-01-01T00:00         | end-date        | ge2024-12-31T16:00:00Z
        endDateTo      | 2025-12-31T23:59         | end-date        | lt2025-12-31T16:00:00Z
        """)
    void eachFilterIsForwardedToTheSource(String parameter, String value, String forwarded,
            String sent) throws Exception {
        MockHttpServletRequestBuilder filtered = records(TAN_WEI_MING);
        for (String each : value.split(",")) {
            filtered.param(parameter, each);
        }

        assertThat(reading(filtered).getStatus()).isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING)).containsEntry(forwarded, List.of(sent));
    }

    /** Every filter at once reaches the source, combined, with the sort. */
    @Test
    void everyFilterAndTheSortAreForwardedTogether() throws Exception {
        assertThat(reading(records(TAN_WEI_MING).param("dispenseStatus", "dispensed")
                .param("startDateFrom", "2025-01-01T00:00").param("startDateTo", "2026-10-18T17:30")
                .param("endDateTo", "2026-12-31T23:59")
                .param("sortBy", "endDate").param("sortOrder", "descending")).getStatus())
                .isEqualTo(200);

        assertThat(sentToTheSource(TAN_WEI_MING)).isEqualTo(Map.ofEntries(
                Map.entry("dispense-status", List.of("dispensed")),
                Map.entry("start-date", List.of("ge2024-12-31T16:00:00Z", "lt2026-10-18T09:31:00Z")),
                Map.entry("end-date", List.of("lt2026-12-31T16:00:00Z")),
                Map.entry("_sort", List.of("-end-date")),
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
        "startDate, -, start-date",
        "-, descending, -",
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
     * the source is called: a column but the two date-times — another history's dates, the
     * Dispense Status and the Dispensary among them — a direction but ascending or descending, a
     * Dispense Status outside its closed set — a label, another case, a Java constant's name or
     * another set's code — and an end that is no date-time to the minute
     * {@code YYYY-MM-DDTHH:mm}: a date alone, seconds, an offset, or a day or minute the
     * calendar or the clock has not.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        sortBy         | issueDate
        sortBy         | dispenseStatus
        sortBy         | dispensary
        sortBy         | start-date
        sortBy         | StartDate
        sortOrder      | asc
        sortOrder      | DESCENDING
        dispenseStatus | PENDING
        dispenseStatus | Dispensed
        dispenseStatus | UNCOLLECTED
        dispenseStatus | collected
        dispenseStatus | fit
        startDateFrom  | 2026-10-18
        startDateTo    | 2026-10-18T17:30:00
        endDateFrom    | 2026-10-18T17:30+08:00
        endDateTo      | 2026-10-18T09:30Z
        endDateTo      | 2026-02-30T10:00
        startDateFrom  | 2026-10-18T24:00
        startDateTo    | 18/10/2026 17:30
        """)
    void aFilterOrSortTheReadCannotTakeIsRefused(String parameter, String value)
            throws Exception {
        MockHttpServletResponse response =
                reading(records(TAN_WEI_MING).param(parameter, value));

        assertThat(body(response)).isEqualTo(apiError(400, "invalid-request",
                "The request could not be read or did not pass validation."));
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    /** One Dispense Status outside its set among good ones refuses the whole read. */
    @Test
    void oneCodeOutsideItsSetAmongOthersIsRefused() throws Exception {
        assertThat(reading(records(TAN_WEI_MING).param("dispenseStatus", "pending")
                .param("dispenseStatus", "collected")).getStatus()).isEqualTo(400);
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @CsvSource({
        "startDateFrom, startDateTo, 2026-10-18T17:31, 2026-10-18T17:30",
        "endDateFrom, endDateTo, 2026-01-01T00:00, 2025-12-31T23:59"})
    void aFromEndAfterItsToEndIsRefused(String fromParameter, String toParameter, String from,
            String to) throws Exception {
        MockHttpServletResponse response = reading(records(TAN_WEI_MING)
                .param(fromParameter, from).param(toParameter, to));

        assertThat(body(response)).isEqualTo(apiError(400, "invalid-request",
                "The request could not be read or did not pass validation."));
        SOURCE.verify(0, anyRequestedFor(anyUrl()));
    }

    /** A range one minute long is a from-end equal to its to-end, and is no refusal. */
    @Test
    void aRangeOfOneMinuteIsRead() throws Exception {
        assertThat(reading(records(TAN_WEI_MING)
                .param("startDateFrom", "2026-10-18T17:30").param("startDateTo", "2026-10-18T17:30"))
                .getStatus()).isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING)).containsEntry("start-date",
                List.of("ge2026-10-18T09:30:00Z", "lt2026-10-18T09:31:00Z"));
    }

    /** A blank Dispense Status, as an empty field sends one, is no filter on its column. */
    @Test
    void aBlankDispenseStatusIsNoFilter() throws Exception {
        assertThat(reading(records(TAN_WEI_MING).param("dispenseStatus", "")).getStatus())
                .isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING)).isEqualTo(Map.of(
                "_offset", List.of("0"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /**
     * The Dispensary and the free text take no filter, and a Medication Record has no Issue
     * Date, Outcome or Status: a filter on one is no parameter the read takes, and nothing of it
     * is passed on.
     */
    @Test
    void aFilterOnAColumnMedicationDoesNotFilterIsNotSentToTheSource() throws Exception {
        assertThat(reading(records(TAN_WEI_MING).param("dispensary", "khatib-camp")
                .param("medication", "Paracetamol").param("prescribingDoctor", "Dr Lim Wei Jie")
                .param("issueDateFrom", "2024-01-01").param("outcome", "fit")
                .param("status", "active")).getStatus()).isEqualTo(200);
        assertThat(sentToTheSource(TAN_WEI_MING)).isEqualTo(Map.of(
                "_offset", List.of("0"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /** The ids of a page's Medication Records, in its order, beside its total. */
    private List<Object> idsAndTotal(MockHttpServletResponse response) throws Exception {
        JsonNode page = body(response);
        return List.of(page.at("/totalResults").asInt(),
                page.at("/records").valueStream().map(record -> record.at("/id").asString())
                        .toList());
    }

    /**
     * A canned sorted read, answered in the order the source sorted it by the whole date-time:
     * latest Start Date first, the record a minute later ahead of the two tied.
     */
    @Test
    void aSortedReadIsAnsweredInTheSourcesSortedOrder() throws Exception {
        assertThat(idsAndTotal(reading(records(TAN_WEI_MING).param("sortBy", "startDate")
                .param("sortOrder", "descending"))))
                .containsExactly(6, List.of("medication-tan-wei-ming-2",
                        "medication-tan-wei-ming-1", "medication-tan-wei-ming-3",
                        "medication-tan-wei-ming-4", "medication-tan-wei-ming-5"));
    }

    /** A canned filtered read, answered as the source filtered it: a to-minute kept whole. */
    @Test
    void aFilteredReadIsAnsweredAsTheSourceFilteredIt() throws Exception {
        assertThat(idsAndTotal(reading(records("dev-patient-05")
                .param("startDateTo", "2025-07-14T16:20"))))
                .containsExactly(3, List.of("medication-tan-hui-min-2",
                        "medication-tan-hui-min-3", "medication-tan-hui-min-4"));
    }

    @Test
    void aFilterTheSourceMatchesNothingWithIsAnEmptyPage() throws Exception {
        assertThat(body(reading(records("dev-patient-05").param("endDateTo", "2023-12-31T23:59"))))
                .isEqualTo(json.readTree("""
                        {"totalResults": 0, "startIndex": 1, "records": []}"""));
    }

    @Test
    void aPatientWithNoRecordsIsAnEmptyPageOfNone() throws Exception {
        assertThat(body(reading(records("erXuFYUfucBZaryVksYEcMg3"))))
                .isEqualTo(json.readTree("""
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
     * The source failing — here, a page whose record ends before it starts — is the application
     * API's {@code 502}, not a FHIR outcome, quoting nothing of the failure.
     */
    @Test
    void aFailingSourceIsABadGatewayApiError() throws Exception {
        MockHttpServletResponse response = reading(records("fault-medication-end-before-start"));

        assertThat(List.of(response.getStatus(), response.getContentType(), body(response)))
                .containsExactly(502, "application/json", apiError(502, "server-error",
                        "The request could not be completed because of a server-side failure."));
    }

    @Test
    void aSourceAnsweringAnErrorIsABadGateway() throws Exception {
        assertThat(reading(records("fault-medication-server-error")).getStatus()).isEqualTo(502);
    }

    /** A row is Restricted data: printed, it names the Medication Record by id alone. */
    @Test
    void aRowPrintsItsRecordsIdAlone() {
        assertThat(new MedicationHistoryPage.Row("medication-tan-wei-ming-1", "Paracetamol",
                "750 mg", "PO", "QDS", null, "30 tablets", "pending", "5 days",
                "2026-10-18T17:30:00+08:00", "2026-10-22T17:30:00+08:00", "nee-soon-camp",
                "Dr Lim Wei Jie"))
                .hasToString("Row[id=medication-tan-wei-ming-1]");
    }
}
