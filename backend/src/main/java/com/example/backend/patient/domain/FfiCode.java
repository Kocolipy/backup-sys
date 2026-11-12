package com.example.backend.patient.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * A member of one of an FFI's closed sets — its FFI Type, Outcome, Higher Authority, Submitting MC
 * or Status — carried on the wire, from the patient source and to the SPA alike, as its code
 * (/docs/domain/ffi.md, "The closed sets"). The SPA names each code with its label.
 */
public interface FfiCode {

    /** The code the set's member is carried as. */
    String code();

    /** The member of {@code set} that {@code code} spells, if any does: a code is matched exactly. */
    static <E extends Enum<E> & FfiCode> Optional<E> of(Class<E> set, String code) {
        return Arrays.stream(set.getEnumConstants())
                .filter(member -> member.code().equals(code))
                .findFirst();
    }
}
