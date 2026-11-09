package com.example.backend.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AbsoluteSessionLifetimePolicyTests {

    private static final Instant CREATED_AT = Instant.parse("2026-09-24T07:00:00Z");

    private static final AbsoluteSessionLifetimePolicy POLICY =
            new AbsoluteSessionLifetimePolicy(Duration.ofHours(8));

    @Test
    void aSessionYoungerThanTheLifetimeIsNotExpired() {
        assertThat(POLICY.isExpired(CREATED_AT, CREATED_AT.plus(Duration.ofHours(7))))
                .isFalse();
    }

    @Test
    void aSessionExactlyAtTheLifetimeIsNotYetExpired() {
        assertThat(POLICY.isExpired(CREATED_AT, CREATED_AT.plus(Duration.ofHours(8))))
                .isFalse();
    }

    @Test
    void aSessionPastTheLifetimeIsExpired() {
        assertThat(POLICY.isExpired(
                CREATED_AT, CREATED_AT.plus(Duration.ofHours(8)).plusMillis(1)))
                .isTrue();
    }

    @Test
    void aPolicyMustHaveAPositiveLifetime() {
        assertThatThrownBy(() -> new AbsoluteSessionLifetimePolicy(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AbsoluteSessionLifetimePolicy(Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AbsoluteSessionLifetimePolicy(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** The instant the session began: the Login that signed it in, or its creation. */
    @Test
    void aStartInstantIsRequired() {
        assertThatThrownBy(() -> POLICY.isExpired(null, CREATED_AT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("start");
    }

    @Test
    void aCurrentInstantIsRequired() {
        assertThatThrownBy(() -> POLICY.isExpired(CREATED_AT, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("now");
    }
}
