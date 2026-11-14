package com.example.backend.patient.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.patient.domain.DatePrecision;
import com.example.backend.patient.domain.DateRange;
import com.example.backend.patient.domain.HistoryColumn;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.SortOrder;
import com.example.backend.patient.infrastructure.http.SourceQuery.Ties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * How a read of the patient source is asked to narrow and sort a history by one of its declared
 * date columns, at the column's precision: the template it is observed under, and the URI
 * variables it sends. Each history's own columns reaching the source is its adapter tests'
 * ({@code PatientSourceFfiAdapterTests} and its siblings); here, columns of each precision as no
 * history yet declares them.
 */
class SourceQueryTests {

    private static final HistoryColumn<LocalDate> SHIPPED =
            new HistoryColumn<>("shipped", "shipped-on", DatePrecision.CALENDAR_DATE);

    private static final HistoryColumn<LocalDateTime> TAKEN_AT =
            new HistoryColumn<>("takenAt", "taken-at", DatePrecision.DATE_TIME);

    /** A read narrowed by {@code range} over {@code column}: its template and its variables. */
    private static <T extends Comparable<? super T>> List<Object> narrowed(
            HistoryColumn<T> column, DateRange<T> range) {
        SourceQuery query = new SourceQuery("/history?");
        query.narrowing(column, range);
        return List.of(query.template(), query.variables());
    }

    @Test
    void aCalendarDateRangeIsSentAsItsTwoDaysGeAndLe() {
        assertThat(narrowed(SHIPPED,
                new DateRange<>(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31))))
                .containsExactly("/history?&shipped-on=ge{shippedFrom}&shipped-on=le{shippedTo}",
                        Map.of("shippedFrom", "2024-01-01", "shippedTo", "2024-12-31"));
    }

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
        "2024-01-01, -, /history?&shipped-on=ge{shippedFrom}",
        "-, 2024-12-31, /history?&shipped-on=le{shippedTo}",
        "-, -, /history?"})
    void eachEndOfACalendarDateRangeIsSentOnlyWhenItIsSet(LocalDate from, LocalDate to,
            String template) {
        assertThat(narrowed(SHIPPED, new DateRange<>(from, to)).getFirst()).isEqualTo(template);
    }

    /**
     * A date-time range's ends are Singapore's wall clock, sent as the instants they name in UTC:
     * {@code ge} the from-minute's first instant, and {@code lt} the instant after the
     * to-minute, so both minutes are wholly inside the range.
     */
    @Test
    void aDateTimeRangeIsSentAsTheInstantsItsMinutesSpanInclusively() {
        assertThat(narrowed(TAKEN_AT, new DateRange<>(LocalDateTime.of(2024, 3, 1, 9, 30),
                LocalDateTime.of(2024, 3, 2, 17, 45))))
                .containsExactly("/history?&taken-at=ge{takenAtFrom}&taken-at=lt{takenAtTo}",
                        Map.of("takenAtFrom", "2024-03-01T01:30:00Z",
                                "takenAtTo", "2024-03-02T09:46:00Z"));
    }

    /** A range of one minute is the sixty seconds from that minute's start. */
    @Test
    void aDateTimeRangeOfOneMinuteSpansThatMinute() {
        LocalDateTime nineThirty = LocalDateTime.of(2024, 3, 1, 9, 30);

        assertThat(narrowed(TAKEN_AT, new DateRange<>(nineThirty, nineThirty)).get(1))
                .isEqualTo(Map.of("takenAtFrom", "2024-03-01T01:30:00Z",
                        "takenAtTo", "2024-03-01T01:31:00Z"));
    }

    /**
     * Singapore's midnight is the previous day's 16:00 in UTC, and its last minute runs to the
     * next day's midnight there.
     */
    @Test
    void aDateTimeRangeCrossesTheUtcDayAsSingaporesClockDoes() {
        assertThat(narrowed(TAKEN_AT, new DateRange<>(LocalDateTime.of(2024, 1, 1, 0, 0),
                LocalDateTime.of(2024, 12, 31, 23, 59))).get(1))
                .isEqualTo(Map.of("takenAtFrom", "2023-12-31T16:00:00Z",
                        "takenAtTo", "2024-12-31T16:00:00Z"));
    }

    @ParameterizedTest
    @CsvSource(nullValues = "-", value = {
        "2024-03-01T09:30, -, /history?&taken-at=ge{takenAtFrom}",
        "-, 2024-03-01T09:30, /history?&taken-at=lt{takenAtTo}",
        "-, -, /history?"})
    void eachEndOfADateTimeRangeIsSentOnlyWhenItIsSet(LocalDateTime from, LocalDateTime to,
            String template) {
        assertThat(narrowed(TAKEN_AT, new DateRange<>(from, to)).getFirst()).isEqualTo(template);
    }

    /** A sort by a date-time column is the source's own on its search parameter. */
    @ParameterizedTest
    @CsvSource({
        "ASCENDING, BY_ID, '&_sort=taken-at,_id'",
        "DESCENDING, BY_ID, '&_sort=-taken-at,_id'",
        "DESCENDING, THE_SOURCES, '&_sort=-taken-at'"})
    void aDateTimeColumnSortsOnItsSearchParameter(SortOrder order, Ties ties, String sorted) {
        SourceQuery query = new SourceQuery("");
        query.sorted(new HistorySort(TAKEN_AT, order), ties);

        assertThat(query.template()).isEqualTo(sorted);
    }

    /** The source's own order names no column, so the read asks for no sort. */
    @Test
    void theSourcesOwnOrderSendsNoSort() {
        SourceQuery query = new SourceQuery("");
        query.sorted(HistorySort.SOURCE_ORDER, Ties.BY_ID);

        assertThat(query.template()).isEmpty();
    }
}
