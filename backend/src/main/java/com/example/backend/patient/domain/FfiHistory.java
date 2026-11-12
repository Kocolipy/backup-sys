package com.example.backend.patient.domain;

import java.util.List;
import java.util.Objects;

/**
 * One page of a patient's FFI History, as the patient source answered it: the page's FFIs, in the
 * source's own order, and how many FFIs the patient has in all. The service pages and orders
 * nothing itself (ADR 0019).
 *
 * @param totalResults how many FFIs the patient has, every page together
 * @param ffis         the page's FFIs, in the source's order
 */
public record FfiHistory(int totalResults, List<Ffi> ffis) {

    public FfiHistory {
        if (totalResults < 0) {
            throw new IllegalArgumentException("A patient has no fewer than no FFIs");
        }
        ffis = List.copyOf(Objects.requireNonNull(ffis, "ffis"));
    }
}
