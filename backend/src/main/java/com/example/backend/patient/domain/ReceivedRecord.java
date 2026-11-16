package com.example.backend.patient.domain;

import java.util.List;

/**
 * A record shown as the patient source sent it (ADR 0020) — a patient history's, a Patient read
 * by id, or a search's match, whose Last visit is — each such element a {@link SourceField}, and
 * none refused for what it holds.
 */
public interface ReceivedRecord {

    /**
     * The names of its elements whose format was wrong, as the API spells them, in the record's
     * own order: none for a record whose every element is present or missing.
     */
    List<String> invalid();
}
