package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The filters narrowing an FFI History: any of the codes of each closed set but the Status, one
 * Status, and a date range for the Issue Date and for the Expiry Date — each optional, combined
 * with AND. A date range's own rules are {@code PatientFilterTests}'.
 */
class FfiFilterTests {

    private static final DateRange OF_2024 =
            new DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

    @Test
    void noFilterNarrowsNothing() {
        assertThat(FfiFilter.NONE).isEqualTo(new FfiFilter(Set.of(), Set.of(), DateRange.ANY,
                DateRange.ANY, Set.of(), Set.of(), null));
    }

    /** A set of codes that is absent is no filter on its column, as an empty one is. */
    @Test
    void anAbsentSetOfCodesIsNoFilter() {
        assertThat(new FfiFilter(null, null, DateRange.ANY, DateRange.ANY, null, null, null))
                .isEqualTo(FfiFilter.NONE);
    }

    /** Every filter at once, each kept as it was given. */
    @Test
    void everyFilterIsKept() {
        FfiFilter filter = new FfiFilter(Set.of(FfiType.IPPT), Set.of(FfiOutcome.UNFIT), OF_2024,
                DateRange.ANY, Set.of(FfiHigherAuthority.DIS),
                Set.of(FfiSubmittingMc.KRANJI_CAMP), FfiStatus.ACTIVE);

        assertThat(List.of(filter.types(), filter.outcomes(), filter.issueDate(),
                filter.expiryDate(), filter.higherAuthorities(), filter.submittingMcs(),
                filter.status()))
                .containsExactly(Set.of(FfiType.IPPT), Set.of(FfiOutcome.UNFIT), OF_2024,
                        DateRange.ANY, Set.of(FfiHigherAuthority.DIS),
                        Set.of(FfiSubmittingMc.KRANJI_CAMP), FfiStatus.ACTIVE);
    }

    /** The codes of a set are held in their closed set's own order, however they were given. */
    @Test
    void aSetOfCodesIsHeldInItsClosedSetsOrder() {
        FfiFilter filter = new FfiFilter(
                new LinkedHashSet<>(List.of(FfiType.VOCATIONAL, FfiType.IPPT,
                        FfiType.OVERSEAS_EXERCISES)),
                Set.of(), DateRange.ANY, DateRange.ANY,
                new LinkedHashSet<>(List.of(FfiHigherAuthority.DIS,
                        FfiHigherAuthority.CAMO)),
                Set.of(), null);

        assertThat(List.of(List.copyOf(filter.types()), List.copyOf(filter.higherAuthorities())))
                .containsExactly(
                        List.of(FfiType.OVERSEAS_EXERCISES, FfiType.IPPT, FfiType.VOCATIONAL),
                        List.of(FfiHigherAuthority.CAMO, FfiHigherAuthority.DIS));
    }

    /** The filter holds its own copy: a later change to the set it was given changes nothing. */
    @Test
    void aFilterIsUnchangedByItsSetChangingLater() {
        Set<FfiOutcome> given = new HashSet<>(Set.of(FfiOutcome.FIT));
        FfiFilter filter = new FfiFilter(Set.of(), given, DateRange.ANY, DateRange.ANY, Set.of(),
                Set.of(), null);

        given.add(FfiOutcome.UNFIT);

        assertThat(filter.outcomes()).containsExactly(FfiOutcome.FIT);
    }

    @Test
    void eachRangeIsRequiredItsAbsenceBeingAny() {
        assertThatThrownBy(() -> new FfiFilter(null, null, null, DateRange.ANY, null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessage("issueDate");
        assertThatThrownBy(() -> new FfiFilter(null, null, DateRange.ANY, null, null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessage("expiryDate");
    }

    @ParameterizedTest
    @CsvSource({
        "ord-release-from-service, ORD_RELEASE_FROM_SERVICE",
        "cat-a-ocs-scs, CAT_A_OCS_SCS"})
    void aCodeIsReadAsTheMemberItSpells(String code, FfiType type) {
        assertThat(FfiFilter.codeOf(FfiType.class, code)).isEqualTo(type);
    }

    /** A code is matched exactly: a label, another case or another spelling is outside its set. */
    @ParameterizedTest
    @ValueSource(strings = {"IPPT FFI", "IPPT", "ippt-ffi", "swimming"})
    void aCodeOutsideItsClosedSetIsRefused(String code) {
        assertThatThrownBy(() -> FfiFilter.codeOf(FfiType.class, code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An FFI filter's code is outside its closed set");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void noCodeOrABlankOneIsNone(String code) {
        assertThat(FfiFilter.codeOf(FfiStatus.class, code)).isNull();
    }
}
