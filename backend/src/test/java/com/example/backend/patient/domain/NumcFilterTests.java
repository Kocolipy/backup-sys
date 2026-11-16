package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The filters narrowing a NUMC History: any of the NUMC Types, any of the NUMC Outcomes, and a
 * date range for the Issue Date and for the Expiry Date — each optional, combined with AND. A
 * date range's own rules are {@code DateRangeTests}', and how a code is read
 * {@code SourceCodeTests}'.
 */
class NumcFilterTests {

    private static final DateRange OF_2024 =
            new DateRange<>(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

    @Test
    void noFilterNarrowsNothing() {
        assertThat(NumcFilter.NONE)
                .isEqualTo(new NumcFilter(Set.of(), Set.of(), DateRange.any(), DateRange.any()));
    }

    /** A set of codes that is absent is no filter on its column, as an empty one is. */
    @Test
    void anAbsentSetOfCodesIsNoFilter() {
        assertThat(new NumcFilter(null, null, DateRange.any(), DateRange.any()))
                .isEqualTo(NumcFilter.NONE);
    }

    /** Every filter at once, each kept as it was given. */
    @Test
    void everyFilterIsKept() {
        NumcFilter filter = new NumcFilter(Set.of(NumcType.SUBMARINER),
                Set.of(NumcOutcome.WAIVER_GRANTED), DateRange.any(), OF_2024);

        assertThat(List.of(filter.types(), filter.outcomes(), filter.issueDate(),
                filter.expiryDate()))
                .containsExactly(Set.of(NumcType.SUBMARINER), Set.of(NumcOutcome.WAIVER_GRANTED),
                        DateRange.any(), OF_2024);
    }

    /** The codes of a set are held in their closed set's own order, however they were given. */
    @Test
    void aSetOfCodesIsHeldInItsClosedSetsOrder() {
        NumcFilter filter = new NumcFilter(
                new LinkedHashSet<>(List.of(NumcType.DIVING, NumcType.HYPERBARIC_CHAMBER)),
                new LinkedHashSet<>(List.of(NumcOutcome.WAIVER_REJECTED, NumcOutcome.UNFIT,
                        NumcOutcome.FIT)),
                DateRange.any(), DateRange.any());

        assertThat(List.of(List.copyOf(filter.types()), List.copyOf(filter.outcomes())))
                .containsExactly(List.of(NumcType.HYPERBARIC_CHAMBER, NumcType.DIVING),
                        List.of(NumcOutcome.FIT, NumcOutcome.UNFIT,
                                NumcOutcome.WAIVER_REJECTED));
    }

    /** The filter holds its own copy: a later change to the set it was given changes nothing. */
    @Test
    void aFilterIsUnchangedByItsSetChangingLater() {
        Set<NumcType> given = new HashSet<>(Set.of(NumcType.DIVING));
        NumcFilter filter = new NumcFilter(given, Set.of(), DateRange.any(), DateRange.any());

        given.add(NumcType.SUBMARINER);

        assertThat(filter.types()).containsExactly(NumcType.DIVING);
    }

    @Test
    void eachRangeIsRequiredItsAbsenceBeingAny() {
        assertThatThrownBy(() -> new NumcFilter(null, null, null, DateRange.any()))
                .isInstanceOf(NullPointerException.class).hasMessage("issueDate");
        assertThatThrownBy(() -> new NumcFilter(null, null, DateRange.any(), null))
                .isInstanceOf(NullPointerException.class).hasMessage("expiryDate");
    }
}
