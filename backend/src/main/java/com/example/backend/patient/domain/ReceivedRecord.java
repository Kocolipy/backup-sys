package com.example.backend.patient.domain;

import java.util.List;

/**
 * A record of a patient history shown as the patient source sent it (ADR 0020): each of its
 * elements a {@link SourceField}, and none refused for what it holds.
 */
public interface ReceivedRecord {

    /**
     * The names of its elements whose format was wrong, as the API spells them, in the record's
     * own order: none for a record whose every element is present or missing.
     */
    List<String> invalid();
}
