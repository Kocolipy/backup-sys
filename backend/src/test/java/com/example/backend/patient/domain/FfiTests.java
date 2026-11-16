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
 * An FFI as the domain holds it (/docs/domain/ffi.md): every field required, its id in FHIR's id
 * grammar, printed by its id alone; and a page of FFI History counting no fewer than none. How a
 * closed set's member is found by its code is {@code SourceCodeTests}'.
 */
class FfiTests {

    private static final LocalDate ISSUED = LocalDate.of(2024, 1, 10);

    private static final LocalDate EXPIRES = LocalDate.of(2025, 1, 9);

    private static Ffi ippt(String id) {
        return new Ffi(id, FfiType.IPPT, FfiOutcome.REQUEST_WAIVER, ISSUED, EXPIRES,
                FfiHigherAuthority.CNMO, MedicalCentre.KRANJI_CAMP, FfiStatus.ACTIVE);
    }

    @Test
    void anFfiHoldsEveryFieldItWasGiven() {
        Ffi ffi = ippt("ffi-tan-wei-ming-2");

        assertThat(Arrays.<Object>asList(ffi.id(), ffi.type(), ffi.outcome(), ffi.issueDate(),
                ffi.expiryDate(), ffi.higherAuthority(), ffi.submittingMc(), ffi.status()))
                .containsExactly("ffi-tan-wei-ming-2", FfiType.IPPT, FfiOutcome.REQUEST_WAIVER,
                        ISSUED, EXPIRES, FfiHigherAuthority.CNMO, MedicalCentre.KRANJI_CAMP,
                        FfiStatus.ACTIVE);
    }

    /** An id outside FHIR's grammar, none included, is refused, quoting none of it. */
    @ParameterizedTest
    @ValueSource(strings = {"", "ffi tan", "ffi/tan", "ffi_tan",
        "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void anIdOutsideTheGrammarIsRefused(String id) {
        assertThatThrownBy(() -> ippt(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An FFI's id must match the FHIR id grammar");
    }

    @Test
    void anFfiWithNoIdIsRefused() {
        assertThatThrownBy(() -> ippt(null)).isInstanceOf(IllegalArgumentException.class);
    }

    /** Every field is required: each one left out is refused, naming it. */
    static Stream<Arguments> eachFieldLeftOut() {
        Object[] whole = {FfiType.IPPT, FfiOutcome.FIT, ISSUED, EXPIRES, FfiHigherAuthority.CAMO,
            MedicalCentre.HENDON_CAMP, FfiStatus.INACTIVE};
        String[] names = {"type", "outcome", "issueDate", "expiryDate", "higherAuthority",
            "submittingMc", "status"};
        List<Arguments> cases = new ArrayList<>();
        for (int field = 0; field < whole.length; field++) {
            Object[] fields = whole.clone();
            fields[field] = null;
            cases.add(Arguments.of(names[field], fields));
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("eachFieldLeftOut")
    void anFfiMissingAFieldIsRefused(String field, Object[] fields) {
        assertThatThrownBy(() -> new Ffi("ffi-1", (FfiType) fields[0], (FfiOutcome) fields[1],
                (LocalDate) fields[2], (LocalDate) fields[3], (FfiHigherAuthority) fields[4],
                (MedicalCentre) fields[5], (FfiStatus) fields[6]))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(field);
    }

    /** An FFI is the patient's Restricted data: printed, it names its id alone. */
    @Test
    void anFfiPrintsItsIdAlone() {
        assertThat(ippt("ffi-tan-wei-ming-2")).hasToString("Ffi[ffi-tan-wei-ming-2]");
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
