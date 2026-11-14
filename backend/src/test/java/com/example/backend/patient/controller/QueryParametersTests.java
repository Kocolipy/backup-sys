package com.example.backend.patient.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.backend.patient.controller.QueryParameters.RefusedParameterException;
import com.example.backend.patient.domain.DatePrecision;
import com.example.backend.patient.domain.DateRange;
import com.example.backend.patient.domain.HistoryColumn;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.SortOrder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * How a history read takes a range over one of its declared date columns, and its sort, from the
 * query string, at each column's precision, before the patient source is called: each end
 * written as the column's precision writes it, or the request's {@code 400}. Each history's own
 * columns and their calendar dates are its controller tests' ({@code PatientFfiControllerTests}
 * and its siblings), which also hold the {@code 400}'s body; here, a date-time column, as no
 * history yet declares one.
 */
class QueryParametersTests {

    private static final HistoryColumn<LocalDateTime> TAKEN_AT =
            new HistoryColumn<>("takenAt", "taken-at", DatePrecision.DATE_TIME);

    private static final HistoryColumn<LocalDate> SHIPPED =
            new HistoryColumn<>("shipped", "shipped", DatePrecision.CALENDAR_DATE);

    private static DateParameter end(String text) {
        return new DateParameter(text);
    }

    /** The status a refused read is answered with, or {@code null} when it is not refused. */
    private static Integer refusal(Runnable read) {
        RefusedParameterException refused =
                catchThrowableOfType(RefusedParameterException.class, read::run);
        return refused == null ? null : refused.getStatusCode().value();
    }

    @Test
    void aDateTimeRangeIsReadToTheMinuteAtBothEnds() {
        assertThat(QueryParameters.range(TAKEN_AT, end("2024-03-01T09:30"),
                end("2024-03-02T17:45")))
                .isEqualTo(new DateRange<>(LocalDateTime.of(2024, 3, 1, 9, 30),
                        LocalDateTime.of(2024, 3, 2, 17, 45)));
    }

    /** Both ends are inclusive: a range one minute long is a from-time equal to its to-time. */
    @Test
    void aDateTimeRangeOfOneMinuteIsRead() {
        LocalDateTime nineThirty = LocalDateTime.of(2024, 3, 1, 9, 30);

        assertThat(QueryParameters.range(TAKEN_AT, end("2024-03-01T09:30"),
                end("2024-03-01T09:30")))
                .isEqualTo(new DateRange<>(nineThirty, nineThirty));
    }

    @Test
    void anEndLeftOutOrBlankIsOpen() {
        assertThat(QueryParameters.range(TAKEN_AT, null, end(" ")))
                .isEqualTo(DateRange.<LocalDateTime>any());
    }

    /**
     * An end of a date-time range written otherwise than to the minute — with seconds, with an
     * offset, as a date alone — is the {@code 400}, at either end.
     */
    @ParameterizedTest
    @ValueSource(strings = {"2024-03-01T09:30:00", "2024-03-01T09:30:00.000",
        "2024-03-01T09:30+08:00", "2024-03-01T01:30Z", "2024-03-01", "2024-03-01T24:00"})
    void aDateTimeEndNotWrittenToTheMinuteIsRefused(String written) {
        assertThat(List.of(
                refusal(() -> QueryParameters.range(TAKEN_AT, end(written), null)),
                refusal(() -> QueryParameters.range(TAKEN_AT, null, end(written)))))
                .containsExactly(400, 400);
    }

    @Test
    void aDateTimeFromMinuteAfterItsToMinuteIsRefused() {
        assertThat(refusal(() -> QueryParameters.range(TAKEN_AT, end("2024-03-01T09:31"),
                end("2024-03-01T09:30")))).isEqualTo(400);
    }

    /** Each column is read at its own precision: a calendar date's column takes no time. */
    @Test
    void aCalendarDateColumnRefusesADateTime() {
        assertThat(refusal(() -> QueryParameters.range(SHIPPED, end("2024-03-01T09:30"), null)))
                .isEqualTo(400);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {"takenAt, DESCENDING", "shipped, -"})
    void aSortNamesOneOfTheHistorysOwnColumns(String sortBy, SortOrder order) {
        assertThat(QueryParameters.sort(List.of(SHIPPED, TAKEN_AT), sortBy, order))
                .isEqualTo(HistorySort.of(sortBy.equals("takenAt") ? TAKEN_AT : SHIPPED, order));
    }

    @ParameterizedTest
    @ValueSource(strings = {"taken-at", "TakenAt", "issueDate"})
    void aSortByAColumnTheHistoryDoesNotDeclareIsRefused(String sortBy) {
        assertThat(refusal(() -> QueryParameters.sort(List.of(SHIPPED, TAKEN_AT), sortBy,
                SortOrder.ASCENDING))).isEqualTo(400);
    }
}
