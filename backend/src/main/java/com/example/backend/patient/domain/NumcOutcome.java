package com.example.backend.patient.domain;

/**
 * A NUMC Record's NUMC Outcome: whether the patient was found fit for the underwater duty. A set
 * of its own, apart from an FFI's Outcome.
 */
public enum NumcOutcome implements SourceCode {
    FIT("fit"),
    UNFIT("unfit"),
    WAIVER_GRANTED("waiver-granted"),
    WAIVER_REJECTED("waiver-rejected");

    private final String code;

    NumcOutcome(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
