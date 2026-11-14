package com.example.backend.patient.controller;

import com.example.backend.patient.domain.DateRange;
import com.example.backend.patient.domain.HistoryColumn;
import com.example.backend.patient.domain.HistorySort;
import com.example.backend.patient.domain.SortOrder;
import com.example.backend.patient.domain.SourceCode;
import java.beans.PropertyEditorSupport;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.server.ResponseStatusException;

/**
 * How a history read through the patient source reads its filters and sort from the query
 * string, and a Patient search its gender, so each refuses alike, before the patient source is
 * called and quoting nothing the caller sent: a value no member of its set spells fails the
 * binding ({@link #spelled}), and an end of a range not written at its column's precision, a
 * from-end after its to-end, a column the history does not sort by, or a repeated code outside
 * its closed set, is the same {@code 400} ({@link #range}, {@link #sort}, {@link #codes}).
 * Each history passes its own declared date columns ({@link HistoryColumn}); nothing here names
 * one.
 */
final class QueryParameters {

    private QueryParameters() {
    }

    /**
     * An editor reading a parameter's text through {@code read}, whose refusal fails the binding.
     * Registered for a type, it reads each value of a repeated parameter too.
     */
    static PropertyEditorSupport spelled(Function<String, ?> read) {
        return new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(read.apply(text));
            }
        };
    }

    /**
     * Registers a history's sort direction on {@code binder} in its own spellings, which are no
     * Java constant's name: {@code sortOrder} through {@link SortOrder#of}, so a value that spells
     * neither direction fails the binding. The column is read by {@link #sort}.
     */
    static void sortOrder(WebDataBinder binder) {
        binder.registerCustomEditor(SortOrder.class, spelled(SortOrder::of));
    }

    /**
     * The sort {@code sortBy} and {@code order} name ({@link HistorySort#of}), {@code sortBy}
     * spelling one of the history's {@code columns} exactly, or none when blank; or this
     * request's {@code 400} for any other column.
     */
    static HistorySort sort(List<HistoryColumn<?>> columns, String sortBy, SortOrder order) {
        try {
            return HistorySort.of(HistorySort.column(columns, sortBy), order);
        } catch (IllegalArgumentException unsorted) {
            throw new RefusedParameterException(unsorted);
        }
    }

    /**
     * The range over {@code column} from {@code from} to {@code to}, each end read at the
     * column's precision ({@link com.example.backend.patient.domain.DatePrecision#parse}) — a
     * calendar date {@code YYYY-MM-DD}, or a date-time to the minute {@code YYYY-MM-DDTHH:mm} —
     * or this request's {@code 400} for an end written otherwise, or an inverted range.
     */
    static <T extends Comparable<? super T>> DateRange<T> range(HistoryColumn<T> column,
            DateParameter from, DateParameter to) {
        try {
            return new DateRange<>(end(column, from), end(column, to));
        } catch (IllegalArgumentException refused) {
            throw new RefusedParameterException(refused);
        }
    }

    private static <T extends Comparable<? super T>> T end(HistoryColumn<T> column,
            DateParameter end) {
        return end == null ? null : column.precision().parse(end.text());
    }

    /**
     * The members of {@code set} that each of {@code codes} spells, exactly
     * ({@link SourceCode#ofParameter}), none for no codes, or this request's {@code 400} for a
     * code outside the set. A repeated parameter is read here rather than bound to the enum:
     * binding a collection of enums would accept a constant's name, which is no code.
     */
    static <E extends Enum<E> & SourceCode> Set<E> codes(Class<E> set, List<String> codes) {
        if (codes == null) {
            return Set.of();
        }
        try {
            return codes.stream().map(code -> SourceCode.ofParameter(set, code))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (IllegalArgumentException outside) {
            throw new RefusedParameterException(outside);
        }
    }

    /**
     * A value that bound but that the read cannot take — an end of a range not written at its
     * column's precision, a from-end after its to-end, a column the history does not sort by, or
     * a code outside its closed set: the caller's error, answered as a parameter that cannot be
     * bound is — the application API's generic {@code 400}, with its one refusal record. The
     * refusal travels as the cause, so the record names it as the reason; it carries no message
     * of its own and nothing the caller sent.
     */
    static final class RefusedParameterException extends ResponseStatusException {

        RefusedParameterException(IllegalArgumentException refusal) {
            super(HttpStatus.BAD_REQUEST, null, refusal);
        }
    }
}
