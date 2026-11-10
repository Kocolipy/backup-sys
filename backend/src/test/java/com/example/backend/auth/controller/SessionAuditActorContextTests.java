package com.example.backend.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Who an event recorded while serving a request names as its actor: the User the request's
 * session was signed in to, read through the signed-in session module, or nobody.
 */
class SessionAuditActorContextTests {

    private static final UUID OWNER = UUID.fromString("0c9f3e1a-7b2d-4c5e-8f6a-2d1b3c4e5f60");

    private final SessionAuditActorContext actors = new SessionAuditActorContext();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static MockHttpServletRequest serving() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    @Test
    void aRequestServedUnderASignedInSessionNamesItsOwner() {
        HttpSessionAttributes.signedIn(serving().getSession())
                .signIn(OWNER, Instant.parse("2026-10-10T08:00:00Z"));

        assertThat(actors.signedInUser()).contains(OWNER);
    }

    /** A request with no session names nobody, and is not given one by being asked. */
    @Test
    void aRequestWithNoSessionNamesNobody() {
        MockHttpServletRequest request = serving();

        // asList, not List.of: the second value, the session the request still has not, is null.
        assertThat(Arrays.asList(actors.signedInUser(), request.getSession(false)))
                .containsExactly(Optional.empty(), null);
    }

    @Test
    void aSessionNobodySignedInToNamesNobody() {
        serving().getSession();

        assertThat(actors.signedInUser()).isEmpty();
    }

    /** A scheduled job's thread serves no request at all. */
    @Test
    void noRequestNamesNobody() {
        assertThat(actors.signedInUser()).isEmpty();
    }
}
