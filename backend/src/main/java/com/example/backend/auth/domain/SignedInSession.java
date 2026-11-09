package com.example.backend.auth.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The signed-in session's own state, and the one place that reads or writes it: whose session it
 * is, and when it was signed in. Its callers ask it questions — who owns this session, has it
 * outlived its lifetime — rather than reading the attribute map, so the shape is defined once and every caller gets the same answer from it.
 *
 * <ul>
 *   <li><b>Signing in starts from nothing.</b> Every attribute this module writes belongs to one
 *       sign-in, and {@link #signIn} removes them all before it records the new one, so a Login
 *       over a session another Login signed in carries none of the earlier sign-in's state over:
 *       not its owner, not its sign-in instant. The module writes only the members of one closed set,
 *       so an attribute added later is one more member, and reset with the rest. What the module
 *       does not write — the security context, the CSRF token, the framework's own — is left
 *       to its owner.
 *   <li><b>The owner</b> is the User's stable id (the SCIM resource id), written at sign-in under
 *       Spring Session's principal index, which the indexed repository finds a User's sessions by,
 *       so a revocation reaches the session by an id that survives a later username change. It is
 *       parsed here and nowhere else: a session with no index, an index of another type, or one
 *       that is not a well-formed id, identifies nobody — the answer an anonymous request gets —
 *       never a failure.
 *   <li><b>The absolute session lifetime</b> counts from the Login that signed the session in:
 *       the instant {@link #signIn} records. A session nobody signed in to — a Guest's, minted to
 *       hold a CSRF token — counts from its creation, and so does one whose sign-in instant is
 *       missing or of another type, which only shortens its life.
 * </ul>
 *
 * <p>Domain code over a session it does not own: the session is whatever {@link Attributes} the
 * caller adapts — the servlet container's {@code HttpSession} at a web adapter — so the shape
 * stays plain Java, and its tests
 * run over a map. Clock-free, as {@link AbsoluteSessionLifetimePolicy} is: a question about the
 * lifetime is asked of a policy at an instant the caller read from the injected clock, and a
 * sign-in is recorded at one.
 */
public final class SignedInSession {

    /**
     * The attribute the owner's stable id is written under: Spring Session's principal index,
     * {@code FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME}. Spelled out because the
     * domain does not depend on Spring Session; {@code SignedInSessionTests} pins the two
     * together.
     */
    public static final String OWNER_ATTRIBUTE =
            "org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME";

    /**
     * The attribute the instant the session was signed in at is kept under, which its absolute
     * lifetime counts from.
     */
    public static final String SIGNED_IN_AT_ATTRIBUTE = "app.signed-in-at";

    /**
     * Every attribute this module reads or writes, each of which belongs to one sign-in: the only
     * names {@link #read} and {@link #write} accept, and the ones {@link #signIn} removes. A new attribute is a new
     * member, so it is reset with the rest by construction.
     */
    private enum SignInAttribute {
        OWNER(OWNER_ATTRIBUTE),
        SIGNED_IN_AT(SIGNED_IN_AT_ATTRIBUTE);

        private final String name;

        SignInAttribute(String name) {
            this.name = name;
        }
    }

    /**
     * A session as this module sees it: an attribute map and a creation time — what the servlet
     * container's session offers, so an adapter implements it by delegating.
     */
    public interface Attributes {

        /** The attribute stored under {@code name}, or {@code null} for none. */
        Object attribute(String name);

        /** Stores {@code value} under {@code name}. */
        void setAttribute(String name, Object value);

        /** Removes whatever is stored under {@code name}; nothing happens when there is none. */
        void removeAttribute(String name);

        /** When the session was created. */
        Instant createdAt();
    }

    private final Attributes session;

    private SignedInSession(Attributes session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    /** The session {@code session} adapts, as this module reads and writes it. */
    public static SignedInSession of(Attributes session) {
        return new SignedInSession(session);
    }

    /**
     * Signs the session in to {@code owner} at {@code at}: everything an earlier sign-in kept on
     * it is removed, the session is indexed by the User's stable id, and its absolute lifetime
     * counts from {@code at}.
     *
     * @param at the instant the Login signed the session in, read from the injected clock
     */
    public void signIn(UUID owner, Instant at) {
        for (SignInAttribute earlier : SignInAttribute.values()) {
            session.removeAttribute(earlier.name);
        }
        write(SignInAttribute.OWNER, owner.toString());
        write(SignInAttribute.SIGNED_IN_AT, at);
    }

    /**
     * The User whose session this is, by stable id.
     *
     * @return the owner; empty for a session nobody signed in to, and for one whose index is not a
     *     well-formed stable id, either of which identifies nobody
     */
    public Optional<UUID> owner() {
        if (!(read(SignInAttribute.OWNER) instanceof String indexed)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(indexed));
        } catch (IllegalArgumentException notAnId) {
            return Optional.empty();
        }
    }

    /** Whether the session has outlived {@code lifetime} as of {@code now}. */
    public boolean hasOutlived(AbsoluteSessionLifetimePolicy lifetime, Instant now) {
        return lifetime.isExpired(lifetimeStart(), now);
    }

    /**
     * What the absolute lifetime counts from: the instant the session was signed in at, or its
     * creation for a session holding none — nobody signed in to it, or what is there is no instant.
     */
    private Instant lifetimeStart() {
        return read(SignInAttribute.SIGNED_IN_AT) instanceof Instant signedInAt
                ? signedInAt
                : session.createdAt();
    }

    /** Every read this module makes: of a member of the one set {@link #signIn} resets. */
    private Object read(SignInAttribute attribute) {
        return session.attribute(attribute.name);
    }

    /** Every write this module makes: under a member of the one set {@link #signIn} resets. */
    private void write(SignInAttribute attribute, Object value) {
        session.setAttribute(attribute.name, value);
    }
}
