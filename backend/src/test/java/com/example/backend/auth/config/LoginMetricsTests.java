package com.example.backend.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.audit.domain.AuditRefusalReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@link LoginMetrics}, the adapter the alert rules read: one {@code login} counter, whose series
 * exist from startup at zero, spelled as the rules spell them, each with the same tag keys, and
 * each count moving its one series.
 */
class LoginMetricsTests {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final LoginMetrics metrics = new LoginMetrics(registry);

    /**
     * An alert on a count's increase sees the first event only if the series was there at 0. The
     * series are spelled out as the alert rules spell them, and are exactly these: a success and
     * each refusal reason, so a reason added to the closed set fails here until it is.
     */
    @Test
    void exactlyTheseSeriesExistAtZeroFromTheStart() {
        assertThat(registry.find("login").counters())
                .extracting(counter -> counter.getId().getTag("method") + ","
                        + counter.getId().getTag("outcome") + "," + counter.getId().getTag("reason"))
                .containsExactlyInAnyOrder(
                        "password,success,none",
                        "password,refused,BAD_CREDENTIALS",
                        "password,refused,UNKNOWN_ACCOUNT",
                        "password,refused,ACCOUNT_LOCKED",
                        "password,refused,ACCOUNT_DISABLED",
                        "password,refused,OTHER");
        assertThat(totalEndings()).isZero();
    }

    /** Prometheus requires every series of one metric name to carry the same tag keys. */
    @Test
    void everySeriesCarriesTheSameTagKeys() {
        assertThat(registry.find("login").counters()).allSatisfy(counter ->
                assertThat(tagKeys(counter)).containsExactlyInAnyOrder("method", "outcome", "reason"));
    }

    /** The scrape's {@code # HELP} line for the meter, as an operator reads it. */
    @Test
    void theMeterSaysWhatItCounts() {
        assertThat(ending("password", "success", "none").getId().getDescription())
                .isEqualTo("Logins that ended, by login method, outcome and refusal reason");
    }

    @Test
    void aSignedInLoginIsCountedAsASuccessAndNothingElse() {
        metrics.signedIn();

        assertThat(ending("password", "success", "none").count()).isEqualTo(1.0);
        assertThat(totalEndings()).isEqualTo(1.0);
    }

    @ParameterizedTest
    @EnumSource(value = AuditRefusalReason.class,
            names = {"BAD_CREDENTIALS", "UNKNOWN_ACCOUNT", "ACCOUNT_LOCKED"})
    void aRefusalIsCountedUnderItsReasonAndNothingElse(AuditRefusalReason reason) {
        metrics.refused(reason);

        assertThat(ending("password", "refused", reason.name()).count()).isEqualTo(1.0);
        assertThat(totalEndings()).isEqualTo(1.0);
    }

    private Counter ending(String method, String outcome, String reason) {
        Counter counter = registry.find("login")
                .tag("method", method).tag("outcome", outcome).tag("reason", reason).counter();
        assertThat(counter).as("login{method=%s,outcome=%s,reason=%s}", method, outcome, reason)
                .isNotNull();
        return counter;
    }

    private double totalEndings() {
        return registry.find("login").counters().stream().mapToDouble(Counter::count).sum();
    }

    private static Set<String> tagKeys(Meter meter) {
        return meter.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet());
    }
}
