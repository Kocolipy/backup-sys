package com.example.backend.patient.domain;

/** An FFI's Submitting MC: the camp medical centre that submitted it. */
public enum FfiSubmittingMc implements SourceCode {
    NEE_SOON_CAMP("nee-soon-camp"),
    KRANJI_CAMP("kranji-camp"),
    HENDON_CAMP("hendon-camp"),
    MANDAI_HILL_CAMP("mandai-hill-camp");

    private final String code;

    FfiSubmittingMc(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
