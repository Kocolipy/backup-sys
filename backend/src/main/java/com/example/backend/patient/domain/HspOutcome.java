package com.example.backend.patient.domain;

/** An HSP Record's Outcome: the risk the patient was assessed at. */
public enum HspOutcome implements SourceCode {
    LOW("low"),
    MODERATE("moderate"),
    HIGH("high");

    private final String code;

    HspOutcome(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
