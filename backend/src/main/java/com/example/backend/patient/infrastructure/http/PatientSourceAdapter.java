package com.example.backend.patient.infrastructure.http;

import com.example.backend.observability.OutboundCallFailure;
import com.example.backend.observability.RedactedFaultException;
import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.domain.DateRange;
import com.example.backend.patient.domain.Patient;
import com.example.backend.patient.domain.PatientId;
import com.example.backend.patient.domain.PatientMatches;
import com.example.backend.patient.domain.PatientFilter;
import com.example.backend.patient.domain.PatientQuery;
import com.example.backend.patient.domain.PatientSort;
import com.example.backend.patient.domain.PatientStore;
import com.example.backend.patient.infrastructure.http.PatientSourceException.Failure;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adapts the domain's {@link PatientStore} port onto the in-house patient source (ADR 0014):
 * {@code GET {base}/Patient/{id}} for a read by id, and {@code GET {base}/Patient?…} for a
 * Patient search ({@link #search}, ADR 0018). The source is assumed to answer FHIR R4 and to need
 * no authorisation, so the call carries no credential of any kind. The patient's id is its EHR's
 * FHIR id, sent unchanged.
 *
 * <ul>
 *   <li><b>Found</b>: the {@code 200}'s R4 {@code Patient}, copied into the domain's
 *       ({@link R4Patient}).
 *   <li><b>Not found</b>: a {@code 404} is no patient under the id, the ordinary answer the SPA
 *       reads as "No patient in context".
 *   <li><b>Anything else</b> — another status, no answer within the timeouts, a connection lost
 *       before the answer was in, the body's included, a body that is not a Patient the service
 *       can hold — is a {@link PatientSourceException}, a fault answered
 *       {@code 502}, naming the failure or the broken rule, whose message and cause carry neither
 *       the id nor anything of the body.
 * </ul>
 *
 * <p>No Patient is cached, in memory or in Redis: every read is a call to the source. The client is
 * built from Spring Boot's {@link RestClient.Builder}, so each call is observed as every client's
 * is ({@code http.client.requests}, under the URI template, never the id), on the JDK client with
 * redirects off and the timeouts below: a fixed connect timeout, and a read timeout a deployment
 * may set ({@code APP_PATIENT_SOURCE_READ_TIMEOUT}). Nothing here logs.
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

    /** The sort's tie-break and the source's paging, which every search asks for alike. */
    private static final String SEARCH_PAGE =
            ",_id&_count={count}&_offset={offset}&_total=accurate";

    /** A name search: FHIR's {@code name:contains}, matched as the source decides. */
    static final String SEARCH_BY_NAME = "/Patient?name:contains={name}";

    /** An NRIC search: the identifier under the NRIC NamingSystem, exactly. */
    static final String SEARCH_BY_NRIC = "/Patient?identifier={identifier}";

    /** The source's search parameter for the date of birth. */
    private static final String BIRTH_DATE = "birthdate";

    /** The source's search parameter for the Last visit (ADR 0018). */
    private static final String LAST_VISIT = "last-visit";

    private final RestClient source;

    /** The client's read timeout, by which a body read that failed is told timed out. */
    private final Duration readTimeout;

    /**
     * @param baseUrl     the source's FHIR base, {@code APP_PATIENT_SOURCE_BASE_URL}: required,
     *                    with no default, so a deployment that forgot it fails to start
     * @param readTimeout {@code APP_PATIENT_SOURCE_READ_TIMEOUT}, as {@link #readTimeout} reads it
     */
    @Autowired
    PatientSourceAdapter(RestClient.Builder builder,
            @Value("${app.patient-source.base-url}") String baseUrl,
            @Value("${app.patient-source.read-timeout:}") String readTimeout) {
        this(builder, baseUrl, CONNECT_TIMEOUT, readTimeout(readTimeout));
    }

    /** An adapter on the source at {@code baseUrl}, under the default timeouts. */
    PatientSourceAdapter(RestClient.Builder builder, String baseUrl) {
        this(builder, baseUrl, CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    PatientSourceAdapter(RestClient.Builder builder, String baseUrl, Duration connectTimeout,
            Duration readTimeout) {
        JdkClientHttpRequestFactory requests =
                new JdkClientHttpRequestFactory(httpClient(connectTimeout));
        requests.setReadTimeout(readTimeout);
        this.readTimeout = readTimeout;
        this.source = builder
                .baseUrl(base(baseUrl))
                .requestFactory(requests)
                .build();
    }

    @Override
    public Optional<Patient> findById(PatientId id) {
        return called(() -> source.get()
                .uri("/Patient/{id}", id.value())
                .accept(FHIR_JSON)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                        return Optional.<Patient>empty();
                    }
                    if (!response.getStatusCode().isSameCodeAs(HttpStatus.OK)) {
                        throw PatientSourceException.answered(response.getStatusCode().value());
                    }
                    return Optional.of(response.bodyTo(R4Patient.class).toPatient(id));
                }));
    }

    /**
     * {@code GET {base}/Patient?…}: the source's own search, matched, filtered, sorted, paged and
     * counted by the source (ADR 0018) — {@code gender} by its FHIR code, each end of a date
     * range as its search parameter, {@code birthdate} or {@code last-visit}, {@code ge} the
     * from-date and {@code le} the to-date, the sort as {@code _sort} on the same parameters,
     * {@code -} first for descending, ties broken by id, from {@code startIndex - 1},
     * {@code count} of them, with an accurate total. Its one answer is a {@code 200} searchset
     * Bundle; anything else — a {@code 404} included — is a fault, as is a Bundle with no total
     * or holding a Patient with no id. The query's text and the filters' values are URI
     * variables, never part of the template, so the call is observed under the template alone:
     * which filters were asked for and the sort, and none of their values.
     */
    @Override
    public PatientMatches search(PatientQuery query, PatientFilter filter, PatientSort sort,
            PageRequest page) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("count", page.count());
        variables.put("offset", page.offset());
        StringBuilder template = new StringBuilder(switch (query) {
            case PatientQuery.ByName name -> {
                variables.put("name", name.text());
                yield SEARCH_BY_NAME;
            }
            case PatientQuery.ByNric nric -> {
                variables.put("identifier", Patient.NRIC_SYSTEM + "|" + nric.nric());
                yield SEARCH_BY_NRIC;
            }
        });
        if (filter.gender() != null) {
            template.append("&gender={gender}");
            variables.put("gender", filter.gender().code());
        }
        narrowing(template, variables, BIRTH_DATE, "birthDate", filter.birthDate());
        narrowing(template, variables, LAST_VISIT, "lastVisit", filter.lastVisit());
        template.append("&_sort=").append(sorted(sort)).append(SEARCH_PAGE);
        String uri = template.toString();
        return called(() -> source.get()
                .uri(uri, variables)
                .accept(FHIR_JSON)
                .exchange((request, response) -> {
                    if (!response.getStatusCode().isSameCodeAs(HttpStatus.OK)) {
                        throw PatientSourceException.answered(response.getStatusCode().value());
                    }
                    return response.bodyTo(R4SearchBundle.class).toMatches();
                }));
    }

    /**
     * {@code range} narrowing the search by {@code parameter}: the parameter once for each end
     * the range has, {@code ge} its from-date and {@code le} its to-date, each date the URI
     * variable {@code variable} named for its end.
     */
    private static void narrowing(StringBuilder template, Map<String, Object> variables,
            String parameter, String variable, DateRange range) {
        if (range.from() != null) {
            template.append('&').append(parameter).append("=ge{").append(variable)
                    .append("From}");
            variables.put(variable + "From", range.from().toString());
        }
        if (range.to() != null) {
            template.append('&').append(parameter).append("=le{").append(variable)
                    .append("To}");
            variables.put(variable + "To", range.to().toString());
        }
    }

    /** The source's search parameter each column sorts on. */
    private static final Map<PatientSort.Field, String> SORTED_ON = Map.of(
            PatientSort.Field.BIRTH_DATE, BIRTH_DATE,
            PatientSort.Field.LAST_VISIT, LAST_VISIT);

    /** The {@code _sort} {@code sort} asks for, before its tie-break: {@code -} first descending. */
    private static String sorted(PatientSort sort) {
        String parameter = SORTED_ON.get(sort.field());
        return sort.order() == PatientSort.Order.DESCENDING ? "-" + parameter : parameter;
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
     * The JDK client every call is made on: {@code connectTimeout} to connect, and no redirect
     * followed, since a redirect away from the configured source is not its answer. Following
     * none is the JDK builder's own default, {@link HttpClient.Redirect#NEVER}, so the builder is
     * left at it: a redirect reaches {@link #findById} as the {@code 3xx} it is, a fault.
     */
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
