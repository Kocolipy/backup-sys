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
 * An FFI as the domain holds it (/docs/domain/ffi.md; ADR 0020): each field as the patient source
 * sent it — present, missing or invalid — none refused, naming the fields whose format was wrong,
 * printed by its id alone; and a page of FFI History counting no fewer than none. How a field is
 * classified as it is read is {@code ReceivedFieldsTests}'.
 */
class FfiTests {

    private static final LocalDate ISSUED = LocalDate.of(2024, 1, 10);

    private static final LocalDate EXPIRES = LocalDate.of(2025, 1, 9);

    private static Ffi ippt(String id) {
        return new Ffi(SourceField.present(id), SourceField.present("ippt"),
                SourceField.present("request-waiver"), SourceField.present(ISSUED),
                SourceField.present(EXPIRES), SourceField.present("cnmo"),
                SourceField.present("kranji-camp"), SourceField.present("active"));
    }

    /** An FFI whose every field is missing, but those {@code fields} names, which are invalid. */
    private static Ffi invalidIn(String... fields) {
        List<String> named = List.of(fields);
        SourceField<String> text = SourceField.missing();
        SourceField<LocalDate> day = SourceField.missing();
        return new Ffi(
                named.contains("id") ? SourceField.invalid() : text,
                named.contains("type") ? SourceField.invalid() : text,
                named.contains("outcome") ? SourceField.invalid() : text,
                named.contains("issueDate") ? SourceField.invalid() : day,
                named.contains("expiryDate") ? SourceField.invalid() : day,
                named.contains("higherAuthority") ? SourceField.invalid() : text,
                named.contains("submittingMc") ? SourceField.invalid() : text,
                named.contains("status") ? SourceField.invalid() : text);
    }

    /**
     * The source is the truth: an FFI whose fields are missing or invalid, whose id is outside
     * FHIR's grammar, whose codes are no members of their closed sets or which expires before it
     * is issued, is an FFI all the same.
     */
    @Test
    void noFfiIsRefusedForWhatItHolds() {
        Ffi ffi = new Ffi(SourceField.present("ffi tan/1"), SourceField.present("swimming"),
                SourceField.missing(), SourceField.invalid(),
                SourceField.present(ISSUED.minusYears(1)), SourceField.present("COMD MMI"),
                SourceField.present("tengah-air-base"), SourceField.present("expired"));

        assertThat(Arrays.<Object>asList(ffi.id(), ffi.type(), ffi.outcome(), ffi.issueDate(),
                ffi.expiryDate(), ffi.higherAuthority(), ffi.submittingMc(), ffi.status()))
                .containsExactly(SourceField.present("ffi tan/1"),
                        SourceField.present("swimming"), SourceField.missing(),
                        SourceField.invalid(), SourceField.present(LocalDate.of(2023, 1, 10)),
                        SourceField.present("COMD MMI"), SourceField.present("tengah-air-base"),
                        SourceField.present("expired"));
    }

    /** Each field is held as some field, even one the source never sent: none is {@code null}. */
    static Stream<Arguments> eachFieldLeftOut() {
        String[] names = {"id", "type", "outcome", "issueDate", "expiryDate", "higherAuthority",
            "submittingMc", "status"};
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
    void anFfiHoldsEachFieldAsAField(String field, SourceField<?>[] fields) {
        assertThatThrownBy(() -> new Ffi((SourceField<String>) fields[0],
                (SourceField<String>) fields[1], (SourceField<String>) fields[2],
                (SourceField<LocalDate>) fields[3], (SourceField<LocalDate>) fields[4],
                (SourceField<String>) fields[5], (SourceField<String>) fields[6],
                (SourceField<String>) fields[7]))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(field);
    }

    /** An FFI names the fields whose format was wrong, as the API spells them, in its order. */
    @Test
    void anFfiNamesItsInvalidFieldsInItsOrder() {
        assertThat(invalidIn("status", "id", "issueDate", "submittingMc").invalid())
                .containsExactly("id", "issueDate", "submittingMc", "status");
    }

    @Test
    void anFfiNamesEveryOtherFieldInvalidToo() {
        assertThat(invalidIn("type", "outcome", "expiryDate", "higherAuthority").invalid())
                .containsExactly("type", "outcome", "expiryDate", "higherAuthority");
    }

    @Test
    void anFfiMissingFieldsButNoneInvalidNamesNone() {
        assertThat(invalidIn().invalid()).isEmpty();
    }

    /** An FFI is the patient's Restricted data: printed, it names its id alone. */
    @Test
    void anFfiPrintsItsIdAlone() {
        assertThat(ippt("ffi-tan-wei-ming-2")).hasToString("Ffi[ffi-tan-wei-ming-2]");
    }

    @Test
    void anFfiWithNoIdPrintsNoneOfItsFields() {
        assertThat(invalidIn("id")).hasToString("Ffi[no id]");
    }

    @Test
    void aPageOfHistoryHoldsItsTotalAndItsFfisInOrder() {
        List<Ffi> ffis = new ArrayList<>(List.of(ippt("ffi-2"), ippt("ffi-1")));
        FfiHistory history = new FfiHistory(7, ffis);
        ffis.clear();

        assertThat(List.of(history.totalResults(), history.ffis()))
                .containsExactly(7, List.of(ippt("ffi-2"), ippt("ffi-1")));
    }

    @Test
    void aPageOfHistoryCountsNoFewerThanNone() {
        assertThat(new FfiHistory(0, List.of()).totalResults()).isZero();
        assertThatThrownBy(() -> new FfiHistory(-1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPageOfHistoryHoldsItsFfis() {
        assertThatThrownBy(() -> new FfiHistory(0, null)).isInstanceOf(NullPointerException.class);
    }
}
