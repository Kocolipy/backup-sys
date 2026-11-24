package com.example.backend.patient.infrastructure.http;

import static com.example.backend.patient.infrastructure.http.ReceivedFields.day;
import static com.example.backend.patient.infrastructure.http.ReceivedFields.text;

import com.example.backend.patient.domain.HspRecord;
import com.example.backend.patient.infrastructure.http.PatientSourceException.Failure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

/**
 * One entry of a patient's HSP History as the in-house patient source answers it: a custom
 * {@code "resourceType": "HSP"} object of flat fields, FHIR-shaped but no FHIR resource (ADR
 * 0019), read as received (ADR 0020). Each field is bound as the raw JSON it arrived as and read
 * by {@link ReceivedFields} — present, missing or invalid — so no field fails the read; any other
 * field the source sends is never bound.
 *
 * @param resourceType must be {@code HSP}: an entry of another resource is no HSP Record
 * @param id           the record's own id, never checked
 * @param outcome      its Outcome's code
 * @param startDate    its Start Date, a calendar date
 * @param endDate      its End Date, a calendar date
 * @param submittingMc its Submitting MC's code
 * @param status       its Status's code
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SourceHsp(String resourceType, JsonNode id, JsonNode outcome, JsonNode startDate,
        JsonNode endDate, JsonNode submittingMc, JsonNode status) {

    /**
     * The domain's HSP Record this entry is, each field as it was received.
     *
     * @throws PatientSourceException when the entry is no HSP Record, quoting no value
     */
    HspRecord toRecord() {
        if (!"HSP".equals(resourceType)) {
            throw PatientSourceException.failed(Failure.ANOTHER_RESOURCE);
        }
        return new HspRecord(text(id), text(outcome), day(startDate), day(endDate),
                text(submittingMc), text(status));
    }
}
