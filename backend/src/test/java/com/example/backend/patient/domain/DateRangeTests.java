package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A range a history narrows one of its date columns by, of calendar dates or of date-times to the
 * minute: each end optional and inclusive, and a from-end never after its to-end.
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
        assertThat(new DateRange<>(from, to)).extracting(DateRange::from, DateRange::to)
                .containsExactly(from, to);
    }

    @Test
    void noRangeIsOpenAtBothEnds() {
        assertThat(DateRange.any()).isEqualTo(new DateRange<>(null, null));
    }

    @Test
    void aRangeWhoseFromDateIsAfterItsToDateIsRefused() {
        assertThatThrownBy(() -> new DateRange<>(DEC_31, JAN_1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A date range's from-date may not be after its to-date");
    }

    @Test
    void aRangeOneDayInvertedIsRefused() {
        assertThatThrownBy(() -> new DateRange<>(JAN_1.plusDays(1), JAN_1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final LocalDateTime NINE_THIRTY = LocalDateTime.of(2024, 3, 1, 9, 30);

    /** A range of date-times one minute long is a from-time equal to its to-time. */
    @Test
    void aRangeOfDateTimesOneMinuteLongIsARange() {
        assertThat(new DateRange<>(NINE_THIRTY, NINE_THIRTY))
                .extracting(DateRange::from, DateRange::to)
                .containsExactly(NINE_THIRTY, NINE_THIRTY);
    }

    /** A from-time one minute after its to-time, on the same day, is refused as an inverted date. */
    @Test
    void aRangeOfDateTimesOneMinuteInvertedIsRefused() {
        assertThatThrownBy(() -> new DateRange<>(NINE_THIRTY.plusMinutes(1), NINE_THIRTY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A date range's from-date may not be after its to-date");
    }
}
