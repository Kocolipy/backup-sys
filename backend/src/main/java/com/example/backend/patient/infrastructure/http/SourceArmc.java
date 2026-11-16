package com.example.backend.patient.infrastructure.http;

import static com.example.backend.patient.infrastructure.http.SourceFields.coded;
import static com.example.backend.patient.infrastructure.http.SourceFields.day;
import static com.example.backend.patient.infrastructure.http.SourceFields.named;
import static com.example.backend.patient.infrastructure.http.SourceFields.optionalDay;

import com.example.backend.patient.domain.ArmcBoard;
import com.example.backend.patient.domain.ArmcBoardType;
import com.example.backend.patient.domain.ArmcOutcome;
import com.example.backend.patient.infrastructure.http.PatientSourceException.Failure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

/**
 * One entry of a patient's ARMC History as the in-house patient source answers it: a custom
 * {@code "resourceType": "ARMC"} object of flat fields, FHIR-shaped but no FHIR resource (ADR
 * 0019, D6). Every field is required but {@code endDate}, absent for a board still open; any
 * other the source sends is never bound.
 *
 * @param resourceType    must be {@code ARMC}
 * @param id              the board's own id, in FHIR's id grammar
 * @param boardType       its ARMC Board Type's code
 * @param outcome         its ARMC Outcome's code
 * @param startDate       its Start Date, to the day
 * @param endDate         its End Date, to the day and on or after the Start Date, or absent
 * @param medicalOfficer1 its Medical Officer #1's name
 * @param medicalOfficer2 its Medical Officer #2's name
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SourceArmc(String resourceType, String id, String boardType, String outcome,
        String startDate, String endDate, String medicalOfficer1, String medicalOfficer2) {

    /**
     * The domain's ARMC Board this entry is.
     *
     * @throws PatientSourceException when the entry is no ARMC Board, has no id or one outside
     *                                FHIR's grammar, lacks a required field or names a blank
     *                                Medical Officer, holds a code outside its closed set or a
     *                                date not to the day, or ends before it starts, naming which
     *                                under an ARMC reason and quoting no value
     */
    ArmcBoard toBoard() {
        if (!"ARMC".equals(resourceType)) {
            throw PatientSourceException.failed(Failure.ANOTHER_RESOURCE);
        }
        String ownId = SourceSearchset.ownId(id, Failure.ARMC_NO_ID, Failure.ARMC_INVALID_ID);
        ArmcBoardType type = coded(ArmcBoardType.class, boardType, Failure.NO_ARMC_BOARD_TYPE,
                Failure.UNKNOWN_ARMC_BOARD_TYPE);
        ArmcOutcome found = coded(ArmcOutcome.class, outcome, Failure.NO_ARMC_OUTCOME,
                Failure.UNKNOWN_ARMC_OUTCOME);
        LocalDate started = day(startDate, Failure.NO_ARMC_START_DATE,
                Failure.ARMC_START_DATE_NOT_TO_THE_DAY);
        LocalDate ended = optionalDay(endDate, Failure.ARMC_END_DATE_NOT_TO_THE_DAY);
        if (ArmcBoard.endsBeforeItStarts(started, ended)) {
            throw PatientSourceException.failed(Failure.ARMC_END_BEFORE_START);
        }
        return new ArmcBoard(ownId, type, found, started, ended,
                named(medicalOfficer1, Failure.NO_ARMC_MEDICAL_OFFICER_1),
                named(medicalOfficer2, Failure.NO_ARMC_MEDICAL_OFFICER_2));
    }
}
