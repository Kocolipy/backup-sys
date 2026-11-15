package com.example.backend.patient.infrastructure.http;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.backend.observability.LogEvent.ErrorCategory;
import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.PatientSourceStubs;
import com.example.backend.patient.domain.DateRange;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.NumcFilter;
import com.example.backend.patient.domain.NumcHistory;
import com.example.backend.patient.domain.NumcOutcome;
import com.example.backend.patient.domain.NumcType;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.SortOrder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.RestClient;

/**
 * A patient's NUMC History as the HTTP adapter reads it (ADR 0019, D5), against the patient
 * source's mock in process: the one call it sends — the page, counted by the source, with each
 * filter and the sort as the source's own search parameters — and what a failure of that call
 * is, each NUMC Record's under a reason naming NUMC. What the mock's NUMC Histories hold is
 * {@code PatientStoreContract}'s. What every history shares — the page's call, the statuses that
 * are no page, the timeout, the fault stubs they have in common — is
 * {@code PatientSourceHistoriesAdapterTests}'.
 */
class PatientSourceNumcAdapterTests {

    @RegisterExtension
    static final WireMockExtension SOURCE = PatientSourceStubs.extension();

    private static final PageRequest SECOND_PAGE_OF_FIVE = new PageRequest(6, 5);

    /** A patient id no stub file names, for a case's own stub, and which no message may quote. */
    private static final String PROBE_PATIENT = "probe-patient-N3c7";

    private static final String PROBE_PATH = "/Patient/" + PROBE_PATIENT + "/numc";

    /** One NUMC Record as the source answers it, every field there and well-formed. */
    private static final Map<String, String> WELL_FORMED = Map.of(
            "resourceType", "NUMC",
            "id", "probe-numc-W5q1",
            "type", "submariner",
            "outcome", "waiver-granted",
            "issueDate", "2017-02-13",
            "expiryDate", "2018-02-12");

    /** {@link #WELL_FORMED} with {@code field} set to {@code value}, or left out for {@code null}. */
    private static String record(String field, String value) {
        Map<String, String> fields = new TreeMap<>(WELL_FORMED);
        if (value == null) {
            fields.remove(field);
        } else {
            fields.put(field, value);
        }
        StringBuilder json = new StringBuilder("{");
        fields.forEach((name, member) -> json.append(json.length() > 1 ? ", " : "")
                .append('"').append(name).append("\": \"").append(member).append('"'));
        return json.append('}').toString();
    }

    private static String page(String... records) {
        return """
                {"resourceType": "Bundle", "type": "searchset", "total": %d,
                 "entry": [%s]}""".formatted(records.length,
                String.join(", ", Stream.of(records)
                        .map(record -> "{\"resource\": " + record + "}").toList()));
    }

    private static PatientSourceAdapter adapter() {
        return new PatientSourceAdapter(RestClient.builder(), SOURCE.baseUrl());
    }

    private static void answering(String body) {
        SOURCE.stubFor(get(urlPathEqualTo(PROBE_PATH)).atPriority(1).willReturn(okJson(body)));
    }

    /** A fault as its record reports it: its fixed message, its reason and its category. */
    private record Fault(String message, String reason, ErrorCategory category) {
    }

    private static Fault faultReading(PatientSourceAdapter adapter, String patient) {
        PatientSourceException fault = (PatientSourceException) catchThrowable(() ->
                unfiltered(adapter, patient));
        return new Fault(fault.getMessage(), fault.reason(), fault.category());
    }

    private static Fault faultReading(PatientSourceAdapter adapter) {
        return faultReading(adapter, PROBE_PATIENT);
    }

    private static Fault data(String message, String reason) {
        return new Fault(message, reason, ErrorCategory.DATA);
    }

    /** The second page of five of {@code patient}'s NUMC History, unfiltered, in the source's order. */
    private static Optional<NumcHistory> unfiltered(PatientSourceAdapter adapter, String patient) {
        return adapter.findNumcHistory(new PatientId(patient), NumcFilter.NONE,
                HistorySort.SOURCE_ORDER, SECOND_PAGE_OF_FIVE);
    }

    // ---- the call ------------------------------------------------------------------------

    /** The one NUMC History read the source received. */
    private static LoggedRequest sent() {
        return SOURCE.findAll(getRequestedFor(urlPathEqualTo(PROBE_PATH))).getFirst();
    }

    /** The one NUMC History read the source received, every value of each query parameter. */
    private static Map<String, List<String>> sentQuery() {
        Map<String, List<String>> query = new TreeMap<>();
        sent().getQueryParams().forEach((name, value) -> query.put(name, value.values()));
        return query;
    }

