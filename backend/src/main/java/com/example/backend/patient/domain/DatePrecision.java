package com.example.backend.patient.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalQuery;

/**
 * How finely a history's date column is written (/docs/domain/ffi.md, "Sort and filters"): a
 * calendar date ({@link #CALENDAR_DATE}) or a date-time to the minute ({@link #DATE_TIME}). The
 * precision decides what our API takes for each end of a range over the column, as
 * {@link #parse} reads it; the two order alike, so a range over either is checked alike
 * ({@link DateRange}).
 *
 * @param <T> the value an end of a range over the column holds
 */
public sealed interface DatePrecision<T extends Comparable<? super T>>
        permits DatePrecision.CalendarDate, DatePrecision.DateTime {

    /** A calendar date, {@code YYYY-MM-DD}. */
    CalendarDate CALENDAR_DATE = new CalendarDate();

    /** A date-time to the minute in Singapore time, {@code YYYY-MM-DDTHH:mm}, with no offset. */
    DateTime DATE_TIME = new DateTime();

    /**
     * The value {@code text} writes at this precision, exactly, or {@code null} for a blank text.
     *
     * @throws IllegalArgumentException for any other text
     */
    T parse(String text);

    /**
     * A calendar date: a day, with no time and no zone, {@code YYYY-MM-DD} — a day the calendar
     * has, its four digits of year, two of month and two of day.
     */
    record CalendarDate() implements DatePrecision<LocalDate> {

        private static final DateTimeFormatter WRITTEN =
                DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

        @Override
        public LocalDate parse(String text) {
            return written(text, WRITTEN, LocalDate::from, "A calendar date is written YYYY-MM-DD");
        }
    }

    /**
     * A date-time to the minute, Singapore's wall clock, with no offset,
     * {@code YYYY-MM-DDTHH:mm}: a calendar date, {@code T}, and a minute the clock has. Seconds,
     * an offset and a date alone are refused.
     */
    record DateTime() implements DatePrecision<LocalDateTime> {

        /** The wall clock a date-time column is written in: Singapore's, a fixed +08:00. */
        public static final ZoneId ZONE = ZoneId.of("Asia/Singapore");

        private static final DateTimeFormatter WRITTEN = DateTimeFormatter
                .ofPattern("uuuu-MM-dd'T'HH:mm").withResolverStyle(ResolverStyle.STRICT);

        @Override
        public LocalDateTime parse(String text) {
            return written(text, WRITTEN, LocalDateTime::from,
                    "A date-time is written YYYY-MM-DDTHH:mm");
        }
    }

    /**
     * {@code text} read whole by {@code format} as {@code query} takes it, {@code null} for a
     * blank text, or a refusal naming the form, {@code refusal}, and nothing of the text.
     */
    private static <T> T written(String text, DateTimeFormatter format, TemporalQuery<T> query,
            String refusal) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return format.parse(text, query);
        } catch (DateTimeParseException unwritten) {
            throw new IllegalArgumentException(refusal);
        }
    }
}
