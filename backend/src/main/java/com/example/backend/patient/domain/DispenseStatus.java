package com.example.backend.patient.domain;

/** A Medication Record's Dispense Status: whether its medication was dispensed and collected. */
public enum DispenseStatus implements SourceCode {
    PENDING("pending"),
    DISPENSED("dispensed"),
    UNCOLLECTED("uncollected");

    private final String code;

    DispenseStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
