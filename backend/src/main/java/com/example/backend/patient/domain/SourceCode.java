package com.example.backend.patient.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * A member of a closed set the patient source carries by code: on the wire, from the source and
 * to the SPA alike, the member travels as its code, never its label or its Java name. The SPA
 * names each code with its label.
 */
public interface SourceCode {

    /** The code the set's member is carried as. */
    String code();

    /** The member of {@code set} that {@code code} spells, if any does: a code is matched exactly. */
    static <E extends Enum<E> & SourceCode> Optional<E> of(Class<E> set, String code) {
        return Arrays.stream(set.getEnumConstants())
                .filter(member -> member.code().equals(code))
                .findFirst();
    }

    /**
     * The member of {@code set} that a request parameter's {@code code} spells, exactly, or
     * {@code null} for a blank one, which is no filter.
     *
     * @throws IllegalArgumentException for any other spelling: a label, another case, a code of
     *                                  no member
     */
    static <E extends Enum<E> & SourceCode> E ofParameter(Class<E> set, String code) {
        return Spelling.spelledAs(set.getEnumConstants(), code, SourceCode::code,
                "A code is outside its closed set");
    }
}
