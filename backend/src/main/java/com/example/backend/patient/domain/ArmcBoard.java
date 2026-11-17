package com.example.backend.patient.domain;

import static java.util.Map.entry;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One ARMC Board in a patient's ARMC History: one aeromedical fitness board the patient sat, as
 * the patient source holds it for the Aeromedical Centre (/docs/domain/armc.md, ADR 0019, D6).
 * Read from the source on every read, never held, and shown as received (ADR 0020): each element
 * is a {@link SourceField}, present, missing or invalid, and none is refused for what it holds —
 * an id outside FHIR's grammar, a code outside its closed set, an End Date before the Start Date
 * or a blank Medical Officer, which is missing, included. A board with no End Date is still open.
 *
 * <p>Printed by its id alone: an ARMC Board is the patient's Restricted data, so neither a board
 * nor a message may carry what it says.
 *
 * @param id              the board's id at the source, never checked
 * @param boardType       its ARMC Board Type's code, known to {@link ArmcBoardType} or not
 * @param outcome         its ARMC Outcome's code, known to {@link ArmcOutcome} or not
 * @param startDate       its Start Date, a calendar date
 * @param endDate         its End Date, a calendar date, checked against nothing; missing for a
 *                        board still open
 * @param medicalOfficer1 its Medical Officer #1, a name as the source gives it, linked to no User
 * @param medicalOfficer2 its Medical Officer #2, likewise
 */
public record ArmcBoard(SourceField<String> id, SourceField<String> boardType,
        SourceField<String> outcome, SourceField<LocalDate> startDate,
        SourceField<LocalDate> endDate, SourceField<String> medicalOfficer1,
        SourceField<String> medicalOfficer2) implements ReceivedRecord {

    public ArmcBoard {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(boardType, "boardType");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        Objects.requireNonNull(medicalOfficer1, "medicalOfficer1");
        Objects.requireNonNull(medicalOfficer2, "medicalOfficer2");
    }

    @Override
    public List<String> invalid() {
        return SourceField.invalidAmong(entry("id", id), entry("boardType", boardType),
                entry("outcome", outcome), entry("startDate", startDate),
                entry("endDate", endDate), entry("medicalOfficer1", medicalOfficer1),
                entry("medicalOfficer2", medicalOfficer2));
    }

    @Override
    public String toString() {
        return "ArmcBoard[" + id.orElse("no id") + "]";
    }
}
