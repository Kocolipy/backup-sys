package com.example.backend.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.session.FindByIndexNameSessionRepository;

/**
 * The signed-in session's shape, over a map-backed session: the module reads and writes nothing
 * but the attribute map and the creation time, so a map stands in for the servlet container's
 * session.
 */
class SignedInSessionTests {

    private static final UUID ADA = UUID.fromString("0b7c6a52-5c0e-4a8f-9f61-3d2f9a1c7e10");

    /** Eight hours from a creation at 08:00Z: the lifetime ends at 16:00Z. */
    private static final AbsoluteSessionLifetimePolicy LIFETIME =
            new AbsoluteSessionLifetimePolicy(Duration.ofHours(8));

    // ---- the owner's stable id ------------------------------------------------------------------

    @Test
    void aSessionIndexedByAStableIdIsOwnedByIt() {
        MapBackedSession session = new MapBackedSession();
        session.attributes.put(SignedInSession.OWNER_ATTRIBUTE, "0b7c6a52-5c0e-4a8f-9f61-3d2f9a1c7e10");

        assertThat(SignedInSession.of(session).owner()).contains(ADA);
    }

    @Test
    void aSessionWithNoIndexIdentifiesNobody() {
        assertThat(SignedInSession.of(new MapBackedSession()).owner()).isEmpty();
    }

    /** A value that does not parse as a stable id is not one: nobody, never a failure. */
    @Test
    void aMalformedIndexIdentifiesNobody() {
        MapBackedSession session = new MapBackedSession();
        session.attributes.put(SignedInSession.OWNER_ATTRIBUTE, "not-an-id");

        assertThat(SignedInSession.of(session).owner()).isEmpty();
    }

    /** The index is written as a string; anything else under its name is not an id either. */
    @Test
    void anIndexOfAnotherTypeIdentifiesNobody() {
        MapBackedSession session = new MapBackedSession();
        session.attributes.put(SignedInSession.OWNER_ATTRIBUTE, ADA);

        assertThat(SignedInSession.of(session).owner()).isEmpty();
    }

