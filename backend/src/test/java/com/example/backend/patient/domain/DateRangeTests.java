package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A range of calendar dates an FFI History narrows by: each end optional and inclusive, and a
 * from-date never after its to-date.
 */
class DateRangeTests {

    private static final LocalDate JAN_1 = LocalDate.of(1980, 1, 1);

    private static final LocalDate DEC_31 = LocalDate.of(1989, 12, 31);

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
        "1980-01-01, 1989-12-31",
        "1980-01-01, 1980-01-01",
        "1980-01-01, -",
        "-, 1989-12-31",
        "-, -"})
    void aRangeTakesEitherEndOrBothOrNeitherOneDayLongAtTheLeast(LocalDate from, LocalDate to) {
        assertThat(new DateRange(from, to)).extracting(DateRange::from, DateRange::to)
                .containsExactly(from, to);
    }

    @Test
    void noRangeIsOpenAtBothEnds() {
        assertThat(DateRange.ANY).isEqualTo(new DateRange(null, null));
    }

    @Test
    void aRangeWhoseFromDateIsAfterItsToDateIsRefused() {
        assertThatThrownBy(() -> new DateRange(DEC_31, JAN_1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A date range's from-date may not be after its to-date");
    }

    @Test
    void aRangeOneDayInvertedIsRefused() {
        assertThatThrownBy(() -> new DateRange(JAN_1.plusDays(1), JAN_1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
