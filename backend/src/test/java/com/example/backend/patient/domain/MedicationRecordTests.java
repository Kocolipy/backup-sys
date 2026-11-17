package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A Medication Record as the domain holds it (/docs/domain/medication.md; ADR 0020): each element
 * as the patient source sent it — present, missing or invalid — none refused, naming the elements
 * whose format was wrong, printed by its id alone; and a page of Medication History counting no
 * fewer than none. How a field is classified as it is read is {@code ReceivedFieldsTests}'.
 */
class MedicationRecordTests {

    private static final ZoneOffset SINGAPORE = ZoneOffset.ofHours(8);

    private static final OffsetDateTime STARTED = OffsetDateTime.of(2026, 10, 18, 17, 30, 0, 0,
            SINGAPORE);

    private static final OffsetDateTime ENDED = OffsetDateTime.of(2026, 10, 22, 17, 30, 0, 0,
            SINGAPORE);

    /** The names of a Medication Record's elements, as the API spells them, in its order. */
    private static final List<String> ELEMENTS = List.of("id", "medication", "dosage", "route",
            "frequency", "step", "quantity", "dispenseStatus", "duration", "startDate", "endDate",
            "dispensary", "prescribingDoctor");

    private static MedicationRecord paracetamol(String id) {
        return new MedicationRecord(SourceField.present(id), SourceField.present("Paracetamol"),
                SourceField.present("750 mg"), SourceField.present("PO"),
                SourceField.present("QDS"), SourceField.missing(),
                SourceField.present("30 tablets"), SourceField.present("pending"),
                SourceField.present("5 days"), SourceField.present(STARTED),
                SourceField.present(ENDED), SourceField.present("nee-soon-camp"),
                SourceField.present("Dr Lim Wei Jie"));
    }

    /** A record whose every element is {@code fields}' own, in {@link #ELEMENTS}' order. */
    @SuppressWarnings("unchecked")
    private static MedicationRecord of(SourceField<?>... fields) {
        return new MedicationRecord((SourceField<String>) fields[0],
                (SourceField<String>) fields[1], (SourceField<String>) fields[2],
                (SourceField<String>) fields[3], (SourceField<String>) fields[4],
                (SourceField<String>) fields[5], (SourceField<String>) fields[6],
                (SourceField<String>) fields[7], (SourceField<String>) fields[8],
                (SourceField<OffsetDateTime>) fields[9], (SourceField<OffsetDateTime>) fields[10],
                (SourceField<String>) fields[11], (SourceField<String>) fields[12]);
    }

    private static List<Object> elementsOf(MedicationRecord record) {
        return Arrays.asList(record.id(), record.medication(), record.dosage(), record.route(),
                record.frequency(), record.step(), record.quantity(), record.dispenseStatus(),
                record.duration(), record.startDate(), record.endDate(), record.dispensary(),
                record.prescribingDoctor());
    }

    @Test
    void aRecordHoldsEveryElementItWasGiven() {
        MedicationRecord record = new MedicationRecord(SourceField.present("medication-1"),
                SourceField.present("Prednisolone"), SourceField.present("30 mg"),
                SourceField.present("PO"), SourceField.present("OM"), SourceField.present("1"),
                SourceField.present("14 tablets"), SourceField.present("dispensed"),
                SourceField.present("7 days"), SourceField.present(STARTED),
                SourceField.present(ENDED), SourceField.present("khatib-camp"),
                SourceField.present("Dr Chen Mei Ling"));

        assertThat(elementsOf(record)).containsExactly(SourceField.present("medication-1"),
                SourceField.present("Prednisolone"), SourceField.present("30 mg"),
                SourceField.present("PO"), SourceField.present("OM"), SourceField.present("1"),
                SourceField.present("14 tablets"), SourceField.present("dispensed"),
                SourceField.present("7 days"), SourceField.present(STARTED),
                SourceField.present(ENDED), SourceField.present("khatib-camp"),
                SourceField.present("Dr Chen Mei Ling"));
    }

