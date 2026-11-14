package com.example.backend.patient.domain;

import java.util.List;
import java.util.Objects;

/**
 * One page of a patient's Medication History, as the patient source answered it: the page's
 * Medication Records, in the source's own order, and how many the patient has in all. The
 * service pages and orders nothing itself (ADR 0019, D7).
 *
 * @param totalResults how many Medication Records the patient has, every page together
 * @param records      the page's Medication Records, in the source's order
 */
public record MedicationHistory(int totalResults, List<MedicationRecord> records) {

    public MedicationHistory {
        if (totalResults < 0) {
            throw new IllegalArgumentException(
                    "A patient has no fewer than no Medication Records");
        }
        records = List.copyOf(Objects.requireNonNull(records, "records"));
    }
}
