package com.example.backend.patient.domain;

import java.util.Arrays;
import java.util.function.Function;

/**
 * Reads a request parameter's text as the member of a closed set it spells, exactly: a sort's
 * column or direction, or a filter's code ({@link SourceCode#ofParameter}). A blank text names
 * none, and any other spelling is refused.
 */
final class Spelling {

    private Spelling() {
    }

    /**
     * The member of {@code values} whose {@code spelling} is exactly {@code text}, or {@code null}
     * for a blank text.
     *
     * @throws IllegalArgumentException with {@code refusal}, for any other spelling
     */
    static <E> E spelledAs(E[] values, String text, Function<E, String> spelling,
            String refusal) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return Arrays.stream(values)
                .filter(value -> spelling.apply(value).equals(text))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(refusal));
    }
}
