package com.example.backend.patient.infrastructure.http;

import com.example.backend.observability.InvalidSourceFields;
import com.example.backend.observability.SourceRead;
import com.example.backend.observability.OutboundCallFailure;
import com.example.backend.observability.RedactedFaultException;
import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.domain.ArmcFilter;
import com.example.backend.patient.domain.ArmcHistory;
import com.example.backend.patient.domain.FfiFilter;
import com.example.backend.patient.domain.FfiHistory;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.HspFilter;
import com.example.backend.patient.domain.HspHistory;
import com.example.backend.patient.domain.MedicationFilter;
import com.example.backend.patient.domain.MedicationHistory;
import com.example.backend.patient.domain.NumcFilter;
import com.example.backend.patient.domain.NumcHistory;
import com.example.backend.patient.domain.Patient;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.PatientMatch;
import com.example.backend.patient.domain.PatientMatches;
import com.example.backend.patient.domain.PatientFilter;
import com.example.backend.patient.domain.PatientQuery;
import com.example.backend.patient.domain.PatientStore;
import com.example.backend.patient.domain.ReceivedRecord;
import com.example.backend.patient.infrastructure.http.PatientSourceException.Failure;
import com.example.backend.patient.infrastructure.http.SourceQuery.Ties;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adapts the domain's {@link PatientStore} port onto the in-house patient source (ADR 0014):
 * {@code GET {base}/Patient/{id}} for a read by id, {@code GET {base}/Patient?…} for a
 * Patient search ({@link #search}, ADR 0018), {@code GET {base}/Patient/{id}/ffi?…} for a
 * patient's FFI History ({@link #findFfiHistory}, ADR 0019), and
 * {@code GET {base}/Patient/{id}/numc?…} for a patient's NUMC History ({@link #findNumcHistory},
 * ADR 0019, D5), {@code GET {base}/Patient/{id}/armc?…} for a patient's ARMC History
 * ({@link #findArmcHistory}, ADR 0019, D6), {@code GET {base}/Patient/{id}/hsp?…} for a
 * patient's HSP History ({@link #findHspHistory}, ADR 0019), and
 * {@code GET {base}/Patient/{id}/medication?…} for a patient's Medication History
 * ({@link #findMedicationHistory}, ADR 0019, D7). The source is assumed to answer FHIR R4 and to need
 * no authorisation, so the call carries no credential of any kind. The patient's id is its EHR's
 * FHIR id, sent unchanged.
 *
 * <ul>
 *   <li><b>Found</b>: the {@code 200}'s R4 {@code Patient} under the id asked for, copied into
 *       the domain's as received ({@link R4Patient}, ADR 0020).
 *   <li><b>Not found</b>: a {@code 404} is no patient under the id, the ordinary answer the SPA
 *       reads as "No patient in context".
 *   <li><b>Anything else</b> — another status, no answer within the timeouts, a connection lost
 *       before the answer was in, the body's included, a body that is not a Patient under the id
 *       asked for — is a {@link PatientSourceException}, a fault answered
 *       {@code 502}, naming the failure or the broken rule, whose message and cause carry neither
 *       the id nor anything of the body.
 * </ul>
 *
 * <p>No Patient is cached, in memory or in Redis: every read is a call to the source. The client is
 * built from Spring Boot's {@link RestClient.Builder}, so each call is observed as every client's
 * is ({@code http.client.requests}, under the URI template, never the id), on the JDK client with
 * redirects off and the timeouts below: a fixed connect timeout, and a read timeout a deployment
 * may set ({@code APP_PATIENT_SOURCE_READ_TIMEOUT}). Nothing here logs: a Patient read by id, a
 * Patient search, and an FFI, a NUMC, an ARMC, an HSP and a Medication History, shown as received
 * (ADR 0020), hand the paths of the fields their answer held in the wrong format — and a search,
 * of its dropped matches and its entries that are no Patient — to {@link InvalidSourceFields},
 * which reports them.
 */
@Component
class PatientSourceAdapter implements PatientStore {

    /** How long a connection to the source may take. */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /**
     * How long the source may take to answer once connected, when
     * {@code APP_PATIENT_SOURCE_READ_TIMEOUT} is unset.
     */
    static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);

    private static final MediaType FHIR_JSON = MediaType.parseMediaType("application/fhir+json");

    private static final Set<String> SCHEMES = Set.of("http", "https");

    /** The source's paging, which every search asks for alike. */
    private static final String SEARCH_PAGE = "&" + SourceSearchset.PAGE_QUERY;

    /** A searchset of Patients, a search's answer. */
    private static final ParameterizedTypeReference<SourceSearchset<R4Patient>> PATIENTS =
            new ParameterizedTypeReference<>() {
            };

    /**
     * A patient's FFI History, a page at a time (ADR 0019), before the filters and the sort a read
     * adds to it.
     */
    static final String FFI_HISTORY = "/Patient/{id}/ffi?" + SourceSearchset.PAGE_QUERY;

    /** A searchset of FFIs, an FFI History's answer. */
    private static final ParameterizedTypeReference<SourceSearchset<SourceFfi>> FFIS =
            new ParameterizedTypeReference<>() {
            };

    /**
     * A patient's NUMC History, a page at a time (ADR 0019), before the filters and the sort a
     * read adds to it.
     */
    static final String NUMC_HISTORY = "/Patient/{id}/numc?" + SourceSearchset.PAGE_QUERY;

    /** A searchset of NUMC Records, a NUMC History's answer. */
    private static final ParameterizedTypeReference<SourceSearchset<SourceNumc>> NUMC_RECORDS =
            new ParameterizedTypeReference<>() {
            };

    /**
     * A patient's ARMC History, a page at a time (ADR 0019, D6), before the filters and the sort a
     * read adds to it.
     */
    static final String ARMC_HISTORY = "/Patient/{id}/armc?" + SourceSearchset.PAGE_QUERY;

    /** A searchset of ARMC Boards, an ARMC History's answer. */
    private static final ParameterizedTypeReference<SourceSearchset<SourceArmc>> ARMC_BOARDS =
            new ParameterizedTypeReference<>() {
            };

    /**
     * A patient's HSP History, a page at a time (ADR 0019), before the filters and the sort a read
     * adds to it.
     */
    static final String HSP_HISTORY = "/Patient/{id}/hsp?" + SourceSearchset.PAGE_QUERY;

    /** A searchset of HSP Records, an HSP History's answer. */
    private static final ParameterizedTypeReference<SourceSearchset<SourceHsp>> HSP_RECORDS =
            new ParameterizedTypeReference<>() {
            };

    /**
     * A patient's Medication History, a page at a time (ADR 0019, D7), before the filters and the
     * sort a read adds to it.
     */
    static final String MEDICATION_HISTORY = "/Patient/{id}/medication?"
            + SourceSearchset.PAGE_QUERY;

    /** A searchset of Medication Records, a Medication History's answer. */
    private static final ParameterizedTypeReference<SourceSearchset<SourceMedication>>
            MEDICATION_RECORDS = new ParameterizedTypeReference<>() {
            };

    /** A name search: FHIR's {@code name:contains}, matched as the source decides. */
    static final String SEARCH_BY_NAME = "/Patient?name:contains={name}";

    /** An NRIC search: the identifier under the NRIC NamingSystem, exactly. */
    static final String SEARCH_BY_NRIC = "/Patient?identifier={identifier}";

    private final RestClient source;

    /** The client's read timeout, by which a body read that failed is told timed out. */
    private final Duration readTimeout;

    /** Where a response's invalid fields are reported: one WARN, and a count for each. */
    private final InvalidSourceFields invalidFields;

    /**
     * @param baseUrl     the source's FHIR base, {@code APP_PATIENT_SOURCE_BASE_URL}: required,
     *                    with no default, so a deployment that forgot it fails to start
     * @param readTimeout {@code APP_PATIENT_SOURCE_READ_TIMEOUT}, as {@link #readTimeout} reads it
     * @param meters      where an answer's invalid fields are counted ({@link InvalidSourceFields})
     */
    @Autowired
    PatientSourceAdapter(RestClient.Builder builder,
            @Value("${app.patient-source.base-url}") String baseUrl,
            @Value("${app.patient-source.read-timeout:}") String readTimeout,
            MeterRegistry meters) {
        this(builder, baseUrl, CONNECT_TIMEOUT, readTimeout(readTimeout), meters);
    }

    /** An adapter on the source at {@code baseUrl}, under the default timeouts. */
    PatientSourceAdapter(RestClient.Builder builder, String baseUrl, MeterRegistry meters) {
        this(builder, baseUrl, CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, meters);
    }

    PatientSourceAdapter(RestClient.Builder builder, String baseUrl, Duration connectTimeout,
            Duration readTimeout, MeterRegistry meters) {
        JdkClientHttpRequestFactory requests =
                new JdkClientHttpRequestFactory(httpClient(connectTimeout));
        requests.setReadTimeout(readTimeout);
        this.readTimeout = readTimeout;
        this.invalidFields = new InvalidSourceFields(meters);
        this.source = builder
                .baseUrl(base(baseUrl))
                .requestFactory(requests)
                .build();
    }

    /**
     * {@code GET {base}/Patient/{id}}: the Patient under {@code id}, read as received (ADR 0020).
     * A {@code 404} is no patient under the id; the one other good answer is a {@code 200}
     * Patient under exactly this id, and anything else is a fault — a Patient under another id
     * among them, since every read of this patient is made by the id asked for. What its NRIC,
     * official name's text, gender and birth date hold never is: the answer's invalid fields are
     * reported, each by its path in the answer.
     */
    @Override
    public Optional<Patient> findById(PatientId id) {
        return called(() -> source.get()
                .uri("/Patient/{id}", id.value())
                .accept(FHIR_JSON)
                .exchange((request, response) -> {
                    if (!found(response)) {
                        return Optional.<Patient>empty();
                    }
                    R4Patient answer = response.bodyTo(R4Patient.class);
                    Patient patient = answer.toPatient(id);
                    invalidFields.report(SourceRead.PATIENT, answer.invalidFields(patient));
                    return Optional.of(patient);
                }));
    }

    /**
     * {@code GET {base}/Patient?…}: the source's own search, matched, filtered, paged and counted
     * by the source (ADR 0018) — {@code gender} by its FHIR code, from {@code startIndex - 1},
     * {@code count} of them, with an accurate total — and no {@code _sort}: the source's own
     * order is stable, so its pages are consistent. Its one answer is a {@code 200} searchset
     * Bundle; anything else — a {@code 404} included — is a fault, as is a Bundle with no total
     * or holding an entry with no resource. What a match holds never is: it is read as received
     * (ADR 0020), a match with no id in FHIR's grammar dropped and counted, and an entry that is
     * no Patient shown as a match with no Patient ({@link #matchesOf}). The query's text and the
     * filter's value are URI
     * variables, never part of the template, so the call is observed under the template alone:
     * whether the gender filter was asked for, and none of the values.
     */
    @Override
    public PatientMatches search(PatientQuery patientQuery, PatientFilter filter,
            PageRequest page) {
        SourceQuery query = switch (patientQuery) {
            case PatientQuery.ByName name ->
                    new SourceQuery(SEARCH_BY_NAME).variable("name", name.text());
            case PatientQuery.ByNric nric -> new SourceQuery(SEARCH_BY_NRIC)
                    .variable("identifier", Patient.NRIC_SYSTEM + "|" + nric.nric());
        };
        query.paged(page);
        if (filter.gender() != null) {
            query.parameter("gender", "gender", filter.gender().code());
        }
        query.append(SEARCH_PAGE);
        String uri = query.template();
        Map<String, Object> variables = query.variables();
        return called(() -> source.get()
                .uri(uri, variables)
                .accept(FHIR_JSON)
                .exchange((request, response) -> {
                    if (!response.getStatusCode().isSameCodeAs(HttpStatus.OK)) {
                        throw PatientSourceException.answered(response.getStatusCode().value());
                    }
                    return matchesOf(response.bodyTo(PATIENTS).page(Function.identity()));
                }));
    }

    /**
     * A search's page as the service shows it, after its faults are reported: each entry that is
     * a Patient under an id of its own in FHIR's grammar as its match, its fields as received;
     * each entry that is a Patient with no such id dropped and counted, its {@code id} reported;
     * and each entry that is no Patient as a match with no Patient, its {@code resourceType}
     * reported. Every path is the field's in the answer, {@code entry[2].id}, the entry counted
     * from zero, and the total is the source's, unchanged.
     */
    private PatientMatches matchesOf(SourceSearchset.Page<R4Patient> answers) {
        List<PatientMatch> matches = new ArrayList<>();
        List<InvalidSourceFields.Field> faults = new ArrayList<>();
        int dropped = 0;
        for (int entry = 0; entry < answers.entries().size(); entry++) {
            R4Patient answer = answers.entries().get(entry);
            String at = "entry[" + entry + "].";
            if (!answer.isPatient()) {
                matches.add(new PatientMatch.NotAPatient());
                faults.add(new InvalidSourceFields.Field(at + R4Patient.RESOURCE_TYPE,
                        R4Patient.RESOURCE_TYPE));
                continue;
            }
            Optional<PatientId> own = answer.ownId();
            if (own.isEmpty()) {
                dropped++;
                faults.add(new InvalidSourceFields.Field(at + R4Patient.ID, R4Patient.ID));
                continue;
            }
            PatientMatch.OfPatient match = answer.toMatch(own.get());
            matches.add(match);
            for (InvalidSourceFields.Field field : answer.invalidFields(match)) {
                faults.add(new InvalidSourceFields.Field(at + field.path(), field.name()));
            }
        }
        invalidFields.report(SourceRead.SEARCH, faults);
        return new PatientMatches(answers.total(), matches, dropped);
    }

    /**
     * {@code GET {base}/Patient/{id}/ffi?…}: the patient's FFI History, filtered, sorted, paged and
     * counted by the source (ADR 0019) — each set of codes as its search parameter once, its codes
     * joined by commas, FHIR's OR ({@code type}, {@code outcome}, {@code higher-authority},
     * {@code submitting-mc}); the Status as {@code status}; each end of a date range as its search
     * parameter, {@code issue-date} or {@code expiry-date}, {@code ge} the from-date and {@code le}
     * the to-date; and a sort as {@code _sort} on the same date parameters, {@code -} first for
     * descending, ties broken by id. With no column to sort by, no {@code _sort} is sent, and the
     * order is the source's own. A {@code 404} is no patient under the id; the one other good
     * answer is a {@code 200} searchset of FFIs, and anything else is a fault, as is a searchset
     * with no total or an entry that is no FFI. What an FFI's fields hold never is: each is read
     * as received (ADR 0020), and the answer's invalid fields are reported. The id and the
     * filters' values are URI variables, so the call is observed under the template alone: which
     * filters were asked for and the sort, and none of their values.
     */
    @Override
    public Optional<FfiHistory> findFfiHistory(PatientId id, FfiFilter filter, HistorySort sort,
            PageRequest page) {
        SourceQuery query = new SourceQuery(FFI_HISTORY).variable("id", id.value());
        query.paged(page);
        query.anyOf("type", "type", filter.types());
        query.anyOf("outcome", "outcome", filter.outcomes());
        query.narrowing(FfiHistory.ISSUE_DATE, filter.issueDate());
        query.narrowing(FfiHistory.EXPIRY_DATE, filter.expiryDate());
        query.anyOf("higher-authority", "higherAuthority", filter.higherAuthorities());
        query.anyOf("submitting-mc", "submittingMc", filter.submittingMcs());
        if (filter.status() != null) {
            query.parameter("status", "status", filter.status().code());
        }
        query.sorted(sort, Ties.BY_ID);
        return history(query, FFIS, SourceFfi::toFfi,
                reportingInvalidFields(SourceRead.FFI, FfiHistory::new));
    }

    /**
     * {@code GET {base}/Patient/{id}/numc?…}: the patient's NUMC History, filtered, sorted, paged
     * and counted by the source (ADR 0019) — the NUMC Types and the NUMC Outcomes each as its
     * search parameter once, {@code type} and {@code outcome}, its codes joined by commas, FHIR's
     * OR; each end of a date range as its search parameter, {@code issue-date} or
     * {@code expiry-date}, {@code ge} the from-date and {@code le} the to-date; and a sort as
     * {@code _sort} on the same date parameters, {@code -} first for descending, ties broken by
     * id. With no column to sort by, no {@code _sort} is sent, and the order is the source's own.
     * A {@code 404} is no patient under the id; the one other good answer is a {@code 200}
     * searchset of NUMC Records, and anything else is a fault, as is a searchset with no total or
     * an entry that is no NUMC Record the service can hold. The id and the filters' values are
     * URI variables, so the call is observed under the template alone: which filters were asked
     * for and the sort, and none of their values.
     */
    @Override
    public Optional<NumcHistory> findNumcHistory(PatientId id, NumcFilter filter,
            HistorySort sort, PageRequest page) {
        SourceQuery query = new SourceQuery(NUMC_HISTORY).variable("id", id.value());
        query.paged(page);
        query.anyOf("type", "type", filter.types());
        query.anyOf("outcome", "outcome", filter.outcomes());
        query.narrowing(NumcHistory.ISSUE_DATE, filter.issueDate());
        query.narrowing(NumcHistory.EXPIRY_DATE, filter.expiryDate());
        query.sorted(sort, Ties.BY_ID);
        return history(query, NUMC_RECORDS, SourceNumc::toRecord,
                reportingInvalidFields(SourceRead.NUMC, NumcHistory::new));
    }

    /**
     * {@code GET {base}/Patient/{id}/armc?…}: the patient's ARMC History, filtered, sorted, paged
     * and counted by the source (ADR 0019, D6) — the ARMC Outcome as {@code outcome}, its one
     * code; each end of a date range as its search parameter, {@code start-date} or
     * {@code end-date}, {@code ge} the from-date and {@code le} the to-date, the source leaving a
     * board with no End Date out of any End Date range; and a sort as {@code _sort} on the same
     * date parameters, {@code -} first for descending, the column alone: the source breaks ties.
     * With no column to sort by, no {@code _sort} is sent, and the order is the source's own. A
     * {@code 404} is no patient under the id; the one other good answer is a {@code 200}
     * searchset of ARMC Boards, each read as received (ADR 0020), and anything else is a fault, as
     * is a searchset with no total or an entry that is no ARMC Board. The id and the filters'
     * values are URI variables, so the call is observed under the template alone: which filters
     * were asked for and the sort, and none of their values.
     */
    @Override
    public Optional<ArmcHistory> findArmcHistory(PatientId id, ArmcFilter filter,
            HistorySort sort, PageRequest page) {
        SourceQuery query = new SourceQuery(ARMC_HISTORY).variable("id", id.value());
        query.paged(page);
        if (filter.outcome() != null) {
            query.parameter("outcome", "outcome", filter.outcome().code());
        }
        query.narrowing(ArmcHistory.START_DATE, filter.startDate());
        query.narrowing(ArmcHistory.END_DATE, filter.endDate());
        query.sorted(sort, Ties.THE_SOURCES);
        return history(query, ARMC_BOARDS, SourceArmc::toBoard,
                reportingInvalidFields(SourceRead.ARMC, ArmcHistory::new));
    }

    /**
     * {@code GET {base}/Patient/{id}/hsp?…}: the patient's HSP History, filtered, sorted, paged and
     * counted by the source (ADR 0019) — the Outcomes and the Submitting MCs each as its search
     * parameter once, {@code outcome} and {@code submitting-mc}, its codes joined by commas, FHIR's
     * OR; the Status as {@code status}; each end of a date range as its search parameter,
     * {@code start-date} or {@code end-date}, {@code ge} the from-date and {@code le} the to-date;
     * and a sort as {@code _sort} on the same date parameters, {@code -} first for descending, ties
     * broken by id. With no column to sort by, no {@code _sort} is sent, and the order is the
     * source's own. A {@code 404} is no patient under the id; the one other good answer is a
     * {@code 200} searchset of HSP Records, and anything else is a fault, as is a searchset with no
     * total or an entry that is no HSP Record. What a record's fields hold never is: each is read as
     * received (ADR 0020), and the answer's invalid fields are reported. The id and the filters'
     * values are URI variables, so the call is observed under the template alone: which filters
     * were asked for and the sort, and none of their values.
     */
    @Override
    public Optional<HspHistory> findHspHistory(PatientId id, HspFilter filter, HistorySort sort,
            PageRequest page) {
        SourceQuery query = new SourceQuery(HSP_HISTORY).variable("id", id.value());
        query.paged(page);
        query.anyOf("outcome", "outcome", filter.outcomes());
        query.narrowing(HspHistory.START_DATE, filter.startDate());
        query.narrowing(HspHistory.END_DATE, filter.endDate());
        query.anyOf("submitting-mc", "submittingMc", filter.submittingMcs());
        if (filter.status() != null) {
            query.parameter("status", "status", filter.status().code());
        }
        query.sorted(sort, Ties.BY_ID);
        return history(query, HSP_RECORDS, SourceHsp::toRecord,
                reportingInvalidFields(SourceRead.HSP, HspHistory::new));
    }

    /**
     * {@code GET {base}/Patient/{id}/medication?…}: the patient's Medication History, filtered,
     * sorted, paged and counted by the source (ADR 0019, D7) — the Dispense Statuses as
     * {@code dispense-status} once, its codes joined by commas, FHIR's OR; each end of a date-time
     * range as its search parameter, {@code start-date} or {@code end-date}, the instant its
     * Singapore minute names in UTC, {@code ge} the from-minute and {@code lt} the minute after the
     * to-minute ({@link SourceQuery#narrowing}); and a sort as {@code _sort} on the same date
     * parameters, {@code -} first for descending, the column alone: the source breaks ties. With
     * no column to sort by, no {@code _sort} is sent, and the order is the source's own. A
     * {@code 404} is no patient under the id; the one other good answer is a {@code 200}
     * searchset of Medication Records, and anything else is a fault, as is a searchset with no
     * total or an entry that is no Medication Record. What a Medication Record's fields hold
     * never is: each is read as received (ADR 0020), and the answer's invalid fields are
     * reported. The id and the
     * filters' values are URI variables, so the call is observed under the template alone: which
     * filters were asked for and the sort, and none of their values.
     */
    @Override
    public Optional<MedicationHistory> findMedicationHistory(PatientId id,
            MedicationFilter filter, HistorySort sort, PageRequest page) {
        SourceQuery query = new SourceQuery(MEDICATION_HISTORY).variable("id", id.value());
        query.paged(page);
        query.anyOf("dispense-status", "dispenseStatus", filter.dispenseStatuses());
        query.narrowing(MedicationHistory.START_DATE, filter.startDate());
        query.narrowing(MedicationHistory.END_DATE, filter.endDate());
        query.sorted(sort, Ties.THE_SOURCES);
        return history(query, MEDICATION_RECORDS, SourceMedication::toRecord,
                reportingInvalidFields(SourceRead.MEDICATION, MedicationHistory::new));
    }

    /**
     * {@code query}'s answer as one page of a patient's history: empty on a {@code 404}, no
     * patient under the id; on a {@code 200}, the searchset {@code body} reads, each entry read
     * by {@code read}, and its total and entries made the page by {@code page}. Any other status
     * is a fault, as is a searchset with no total or an entry {@code read} refuses.
     */
    private <R, E, H> Optional<H> history(SourceQuery query,
            ParameterizedTypeReference<SourceSearchset<R>> body, Function<R, E> read,
            BiFunction<Integer, List<E>, H> page) {
        String uri = query.template();
        Map<String, Object> variables = query.variables();
        return called(() -> source.get()
                .uri(uri, variables)
                .accept(FHIR_JSON)
                .exchange((request, response) -> {
                    if (!found(response)) {
                        return Optional.<H>empty();
                    }
                    SourceSearchset.Page<E> entries = response.bodyTo(body).page(read);
                    return Optional.of(page.apply(entries.total(), entries.entries()));
                }));
    }

    /**
     * {@code page}, after the invalid fields of its entries are reported as the read {@code read}'s
     * ({@link InvalidSourceFields}): a page of a history shown as received (ADR 0020).
     */
    private <E extends ReceivedRecord, H> BiFunction<Integer, List<E>, H> reportingInvalidFields(
            SourceRead read, BiFunction<Integer, List<E>, H> page) {
        return (total, entries) -> {
            invalidFields.report(read, invalidAmong(entries));
            return page.apply(total, entries);
        };
    }

    /**
     * Each field of {@code entries} whose format was wrong, by its path in the searchset —
     * {@code entry[3].issueDate}, the entry counted from zero — in the searchset's order.
     */
    private static List<InvalidSourceFields.Field> invalidAmong(
            List<? extends ReceivedRecord> entries) {
        List<InvalidSourceFields.Field> invalid = new ArrayList<>();
        for (int entry = 0; entry < entries.size(); entry++) {
            for (String field : entries.get(entry).invalid()) {
                invalid.add(new InvalidSourceFields.Field("entry[" + entry + "]." + field,
                        field));
            }
        }
        return invalid;
    }

    /**
     * Whether a read of one patient by its id found them: a {@code 200} is the patient's answer
     * and a {@code 404} is no patient under the id. Any other status is a fault.
     *
     * @throws PatientSourceException for any status but those two
     */
    private static boolean found(ClientHttpResponse response) throws IOException {
        if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return false;
        }
        if (!response.getStatusCode().isSameCodeAs(HttpStatus.OK)) {
            throw PatientSourceException.answered(response.getStatusCode().value());
        }
        return true;
    }

    /**
     * {@code call}'s answer, or its failure as a {@link PatientSourceException}: one the answer
     * raised itself as it is, and any other, after the time it took, classified as ADR 0014's
     * table has it, its cause redacted.
     */
    private <T> T called(Supplier<T> call) {
        long sent = System.nanoTime();
        try {
            return call.get();
        } catch (PatientSourceException fault) {
            throw fault;
        } catch (RuntimeException failed) {
            Duration waited = Duration.ofNanos(System.nanoTime() - sent);
            throw PatientSourceException.failed(failure(failed, waited), redacted(failed));
        }
    }

    /**
     * A call that failed with no status, after {@code waited}, as ADR 0014's table has it
     * ({@link OutboundCallFailure}): no answer — before the headers, or while reading the body —
     * timed out or found the source unreachable, and anything else was an answer that could not
     * be read. The call is timed, since Spring's JDK client cuts a stalled body off at the read
     * timeout as a bare I/O failure, and only the time tells that from a reset.
     */
    private Failure failure(RuntimeException failed, Duration waited) {
        return Failure.of(OutboundCallFailure.failed(
                failed, HttpTimeoutException.class, waited, readTimeout).reason());
    }

    /** {@code fault} redacted to its type: its message can quote the URL, and so the id. */
    private static RedactedFaultException redacted(RuntimeException fault) {
        return RedactedFaultException.of(fault, fault.getClass().getName());
    }

    /**
     * The read timeout {@code setting} names, in either of Boot's duration styles ({@code 1500ms},
     * {@code PT1.5S}), or {@link #DEFAULT_READ_TIMEOUT} when it is unset or blank. Anything else
     * but a positive duration fails startup, naming the variable, as a bad base URL does.
     */
    static Duration readTimeout(String setting) {
        if (setting == null || setting.isBlank()) {
            return DEFAULT_READ_TIMEOUT;
        }
        Duration timeout;
        try {
            timeout = DurationStyle.detectAndParse(setting.strip());
        } catch (IllegalArgumentException unreadable) {
            timeout = Duration.ZERO;
        }
        if (!timeout.isPositive()) {
            throw new IllegalStateException(
                    "APP_PATIENT_SOURCE_READ_TIMEOUT must be a positive duration");
        }
        return timeout;
    }

    /**
     * The JDK client every call is made on: {@code connectTimeout} to connect, and no redirect
     * followed, since a redirect away from the configured source is not its answer. Following
     * none is the JDK builder's own default, {@link HttpClient.Redirect#NEVER}, so the builder is
     * left at it: a redirect reaches {@link #findById} as the {@code 3xx} it is, a fault.
     */
    static HttpClient httpClient(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
    }

    /**
     * The base URL, which must be an absolute http or https URL. A path is kept, and a trailing
     * slash is harmless: the client joins {@code /Patient/{id}} to it with one slash.
     */
    private static String base(String configured) {
        URI uri = parsed(configured);
        if (uri == null || !SCHEMES.contains(String.valueOf(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IllegalStateException(
                    "APP_PATIENT_SOURCE_BASE_URL must be an absolute http or https URL");
        }
        return configured;
    }

    private static URI parsed(String configured) {
        if (configured == null) {
            return null;
        }
        try {
            return new URI(configured);
        } catch (URISyntaxException unparseable) {
            return null;
        }
    }
}
