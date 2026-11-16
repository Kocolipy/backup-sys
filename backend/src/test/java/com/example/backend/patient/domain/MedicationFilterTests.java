package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The filters narrowing a Medication History: any of the Dispense Statuses, and a date-time
 * range for the Start Date and for the End Date — each optional, combined with AND. A date
 * range's own rules are {@code DateRangeTests}', and how a code is read
 * {@code SourceCodeTests}'.
 */
class MedicationFilterTests {

    private static final DateRange<LocalDateTime> ON_18_OCTOBER_2026 = new DateRange<>(
            LocalDateTime.of(2026, 10, 18, 0, 0), LocalDateTime.of(2026, 10, 18, 23, 59));

    @Test
    void noFilterNarrowsNothing() {
        assertThat(MedicationFilter.NONE)
                .isEqualTo(new MedicationFilter(Set.of(), DateRange.any(), DateRange.any()));
    }

    /** A set of Dispense Statuses that is absent is no filter on its column, as an empty one is. */
    @Test
    void anAbsentSetOfDispenseStatusesIsNoFilter() {
        assertThat(new MedicationFilter(null, DateRange.any(), DateRange.any()))
                .isEqualTo(MedicationFilter.NONE);
    }

    /** Every filter at once, each kept as it was given. */
    @Test
    void everyFilterIsKept() {
        MedicationFilter filter = new MedicationFilter(Set.of(DispenseStatus.UNCOLLECTED),
                ON_18_OCTOBER_2026, DateRange.any());

        assertThat(List.of(filter.dispenseStatuses(), filter.startDate(), filter.endDate()))
                .containsExactly(Set.of(DispenseStatus.UNCOLLECTED), ON_18_OCTOBER_2026,
                        DateRange.any());
    }

    /** The Dispense Statuses are held in their closed set's own order, however they were given. */
    @Test
    void theDispenseStatusesAreHeldInTheirClosedSetsOrder() {
        MedicationFilter filter = new MedicationFilter(
                new LinkedHashSet<>(List.of(DispenseStatus.UNCOLLECTED, DispenseStatus.PENDING,
                        DispenseStatus.DISPENSED)),
                DateRange.any(), DateRange.any());

        assertThat(List.copyOf(filter.dispenseStatuses())).containsExactly(
                DispenseStatus.PENDING, DispenseStatus.DISPENSED, DispenseStatus.UNCOLLECTED);
    }

    /** The filter holds its own copy: a later change to the set it was given changes nothing. */
    @Test
    void aFilterIsUnchangedByItsSetChangingLater() {
        Set<DispenseStatus> given = new HashSet<>(Set.of(DispenseStatus.PENDING));
        MedicationFilter filter = new MedicationFilter(given, DateRange.any(), DateRange.any());

        given.add(DispenseStatus.DISPENSED);

        assertThat(filter.dispenseStatuses()).containsExactly(DispenseStatus.PENDING);
    }

    @Test
    void eachRangeIsRequiredItsAbsenceBeingAny() {
        assertThatThrownBy(() -> new MedicationFilter(null, null, DateRange.any()))
                .isInstanceOf(NullPointerException.class).hasMessage("startDate");
        assertThatThrownBy(() -> new MedicationFilter(null, DateRange.any(), null))
                .isInstanceOf(NullPointerException.class).hasMessage("endDate");
    }
}