    /**
     * The owner is kept under Spring Session's principal index, the attribute its indexed
     * repository finds a User's sessions by. Spelled out in the domain, which does not depend on
     * Spring Session, so this pins the two spellings together.
     */
    @Test
    void theOwnerIsKeptUnderSpringSessionsPrincipalIndex() {
        assertThat(SignedInSession.OWNER_ATTRIBUTE)
                .isEqualTo(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
    }

    // ---- signing in ----------------------------------------------------------------------------

    /** Grace: the User a later Login over Ada's session signs it in to. */
    private static final UUID GRACE = UUID.fromString("7d1e2f30-4a5b-4c6d-8e9f-a0b1c2d3e4f5");

    /** A Login at noon over a session created at 08:00Z. */
    private static final Instant NOON = Instant.parse("2026-10-10T12:00:00Z");

    /** The Groups a sign-in of Ada or Grace is made with, unless a test says otherwise. */
    private static final List<String> AUDITOR = List.of("Auditor");

    @Test
    void aSessionSignedInIsOwnedByItsUser() {
        SignedInSession signedIn = SignedInSession.of(new MapBackedSession());

        signedIn.signIn(ADA, NOON, AUDITOR);

        assertThat(signedIn.owner()).contains(ADA);
    }

    /**
     * Spring Session's indexed repository finds a session by the string its principal index
     * holds, so the owner is written as the stable id's canonical string.
     */
    @Test
    void signingInIndexesTheSessionByTheStableIdsString() {
        MapBackedSession session = new MapBackedSession();

        SignedInSession.of(session).signIn(ADA, NOON, AUDITOR);

        assertThat(session.attributes)
                .containsEntry(SignedInSession.OWNER_ATTRIBUTE, "0b7c6a52-5c0e-4a8f-9f61-3d2f9a1c7e10");
    }

    /**
     * Signing in records whose session it is, when the sign-in began and the Groups it was made
     * with, and nothing else.
     */
    @Test
    void signingInRecordsTheOwnerWhenItBeganAndItsGroupsAlone() {
        MapBackedSession session = new MapBackedSession();

        SignedInSession.of(session).signIn(ADA, NOON, AUDITOR);

        assertThat(session.attributes).containsOnlyKeys(
                "org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME",
                "app.signed-in-at",
                "app.signed-in-groups");
    }

    /**
     * Everything an earlier sign-in kept goes, whatever it was: what is left is the new sign-in
     * alone, the new owner, when it began and its Groups.
     */
    @Test
    void signingInOverAnEarlierSignInLeavesOnlyTheNewOne() {
        MapBackedSession stored = new MapBackedSession();
        SignedInSession session = SignedInSession.of(stored);
        session.signIn(ADA, Instant.parse("2026-10-10T09:00:00Z"), List.of("Admin", "Helpdesk"));

        session.signIn(GRACE, NOON, AUDITOR);

        assertThat(stored.attributes).containsOnly(
                Map.entry(SignedInSession.OWNER_ATTRIBUTE, "7d1e2f30-4a5b-4c6d-8e9f-a0b1c2d3e4f5"),
                Map.entry("app.signed-in-at", NOON),
                Map.entry("app.signed-in-groups", List.of("Auditor")));
    }

    /**
     * What is not the sign-in's own is left where it is: the security context the Login saved
     * just before signing the session in, say, which the session's own framework owns.
     */
    @Test
    void signingInLeavesWhatIsNotTheSignInsOwn() {
        MapBackedSession stored = new MapBackedSession();
        stored.attributes.put("SPRING_SECURITY_CONTEXT", "the saved context");

        SignedInSession.of(stored).signIn(ADA, NOON, AUDITOR);

        assertThat(stored.attributes).containsEntry("SPRING_SECURITY_CONTEXT", "the saved context");
    }

    // ---- the Groups the session was signed in with ----------------------------------------------

    /** The Groups are the sign-in's, in the order the sign-in was handed them. */
    @Test
    void aSessionReportsTheGroupsItWasSignedInWithInTheirOrder() {
        SignedInSession session = SignedInSession.of(new MapBackedSession());

        session.signIn(ADA, NOON, List.of("Helpdesk", "auditor", "Ward 7B"));

        assertThat(session.groups()).containsExactly("Helpdesk", "auditor", "Ward 7B");
    }

    /** A sign-in in no Group keeps that answer: none. */
    @Test
    void aSessionSignedInWithNoGroupsReportsNone() {
        SignedInSession session = SignedInSession.of(new MapBackedSession());

        session.signIn(ADA, NOON, List.of());

        assertThat(session.groups()).isEmpty();
    }

    @Test
    void aSessionNobodySignedInToReportsNoGroups() {
        assertThat(SignedInSession.of(new MapBackedSession()).groups()).isEmpty();
    }

    /** Something else under the Groups' name is no list of them: none, never a failure. */
    @Test
    void aGroupsAttributeOfAnotherTypeReportsNone() {
        MapBackedSession stored = new MapBackedSession();
        stored.attributes.put("app.signed-in-groups", "Auditor");

        assertThat(SignedInSession.of(stored).groups()).isEmpty();
    }

    /** A list holding anything but names is not the sign-in's either. */
    @Test
    void aGroupsListHoldingANonNameReportsNone() {
        MapBackedSession stored = new MapBackedSession();
        stored.attributes.put("app.signed-in-groups", List.of("Auditor", 7));

        assertThat(SignedInSession.of(stored).groups()).isEmpty();
    }

    /**
     * What is stored is the sign-in's own copy: a later change to the list the Login handed over
     * is not the session's.
     */
    @Test
    void theGroupsStoredAreTheSignInsOwnCopy() {
        SignedInSession session = SignedInSession.of(new MapBackedSession());
        List<String> handed = new ArrayList<>(List.of("Auditor"));

        session.signIn(ADA, NOON, handed);
        handed.add("Helpdesk");

        assertThat(session.groups()).containsExactly("Auditor");
    }

    // ---- the absolute session lifetime ---------------------------------------------------------

    /**
     * Counted from the Login, not from the session's creation: signed in at noon, a session
     * created at 08:00Z is still within its 8 hours at 16:00:01Z.
     */
    @Test
    void aSessionSignedInLaterHasNotOutlivedItsLifetimeEightHoursAfterItsCreation() {
        SignedInSession session = SignedInSession.of(new MapBackedSession());
        session.signIn(ADA, NOON, AUDITOR);

        assertThat(session.hasOutlived(LIFETIME, Instant.parse("2026-10-10T16:00:01Z"))).isFalse();
    }

    /** And has outlived it once 8 hours have passed since the Login. */
    @Test
    void aSessionSignedInLaterOutlivesItsLifetimeEightHoursAfterTheLogin() {
        SignedInSession session = SignedInSession.of(new MapBackedSession());
        session.signIn(ADA, NOON, AUDITOR);

        assertThat(session.hasOutlived(LIFETIME, Instant.parse("2026-10-10T20:00:01Z"))).isTrue();
    }

    /**
     * The Login that counts is the latest: a session signed in again at noon is measured from
     * noon, not from the first sign-in at 09:00Z.
     */
    @Test
    void aSessionSignedInAgainCountsFromTheLatestLogin() {
        SignedInSession session = SignedInSession.of(new MapBackedSession());
        session.signIn(ADA, Instant.parse("2026-10-10T09:00:00Z"), List.of("Admin", "Helpdesk"));
        session.signIn(GRACE, NOON, AUDITOR);

        assertThat(session.hasOutlived(LIFETIME, Instant.parse("2026-10-10T17:00:01Z"))).isFalse();
    }

    /**
     * Something else under the sign-in instant's name is no instant: the session counts from its
     * creation, as one nobody signed in to does.
     */
    @Test
    void aSignInInstantOfAnotherTypeCountsFromTheSessionsCreation() {
        MapBackedSession stored = new MapBackedSession();
        stored.attributes.put("app.signed-in-at", "not an instant");

        assertThat(SignedInSession.of(stored)
                .hasOutlived(LIFETIME, Instant.parse("2026-10-10T16:00:01Z"))).isTrue();
    }

    @Test
    void aSessionPastItsAbsoluteLifetimeHasOutlivedIt() {
        assertThat(SignedInSession.of(new MapBackedSession())
                .hasOutlived(LIFETIME, Instant.parse("2026-10-10T16:00:01Z"))).isTrue();
    }

    /** At the boundary the session is still within its lifetime, as the policy has it. */
    @Test
    void aSessionExactlyAtItsAbsoluteLifetimeHasNotOutlivedIt() {
        assertThat(SignedInSession.of(new MapBackedSession())
                .hasOutlived(LIFETIME, Instant.parse("2026-10-10T16:00:00Z"))).isFalse();
    }

    /**
     * A session as a map of attributes and a creation time: the two things the module reads and
     * writes.
     */
    static final class MapBackedSession implements SignedInSession.Attributes {

        final Map<String, Object> attributes = new HashMap<>();

        final Instant createdAt;

        MapBackedSession() {
            this(Instant.parse("2026-10-10T08:00:00Z"));
        }

        MapBackedSession(Instant createdAt) {
            this.createdAt = createdAt;
        }

        @Override
        public Object attribute(String name) {
            return attributes.get(name);
        }

        @Override
        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }

        @Override
        public void removeAttribute(String name) {
            attributes.remove(name);
        }

        @Override
        public Instant createdAt() {
            return createdAt;
        }
    }
}
