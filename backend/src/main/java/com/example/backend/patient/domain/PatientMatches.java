package com.example.backend.patient.domain;

import java.util.List;
import java.util.Objects;

/**
 * One page of a Patient search, as the patient source answered it: the matches on the page, in
 * the source's order — newest Last visit first — and how many Patients the search matched in all.
 * The service pages, sorts and filters nothing itself (ADR 0018).
 *
 * @param totalResults how many Patients the search matched, every page together
 * @param patients     the page's matches, in the source's order
 */
public record PatientMatches(int totalResults, List<PatientMatch> patients) {

    public PatientMatches {
        if (totalResults < 0) {
            throw new IllegalArgumentException("A search matches no fewer than no Patients");
        }
        patients = List.copyOf(Objects.requireNonNull(patients, "patients"));
    }
}
