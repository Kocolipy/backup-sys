package com.example.backend.auth.config;

import com.example.backend.audit.domain.AuditRefusalReason;
import com.example.backend.auth.application.LoginCounts;
import com.example.backend.auth.application.LoginOutcome;
import com.example.backend.auth.application.PasswordRefusalReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * The {@code login} counter (ADR 0001, "One {@code login} counter"), the adapter of
 * {@link LoginCounts}: one count per Login that ended, tagged by its {@code method} —
 * {@code password} — its {@code outcome} — {@code success} or {@code refused} — and its
 * {@code reason}.
 *
 * <p>A success carries {@code reason=none}, so every series of the meter has the same tag keys,
 * which Prometheus requires of one metric name; a refusal carries its reason by the
 * {@link AuditRefusalReason} constant's name, as the audit trail spells it. The reason is what
 * Logging §2.2 keeps out of the operational log, because it tells whether an account exists; a
 * count names no account, so it may carry it.
 *
 * <p>Every series exists from the start, at zero: an alert on a count's increase then sees the
 * first event too, which a series appearing at 1 would hide from it. Those are a success and
 * each of {@link PasswordRefusalReason}'s reasons, and nothing else.
 *
 * <p>In this configuration package because that is the adapter layer the onion layering lets
 * implement an application port ({@link LoginMetricsConfig}).
 */
public final class LoginMetrics implements LoginCounts {

    /** The meter's name. */
    static final String LOGIN = "login";

    /** The reason tag's value on a success, which has none. */
    static final String NO_REASON = "none";

    /** How a Login ended, as the {@code outcome} tag spells it. */
    private enum Outcome {
        SUCCESS("success"),
        REFUSED("refused");

        private final String tag;

        Outcome(String tag) {
            this.tag = tag;
        }
    }

    private final MeterRegistry registry;

    public LoginMetrics(MeterRegistry registry) {
        this.registry = registry;
        ending(Outcome.SUCCESS, NO_REASON);
        for (PasswordRefusalReason reason : PasswordRefusalReason.values()) {
            ending(Outcome.REFUSED, reason.audited().name());
        }
    }

    @Override
    public void signedIn() {
        // The registry hands back the one meter per outcome and reason, registering it once.
        ending(Outcome.SUCCESS, NO_REASON).increment();
    }

    @Override
    public void refused(AuditRefusalReason reason) {
        ending(Outcome.REFUSED, reason.name()).increment();
    }

    private Counter ending(Outcome outcome, String reason) {
        return Counter.builder(LOGIN)
                .description("Logins that ended, by login method, outcome and refusal reason")
                .tag("method", LoginOutcome.loginMethod())
                .tag("outcome", outcome.tag)
                .tag("reason", reason)
                .register(registry);
    }
}
