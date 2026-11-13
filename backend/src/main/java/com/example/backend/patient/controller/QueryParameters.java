package com.example.backend.patient.controller;

import com.example.backend.patient.domain.DateRange;
import com.example.backend.patient.domain.FfiCode;
import com.example.backend.patient.domain.FfiFilter;
import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * How an FFI History reads its filters and sort from the query string, and a Patient search its
 * gender, so both refuse alike, before the patient source is called and quoting nothing the
 * caller sent: a
 * value no member of its set spells fails the binding ({@link #spelled}), as a date that is no
 * calendar date ({@link #DATE}) does, and a from-date after its to-date, or a repeated code
 * outside its closed set, is the same {@code 400} ({@link #range}, {@link #codes}).
 */
final class QueryParameters {

    /** A calendar date, exactly: four digits of year, two of month and two of day. */
    static final String DATE = "yyyy-MM-dd";

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

    /** The range from {@code from} to {@code to}, or this request's {@code 400} for an inverted one. */
    static DateRange range(LocalDate from, LocalDate to) {
        try {
            return new DateRange(from, to);
        } catch (IllegalArgumentException inverted) {
            throw new RefusedParameterException(inverted);
        }
    }

    /**
     * The members of {@code set} that each of {@code codes} spells, exactly
     * ({@link FfiFilter#codeOf}), none for no codes, or this request's {@code 400} for a code
     * outside the set. A repeated parameter is read here rather than bound to the enum: binding a
     * collection of enums would accept a constant's name, which is no code.
     */
    static <E extends Enum<E> & FfiCode> Set<E> codes(Class<E> set, List<String> codes) {
        if (codes == null) {
            return Set.of();
        }
        try {
            return codes.stream().map(code -> FfiFilter.codeOf(set, code))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (IllegalArgumentException outside) {
            throw new RefusedParameterException(outside);
        }
    }

    /**
     * A value that bound but that the read cannot take — a from-date after its to-date, or a code
     * outside its closed set: the caller's error, answered as a parameter that cannot be bound is
     * — the application API's generic {@code 400}, with its one refusal record. The refusal
     * travels as the cause, so the record names it as the reason; it carries no message of its own
     * and nothing the caller sent.
     */
    static final class RefusedParameterException extends ResponseStatusException {

        RefusedParameterException(IllegalArgumentException refusal) {
            super(HttpStatus.BAD_REQUEST, null, refusal);
        }
    }
}
