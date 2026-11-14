package com.example.backend.patient.domain;

/** A NUMC Record's NUMC Type: the underwater duty the patient was assessed for. */
public enum NumcType implements SourceCode {
    HYPERBARIC_CHAMBER("hyperbaric-chamber"),
    SUBMARINER("submariner"),
    DIVING("diving");

    private final String code;

    NumcType(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
