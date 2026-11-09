package com.example.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.example.backend.audit.CapturedLog;
import com.example.backend.auth.RecordingLoginCounts;
import com.example.backend.auth.application.LoginOutcome.PasswordRefused;
import com.example.backend.auth.application.LoginOutcome.SignedIn;
import com.example.backend.observability.LogContext;
import com.example.backend.observability.LogEvent;
import com.example.backend.observability.SessionHash;
import com.example.backend.scim.InMemoryScimUserRepository;
import com.example.backend.scim.ScimIdentities;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * How a Login's ending is recorded ({@link LoginOutcomeService}): for each outcome, the log
 * records and the counts it moves — the whole table at one interface.
 */
class LoginOutcomeServiceTests {

    private static final String SESSION = "the-login-session";

    private final RecordingLoginCounts counts = new RecordingLoginCounts();

    private final InMemoryScimUserRepository users = new InMemoryScimUserRepository();

    private final LoginOutcomeService outcomes = new LoginOutcomeService(counts);

    // ---- signed in ----------------------------------------------------------------------------

    @Test
    void aSignedInLoginIsOneInfoNamingTheUserMethodAndSession() {
        UUID user = UUID.randomUUID();

        List<ILoggingEvent> records = recording(new SignedIn(user), SESSION);

        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.getLevel()).isEqualTo(Level.INFO);
            assertThat(record.getFormattedMessage()).isEqualTo("Login accepted");
            assertThat(record.getMDCPropertyMap()).containsEntry(LogContext.USER_ID, user.toString());
            assertThat(CapturedLog.fields(record))
                    .containsEntry(LogEvent.LOGIN_METHOD, "password")
                    .containsEntry(LogEvent.SESSION_HASH, SessionHash.of(SESSION));
        });
        assertThat(counts.moved()).containsExactly("password:success");
    }

    /** A browser with no session is named by nothing, not by a hash of nothing. */
    @Test
    void aSignedInLoginWithNoSessionCarriesNoSessionHash() {
        List<ILoggingEvent> records = recording(new SignedIn(UUID.randomUUID()), null);

        assertThat(CapturedLog.fields(records.getFirst())).doesNotContainKey(LogEvent.SESSION_HASH);
    }

    // ---- password refused ---------------------------------------------------------------------

    /**
     * A password refusal is counted by the login decision that refused it
     * ({@code PasswordLoginRecordingTests}), so recording its ending counts toward no failure
     * run.
     */
    @Test
    void aPasswordRefusalCountsTowardNoFailureRun() {
        UUID ada = users.given(ScimIdentities.user("ada")).id();

        outcomes.record(new PasswordRefused(ada, PasswordRefusalReason.BAD_CREDENTIALS), SESSION);

        assertThat(users.require("ada").login().failedLoginAttempts()).isZero();
    }

    /**
     * Logging §2.2: the reason tells whether the account exists, so the operational log says only
     * that the Login was refused, and names the session it ran in, but neither a user nor the
     * submitted name.
     */
    @ParameterizedTest(name = "{0} is one generic WARN")
    @EnumSource(value = PasswordRefusalReason.class,
            names = {"BAD_CREDENTIALS", "ACCOUNT_LOCKED", "ACCOUNT_DISABLED", "OTHER"})
    void aPasswordRefusalIsOneGenericWarnWithNoReasonNoUserAndTheSessionsHash(
            PasswordRefusalReason reason) {
        UUID lovelace = users.given(ScimIdentities.user("lovelace")).id();
        List<ILoggingEvent> records;
        try (LogContext.Scope colleague = LogContext.userId(UUID.randomUUID())) {
            records = recording(new PasswordRefused(lovelace, reason), SESSION);
        }

        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.getLevel()).isEqualTo(Level.WARN);
            assertThat(record.getFormattedMessage()).isEqualTo("Login refused");
            assertThat(record.getMDCPropertyMap()).doesNotContainKey(LogContext.USER_ID);
            assertThat(CapturedLog.fields(record))
                    .containsEntry(LogEvent.LOGIN_METHOD, "password")
                    .containsEntry(LogEvent.SESSION_HASH, SessionHash.of(SESSION))
                    .doesNotContainKey(LogEvent.REASON);
            assertThat(record.getFormattedMessage()).doesNotContain("lovelace");
            assertThat(CapturedLog.fields(record).values())
                    .noneSatisfy(value -> assertThat(String.valueOf(value)).contains("lovelace"));
        });
    }

    @ParameterizedTest(name = "{0} is counted as password:refused:{0}")
    @EnumSource(value = PasswordRefusalReason.class,
            names = {"BAD_CREDENTIALS", "ACCOUNT_LOCKED", "ACCOUNT_DISABLED", "OTHER"})
    void aPasswordRefusalIsCountedUnderItsReason(PasswordRefusalReason reason) {
        UUID ada = users.given(ScimIdentities.user("ada")).id();

        outcomes.record(new PasswordRefused(ada, reason), SESSION);

        assertThat(counts.moved()).containsExactly("password:refused:" + reason.name());
    }

    /**
     * A refusal recorded against no User — its authentication resolved none — is counted as the
     * unknown account it is.
     */
    @Test
    void aPasswordRefusalOfAnUnknownNameIsCountedAsAnUnknownAccount() {
        outcomes.record(new PasswordRefused(null, PasswordRefusalReason.BAD_CREDENTIALS), SESSION);

        assertThat(counts.moved()).containsExactly("password:refused:UNKNOWN_ACCOUNT");
    }

    @Test
    void aPasswordRefusalWithNoSessionCarriesNoSessionHash() {
        List<ILoggingEvent> records = recording(
                new PasswordRefused(null, PasswordRefusalReason.BAD_CREDENTIALS), null);

        assertThat(CapturedLog.fields(records.getFirst())).doesNotContainKey(LogEvent.SESSION_HASH);
    }

    /**
     * Spring Security never reports an unknown account — recording the refusal settles it — so a
     * refusal for one is a caller's bug, refused rather than recorded.
     */
    @Test
    void aPasswordRefusalHasOnlyAReasonSpringSecurityReports() {
        assertThatThrownBy(() -> new PasswordRefused(
                        UUID.randomUUID(), PasswordRefusalReason.UNKNOWN_ACCOUNT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("not a password Login's refusal: UNKNOWN_ACCOUNT");
    }

    // ---- what an outcome can be ---------------------------------------------------------------

    /** No outcome is no ending: nothing to record, and a caller's bug rather than a refusal. */
    @Test
    void noOutcomeIsRefusedAsANullPointer() {
        assertThatThrownBy(() -> outcomes.record(null, SESSION))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void anOutcomeLacksNothingItRecords() {
        assertThatThrownBy(() -> new SignedIn(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PasswordRefused(UUID.randomUUID(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("a refusal has a reason");
    }

    /** The {@code user-authentication} records written while {@code outcome} is recorded. */
    private List<ILoggingEvent> recording(LoginOutcome outcome, String sessionId) {
        try (CapturedLog captured = CapturedLog.attach()) {
            outcomes.record(outcome, sessionId);
            return captured.withAction(Level.INFO, LogEvent.ACTION, "user-authentication");
        }
    }
}
