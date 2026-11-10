package com.example.backend.audit.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for "who is the signed-in User this request is served for", for an event whose actor is
 * the caller and whose module has no other way to name it — a Patient search, whose module knows
 * nothing of sessions (ADR 0014, "The module's edges").
 *
 * <p>A port for the reason {@link AuditRequestContext} is one: the caller states what it needs,
 * and the one adapter that may read a signed-in session's attributes — the session module's own —
 * owns how. The answer is a stable id, as every actor an event names is.
 */
public interface AuditActorContext {

    /**
     * The stable id of the User the current request's session is signed in to, or none when this
     * thread serves no request, or one with no session or a session nobody signed in to.
     */
    Optional<UUID> signedInUser();
}
