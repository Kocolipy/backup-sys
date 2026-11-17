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

/**
 * A NUMC Record as the domain holds it (/docs/domain/numc.md; ADR 0020): each element as the
 * patient source sent it — present, missing or invalid — none refused, naming the elements whose
 * format was wrong, printed by its id alone; and a page of NUMC History counting no fewer than
 * none. How a field is classified as it is read is {@code ReceivedFieldsTests}'.
 */
class NumcRecordTests {

    private static final LocalDate ISSUED = LocalDate.of(2024, 1, 10);

    private static final LocalDate EXPIRES = LocalDate.of(2025, 1, 9);

    private static NumcRecord submariner(String id) {
        return new NumcRecord(SourceField.present(id), SourceField.present("submariner"),
                SourceField.present("waiver-rejected"), SourceField.present(ISSUED),
                SourceField.present(EXPIRES));
    }

    /**
     * The source is the truth: a record whose every element is missing or invalid, whose id is
     * outside FHIR's grammar, whose code is no member of its closed set or which expires before it
     * is issued, is a record all the same.
     */
    @Test
    void noRecordIsRefusedForWhatItHolds() {
        NumcRecord record = new NumcRecord(SourceField.present("numc tan/1"),
                SourceField.present("ippt"), SourceField.missing(), SourceField.invalid(),
                SourceField.present(ISSUED.minusYears(1)));

        assertThat(Arrays.<Object>asList(record.id(), record.type(), record.outcome(),
                record.issueDate(), record.expiryDate()))
                .containsExactly(SourceField.present("numc tan/1"), SourceField.present("ippt"),
                        SourceField.missing(), SourceField.invalid(),
                        SourceField.present(LocalDate.of(2023, 1, 10)));
    }

    /** Each element is held as some field, even one the source never sent: none is {@code null}. */
    static Stream<Arguments> eachElementLeftOut() {
        String[] names = {"id", "type", "outcome", "issueDate", "expiryDate"};
        List<Arguments> cases = new ArrayList<>();
        for (int element = 0; element < names.length; element++) {
            SourceField<?>[] fields = {SourceField.missing(), SourceField.missing(),
                SourceField.missing(), SourceField.missing(), SourceField.missing()};
            fields[element] = null;
            cases.add(Arguments.of(names[element], fields));
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("eachElementLeftOut")
    @SuppressWarnings("unchecked")
    void aRecordHoldsEachElementAsAField(String element, SourceField<?>[] fields) {
        assertThatThrownBy(() -> new NumcRecord((SourceField<String>) fields[0],
                (SourceField<String>) fields[1], (SourceField<String>) fields[2],
                (SourceField<LocalDate>) fields[3], (SourceField<LocalDate>) fields[4]))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(element);
    }

    /** A record names the elements whose format was wrong, as the API spells them, in its order. */
    @Test
    void aRecordNamesItsInvalidElementsInItsOrder() {
        NumcRecord record = new NumcRecord(SourceField.invalid(), SourceField.present("diving"),
                SourceField.invalid(), SourceField.missing(), SourceField.invalid());

        assertThat(record.invalid()).containsExactly("id", "outcome", "expiryDate");
    }

    @Test
    void aRecordWithTheTypeOrTheIssueDateInvalidNamesIt() {
        NumcRecord record = new NumcRecord(SourceField.missing(), SourceField.invalid(),
                SourceField.missing(), SourceField.invalid(), SourceField.missing());

        assertThat(record.invalid()).containsExactly("type", "issueDate");
    }

    @Test
    void aRecordMissingElementsButNoneInvalidNamesNone() {
        NumcRecord record = new NumcRecord(SourceField.missing(), SourceField.missing(),
                SourceField.missing(), SourceField.missing(), SourceField.missing());

        assertThat(record.invalid()).isEmpty();
    }

    /** A NUMC Record is the patient's Restricted data: printed, it names its id alone. */
    @Test
    void aRecordPrintsItsIdAlone() {
        assertThat(submariner("numc-tan-wei-ming-2")).hasToString("NumcRecord[numc-tan-wei-ming-2]");
    }

    @Test
    void aRecordWithNoIdPrintsNoneOfItsElements() {
        NumcRecord record = new NumcRecord(SourceField.invalid(), SourceField.present("diving"),
                SourceField.present("fit"), SourceField.present(ISSUED),
                SourceField.present(EXPIRES));

        assertThat(record).hasToString("NumcRecord[no id]");
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