    /** The second page of five of the probe patient's NUMC History, filtered and sorted so. */
    private static void reading(NumcFilter filter, HistorySort sort) {
        answering(page());
        adapter().findNumcHistory(new PatientId(PROBE_PATIENT), filter, sort, SECOND_PAGE_OF_FIVE);
    }

    /**
     * Each sort is the source's {@code _sort} on its own search parameter — {@code issue-date}
     * or {@code expiry-date}, descending with a leading {@code -} — its ties broken by id.
     */
    @ParameterizedTest
    @CsvSource({
        "issueDate, ASCENDING, 'issue-date,_id'",
        "issueDate, DESCENDING, '-issue-date,_id'",
        "expiryDate, ASCENDING, 'expiry-date,_id'",
        "expiryDate, DESCENDING, '-expiry-date,_id'"})
    void aSortIsTheSourcesOwnOnItsSearchParameter(String field, SortOrder order,
            String sorted) {
        reading(NumcFilter.NONE,
                new HistorySort(HistorySort.column(NumcHistory.DATE_COLUMNS, field), order));

        assertThat(sentQuery()).containsEntry("_sort", List.of(sorted));
    }

    /**
     * A filter on a closed set is its search parameter once, its codes joined by commas — FHIR's
     * OR — in the set's own order, whatever order they were asked in.
     */
    @Test
    void eachSetOfCodesIsOneParameterItsCodesJoinedByCommas() {
        reading(new NumcFilter(Set.of(NumcType.DIVING, NumcType.SUBMARINER),
                Set.of(NumcOutcome.WAIVER_REJECTED, NumcOutcome.FIT, NumcOutcome.UNFIT),
                DateRange.any(), DateRange.any()), HistorySort.SOURCE_ORDER);

        assertThat(sentQuery()).isEqualTo(Map.of(
                "type", List.of("submariner,diving"),
                "outcome", List.of("fit,unfit,waiver-rejected"),
                "_offset", List.of("5"),
                "_count", List.of("5"),
                "_total", List.of("accurate")));
    }

    /** The comma between two codes reaches the source as a comma, FHIR's OR, not escaped. */
    @Test
    void theCommaBetweenCodesIsSentAsItIs() {
        reading(new NumcFilter(Set.of(), Set.of(NumcOutcome.WAIVER_GRANTED, NumcOutcome.FIT),
                DateRange.any(), DateRange.any()), HistorySort.SOURCE_ORDER);

        assertThat(sent().getUrl().split("[?&]")).contains("outcome=fit,waiver-granted");
    }

    /**
     * A date range is its search parameter once for each end it has — {@code ge} the from-date and
     * {@code le} the to-date, both inclusive — the Issue Date's {@code issue-date} and the Expiry
     * Date's {@code expiry-date}.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "-", delimiter = '|', value = {
        "2024-01-01 | 2024-12-31 | ge2024-01-01,le2024-12-31",
        "2024-01-01 | -          | ge2024-01-01",
        "-          | 2024-12-31 | le2024-12-31"})
    void aDateRangeIsItsSearchParameterForEachEnd(LocalDate from, LocalDate to, String ends) {
        reading(new NumcFilter(null, null, new DateRange<>(from, to), DateRange.any()),
                HistorySort.SOURCE_ORDER);
        Map<String, List<String>> issued = sentQuery();
        SOURCE.resetRequests();
        reading(new NumcFilter(null, null, DateRange.any(), new DateRange<>(from, to)),
                HistorySort.SOURCE_ORDER);
        Map<String, List<String>> expiring = sentQuery();

        assertThat(Arrays.asList(issued.get("issue-date"), issued.containsKey("expiry-date"),
                expiring.get("expiry-date"), expiring.containsKey("issue-date")))
                .containsExactly(List.of(ends.split(",")), false, List.of(ends.split(",")),
                        false);
    }

    /** Every filter and a sort at once, each sent as its own. */
    @Test
    void everyFilterNarrowsTheReadAtOnceWithTheSort() {
        reading(new NumcFilter(Set.of(NumcType.HYPERBARIC_CHAMBER),
                        Set.of(NumcOutcome.WAIVER_GRANTED),
                        new DateRange<>(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31)),
                        new DateRange<>(LocalDate.of(2024, 1, 1), null)),
                new HistorySort(NumcHistory.ISSUE_DATE, SortOrder.DESCENDING));

