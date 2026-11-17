package com.example.backend.patient.controller;

import com.example.backend.patient.domain.Patient;
import com.example.backend.patient.domain.PatientMatch;
import com.example.backend.patient.domain.PatientMatches;
import java.time.LocalDate;
import java.util.List;

/**
 * One page of a Patient search, as {@code GET /api/patient-search} answers it in plain JSON — the
 * application API's own shape, not FHIR — beside the paged reads' {@code totalResults} and
 * {@code startIndex} (ADR 0017's vocabulary, ADR 0018's source), and how many of the page's
 * matches were dropped.
 *
 * @param totalResults   how many Patients the search matched, as the patient source counted them,
 *                       whatever of the page could be shown
 * @param startIndex     the one-based index of the page's first entry, as the page was asked for
 * @param droppedResults how many of the page's matches were dropped: a Patient whose id is missing
 *                       or outside FHIR's id grammar, which no row could select
 * @param patients       the page's matches that are shown, in the source's order
 */
public record PatientSearchPage(int totalResults, int startIndex, int droppedResults,
        List<Row> patients) {

    /** The page {@code matches} answered for the page starting at {@code startIndex}. */
    static PatientSearchPage of(PatientMatches matches, int startIndex) {
        return new PatientSearchPage(matches.totalResults(), startIndex, matches.droppedResults(),
                matches.patients().stream().map(Row::of).toList());
    }

    /**
     * One match: the Patient's id — its EHR's FHIR id, which {@code GET /api/patient/{id}} reads
     * it by — its official name's text, its NRIC, its gender code, and its dates as
     * {@code YYYY-MM-DD}. Shown as received (ADR 0020): each field but the id is {@code null} when
     * the source sent it missing or in the wrong format, and {@code invalid} names it in the
     * second case; a gender outside the four codes is its raw code. The id is always a FHIR id,
     * but for an entry that is no Patient: its row has no id, so nothing selects it, and its
     * every other field is {@code null} and named in {@code invalid}.
     *
     * @param invalid the fields whose format was wrong, in the row's order
     */
    public record Row(String id, String name, String nric, String gender, String birthDate,
            String lastVisit, List<String> invalid) {

        static Row of(PatientMatch match) {
            return switch (match) {
                case PatientMatch.OfPatient(Patient patient, var lastVisit) -> new Row(
                        patient.id().value(),
                        patient.nameText().value(),
                        patient.nric().value(),
                        patient.gender().value(),
                        patient.birthDate().map(LocalDate::toString).value(),
                        lastVisit.map(LocalDate::toString).value(),
                        match.invalid());
                case PatientMatch.NotAPatient() ->
                        new Row(null, null, null, null, null, null, match.invalid());
            };
        }

        /** A match is Restricted data: printed, it names the Patient by id alone. */
        @Override
        public String toString() {
            return "Row[id=" + id + "]";
        }
    }
}
