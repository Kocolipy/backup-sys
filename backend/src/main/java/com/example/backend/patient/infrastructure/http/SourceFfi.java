package com.example.backend.patient.infrastructure.http;

import static com.example.backend.patient.infrastructure.http.ReceivedFields.day;
import static com.example.backend.patient.infrastructure.http.ReceivedFields.text;

import com.example.backend.patient.domain.Ffi;
import com.example.backend.patient.infrastructure.http.PatientSourceException.Failure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

/**
 * One entry of a patient's FFI History as the in-house patient source answers it: a custom
 * {@code "resourceType": "FFI"} object of flat fields, FHIR-shaped but no FHIR resource (ADR
 * 0019), read as received (ADR 0020). Each field is bound as the raw JSON it arrived as and read
 * by {@link ReceivedFields} — present, missing or invalid — so no field fails the read; any other
 * field the source sends is never bound.
 *
 * @param resourceType    must be {@code FFI}: an entry of another resource is no FFI
 * @param id              the FFI's own id, never checked
 * @param type            its FFI Type's code
 * @param outcome         its Outcome's code
 * @param issueDate       its Issue Date, a calendar date
 * @param expiryDate      its Expiry Date, a calendar date
 * @param higherAuthority its Higher Authority's code
 * @param submittingMc    its Submitting MC's code
 * @param status          its Status's code
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SourceFfi(String resourceType, JsonNode id, JsonNode type, JsonNode outcome,
        JsonNode issueDate, JsonNode expiryDate, JsonNode higherAuthority, JsonNode submittingMc,
        JsonNode status) {

    /**
     * The domain's FFI this entry is, each field as it was received.
     *
     * @throws PatientSourceException when the entry is no FFI, quoting no value
     */
    Ffi toFfi() {
        if (!"FFI".equals(resourceType)) {
            throw PatientSourceException.failed(Failure.ANOTHER_RESOURCE);
        }
        return new Ffi(text(id), text(type), text(outcome), day(issueDate), day(expiryDate),
                text(higherAuthority), text(submittingMc), text(status));
    }
}
