package com.example.backend.patient.infrastructure.http;

import com.example.backend.patient.domain.PatientMatch;
import com.example.backend.patient.domain.PatientMatches;
import com.example.backend.patient.infrastructure.http.PatientSourceException.Failure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The in-house patient source's answer to a Patient search: a FHIR R4 searchset {@code Bundle},
 * as far as the service reads it — its {@code total} and each entry's {@code resource}. Every
 * other element ({@code link}, {@code search}, {@code fullUrl}) is never bound.
 *
 * <p>The source matched, sorted and paged it, and counted every match (ADR 0018): the service
 * takes the page and the total as they are. Without a total there is no telling how many pages
 * there are, so a Bundle without one is a failure of the source, as an entry with no Patient the
 * service can hold is.
 *
 * @param resourceType must be {@code Bundle}
 * @param type         must be {@code searchset}
 * @param total        how many Patients the search matched in all; required
 * @param entry        the page's matches, in the source's order; none for a page with none
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record R4SearchBundle(String resourceType, String type, Integer total, List<Entry> entry) {

    /**
     * The page of matches this Bundle is.
     *
     * @throws PatientSourceException when it is no searchset Bundle, has no total, or holds an
     *                                entry that is no Patient the service can hold, naming which
     *                                and quoting no value
     */
    PatientMatches toMatches() {
        if (!"Bundle".equals(resourceType) || !"searchset".equals(type)) {
            throw PatientSourceException.failed(Failure.NOT_A_SEARCHSET);
        }
        if (total == null || total < 0) {
            throw PatientSourceException.failed(Failure.NO_TOTAL);
        }
        return new PatientMatches(total, (entry == null ? List.<Entry>of() : entry).stream()
                .map(Entry::match)
                .toList());
    }

    /** One entry of the Bundle: only its resource is read. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entry(R4Patient resource) {

        private PatientMatch match() {
            if (resource == null) {
                throw PatientSourceException.failed(Failure.ANOTHER_RESOURCE);
            }
            return resource.toMatch();
        }
    }
}
