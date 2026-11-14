package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A Medication Record as the domain holds it (/docs/domain/medication.md): its six required
 * elements there and its six optional ones there or not, its End Date not before its Start Date,
 * its Medication and Prescribing Doctor not blank, its id in FHIR's id grammar, printed by its id
 * alone; and a page of Medication History counting no fewer than none. How a closed set's member
 * is found by its code is {@code SourceCodeTests}'.
 */
class MedicationRecordTests {

    private static final ZoneOffset SINGAPORE = ZoneOffset.ofHours(8);

    private static final OffsetDateTime STARTED = OffsetDateTime.of(2026, 10, 18, 17, 30, 0, 0,
            SINGAPORE);

    private static final OffsetDateTime ENDED = OffsetDateTime.of(2026, 10, 22, 17, 30, 0, 0,
            SINGAPORE);

    private static MedicationRecord record(String id, OffsetDateTime startDate,
            OffsetDateTime endDate) {
        return new MedicationRecord(id, "Paracetamol", "750 mg", "PO", "QDS", null, "30 tablets",
                DispenseStatus.PENDING, "5 days", startDate, endDate, MedicalCentre.NEE_SOON_CAMP,
                "Dr Lim Wei Jie");
    }

    private static MedicationRecord paracetamol(String id) {
        return record(id, STARTED, ENDED);
    }

    @Test
    void aRecordHoldsEveryElementItWasGiven() {
        MedicationRecord record = new MedicationRecord("medication-1", "Prednisolone", "30 mg",
                "PO", "OM", "1", "14 tablets", DispenseStatus.DISPENSED, "7 days", STARTED, ENDED,
                MedicalCentre.KHATIB_CAMP, "Dr Chen Mei Ling");

        assertThat(Arrays.<Object>asList(record.id(), record.medication(), record.dosage(),
                record.route(), record.frequency(), record.step(), record.quantity(),
                record.dispenseStatus(), record.duration(), record.startDate(), record.endDate(),
                record.dispensary(), record.prescribingDoctor()))
                .containsExactly("medication-1", "Prednisolone", "30 mg", "PO", "OM", "1",
                        "14 tablets", DispenseStatus.DISPENSED, "7 days", STARTED, ENDED,
                        MedicalCentre.KHATIB_CAMP, "Dr Chen Mei Ling");
    }

    /** Every optional element may be absent: a record with none of them is still a record. */
    @Test
    void aRecordMayLackEveryOptionalElement() {
        MedicationRecord record = new MedicationRecord("medication-bare",
                "Oral rehydration salts", null, null, null, null, null, DispenseStatus.DISPENSED,
                null, STARTED, ENDED, MedicalCentre.KRANJI_CAMP, "Dr Lim Wei Jie");

        assertThat(Arrays.asList(record.dosage(), record.route(), record.frequency(),
                record.step(), record.quantity(), record.duration()))
                .containsOnlyNulls().hasSize(6);
    }

    @Test
    void aRecordMayEndTheMinuteItStarts() {
        assertThat(record("medication-at-once", STARTED, STARTED).endDate()).isEqualTo(STARTED);
    }

    /** The End Date is compared as an instant, whatever offset each date was given in. */
    @Test
    void anEndDateInAnotherOffsetIsComparedAsTheSameInstant() {
        OffsetDateTime sameInstantInUtc = STARTED.withOffsetSameInstant(ZoneOffset.UTC);

        assertThat(record("medication-utc", STARTED, sameInstantInUtc).endDate())
                .isEqualTo(sameInstantInUtc);
    }

    @Test
    void aRecordEndingBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> record("medication-backwards", STARTED,
                STARTED.minusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A Medication Record's End Date must not be before its Start Date");
    }

