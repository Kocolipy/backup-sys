package com.example.backend.patient.infrastructure.http;

import static com.example.backend.patient.infrastructure.http.SourceFields.coded;
import static com.example.backend.patient.infrastructure.http.SourceFields.dateTime;
import static com.example.backend.patient.infrastructure.http.SourceFields.named;

import com.example.backend.patient.domain.DispenseStatus;
import com.example.backend.patient.domain.MedicalCentre;
import com.example.backend.patient.domain.MedicationRecord;
import com.example.backend.patient.infrastructure.http.PatientSourceException.Failure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

/**
 * One entry of a patient's Medication History as the in-house patient source answers it: a
 * custom {@code "resourceType": "Medication"} object of flat fields, FHIR-shaped but no FHIR
 * resource (ADR 0019, D7). The {@code dosage}, {@code route}, {@code frequency}, {@code step},
 * {@code quantity} and {@code duration} are optional free text, absent when the source has none;
 * every other field is required. Any other field the source sends is never bound.
 *
 * @param resourceType      must be {@code Medication}
 * @param id                the record's own id, in FHIR's id grammar
 * @param medication        its Medication, free text
 * @param dosage            its Dosage, or absent
 * @param route             its Route, or absent
 * @param frequency         its Frequency, or absent
 * @param step              its Step, or absent
 * @param quantity          its Quantity, or absent
 * @param dispenseStatus    its Dispense Status's code
 * @param duration          its Duration, or absent
 * @param startDate         its Start Date, ISO-8601 with an offset
 * @param endDate           its End Date, ISO-8601 with an offset, not before the Start Date
 * @param dispensary        its Dispensary's code, a Medical Centre's
 * @param prescribingDoctor its Prescribing Doctor's name
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SourceMedication(String resourceType, String id, String medication, String dosage,
        String route, String frequency, String step, String quantity, String dispenseStatus,
        String duration, String startDate, String endDate, String dispensary,
        String prescribingDoctor) {

    /**
     * The domain's Medication Record this entry is.
     *
     * @throws PatientSourceException when the entry is no Medication Record, has no id or one
     *                                outside FHIR's grammar, lacks a required field or holds a
     *                                blank Medication or Prescribing Doctor, holds a code outside
     *                                its closed set or a date that is no date-time with an
     *                                offset, or ends before it starts, naming which under a
     *                                Medication reason and quoting no value
     */
    MedicationRecord toRecord() {
        if (!"Medication".equals(resourceType)) {
            throw PatientSourceException.failed(Failure.ANOTHER_RESOURCE);
        }
        String ownId = SourceSearchset.ownId(id, Failure.MEDICATION_NO_ID,
                Failure.MEDICATION_INVALID_ID);
        String prescribed = named(medication, Failure.NO_MEDICATION);
        DispenseStatus dispensed = coded(DispenseStatus.class, dispenseStatus,
                Failure.NO_DISPENSE_STATUS, Failure.UNKNOWN_DISPENSE_STATUS);
        OffsetDateTime started = dateTime(startDate, Failure.NO_MEDICATION_START_DATE,
                Failure.MEDICATION_START_DATE_NOT_A_DATE_TIME);
        OffsetDateTime ended = dateTime(endDate, Failure.NO_MEDICATION_END_DATE,
                Failure.MEDICATION_END_DATE_NOT_A_DATE_TIME);
        if (MedicationRecord.endsBeforeItStarts(started, ended)) {
            throw PatientSourceException.failed(Failure.MEDICATION_END_BEFORE_START);
        }
        MedicalCentre centre = coded(MedicalCentre.class, dispensary, Failure.NO_DISPENSARY,
                Failure.UNKNOWN_DISPENSARY);
        return new MedicationRecord(ownId, prescribed, dosage, route, frequency, step, quantity,
                dispensed, duration, started, ended, centre,
                named(prescribingDoctor, Failure.NO_PRESCRIBING_DOCTOR));
    }
}
