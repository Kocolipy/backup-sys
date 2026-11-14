package com.example.backend.patient.controller;

import com.example.backend.patient.domain.MedicationHistory;
import com.example.backend.patient.domain.MedicationRecord;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * One page of a patient's Medication History, as {@code GET /api/patient-medication/{patientId}}
 * answers it in plain JSON — the application API's own shape, not FHIR — beside the paged reads'
 * {@code totalResults} and {@code startIndex} (ADR 0017's vocabulary, ADR 0019's source, D7).
 *
 * @param totalResults how many Medication Records the patient has, as the patient source counted
 *                     them
 * @param startIndex   the one-based index of the page's first record, as the page was asked for
 * @param records      the page's Medication Records, in the source's order
 */
public record MedicationHistoryPage(int totalResults, int startIndex, List<Row> records) {

    /** The page {@code history} answered for the page starting at {@code startIndex}. */
    static MedicationHistoryPage of(MedicationHistory history, int startIndex) {
        return new MedicationHistoryPage(history.totalResults(), startIndex,
                history.records().stream().map(Row::of).toList());
    }

    /**
     * One Medication Record: its id, its free text as the source gave it — no field at all for
     * an optional element the source lacks — each closed set's member as its code, and its
     * date-times ISO-8601 with the offset the source gave, to the second. The SPA names each code
     * with its label and shows each date-time in Singapore time.
     */
    public record Row(String id, String medication,
            @JsonInclude(JsonInclude.Include.NON_NULL) String dosage,
            @JsonInclude(JsonInclude.Include.NON_NULL) String route,
            @JsonInclude(JsonInclude.Include.NON_NULL) String frequency,
            @JsonInclude(JsonInclude.Include.NON_NULL) String step,
            @JsonInclude(JsonInclude.Include.NON_NULL) String quantity,
            String dispenseStatus,
            @JsonInclude(JsonInclude.Include.NON_NULL) String duration,
            String startDate, String endDate, String dispensary, String prescribingDoctor) {

        static Row of(MedicationRecord record) {
            return new Row(
                    record.id(),
                    record.medication(),
                    record.dosage(),
                    record.route(),
                    record.frequency(),
                    record.step(),
                    record.quantity(),
                    record.dispenseStatus().code(),
                    record.duration(),
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(record.startDate()),
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(record.endDate()),
                    record.dispensary().code(),
                    record.prescribingDoctor());
        }

        /** A Medication Record is Restricted data: printed, it names the record by id alone. */
        @Override
        public String toString() {
            return "Row[id=" + id + "]";
        }
    }
}
