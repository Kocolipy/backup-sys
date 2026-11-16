package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What our API takes for an end of a range over a history's date column, at the column's
 * precision: a calendar date {@code YYYY-MM-DD}, or a date-time to the minute
 * {@code YYYY-MM-DDTHH:mm} with no offset — exactly, and nothing else.
 */
class DatePrecisionTests {

    @ParameterizedTest
    @ValueSource(strings = {"2024-01-01", "2024-02-29", "1999-12-31"})
    void aCalendarDateIsReadAsTheDayItWrites(String text) {
        assertThat(DatePrecision.CALENDAR_DATE.parse(text)).isEqualTo(LocalDate.parse(text));
    }

    /** Only a day the calendar has, written {@code YYYY-MM-DD}, is a calendar date. */
    @ParameterizedTest
    @ValueSource(strings = {"2023-02-29", "01/02/2023", "2024-1-1", "2024-12-31T00:00:00",
        "2024-12-31T00:00", "yesterday", " 2024-01-01", "2024-13-01"})
    void anythingElseIsNoCalendarDate(String text) {
        assertThatThrownBy(() -> DatePrecision.CALENDAR_DATE.parse(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A calendar date is written YYYY-MM-DD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-03-01T09:30", "2024-03-01T00:00", "2024-02-29T23:59"})
    void aDateTimeIsReadAsTheMinuteItWrites(String text) {
        assertThat(DatePrecision.DATE_TIME.parse(text)).isEqualTo(LocalDateTime.parse(text));
    }

    /**
     * Only a minute the clock has, on a day the calendar has, written {@code YYYY-MM-DDTHH:mm},
     * is a date-time: seconds, an offset and a date alone are each refused.
     */
    @ParameterizedTest
    @ValueSource(strings = {"2024-03-01T09:30:00", "2024-03-01T09:30:00.000",
        "2024-03-01T09:30+08:00", "2024-03-01T09:30Z", "2024-03-01", "2024-03-01 09:30",
        "2024-03-01T24:00", "2024-03-01T09:60", "2023-02-29T09:30", "2024-03-01T9:30",
        "09:30", "now"})
    void anythingElseIsNoDateTime(String text) {
        assertThatThrownBy(() -> DatePrecision.DATE_TIME.parse(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A date-time is written YYYY-MM-DDTHH:mm");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void noTextOrABlankOneIsNoEnd(String text) {
        assertThat(new Object[] {
            DatePrecision.CALENDAR_DATE.parse(text), DatePrecision.DATE_TIME.parse(text)})
                .containsOnlyNulls();
    }
}
