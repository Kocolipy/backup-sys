package com.example.backend.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.example.backend.audit.CapturedLog;
import com.example.backend.auth.MutableClock;
import com.example.backend.auth.domain.AbsoluteSessionLifetimePolicy;
import com.example.backend.auth.domain.SignedInSession;
import com.example.backend.observability.EndedSession;
import com.example.backend.observability.LogContext;
import com.example.backend.observability.LogEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;

class AbsoluteSessionLifetimeFilterTests {

    private static final AbsoluteSessionLifetimePolicy POLICY =
            new AbsoluteSessionLifetimePolicy(Duration.ofHours(8));

    /**
     * A request with no session at all: the filter must not create one just to
     * check its age, and must let the request through unchanged.
     */
    @Test
    void doesNothingWhenThereIsNoSession() throws Exception {
        MutableClock clock = new MutableClock(Instant.now());
        AbsoluteSessionLifetimeFilter filter = new AbsoluteSessionLifetimeFilter(POLICY, clock);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(request.getSession(false)).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    /**
     * Past the policy's ceiling, the session is invalidated and the security
     * context cleared before the rest of the chain runs — so a protected path
     * sees the request exactly as it would an unauthenticated one, rather than a
     * distinct failure mode.
     */
    @Test
    void invalidatesASessionPastTheLifetimeAndClearsTheSecurityContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("ada", null, null));
        try {
            MutableClock clock = new MutableClock(
                    Instant.ofEpochMilli(session.getCreationTime())
                            .plus(Duration.ofHours(8))
                            .plusMillis(1));
            AbsoluteSessionLifetimeFilter filter =
                    new AbsoluteSessionLifetimeFilter(POLICY, clock);

            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

            assertThat(session.isInvalid()).isTrue();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void aSessionExactlyAtTheLifetimeIsNotYetInvalidated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession();
        MutableClock clock = new MutableClock(
                Instant.ofEpochMilli(session.getCreationTime()).plus(Duration.ofHours(8)));
        AbsoluteSessionLifetimeFilter filter = new AbsoluteSessionLifetimeFilter(POLICY, clock);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isFalse();
    }

    // ---- session-end record ------------------------------------------------------------

    /**
     * An expiry is one INFO {@code session-end} record whose cause is the absolute lifetime and
     * whose {@code user.id} is the session's own account, read from its principal index before
     * the session went; the request is marked so the entry point can say "expired".
     */
    @Test
    void anExpiryIsOneSessionEndRecordNamingTheSessionsAccount() throws Exception {
        UUID owner = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession();
        session.setAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, owner.toString());
        AbsoluteSessionLifetimeFilter filter = new AbsoluteSessionLifetimeFilter(POLICY,
                new MutableClock(Instant.ofEpochMilli(session.getCreationTime())
                        .plus(Duration.ofHours(9))));

        List<ILoggingEvent> records;
        try (CapturedLog captured = CapturedLog.attach()) {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
            records = captured.withAction(Level.TRACE, LogEvent.KIND, "event");
        }

        assertThat(session.isInvalid()).isTrue();
        assertThat(EndedSession.markedOn(request)).isTrue();
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.getLevel()).isEqualTo(Level.INFO);
            assertThat(record.getFormattedMessage()).isEqualTo("Session ended");
            assertThat(CapturedLog.fields(record))
                    .containsEntry(LogEvent.ACTION, "session-end")
                    .containsEntry(LogEvent.CATEGORY, List.of("process"))
                    .containsEntry(LogEvent.TYPE, List.of("end"))
                    .containsEntry(LogEvent.OUTCOME, "success")
                    .containsEntry(LogEvent.REASON, "absolute-lifetime");
            assertThat(record.getMDCPropertyMap())
                    .containsEntry(LogContext.USER_ID, owner.toString());
        });
        assertThat(MDC.get(LogContext.USER_ID)).as("the scope closes").isNull();
    }

    /** A session nobody signed in to, or whose index is not an id, ends naming nobody. */
    @Test
    void anExpiredSessionWithNoAccountEndsNamingNobody() throws Exception {
        for (Object index : new Object[] {null, "not-a-uuid"}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpSession session = (MockHttpSession) request.getSession();
            session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, index);
            AbsoluteSessionLifetimeFilter filter = new AbsoluteSessionLifetimeFilter(POLICY,
                    new MutableClock(Instant.ofEpochMilli(session.getCreationTime())
                            .plus(Duration.ofHours(9))));

            List<ILoggingEvent> records;
            try (CapturedLog captured = CapturedLog.attach()) {
                filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
                records = captured.withAction(Level.TRACE, LogEvent.KIND, "event");
            }

            assertThat(records).singleElement().satisfies(record ->
                    assertThat(record.getMDCPropertyMap()).doesNotContainKey(LogContext.USER_ID));
        }
    }

    /** An unexpired session ends nothing and is recorded nowhere. */
    @Test
    void anUnexpiredSessionWritesNoRecordAndIsNotMarked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession();
        AbsoluteSessionLifetimeFilter filter = new AbsoluteSessionLifetimeFilter(POLICY,
                new MutableClock(Instant.ofEpochMilli(session.getCreationTime())));

        try (CapturedLog captured = CapturedLog.attach()) {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

            assertThat(captured.withAction(Level.TRACE, LogEvent.KIND, "event")).isEmpty();
        }
        assertThat(EndedSession.markedOn(request)).isFalse();
    }
}
