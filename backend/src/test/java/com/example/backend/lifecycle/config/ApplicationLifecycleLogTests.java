package com.example.backend.lifecycle.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.example.backend.audit.CapturedLog;
import com.example.backend.audit.domain.AuditRetentionPolicy;
import com.example.backend.observability.LogEvent;
import com.example.backend.scim.config.ScimReleaseGate;
import com.example.backend.scim.domain.DormancyPolicy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

/**
 * The two lifecycle records' content, from the listener alone: each field the startup record
 * promises, the shutdown record's uptime, and the rule that a listener answers only for its
 * own context. {@code ApplicationLifecycleLogIntegrationTests} shows the same records arriving
 * from a real application's start and close.
 */
class ApplicationLifecycleLogTests {

    private final GenericApplicationContext context = new GenericApplicationContext();

    private final MockEnvironment environment = new MockEnvironment();

    private CapturedLog logs;

    @BeforeEach
    void setUp() {
        context.refresh();
        environment.setActiveProfiles("prod", "feature-x");
        logs = CapturedLog.attach();
    }

    @AfterEach
    void tearDown() {
        logs.close();
        context.close();
    }

    @Test
    void theStartupRecordStatesTheHostTheProfilesAndTheBehaviouralSettings() throws Exception {
        listener(true).started(ready(context));

        ILoggingEvent record = onlyRecord("application-startup");
        Map<String, Object> fields = CapturedLog.fields(record);
        InetAddress host = InetAddress.getLocalHost();
        assertThat(record.getLevel()).isEqualTo(Level.INFO);
        assertThat(record.getFormattedMessage()).isEqualTo("Application started");
        assertThat(fields)
                .containsEntry(LogEvent.KIND, "event")
                .containsEntry(LogEvent.CATEGORY, List.of("process"))
                .containsEntry(LogEvent.TYPE, List.of("start"))
                .containsEntry(LogEvent.OUTCOME, "success")
                .containsEntry(LogEvent.SEVERITY, "low")
                .doesNotContainKey(LogEvent.LOCAL_ACTION)
                .containsEntry(LogEvent.HOST_NAME, host.getHostName())
                .containsEntry(LogEvent.HOST_IP, host.getHostAddress())
                .containsEntry(ApplicationLifecycleLog.PROFILES, List.of("prod", "feature-x"))
                .containsEntry(ApplicationLifecycleLog.SCIM_ENABLED, true)
                .containsEntry(ApplicationLifecycleLog.DORMANCY_LOCKOUT_WINDOW, "PT1464H")
                .containsEntry(ApplicationLifecycleLog.DORMANCY_MEMBERSHIP_REVOCATION_WINDOW,
                        "PT2928H")
                .containsEntry(ApplicationLifecycleLog.AUDIT_RETENTION_PERIOD, "PT9600H")
                .doesNotContainKeys(AUTH_FLOW_KEYS.toArray(String[]::new));
    }

    /**
     * The logging standard forbids timeout or retry values for authentication flows, so the
     * session timeouts and the lockout threshold stay off the record (ADR 0003).
     */
    static final List<String> AUTH_FLOW_KEYS = List.of(
            "app.session.idle_timeout",
            "app.session.absolute_lifetime",
            "app.auth.lockout.max_attempts");

    /** The gate's state is read, not assumed open. */
    @Test
    void aClosedScimGateIsRecordedAsClosed() {
        listener(false).started(ready(context));

        assertThat(CapturedLog.fields(onlyRecord("application-startup")))
                .containsEntry(ApplicationLifecycleLog.SCIM_ENABLED, false);
    }

    @Test
    void theShutdownRecordCarriesTheContextsUptime() throws Exception {
        Thread.sleep(40);
        listener(true).stopping(new ContextClosedEvent(context));

        ILoggingEvent record = onlyRecord("application-shutdown");
        Map<String, Object> fields = CapturedLog.fields(record);
        assertThat(record.getLevel()).isEqualTo(Level.INFO);
        assertThat(record.getFormattedMessage()).isEqualTo("Application shutting down");
        assertThat(fields)
                .containsEntry(LogEvent.KIND, "event")
                .containsEntry(LogEvent.CATEGORY, List.of("process"))
                .containsEntry(LogEvent.TYPE, List.of("end"))
                .containsEntry(LogEvent.OUTCOME, "success")
                .containsEntry(LogEvent.SEVERITY, "low");
        long uptime = (Long) fields.get(LogEvent.DURATION_MS);
        assertThat(uptime).isBetween(40L, System.currentTimeMillis() - context.getStartupDate());
    }

    /**
     * Another context's events — a management child context closing, above all, which reaches
     * its parent's listeners too — write nothing.
     */
    @Test
    void anotherContextsEventsWriteNoRecord() {
        try (GenericApplicationContext child = new GenericApplicationContext()) {
            child.refresh();
            ApplicationLifecycleLog listener = listener(true);

            listener.started(ready(child));
            listener.stopping(new ContextClosedEvent(child));
        }

        assertThat(logs.withAction(Level.TRACE, LogEvent.KIND, "event")).isEmpty();
    }

    /**
     * A host that cannot resolve its own name still gets its record, without {@code host.*}
     * fields rather than with a placeholder.
     */
    @Test
    void anUnresolvableHostLeavesTheHostFieldsOff() {
        ApplicationLifecycleLog.withHost(
                        LoggerFactory.getLogger(ApplicationLifecycleLogTests.class).atInfo()
                                .addKeyValue(LogEvent.KIND, "event"),
                        () -> {
                            throw new UnknownHostException("no resolver");
                        })
                .log("probe");

        List<ILoggingEvent> records = logs.withAction(Level.TRACE, LogEvent.KIND, "event");
        assertThat(records).hasSize(1);
        assertThat(CapturedLog.fields(records.getFirst()))
                .doesNotContainKeys(LogEvent.HOST_NAME, LogEvent.HOST_IP);
    }

    private ApplicationLifecycleLog listener(boolean scimOpen) {
        return new ApplicationLifecycleLog(
                context,
                environment,
                new ScimReleaseGate(scimOpen),
                new DormancyPolicy(Duration.ofDays(61), Duration.ofDays(122)),
                new AuditRetentionPolicy(Duration.ofDays(400), null));
    }

    private static ApplicationReadyEvent ready(GenericApplicationContext context) {
        return new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO);
    }

    private ILoggingEvent onlyRecord(String action) {
        List<ILoggingEvent> records = logs.withAction(Level.TRACE, LogEvent.ACTION, action);
        assertThat(records).hasSize(1);
        return records.getFirst();
    }
}