    /** The End Date rule, as a reader of the source asks it before building a record. */
    @Test
    void anEndDateBeforeTheStartDateIsTheOneThatEndsBeforeItStarts() {
        assertThat(List.of(
                MedicationRecord.endsBeforeItStarts(STARTED, STARTED.minusMinutes(1)),
                MedicationRecord.endsBeforeItStarts(STARTED, STARTED),
                MedicationRecord.endsBeforeItStarts(STARTED, ENDED),
                MedicationRecord.endsBeforeItStarts(STARTED,
                        STARTED.withOffsetSameInstant(ZoneOffset.UTC))))
                .containsExactly(true, false, false, false);
    }

    /** An id outside FHIR's grammar is refused, quoting none of it. */
    @ParameterizedTest
    @ValueSource(strings = {"", "medication tan", "medication/tan", "medication_tan",
        "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void anIdOutsideTheGrammarIsRefused(String id) {
        assertThatThrownBy(() -> paracetamol(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A Medication Record's id must match the FHIR id grammar");
    }

    @Test
    void aRecordWithNoIdIsRefused() {
        assertThatThrownBy(() -> paracetamol(null)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Each required element left out is refused, naming it. */
    static Stream<Arguments> eachRequiredElementLeftOut() {
        Object[] whole = {"Amoxicillin", DispenseStatus.UNCOLLECTED, STARTED, ENDED,
            MedicalCentre.KRANJI_CAMP, "Dr Nur Aisyah binte Rahman"};
        String[] names = {"medication", "dispenseStatus", "startDate", "endDate", "dispensary",
            "prescribingDoctor"};
        List<Arguments> cases = new ArrayList<>();
        for (int element = 0; element < whole.length; element++) {
            Object[] elements = whole.clone();
            elements[element] = null;
            cases.add(Arguments.of(names[element], elements));
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("eachRequiredElementLeftOut")
    void aRecordMissingARequiredElementIsRefused(String element, Object[] elements) {
        assertThatThrownBy(() -> new MedicationRecord("medication-1", (String) elements[0], null,
                null, null, null, null, (DispenseStatus) elements[1], null,
                (OffsetDateTime) elements[2], (OffsetDateTime) elements[3],
                (MedicalCentre) elements[4], (String) elements[5]))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(element);
    }

    /** The Medication and the Prescribing Doctor are text a record must have: blank is refused. */
    @ParameterizedTest
    @ValueSource(strings = {"medication", "prescribingDoctor"})
    void aBlankMedicationOrPrescribingDoctorIsRefused(String element) {
        boolean medication = element.equals("medication");
        assertThatThrownBy(() -> new MedicationRecord("medication-1", medication ? " " : "Ibuprofen",
                null, null, null, null, null, DispenseStatus.PENDING, null, STARTED, ENDED,
                MedicalCentre.HENDON_CAMP, medication ? "Dr Rajesh Kumar" : ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A Medication Record's " + (medication ? "Medication"
                        : "Prescribing Doctor") + " must not be blank");
    }

    /** A Medication Record is the patient's Restricted data: printed, it names its id alone. */
    @Test
    void aRecordPrintsItsIdAlone() {
        assertThat(paracetamol("medication-tan-wei-ming-1"))
                .hasToString("MedicationRecord[medication-tan-wei-ming-1]");
    }

    @Test
    void aPageOfHistoryHoldsItsTotalAndItsRecordsInOrder() {
        List<MedicationRecord> records = new ArrayList<>(List.of(paracetamol("medication-2"),
                paracetamol("medication-1")));
        MedicationHistory history = new MedicationHistory(6, records);
        records.clear();

        assertThat(List.of(history.totalResults(), history.records()))
                .containsExactly(6, List.of(paracetamol("medication-2"),
                        paracetamol("medication-1")));
    }

    @Test
    void aPageOfHistoryCountsNoFewerThanNone() {
        assertThat(new MedicationHistory(0, List.of()).totalResults()).isZero();
        assertThatThrownBy(() -> new MedicationHistory(-1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPageOfHistoryHoldsItsRecords() {
        assertThatThrownBy(() -> new MedicationHistory(0, null))
                .isInstanceOf(NullPointerException.class);
    }
}