    /**
     * The source is the truth: a record whose elements are missing or invalid, whose id is
     * outside FHIR's grammar, whose codes are no members of their closed sets or which ends
     * before it starts, is a record all the same.
     */
    @Test
    void noRecordIsRefusedForWhatItHolds() {
        MedicationRecord record = of(SourceField.present("medication tan/1"),
                SourceField.invalid(), SourceField.missing(), SourceField.missing(),
                SourceField.missing(), SourceField.missing(), SourceField.invalid(),
                SourceField.present("on-hold"), SourceField.missing(),
                SourceField.present(ENDED), SourceField.present(STARTED),
                SourceField.present("tengah-air-base"), SourceField.missing());

        assertThat(elementsOf(record)).containsExactly(SourceField.present("medication tan/1"),
                SourceField.invalid(), SourceField.missing(), SourceField.missing(),
                SourceField.missing(), SourceField.missing(), SourceField.invalid(),
                SourceField.present("on-hold"), SourceField.missing(),
                SourceField.present(OffsetDateTime.of(2026, 10, 22, 17, 30, 0, 0, SINGAPORE)),
                SourceField.present(OffsetDateTime.of(2026, 10, 18, 17, 30, 0, 0, SINGAPORE)),
                SourceField.present("tengah-air-base"), SourceField.missing());
    }

    /** Each element is held as some field, even one the source never sent: none is {@code null}. */
    static Stream<Arguments> eachElementLeftOut() {
        List<Arguments> cases = new ArrayList<>();
        for (int element = 0; element < ELEMENTS.size(); element++) {
            SourceField<?>[] fields = Collections.nCopies(ELEMENTS.size(), SourceField.missing())
                    .toArray(SourceField<?>[]::new);
            fields[element] = null;
            cases.add(Arguments.of(ELEMENTS.get(element), fields));
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("eachElementLeftOut")
    void aRecordHoldsEachElementAsAField(String element, SourceField<?>[] fields) {
        assertThatThrownBy(() -> of(fields))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(element);
    }

    /**
     * A record names each element whose format was wrong, as the API spells it, in its order:
     * one case for each element alone, so each is seen to be named.
     */
    static Stream<Arguments> eachElementInvalid() {
        List<Arguments> cases = new ArrayList<>();
        for (int element = 0; element < ELEMENTS.size(); element++) {
            SourceField<?>[] fields = Collections.nCopies(ELEMENTS.size(), SourceField.missing())
                    .toArray(SourceField<?>[]::new);
            fields[element] = SourceField.invalid();
            cases.add(Arguments.of(ELEMENTS.get(element), fields));
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("eachElementInvalid")
    void aRecordNamesAnInvalidElement(String element, SourceField<?>[] fields) {
        assertThat(of(fields).invalid()).containsExactly(element);
    }

    @Test
    void aRecordNamesItsInvalidElementsInItsOrder() {
        MedicationRecord record = of(SourceField.missing(), SourceField.present("Ibuprofen"),
                SourceField.missing(), SourceField.missing(), SourceField.missing(),
                SourceField.missing(), SourceField.invalid(), SourceField.present("dispensed"),
                SourceField.missing(), SourceField.invalid(), SourceField.invalid(),
                SourceField.missing(), SourceField.invalid());

        assertThat(record.invalid()).containsExactly("quantity", "startDate", "endDate",
                "prescribingDoctor");
    }

    @Test
    void aRecordMissingElementsButNoneInvalidNamesNone() {
        MedicationRecord record = of(Collections.nCopies(ELEMENTS.size(), SourceField.missing())
                .toArray(SourceField<?>[]::new));

        assertThat(record.invalid()).isEmpty();
    }

    /** A Medication Record is the patient's Restricted data: printed, it names its id alone. */
    @Test
    void aRecordPrintsItsIdAlone() {
        assertThat(paracetamol("medication-tan-wei-ming-1"))
                .hasToString("MedicationRecord[medication-tan-wei-ming-1]");
    }

    @Test
    void aRecordWithNoIdPrintsNoneOfItsElements() {
        MedicationRecord record = paracetamol("unused");
        MedicationRecord noId = new MedicationRecord(SourceField.missing(), record.medication(),
                record.dosage(), record.route(), record.frequency(), record.step(),
                record.quantity(), record.dispenseStatus(), record.duration(),
                record.startDate(), record.endDate(), record.dispensary(),
                record.prescribingDoctor());

        assertThat(noId).hasToString("MedicationRecord[no id]");
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
