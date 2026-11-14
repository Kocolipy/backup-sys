package com.example.backend.patient.infrastructure.http;

import static com.example.backend.patient.infrastructure.http.SourceFields.coded;
import static com.example.backend.patient.infrastructure.http.SourceFields.day;

import com.example.backend.patient.domain.Ffi;
import com.example.backend.patient.domain.FfiHigherAuthority;
import com.example.backend.patient.domain.FfiOutcome;
import com.example.backend.patient.domain.FfiStatus;
import com.example.backend.patient.domain.FfiSubmittingMc;
import com.example.backend.patient.domain.FfiType;
import com.example.backend.patient.infrastructure.http.PatientSourceException.Failure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One entry of a patient's FFI History as the in-house patient source answers it: a custom
 * {@code "resourceType": "FFI"} object of flat fields, FHIR-shaped but no FHIR resource (ADR
 * 0019). Every field is required; any other the source sends is never bound.
 *
 * @param resourceType must be {@code FFI}
 * @param id              the FFI's own id, in FHIR's id grammar
 * @param type            its FFI Type's code
 * @param outcome         its Outcome's code
 * @param issueDate       its Issue Date, to the day
 * @param expiryDate      its Expiry Date, to the day
 * @param higherAuthority its Higher Authority's code
 * @param submittingMc    its Submitting MC's code
 * @param status          its Status's code
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SourceFfi(String resourceType, String id, String type, String outcome, String issueDate,
        String expiryDate, String higherAuthority, String submittingMc, String status) {

    /**
     * The domain's FFI this entry is.
     *
     * @throws PatientSourceException when the entry is no FFI, has no id or one outside FHIR's
     *                                grammar, lacks a field, holds a code outside its closed set
     *                                or a date not to the day, naming which and quoting no value
     */
    Ffi toFfi() {
        if (!"FFI".equals(resourceType)) {
            throw PatientSourceException.failed(Failure.ANOTHER_RESOURCE);
        }
        return new Ffi(
                SourceSearchset.ownId(id, Failure.FFI_NO_ID, Failure.FFI_INVALID_ID),
                coded(FfiType.class, type, Failure.NO_FFI_TYPE, Failure.UNKNOWN_FFI_TYPE),
                coded(FfiOutcome.class, outcome, Failure.NO_OUTCOME, Failure.UNKNOWN_OUTCOME),
                day(issueDate, Failure.NO_ISSUE_DATE, Failure.ISSUE_DATE_NOT_TO_THE_DAY),
                day(expiryDate, Failure.NO_EXPIRY_DATE, Failure.EXPIRY_DATE_NOT_TO_THE_DAY),
                coded(FfiHigherAuthority.class, higherAuthority, Failure.NO_HIGHER_AUTHORITY,
                        Failure.UNKNOWN_HIGHER_AUTHORITY),
                coded(FfiSubmittingMc.class, submittingMc, Failure.NO_SUBMITTING_MC,
                        Failure.UNKNOWN_SUBMITTING_MC),
                coded(FfiStatus.class, status, Failure.NO_STATUS, Failure.UNKNOWN_STATUS));
    }
}
