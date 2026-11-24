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
 * The filters narrowing an HSP History: any of the Outcomes and of the Submitting MCs, one
 * Status, and a date range for the Start Date and for the End Date — each optional, combined with
 * AND. A date range's own rules are {@code DateRangeTests}', and how a code is read
 * {@code SourceCodeTests}'.
 */
class HspFilterTests {

    private static final DateRange OF_2024 =
            new DateRange<>(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

    @Test
    void noFilterNarrowsNothing() {
        assertThat(HspFilter.NONE).isEqualTo(new HspFilter(Set.of(), DateRange.any(),
                DateRange.any(), Set.of(), null));
    }

    /** A set of codes that is absent is no filter on its column, as an empty one is. */
    @Test
    void anAbsentSetOfCodesIsNoFilter() {
        assertThat(new HspFilter(null, DateRange.any(), DateRange.any(), null, null))
                .isEqualTo(HspFilter.NONE);
    }

    /** Every filter at once, each kept as it was given. */
    @Test
    void everyFilterIsKept() {
        HspFilter filter = new HspFilter(Set.of(HspOutcome.MODERATE), OF_2024, DateRange.any(),
                Set.of(MedicalCentre.HENDON_CAMP), HspStatus.INACTIVE);

        assertThat(List.of(filter.outcomes(), filter.startDate(), filter.endDate(),
                filter.submittingMcs(), filter.status()))
                .containsExactly(Set.of(HspOutcome.MODERATE), OF_2024, DateRange.any(),
                        Set.of(MedicalCentre.HENDON_CAMP), HspStatus.INACTIVE);
    }

    /** The codes of a set are held in their closed set's own order, however they were given. */
    @Test
    void aSetOfCodesIsHeldInItsClosedSetsOrder() {
        HspFilter filter = new HspFilter(
                new LinkedHashSet<>(List.of(HspOutcome.HIGH, HspOutcome.LOW,
                        HspOutcome.MODERATE)),
                DateRange.any(), DateRange.any(),
                new LinkedHashSet<>(List.of(MedicalCentre.HENDON_CAMP,
                        MedicalCentre.NEE_SOON_CAMP)),
                null);

        assertThat(List.of(List.copyOf(filter.outcomes()), List.copyOf(filter.submittingMcs())))
                .containsExactly(
                        List.of(HspOutcome.LOW, HspOutcome.MODERATE, HspOutcome.HIGH),
                        List.of(MedicalCentre.NEE_SOON_CAMP, MedicalCentre.HENDON_CAMP));
    }

    /** The filter holds its own copy: a later change to the set it was given changes nothing. */
    @Test
    void aFilterIsUnchangedByItsSetChangingLater() {
        Set<HspOutcome> given = new HashSet<>(Set.of(HspOutcome.LOW));
        HspFilter filter = new HspFilter(given, DateRange.any(), DateRange.any(), Set.of(), null);

        given.add(HspOutcome.HIGH);

        assertThat(filter.outcomes()).containsExactly(HspOutcome.LOW);
    }

    @Test
    void eachRangeIsRequiredItsAbsenceBeingAny() {
        assertThatThrownBy(() -> new HspFilter(null, null, DateRange.any(), null, null))
                .isInstanceOf(NullPointerException.class).hasMessage("startDate");
        assertThatThrownBy(() -> new HspFilter(null, DateRange.any(), null, null, null))
                .isInstanceOf(NullPointerException.class).hasMessage("endDate");
    }
}
