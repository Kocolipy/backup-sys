package com.example.backend.patient.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * One field of a record the patient source holds, as it was received (ADR 0020): the source is
 * the truth, so a field is never refused for what it holds. It is {@link Received#PRESENT}, with
 * its value; {@link Received#MISSING} — no key, {@code null}, or a string that is empty or only
 * whitespace; or {@link Received#INVALID} — the wrong JSON type, or a string that is no value of
 * the field's kind. Nothing is checked of a value's meaning: a date in the future, or an end
 * before its start, is present as it came.
 *
 * <p>How a field is read into one is the source adapter's; what each state is shown as — the
 * value, "—" or "Error" — the SPA's.
 *
 * @param received how the field was received
 * @param value    its value when it is present, and {@code null} otherwise
 * @param <T>      the kind of value the field holds
 */
public record SourceField<T>(Received received, T value) {

    /** How a field of the source's record was received. */
    public enum Received {
        /** There, and of the field's kind. */
        PRESENT,
        /** Not there, {@code null}, or a blank string. */
        MISSING,
        /** There, but in the wrong format for the field. */
        INVALID
    }

    public SourceField {
        Objects.requireNonNull(received, "received");
        if ((received == Received.PRESENT) != (value != null)) {
            throw new IllegalArgumentException(
                    "A field is present exactly when it holds a value");
        }
    }

    /** A field received with {@code value}. */
    public static <T> SourceField<T> present(T value) {
        return new SourceField<>(Received.PRESENT, value);
    }

    /** A field the source did not send, or sent blank. */
    public static <T> SourceField<T> missing() {
        return new SourceField<>(Received.MISSING, null);
    }

    /** A field the source sent in the wrong format. */
    public static <T> SourceField<T> invalid() {
        return new SourceField<>(Received.INVALID, null);
    }

    /** Whether the field was received with a value. */
    public boolean isPresent() {
        return received == Received.PRESENT;
    }

    /** Its value when it is present, and {@code other} when it is missing or invalid. */
    public T orElse(T other) {
        return isPresent() ? value : other;
    }

    /** This field with its value, if it has one, made {@code read}'s; one not present stays as it was. */
    public <U> SourceField<U> map(Function<? super T, ? extends U> read) {
        return isPresent()
                ? present(read.apply(value))
                : new SourceField<>(received, null);
    }

    /**
     * The names of {@code fields} that are {@link Received#INVALID}, in the order given: what a
     * record says of its own fields whose format was wrong.
     */
    @SafeVarargs
    public static List<String> invalidAmong(Map.Entry<String, SourceField<?>>... fields) {
        return Arrays.stream(fields)
                .filter(field -> field.getValue().received() == Received.INVALID)
                .map(Map.Entry::getKey)
                .toList();
    }
}
