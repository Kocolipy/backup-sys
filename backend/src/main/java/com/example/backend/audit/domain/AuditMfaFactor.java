package com.example.backend.audit.domain;

/**
 * The multi-factor authentication an SSO Login's {@code LOGIN_SUCCESS} was made with: a closed
 * set, as every classification crossing the {@link AuditTrail} is, and the one the
 * {@code mfa_factor} column's CHECK constraint holds, so no value an identity provider sent can
 * reach the trail as text.
 *
 * <p>An identity provider's attestation that its sign-in enforces MFA is {@link #IDP_ATTESTED};
 * otherwise the factor is an RFC 8176 Authentication Method Reference value that is a possession
 * or inherence factor, or {@link #MFA} when the provider said only {@code mfa}. A password
 * ({@code pwd}) or a PIN is never a second factor, and so never the one recorded. A password
 * Login carries none.
 */
public enum AuditMfaFactor {

    /** The identity provider attests that its sign-in enforces MFA. */
    IDP_ATTESTED("idp-attested"),

    /** {@code amr} said MFA was used, naming no factor. */
    MFA("mfa"),

    /** A one-time password. */
    OTP("otp"),

    /** A hardware-secured key. */
    HWK("hwk"),

    /** A software-secured key. */
    SWK("swk"),

    /** A code sent by SMS. */
    SMS("sms"),

    /** A telephone call. */
    TEL("tel"),

    /** A smart card. */
    SC("sc"),

    /** A fingerprint. */
    FPT("fpt"),

    /** Facial recognition. */
    FACE("face"),

    /** An iris scan. */
    IRIS("iris"),

    /** A retina scan. */
    RETINA("retina"),

    /** Voice biometrics. */
    VBM("vbm");

    private final String value;

    AuditMfaFactor(String value) {
        this.value = value;
    }

    /** The factor as it is recorded and reported, e.g. {@code idp-attested} or {@code otp}. */
    public String value() {
        return value;
    }
}
