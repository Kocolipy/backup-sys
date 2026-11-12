package com.example.backend.patient.domain;

/**
 * An FFI's Status, the patient source's own. It is never derived from the Expiry Date: an FFI
 * past its Expiry Date may still be Active, and nothing in the service works out "expired".
 */
public enum FfiStatus implements FfiCode {
    ACTIVE("active"),
    INACTIVE("inactive");

    private final String code;

    FfiStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
