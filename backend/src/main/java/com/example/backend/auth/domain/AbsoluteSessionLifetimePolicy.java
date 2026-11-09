package com.example.backend.auth.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * How long a session is honoured after it began, regardless of how recently
 * it was used. A signed-in session begins at the Login that signed it in, and
 * one nobody signed in to at its creation; {@link SignedInSession} decides
 * which, and hands this policy the instant.
 *
 * <p>Distinct from the idle timeout the servlet container already enforces
 * ({@code server.servlet.session.timeout}): idle timeout resets on every
 * request, so a session kept continuously active never expires on that basis
 * alone. This is the other half — a fixed ceiling from its start, so a session
 * cannot be kept alive indefinitely just by using it often enough.
 *
 * <p>Pure and clock-free for the same reason {@link LockoutPolicy} is: a test
 * exercises the boundary by choosing {@code start} and {@code now}
 * directly, with nothing to wait for and no system clock to fake.
 */
public record AbsoluteSessionLifetimePolicy(Duration maxLifetime) {

    public AbsoluteSessionLifetimePolicy {
        if (maxLifetime == null || maxLifetime.isZero() || maxLifetime.isNegative()) {
            throw new IllegalArgumentException(
                    "An absolute session lifetime needs a positive duration");
        }
    }

    /**
     * Whether a session that began at {@code start} has outlived its allowance
     * as of {@code now}. Equal to the boundary counts as not yet expired, the
     * same convention {@link Account#isLocked} uses for its own instant
     * comparison.
     */
    public boolean isExpired(Instant start, Instant now) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(now, "now");
        return start.plus(maxLifetime).isBefore(now);
    }
}
