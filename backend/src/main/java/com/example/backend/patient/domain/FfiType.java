package com.example.backend.patient.domain;

/** An FFI's FFI Type: what the Fit for Instruction is for. */
public enum FfiType implements FfiCode {
    OVERSEAS_EXERCISES("overseas-exercises"),
    CAT_A_OCS_SCS("cat-a-ocs-scs"),
    IPPT("ippt"),
    DENTAL("dental"),
    ORD_RELEASE_FROM_SERVICE("ord-release-from-service"),
    VOCATIONAL("vocational");

    private final String code;

    FfiType(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
