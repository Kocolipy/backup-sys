package com.example.backend.patient.infrastructure.http;

import static com.example.backend.patient.infrastructure.http.ReceivedFields.day;
import static com.example.backend.patient.infrastructure.http.ReceivedFields.text;

import com.example.backend.patient.domain.ArmcBoard;
import com.example.backend.patient.infrastructure.http.PatientSourceException.Failure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

/**
 * One entry of a patient's ARMC History as the in-house patient source answers it: a custom
 * {@code "resourceType": "ARMC"} object of flat fields, FHIR-shaped but no FHIR resource (ADR
 * 0019, D6), read as received (ADR 0020). Each element is bound as the raw JSON it arrived as and
 * read by {@link ReceivedFields} — present, missing or invalid — so no field fails the read; any
 * other field the source sends is never bound.
 *
 * @param resourceType    must be {@code ARMC}: an entry of another resource is no ARMC Board
 * @param id              the board's own id, never checked
 * @param boardType       its ARMC Board Type's code
 * @param outcome         its ARMC Outcome's code
 * @param startDate       its Start Date, a calendar date
 * @param endDate         its End Date, a calendar date, missing for a board still open
 * @param medicalOfficer1 its Medical Officer #1's name
 * @param medicalOfficer2 its Medical Officer #2's name
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SourceArmc(String resourceType, JsonNode id, JsonNode boardType, JsonNode outcome,
        JsonNode startDate, JsonNode endDate, JsonNode medicalOfficer1,
        JsonNode medicalOfficer2) {

    /**
     * The domain's ARMC Board this entry is, each element as it was received.
     *
     * @throws PatientSourceException when the entry is no ARMC Board, quoting no value
     */
    ArmcBoard toBoard() {
        if (!"ARMC".equals(resourceType)) {
            throw PatientSourceException.failed(Failure.ANOTHER_RESOURCE);
        }
        return new ArmcBoard(text(id), text(boardType), text(outcome), day(startDate),
                day(endDate), text(medicalOfficer1), text(medicalOfficer2));
    }
}
