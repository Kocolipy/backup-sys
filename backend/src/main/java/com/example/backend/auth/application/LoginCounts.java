package com.example.backend.auth.application;

import com.example.backend.audit.domain.AuditRefusalReason;

/**
 * The counts a Login's ending moves — the one {@code login} counter (ADR 0001, "One
 * {@code login} counter") — for {@link LoginOutcomeService} to move without knowing the meter
 * registry: a port, whose adapter is the Micrometer meter.
 */
public interface LoginCounts {

    /** A Login signed a User in. */
    void signedIn();

    /** A Login was refused for {@code reason}, as the audit trail recorded it. */
    void refused(AuditRefusalReason reason);
}
