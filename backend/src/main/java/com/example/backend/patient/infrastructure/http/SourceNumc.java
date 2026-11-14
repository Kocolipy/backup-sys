package com.example.backend.patient.infrastructure.http;

import static com.example.backend.patient.infrastructure.http.SourceFields.coded;
import static com.example.backend.patient.infrastructure.http.SourceFields.day;

import com.example.backend.patient.domain.NumcOutcome;
import com.example.backend.patient.domain.NumcRecord;
import com.example.backend.patient.domain.NumcType;
import com.example.backend.patient.infrastructure.http.PatientSourceException.Failure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One entry of a patient's NUMC History as the in-house patient source answers it: a custom
 * {@code "resourceType": "NUMC"} object of flat fields, FHIR-shaped but no FHIR resource (ADR
 * 0019, D5). Every field is required; any other the source sends is never bound.
 *
 * @param resourceType must be {@code NUMC}
 * @param id           the record's own id, in FHIR's id grammar
 * @param type         its NUMC Type's code
 * @param outcome      its NUMC Outcome's code
 * @param issueDate    its Issue Date, to the day
 * @param expiryDate   its Expiry Date, to the day
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SourceNumc(String resourceType, String id, String type, String outcome, String issueDate,
        String expiryDate) {

    /**
     * The domain's NUMC Record this entry is.
     *
     * @throws PatientSourceException when the entry is no NUMC Record, has no id or one outside
     *                                FHIR's grammar, lacks a field, holds a code outside its
     *                                closed set or a date not to the day, naming which under a
     *                                NUMC reason and quoting no value
     */
    NumcRecord toRecord() {
        if (!"NUMC".equals(resourceType)) {
            throw PatientSourceException.failed(Failure.ANOTHER_RESOURCE);
        }
        return new NumcRecord(
                SourceSearchset.ownId(id, Failure.NUMC_NO_ID, Failure.NUMC_INVALID_ID),
                coded(NumcType.class, type, Failure.NO_NUMC_TYPE, Failure.UNKNOWN_NUMC_TYPE),
                coded(NumcOutcome.class, outcome, Failure.NO_NUMC_OUTCOME,
                        Failure.UNKNOWN_NUMC_OUTCOME),
                day(issueDate, Failure.NO_NUMC_ISSUE_DATE, Failure.NUMC_ISSUE_DATE_NOT_TO_THE_DAY),
                day(expiryDate, Failure.NO_NUMC_EXPIRY_DATE,
                        Failure.NUMC_EXPIRY_DATE_NOT_TO_THE_DAY));
    }
}
