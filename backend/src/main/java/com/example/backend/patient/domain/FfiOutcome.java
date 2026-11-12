package com.example.backend.patient.domain;

/** An FFI's Outcome: whether the patient was found fit for the instruction. */
public enum FfiOutcome implements FfiCode {
    FIT("fit"),
    REQUEST_WAIVER("request-waiver"),
    UNFIT("unfit");

    private final String code;

    FfiOutcome(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
