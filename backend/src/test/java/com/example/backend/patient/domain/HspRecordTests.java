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
 * An HSP Record as the domain holds it (/docs/domain/hsp.md; ADR 0020): each field as the patient
 * source sent it — present, missing or invalid — none refused, naming the fields whose format was
 * wrong, printed by its id alone; and a page of HSP History counting no fewer than none. How a
 * field is classified as it is read is {@code ReceivedFieldsTests}'.
 */
class HspRecordTests {

    private static final LocalDate STARTED = LocalDate.of(2024, 3, 11);

    private static final LocalDate ENDS = LocalDate.of(2025, 3, 10);

    private static HspRecord low(String id) {
        return new HspRecord(SourceField.present(id), SourceField.present("low"),
                SourceField.present(STARTED), SourceField.present(ENDS),
                SourceField.present("nee-soon-camp"), SourceField.present("active"));
    }

    /** An HSP Record whose every field is missing, but those {@code fields} names, which are invalid. */
    private static HspRecord invalidIn(String... fields) {
        List<String> named = List.of(fields);
        SourceField<String> text = SourceField.missing();
        SourceField<LocalDate> day = SourceField.missing();
        return new HspRecord(
                named.contains("id") ? SourceField.invalid() : text,
                named.contains("outcome") ? SourceField.invalid() : text,
                named.contains("startDate") ? SourceField.invalid() : day,
                named.contains("endDate") ? SourceField.invalid() : day,
                named.contains("submittingMc") ? SourceField.invalid() : text,
                named.contains("status") ? SourceField.invalid() : text);
    }

    /**
     * The source is the truth: an HSP Record whose fields are missing or invalid, whose id is
     * outside FHIR's grammar, whose codes are no members of their closed sets or which ends
     * before it starts, is an HSP Record all the same.
     */
    @Test
    void noHspRecordIsRefusedForWhatItHolds() {
        HspRecord hsp = new HspRecord(SourceField.present("hsp tan/1"),
                SourceField.present("critical"), SourceField.invalid(),
                SourceField.present(STARTED.minusYears(1)), SourceField.present("tengah-air-base"),
                SourceField.present("expired"));

        assertThat(Arrays.<Object>asList(hsp.id(), hsp.outcome(), hsp.startDate(), hsp.endDate(),
                hsp.submittingMc(), hsp.status()))
                .containsExactly(SourceField.present("hsp tan/1"),
                        SourceField.present("critical"), SourceField.invalid(),
                        SourceField.present(LocalDate.of(2023, 3, 11)),
                        SourceField.present("tengah-air-base"), SourceField.present("expired"));
    }

    /** Each field is held as some field, even one the source never sent: none is {@code null}. */
    static Stream<Arguments> eachFieldLeftOut() {
        String[] names = {"id", "outcome", "startDate", "endDate", "submittingMc", "status"};
        List<Arguments> cases = new ArrayList<>();
        for (int field = 0; field < names.length; field++) {
            SourceField<?>[] fields = new SourceField<?>[names.length];
            Arrays.fill(fields, SourceField.missing());
            fields[field] = null;
            cases.add(Arguments.of(names[field], fields));
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("eachFieldLeftOut")
    @SuppressWarnings("unchecked")
    void anHspRecordHoldsEachFieldAsAField(String field, SourceField<?>[] fields) {
        assertThatThrownBy(() -> new HspRecord((SourceField<String>) fields[0],
                (SourceField<String>) fields[1], (SourceField<LocalDate>) fields[2],
                (SourceField<LocalDate>) fields[3], (SourceField<String>) fields[4],
                (SourceField<String>) fields[5]))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(field);
    }

    /** An HSP Record names the fields whose format was wrong, as the API spells them, in its order. */
    @Test
    void anHspRecordNamesItsInvalidFieldsInItsOrder() {
        assertThat(invalidIn("status", "id", "startDate").invalid())
                .containsExactly("id", "startDate", "status");
    }

    @Test
    void anHspRecordNamesEveryOtherFieldInvalidToo() {
        assertThat(invalidIn("submittingMc", "outcome", "endDate").invalid())
                .containsExactly("outcome", "endDate", "submittingMc");
    }

    @Test
    void anHspRecordMissingFieldsButNoneInvalidNamesNone() {
        assertThat(invalidIn().invalid()).isEmpty();
    }

    /** An HSP Record is the patient's Restricted data: printed, it names its id alone. */
    @Test
    void anHspRecordPrintsItsIdAlone() {
        assertThat(low("hsp-tan-wei-ming-1")).hasToString("HspRecord[hsp-tan-wei-ming-1]");
    }

    @Test
    void anHspRecordWithNoIdPrintsNoneOfItsFields() {
        assertThat(invalidIn("id")).hasToString("HspRecord[no id]");
    }

    @Test
    void aPageOfHistoryHoldsItsTotalAndItsRecordsInOrder() {
        List<HspRecord> records = new ArrayList<>(List.of(low("hsp-2"), low("hsp-1")));
        HspHistory history = new HspHistory(7, records);
        records.clear();

        assertThat(List.of(history.totalResults(), history.records()))
                .containsExactly(7, List.of(low("hsp-2"), low("hsp-1")));
    }

    @Test
    void aPageOfHistoryCountsNoFewerThanNone() {
        assertThat(new HspHistory(0, List.of()).totalResults()).isZero();
        assertThatThrownBy(() -> new HspHistory(-1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPageOfHistoryHoldsItsRecords() {
        assertThatThrownBy(() -> new HspHistory(0, null))
                .isInstanceOf(NullPointerException.class);
    }
}
