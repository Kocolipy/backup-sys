package com.example.backend.patient.domain;

/**
 * An ARMC Board's ARMC Outcome: whether the board found the patient fit. A set of its own, apart
 * from an FFI's Outcome and a NUMC Record's NUMC Outcome.
 */
public enum ArmcOutcome implements SourceCode {
    FIT("fit"),
    UNFIT("unfit");

    private final String code;

    ArmcOutcome(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
