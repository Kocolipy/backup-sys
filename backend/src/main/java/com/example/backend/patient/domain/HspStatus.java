package com.example.backend.patient.domain;

/**
 * An HSP Record's Status, the patient source's own. It is never derived from the End Date: a
 * record past its End Date may still be Active, and nothing in the service works out "ended".
 */
public enum HspStatus implements SourceCode {
    ACTIVE("active"),
    INACTIVE("inactive");

    private final String code;

    HspStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