        assertThat(sentQuery()).isEqualTo(Map.ofEntries(
                Map.entry("type", List.of("hyperbaric-chamber")),
                Map.entry("outcome", List.of("waiver-granted")),
                Map.entry("issue-date", List.of("ge2023-01-01", "le2023-12-31")),
                Map.entry("expiry-date", List.of("ge2024-01-01")),
                Map.entry("_sort", List.of("-issue-date,_id")),
                Map.entry("_offset", List.of("5")),
                Map.entry("_count", List.of("5")),
                Map.entry("_total", List.of("accurate"))));
    }

    // ---- a failure of the source ---------------------------------------------------------

    /**
     * Each fault stub of a NUMC History that is its own, by the failure it serves: the stubs every history serves
     * are {@code PatientSourceHistoriesAdapterTests}'.
     */
    static Stream<Arguments> faultStubs() {
        return Stream.of(
                Arguments.of("fault-numc-no-id",
                        data("The patient source's NUMC Record has no id", "numc-no-id")),
                Arguments.of("fault-numc-invalid-id", data(
                        "The patient source's NUMC Record's id is not a FHIR id",
                        "numc-invalid-id")),
                Arguments.of("fault-numc-no-outcome", data(
                        "The patient source's NUMC Record has no outcome", "no-numc-outcome")),
                Arguments.of("fault-numc-unknown-type", data(
                        "The patient source's NUMC Record's type is not a NUMC Type",
                        "unknown-numc-type")),
                Arguments.of("fault-numc-unknown-outcome", data(
                        "The patient source's NUMC Record's outcome is not a NUMC Outcome",
                        "unknown-numc-outcome")),
                Arguments.of("fault-numc-expiry-date-not-to-the-day", data(
                        "The patient source's NUMC Record's expiry date is not to the day",
                        "numc-expiry-date-not-to-the-day")));
    }

    @ParameterizedTest
    @MethodSource("faultStubs")
    void eachFaultStubIsTheFaultItServes(String patient, Fault expected) {
        assertThat(faultReading(adapter(), patient)).isEqualTo(expected);
    }

    /** The cases above, the shared stubs and the slow one are every NUMC fault stub the files hold. */
    @Test
    void everyNumcFaultStubIsReadAbove() {
        Stream<String> covered = Stream.concat(
                PatientSourceHistoriesAdapterTests.sharedFaultStubs("numc").stream(),
                faultStubs().map(arguments -> (String) arguments.get()[0]));

        assertThat(PatientSourceStubs.numcFaults()).extracting(PatientSourceStubs.FaultStub::id)
                .containsExactlyInAnyOrderElementsOf(covered.toList());
    }

    /** Every element is required: a record missing any one is a fault naming it, and NUMC. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        id         | numc-no-id          | The patient source's NUMC Record has no id
        type       | no-numc-type        | The patient source's NUMC Record has no type
        outcome    | no-numc-outcome     | The patient source's NUMC Record has no outcome
        issueDate  | no-numc-issue-date  | The patient source's NUMC Record has no issue date
        expiryDate | no-numc-expiry-date | The patient source's NUMC Record has no expiry date
        """)
    void aRecordMissingAnElementIsAFault(String field, String reason, String message) {
        answering(page(record(field, null)));

        assertThat(faultReading(adapter())).isEqualTo(data(message, reason));
    }

    /**
     * A code outside its closed set is a fault naming the set: a code is matched exactly, so one
     * in another case, a label in place of the code, or another set's code — an FFI Outcome's —
     * is outside it too.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        type    | Submariner Duties | unknown-numc-type    | The patient source's NUMC Record's type is not a NUMC Type
        type    | SUBMARINER        | unknown-numc-type    | The patient source's NUMC Record's type is not a NUMC Type
        type    | ippt              | unknown-numc-type    | The patient source's NUMC Record's type is not a NUMC Type
        outcome | WAIVER GRANTED    | unknown-numc-outcome | The patient source's NUMC Record's outcome is not a NUMC Outcome
        outcome | waiver_granted    | unknown-numc-outcome | The patient source's NUMC Record's outcome is not a NUMC Outcome
        outcome | request-waiver    | unknown-numc-outcome | The patient source's NUMC Record's outcome is not a NUMC Outcome
        """)
    void aCodeOutsideItsClosedSetIsAFault(String field, String code, String reason,
            String message) {
        answering(page(record(field, code)));

        assertThat(faultReading(adapter())).isEqualTo(data(message, reason));
    }

    /** Each date must be a calendar date to the day: a month, a time or no day the calendar has is a fault. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        issueDate  | 2017-02             | numc-issue-date-not-to-the-day  | The patient source's NUMC Record's issue date is not to the day
        issueDate  | 2017-02-13T00:00:00 | numc-issue-date-not-to-the-day  | The patient source's NUMC Record's issue date is not to the day
        expiryDate | 2018                | numc-expiry-date-not-to-the-day | The patient source's NUMC Record's expiry date is not to the day
        expiryDate | 2018-02-30          | numc-expiry-date-not-to-the-day | The patient source's NUMC Record's expiry date is not to the day
        """)
    void aDateNotToTheDayIsAFault(String field, String date, String reason, String message) {
        answering(page(record(field, date)));

        assertThat(faultReading(adapter())).isEqualTo(data(message, reason));
    }

}
