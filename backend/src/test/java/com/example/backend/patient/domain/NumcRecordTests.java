package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
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
 * A NUMC Record as the domain holds it (/docs/domain/numc.md): every element required, its id in
 * FHIR's id grammar, printed by its id alone; and a page of NUMC History counting no fewer than
 * none. How a closed set's member is found by its code is {@code SourceCodeTests}'.
 */
class NumcRecordTests {

    private static final LocalDate ISSUED = LocalDate.of(2024, 1, 10);

    private static final LocalDate EXPIRES = LocalDate.of(2025, 1, 9);

    private static NumcRecord submariner(String id) {
        return new NumcRecord(id, NumcType.SUBMARINER, NumcOutcome.WAIVER_REJECTED, ISSUED,
                EXPIRES);
    }

    @Test
    void aRecordHoldsEveryElementItWasGiven() {
        NumcRecord record = submariner("numc-tan-wei-ming-2");

        assertThat(Arrays.<Object>asList(record.id(), record.type(), record.outcome(),
                record.issueDate(), record.expiryDate()))
                .containsExactly("numc-tan-wei-ming-2", NumcType.SUBMARINER,
                        NumcOutcome.WAIVER_REJECTED, ISSUED, EXPIRES);
    }

    /** An id outside FHIR's grammar is refused, quoting none of it. */
    @ParameterizedTest
    @ValueSource(strings = {"", "numc tan", "numc/tan", "numc_tan",
        "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void anIdOutsideTheGrammarIsRefused(String id) {
        assertThatThrownBy(() -> submariner(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A NUMC Record's id must match the FHIR id grammar");
    }

    @Test
    void aRecordWithNoIdIsRefused() {
        assertThatThrownBy(() -> submariner(null)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Every element is required: each one left out is refused, naming it. */
    static Stream<Arguments> eachElementLeftOut() {
        Object[] whole = {NumcType.DIVING, NumcOutcome.FIT, ISSUED, EXPIRES};
        String[] names = {"type", "outcome", "issueDate", "expiryDate"};
        List<Arguments> cases = new ArrayList<>();
        for (int element = 0; element < whole.length; element++) {
            Object[] elements = whole.clone();
            elements[element] = null;
            cases.add(Arguments.of(names[element], elements));
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("eachElementLeftOut")
    void aRecordMissingAnElementIsRefused(String element, Object[] elements) {
        assertThatThrownBy(() -> new NumcRecord("numc-1", (NumcType) elements[0],
                (NumcOutcome) elements[1], (LocalDate) elements[2], (LocalDate) elements[3]))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(element);
    }

    /** A NUMC Record is the patient's Restricted data: printed, it names its id alone. */
    @Test
    void aRecordPrintsItsIdAlone() {
        assertThat(submariner("numc-tan-wei-ming-2")).hasToString("NumcRecord[numc-tan-wei-ming-2]");
    }

    @Test
    void aPageOfHistoryHoldsItsTotalAndItsRecordsInOrder() {
        List<NumcRecord> records = new ArrayList<>(List.of(submariner("numc-2"),
                submariner("numc-1")));
        NumcHistory history = new NumcHistory(7, records);
        records.clear();

        assertThat(List.of(history.totalResults(), history.records()))
                .containsExactly(7, List.of(submariner("numc-2"), submariner("numc-1")));
    }

    @Test
    void aPageOfHistoryCountsNoFewerThanNone() {
        assertThat(new NumcHistory(0, List.of()).totalResults()).isZero();
        assertThatThrownBy(() -> new NumcHistory(-1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPageOfHistoryHoldsItsRecords() {
        assertThatThrownBy(() -> new NumcHistory(0, null))
                .isInstanceOf(NullPointerException.class);
    }
}
