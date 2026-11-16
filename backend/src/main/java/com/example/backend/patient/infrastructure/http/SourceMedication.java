package com.example.backend.patient.infrastructure.http;

import static com.example.backend.patient.infrastructure.http.ReceivedFields.dateTime;
import static com.example.backend.patient.infrastructure.http.ReceivedFields.text;

import com.example.backend.patient.domain.MedicationRecord;
import com.example.backend.patient.infrastructure.http.PatientSourceException.Failure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

/**
 * One entry of a patient's Medication History as the in-house patient source answers it: a
 * custom {@code "resourceType": "Medication"} object of flat fields, FHIR-shaped but no FHIR
 * resource (ADR 0019, D7), read as received (ADR 0020). Each element is bound as the raw JSON it
 * arrived as and read by {@link ReceivedFields} — present, missing or invalid — so no field fails
 * the read; any other field the source sends is never bound.
 *
 * @param resourceType      must be {@code Medication}: an entry of another resource is no
 *                          Medication Record
 * @param id                the record's own id, never checked
 * @param medication        its Medication, free text
 * @param dosage            its Dosage, free text
 * @param route             its Route, free text
 * @param frequency         its Frequency, free text
 * @param step              its Step, free text
 * @param quantity          its Quantity, free text
 * @param dispenseStatus    its Dispense Status's code, known to {@code DispenseStatus} or not
 * @param duration          its Duration, free text
 * @param startDate         its Start Date, a date-time with its offset or not
 * @param endDate           its End Date, a date-time with its offset or not
 * @param dispensary        its Dispensary's code, known to {@code MedicalCentre} or not
 * @param prescribingDoctor its Prescribing Doctor's name
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SourceMedication(String resourceType, JsonNode id, JsonNode medication, JsonNode dosage,
        JsonNode route, JsonNode frequency, JsonNode step, JsonNode quantity,
        JsonNode dispenseStatus, JsonNode duration, JsonNode startDate, JsonNode endDate,
        JsonNode dispensary, JsonNode prescribingDoctor) {

    /**
     * The domain's Medication Record this entry is, each element as it was received.
     *
     * @throws PatientSourceException when the entry is no Medication Record, quoting no value
     */
    MedicationRecord toRecord() {
        if (!"Medication".equals(resourceType)) {
            throw PatientSourceException.failed(Failure.ANOTHER_RESOURCE);
        }
        return new MedicationRecord(text(id), text(medication), text(dosage), text(route),
                text(frequency), text(step), text(quantity), text(dispenseStatus),
                text(duration), dateTime(startDate), dateTime(endDate), text(dispensary),
                text(prescribingDoctor));
    }
}
