package com.example.backend.patient.infrastructure.http;

import static com.example.backend.patient.infrastructure.http.ReceivedFields.day;
import static com.example.backend.patient.infrastructure.http.ReceivedFields.text;

import com.example.backend.patient.domain.NumcRecord;
import com.example.backend.patient.infrastructure.http.PatientSourceException.Failure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

/**
 * One entry of a patient's NUMC History as the in-house patient source answers it: a custom
 * {@code "resourceType": "NUMC"} object of flat fields, FHIR-shaped but no FHIR resource (ADR
 * 0019, D5), read as received (ADR 0020). Each element is bound as the raw JSON it arrived as and
 * read by {@link ReceivedFields} — present, missing or invalid — so no field fails the read; any
 * other field the source sends is never bound.
 *
 * @param resourceType must be {@code NUMC}: an entry of another resource is no NUMC Record
 * @param id           the record's own id, never checked
 * @param type         its NUMC Type's code
 * @param outcome      its NUMC Outcome's code
 * @param issueDate    its Issue Date, a calendar date
 * @param expiryDate   its Expiry Date, a calendar date
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SourceNumc(String resourceType, JsonNode id, JsonNode type, JsonNode outcome,
        JsonNode issueDate, JsonNode expiryDate) {

    /**
     * The domain's NUMC Record this entry is, each element as it was received.
     *
     * @throws PatientSourceException when the entry is no NUMC Record, quoting no value
     */
    NumcRecord toRecord() {
        if (!"NUMC".equals(resourceType)) {
            throw PatientSourceException.failed(Failure.ANOTHER_RESOURCE);
        }
        return new NumcRecord(text(id), text(type), text(outcome), day(issueDate),
                day(expiryDate));
    }
}
