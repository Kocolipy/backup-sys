package com.example.backend.patient.controller;

import com.example.backend.patient.domain.PatientMatch;
import com.example.backend.patient.domain.PatientMatches;
import java.util.List;

/**
 * One page of a Patient search, as {@code GET /api/patient-search} answers it in plain JSON — the
 * application API's own shape, not FHIR — beside the paged reads' {@code totalResults} and
 * {@code startIndex} (ADR 0017's vocabulary, ADR 0018's source).
 *
 * @param totalResults how many Patients the search matched, as the patient source counted them
 * @param startIndex   the one-based index of the page's first match, as the page was asked for
 * @param patients     the page's matches, in the source's order
 */
public record PatientSearchPage(int totalResults, int startIndex, List<Row> patients) {

    /** The page {@code matches} answered for the page starting at {@code startIndex}. */
    static PatientSearchPage of(PatientMatches matches, int startIndex) {
        return new PatientSearchPage(matches.totalResults(), startIndex,
                matches.patients().stream().map(Row::of).toList());
    }

    /**
     * One match: the Patient's id — its EHR's FHIR id, which {@code GET /api/patient/{id}} reads
     * it by — its official name's text, its NRIC, its gender code, and its dates as
     * {@code YYYY-MM-DD}, the Last visit {@code null} for a Patient with none.
     */
    public record Row(String id, String name, String nric, String gender, String birthDate,
            String lastVisit) {

        static Row of(PatientMatch match) {
            return new Row(
                    match.patient().id().value(),
                    match.patient().name().text(),
                    match.patient().nric(),
                    match.patient().gender().code(),
                    match.patient().birthDate().toString(),
                    match.lastVisit() == null ? null : match.lastVisit().toString());
        }

        /** A match is Restricted data: printed, it names the Patient by id alone. */
        @Override
        public String toString() {
            return "Row[id=" + id + "]";
        }
    }
}
