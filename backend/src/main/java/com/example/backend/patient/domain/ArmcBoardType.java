package com.example.backend.patient.domain;

/** An ARMC Board's ARMC Board Type: the kind of aeromedical fitness board the patient sat. */
public enum ArmcBoardType implements SourceCode {
    ACES("aces");

    private final String code;

    ArmcBoardType(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
