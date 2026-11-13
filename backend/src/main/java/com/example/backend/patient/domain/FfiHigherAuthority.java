package com.example.backend.patient.domain;

/** An FFI's Higher Authority: the medical authority above the medical centre that issued it. */
public enum FfiHigherAuthority implements SourceCode {
    CAMO("camo"),
    CARMO("carmo"),
    CNMO("cnmo"),
    COMD_MMI("comd-mmi"),
    DIS("dis");

    private final String code;

    FfiHigherAuthority(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
