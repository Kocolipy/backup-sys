package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The filters narrowing an ARMC History: one ARMC Outcome, and a date range for the Start Date
 * and for the End Date — each optional, combined with AND. A date range's own rules are
 * {@code DateRangeTests}', and how a code is read {@code SourceCodeTests}'.
 */
class ArmcFilterTests {

    private static final DateRange OF_2023 =
            new DateRange<>(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31));

    @Test
    void noFilterNarrowsNothing() {
        assertThat(Arrays.asList(ArmcFilter.NONE.outcome(), ArmcFilter.NONE.startDate(),
                ArmcFilter.NONE.endDate())).containsExactly(null, DateRange.any(), DateRange.any());
    }

    /** Every filter at once, each kept as it was given. */
    @Test
    void everyFilterIsKept() {
        ArmcFilter filter = new ArmcFilter(ArmcOutcome.UNFIT, OF_2023, DateRange.any());

        assertThat(Arrays.asList(filter.outcome(), filter.startDate(), filter.endDate()))
                .containsExactly(ArmcOutcome.UNFIT, OF_2023, DateRange.any());
    }

    @Test
    void eachRangeIsRequiredItsAbsenceBeingAny() {
        assertThatThrownBy(() -> new ArmcFilter(null, null, DateRange.any()))
                .isInstanceOf(NullPointerException.class).hasMessage("startDate");
        assertThatThrownBy(() -> new ArmcFilter(null, DateRange.any(), null))
                .isInstanceOf(NullPointerException.class).hasMessage("endDate");
    }
}
