package com.example.backend.audit.domain;

/**
 * How a Login proved who was signing in: the login method a {@code LOGIN_SUCCESS} and a
 * {@code LOGIN_FAILURE} carry, and the operational {@code session-start} with them.
 *
 * <p>A closed set, as every classification crossing the {@link AuditTrail} is, and the one the
 * {@code login_method} column's CHECK constraint holds. Password Login is the only login method
 * there is; {@link #SSO} is kept, with {@link AuditMfaFactor}, for an SSO Login.
 */
public enum AuditLoginMethod {

    /** Password Login: submitted credentials, checked here. */
    PASSWORD("password"),

    /** SSO Login: the credential checked by an external identity provider, not here. */
    SSO("sso");

    private final String value;

    AuditLoginMethod(String value) {
        this.value = value;
    }

    /** The method as it is recorded and reported: {@code password} or {@code sso}. */
    public String value() {
        return value;
    }
}
