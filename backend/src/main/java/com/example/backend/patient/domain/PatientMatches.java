package com.example.backend.patient.domain;

import java.util.List;
import java.util.Objects;

/**
 * One page of a Patient search, as the patient source answered it: the matches on the page that
 * can be shown, in the source's own order, how many Patients the search matched in all — the
 * source's own count, unchanged — and how many of the page's entries were dropped, each a
 * Patient whose id is missing or outside FHIR's id grammar, which no row could be selected by
 * (ADR 0018, ADR 0020). The service pages, sorts and filters nothing itself.
 *
 * @param totalResults   how many Patients the search matched, every page together, as the source
 *                       counted them
 * @param patients       the page's matches that can be shown, in the source's order
 * @param droppedResults how many of the page's entries were dropped
 */
public record PatientMatches(int totalResults, List<PatientMatch> patients, int droppedResults) {

    public PatientMatches {
        if (totalResults < 0) {
            throw new IllegalArgumentException("A search matches no fewer than no Patients");
        }
        if (droppedResults < 0) {
            throw new IllegalArgumentException("A page drops no fewer than no matches");
        }
        patients = List.copyOf(Objects.requireNonNull(patients, "patients"));
    }
}
