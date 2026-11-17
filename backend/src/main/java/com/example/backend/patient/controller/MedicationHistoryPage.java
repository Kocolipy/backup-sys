package com.example.backend.patient.controller;

import com.example.backend.patient.domain.MedicationHistory;
import com.example.backend.patient.domain.MedicationRecord;
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
     * One Medication Record as received (ADR 0020): each field the source sent, its free text as
     * it came, each closed set's code as it came — known to its set or not — and its date-times
     * ISO-8601 with the offset the source gave, to the second. A field the source left out, sent
     * blank or sent in the wrong format is {@code null}, and {@code invalid} names those whose
     * format was wrong: a {@code null} field it does not name is missing. The SPA names each
     * known code with its label and shows each date-time in Singapore time.
     *
     * @param invalid the fields whose format was wrong, in the row's order; none when each field
     *                is present or missing
     */
    public record Row(String id, String medication, String dosage, String route,
            String frequency, String step, String quantity, String dispenseStatus,
            String duration, String startDate, String endDate, String dispensary,
            String prescribingDoctor, List<String> invalid) {

        static Row of(MedicationRecord record) {
            return new Row(
                    record.id().value(),
                    record.medication().value(),
                    record.dosage().value(),
                    record.route().value(),
                    record.frequency().value(),
                    record.step().value(),
                    record.quantity().value(),
                    record.dispenseStatus().value(),
                    record.duration().value(),
                    record.startDate().map(DateTimeFormatter.ISO_OFFSET_DATE_TIME::format).value(),
                    record.endDate().map(DateTimeFormatter.ISO_OFFSET_DATE_TIME::format).value(),
                    record.dispensary().value(),
                    record.prescribingDoctor().value(),
                    record.invalid());
        }

        /** A Medication Record is Restricted data: printed, it names the record by id alone. */
        @Override
        public String toString() {
            return "Row[id=" + id + "]";
        }
    }
}
