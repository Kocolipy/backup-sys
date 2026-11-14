package com.example.backend.patient.domain;

import java.util.List;
import java.util.Objects;

/**
 * One page of a patient's ARMC History, as the patient source answered it: the page's ARMC
 * Boards, in the source's own order, and how many the patient has in all. The service pages and
 * orders nothing itself (ADR 0019, D6).
 *
 * @param totalResults how many ARMC Boards the patient has, every page together
 * @param boards       the page's ARMC Boards, in the source's order
 */
public record ArmcHistory(int totalResults, List<ArmcBoard> boards) {

    public ArmcHistory {
        if (totalResults < 0) {
            throw new IllegalArgumentException("A patient has no fewer than no ARMC Boards");
        }
        boards = List.copyOf(Objects.requireNonNull(boards, "boards"));
    }
}
