package com.example.backend.patient.infrastructure.http;

import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.domain.FhirId;
import com.example.backend.patient.infrastructure.http.PatientSourceException.Failure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A page the in-house patient source answers a paged read with: a {@code searchset}
 * {@code Bundle}, as far as the service reads it — its {@code total} and each entry's
 * {@code resource}, of type {@code R}. Every other element ({@code link}, {@code search},
 * {@code fullUrl}) is never bound. A Patient search's page is one, of Patients (ADR 0018), and so
 * are a patient's FFI History, of the source's own FFIs (ADR 0019), and NUMC History, of its own
 * NUMC Records (ADR 0019, D5).
 *
 * <p>Every such read asks for its page alike ({@link #PAGE_QUERY}, {@link #pageVariables}): from
 * the zero-based {@code _offset} {@code startIndex - 1}, {@code _count} entries, with an accurate
 * total. The source paged it and counted every entry (ADR 0018, D3): the service takes the page
 * and the total as they are. Without a total there is no telling how many pages there are, so a
 * page without one is a failure of the source, as one that is no searchset is, or an entry that
 * holds nothing the service can read.
 *
 * @param resourceType must be {@code Bundle}
 * @param type         must be {@code searchset}
 * @param total        how many entries the read has in all, every page together; required
 * @param entry        the page's entries, in the source's order; none for a page with none
 * @param <R>          what each entry's {@code resource} binds to
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SourceSearchset<R>(String resourceType, String type, Integer total, List<Entry<R>> entry) {

    /**
     * The page's query parameters, which every paged read sends alike after its own: the URI
     * variables {@link #pageVariables} names.
     */
    static final String PAGE_QUERY = "_count={count}&_offset={offset}&_total=accurate";

    /** The URI variables {@link #PAGE_QUERY} reads, for {@code page}, put into {@code variables}. */
    static void pageVariables(Map<String, Object> variables, PageRequest page) {
        variables.put("count", page.count());
        variables.put("offset", page.offset());
    }

    /**
     * The page this searchset is: its total, and each entry's resource read by {@code read}, in
     * the source's order.
     *
     * @throws PatientSourceException when it is no searchset Bundle, has no total, or holds an
     *                                entry with no resource — or one {@code read} refuses —
     *                                naming which and quoting no value
     */
    <T> Page<T> page(Function<R, T> read) {
        if (!"Bundle".equals(resourceType) || !"searchset".equals(type)) {
            throw PatientSourceException.failed(Failure.NOT_A_SEARCHSET);
        }
        if (total == null || total < 0) {
            throw PatientSourceException.failed(Failure.NO_TOTAL);
        }
        return new Page<>(total, (entry == null ? List.<Entry<R>>of() : entry).stream()
                .map(each -> read.apply(held(each.resource())))
                .toList());
    }

    /** {@code resource}, which an entry must hold: one with none is another resource. */
    private static <R> R held(R resource) {
        if (resource == null) {
            throw PatientSourceException.failed(Failure.ANOTHER_RESOURCE);
        }
        return resource;
    }

    /**
     * An entry's own {@code id}, which a later read could open it by: it must be there, and in
     * FHIR's id grammar.
     *
     * @param none    the failure of an entry with no id
     * @param invalid the failure of an id outside the grammar
     */
    static String ownId(String id, Failure none, Failure invalid) {
        if (id == null) {
            throw PatientSourceException.failed(none);
        }
        if (!FhirId.matches(id)) {
            throw PatientSourceException.failed(invalid);
        }
        return id;
    }

    /**
     * One page, as the source answered it.
     *
     * @param total   how many entries the read has in all
     * @param entries the page's entries, each as its read made it
     */
    record Page<T>(int total, List<T> entries) {
    }

    /** One entry of the Bundle: only its resource is read. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entry<R>(R resource) {
    }
}
