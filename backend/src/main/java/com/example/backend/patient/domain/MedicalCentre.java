package com.example.backend.patient.domain;

/**
 * A Medical Centre: a camp medical centre. An FFI's Submitting MC and a Medication Record's
 * Dispensary each name one.
 */
public enum MedicalCentre implements SourceCode {
    NEE_SOON_CAMP("nee-soon-camp"),
    KRANJI_CAMP("kranji-camp"),
    HENDON_CAMP("hendon-camp"),
    MANDAI_HILL_CAMP("mandai-hill-camp"),
    KHATIB_CAMP("khatib-camp");

    private final String code;

    MedicalCentre(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
