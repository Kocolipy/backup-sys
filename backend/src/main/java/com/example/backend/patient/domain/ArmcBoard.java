package com.example.backend.patient.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One ARMC Board in a patient's ARMC History: one aeromedical fitness board the patient sat, as
 * the patient source holds it for the Aeromedical Centre (/docs/domain/armc.md, ADR 0019, D6).
 * Read from the source on every read, never held. Every element is required but the End Date: a
 * board with none is still open, and one with an End Date ends on or after its Start Date.
 *
 * <p>Printed by its id alone: an ARMC Board is the patient's Restricted data, so neither a board
 * nor a message may carry what it says.
 *
 * @param id              the board's id at the source, in FHIR's id grammar
 * @param boardType       its ARMC Board Type
 * @param outcome         its ARMC Outcome
 * @param startDate       its Start Date, a calendar date
 * @param endDate         its End Date, a calendar date on or after the Start Date, or
 *                        {@code null} for a board still open
 * @param medicalOfficer1 its Medical Officer #1, a name as the source gives it, linked to no User
 * @param medicalOfficer2 its Medical Officer #2, likewise
 */
public record ArmcBoard(String id, ArmcBoardType boardType, ArmcOutcome outcome,
        LocalDate startDate, LocalDate endDate, String medicalOfficer1, String medicalOfficer2) {

    public ArmcBoard {
        if (!FhirId.matches(id)) {
            throw new IllegalArgumentException(
                    "An ARMC Board's id must match the FHIR id grammar");
        }
        Objects.requireNonNull(boardType, "boardType");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(startDate, "startDate");
        named(Objects.requireNonNull(medicalOfficer1, "medicalOfficer1"), 1);
        named(Objects.requireNonNull(medicalOfficer2, "medicalOfficer2"), 2);
        if (endsBeforeItStarts(startDate, endDate)) {
            throw new IllegalArgumentException(
                    "An ARMC Board's End Date must not be before its Start Date");
        }
    }

    /**
     * Whether a board from {@code startDate} to {@code endDate} ends before it starts, which no
     * ARMC Board does: a board still open, with no End Date, never does.
     */
    public static boolean endsBeforeItStarts(LocalDate startDate, LocalDate endDate) {
        return endDate != null && endDate.isBefore(startDate);
    }

    private static void named(String medicalOfficer, int which) {
        if (medicalOfficer.isBlank()) {
            throw new IllegalArgumentException(
                    "An ARMC Board's Medical Officer #" + which + " must be named");
        }
    }

    @Override
    public String toString() {
        return "ArmcBoard[" + id + "]";
    }
}
