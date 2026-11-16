package com.example.backend.patient.infrastructure.http;

import com.example.backend.paging.domain.PageRequest;
import com.example.backend.patient.domain.DatePrecision;
import com.example.backend.patient.domain.DateRange;
import com.example.backend.patient.domain.HistoryColumn;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.SortOrder;
import com.example.backend.patient.domain.SourceCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A read of the patient source as it is built: its URI template, and the URI variables the
 * template names. Every value the read sends is a variable, never part of the template, so the
 * call is observed under the template alone. A history's date columns reach it only through
 * their declarations ({@link HistoryColumn}), so nothing here names one.
 */
final class SourceQuery {

    /** How a sorted history's ties are broken: by id, sent after the column, or as the source decides. */
    enum Ties {
        BY_ID(",_id"),
        THE_SOURCES("");

        private final String sent;

        Ties(String sent) {
            this.sent = sent;
        }
    }

    private final StringBuilder template;

    private final Map<String, Object> variables = new HashMap<>();

    /** A read whose template starts {@code start}. */
    SourceQuery(String start) {
        this.template = new StringBuilder(start);
    }

    /** {@code value} as the URI variable {@code name}, which the template names already. */
    SourceQuery variable(String name, Object value) {
        variables.put(name, value);
        return this;
    }

    /** The variables of the source's paging for {@code page} ({@link SourceSearchset}). */
    void paged(PageRequest page) {
        SourceSearchset.pageVariables(variables, page);
    }

    /** {@code text} added to the template as it is. */
    void append(String text) {
        template.append(text);
    }

    /** {@code parameter} once, its value {@code value} the URI variable {@code variable}. */
    void parameter(String parameter, String variable, Object value) {
        template.append('&').append(parameter).append("={").append(variable).append('}');
        variables.put(variable, value);
    }

    /**
     * {@code range} narrowing the read by {@code column}: the column's search parameter once for
     * each end the range has, each end the URI variable named for the column and its end
     * ({@code <name>From}, {@code <name>To}), both ends inclusive at the column's precision.
     *
     * <ul>
     *   <li>A calendar date is sent as the day, {@code ge} its from-date and {@code le} its
     *       to-date.
     *   <li>A date-time to the minute, Singapore's wall clock, is sent as the instant it names, in
     *       UTC to the second, {@code YYYY-MM-DDTHH:mm:ssZ}: {@code ge} the first instant of its
     *       from-minute, and {@code lt} the first instant after its to-minute, so the whole
     *       to-minute is in the range.
     * </ul>
     */
    <T extends Comparable<? super T>> void narrowing(HistoryColumn<T> column, DateRange<T> range) {
        Bounds bounds = bounds(column.precision(), range);
        if (bounds.from() != null) {
            bounded(column.searchParameter(), bounds.from(), column.name() + "From");
        }
        if (bounds.to() != null) {
            bounded(column.searchParameter(), bounds.to(), column.name() + "To");
        }
    }

    /** A range's end as the source is asked for it: its comparison prefix and its value. */
    private record Bound(String prefix, String value) {
    }

    /** A range's two ends as the source is asked for them, {@code null} for an end it has not. */
    private record Bounds(Bound from, Bound to) {
    }

    /**
     * {@code range}'s ends at {@code precision}. The precision's switch arm is the only one its
     * column's range reaches: a column of a precision holds that precision's values
     * ({@code HistoryColumn<T>} with {@code DatePrecision<T>}), so each arm's cast restates what
     * the sealed precision already decides.
     */
    @SuppressWarnings("unchecked")
    private static <T extends Comparable<? super T>> Bounds bounds(DatePrecision<T> precision,
            DateRange<T> range) {
        return switch (precision) {
            case DatePrecision.CalendarDate _ -> days((DateRange<LocalDate>) range);
            case DatePrecision.DateTime _ -> minutes((DateRange<LocalDateTime>) range);
        };
    }

    /** A range of calendar dates: {@code ge} its from-date and {@code le} its to-date. */
    private static Bounds days(DateRange<LocalDate> range) {
        return new Bounds(
                range.from() == null ? null : new Bound("ge", range.from().toString()),
                range.to() == null ? null : new Bound("le", range.to().toString()));
    }

    /**
     * A range of date-times: {@code ge} the first instant of its from-minute, and {@code lt} the
     * first instant after its to-minute.
     */
    private static Bounds minutes(DateRange<LocalDateTime> range) {
        return new Bounds(
                range.from() == null ? null : new Bound("ge", instant(range.from())),
                range.to() == null ? null : new Bound("lt", instant(range.to().plusMinutes(1))));
    }

    /**
     * The instant {@code minute}, Singapore's wall clock, names, in UTC to the second:
     * {@code YYYY-MM-DDTHH:mm:ssZ}. UTC's {@code Z} keeps the value free of a {@code +}, which a
     * query string reads as a space.
     */
    private static String instant(LocalDateTime minute) {
        return DateTimeFormatter.ISO_INSTANT.format(
                minute.atZone(DatePrecision.DateTime.ZONE).toInstant());
    }

    private void bounded(String parameter, Bound bound, String variable) {
        template.append('&').append(parameter).append('=').append(bound.prefix()).append('{')
                .append(variable).append('}');
        variables.put(variable, bound.value());
    }

    /**
     * {@code codes} narrowing the read by {@code parameter}: the parameter once, its codes joined
     * by commas — FHIR's OR — in their set's order; nothing for no codes. Each code is a URI
     * variable of its own, {@code variable} and its place, so the commas stay in the template and
     * reach the source as commas: a value's own comma would be encoded, and read as part of a
     * code.
     */
    void anyOf(String parameter, String variable, Set<? extends SourceCode> codes) {
        if (codes.isEmpty()) {
            return;
        }
        template.append('&').append(parameter).append('=');
        int place = 0;
        for (SourceCode code : codes) {
            String name = variable + place;
            template.append(place == 0 ? "" : ",").append('{').append(name).append('}');
            variables.put(name, code.code());
            place++;
        }
    }

    /**
     * {@code sort} ordering the read: {@code _sort} on its column's search parameter, {@code -}
     * first for descending, then its {@code ties}' tie-break, if any; nothing for the source's own
     * order, whose sort names no column. A date-time column is sorted by its whole date-time, as
     * the source holds it.
     */
    void sorted(HistorySort sort, Ties ties) {
        if (sort.field() == null) {
            return;
        }
        String parameter = sort.field().searchParameter();
        template.append("&_sort=")
                .append(sort.order() == SortOrder.DESCENDING ? "-" + parameter : parameter)
                .append(ties.sent);
    }

    /** The template as built so far. */
    String template() {
        return template.toString();
    }

    /** The URI variables the template names. */
    Map<String, Object> variables() {
        return variables;
    }
}
