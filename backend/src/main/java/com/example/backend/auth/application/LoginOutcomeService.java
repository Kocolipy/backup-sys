package com.example.backend.auth.application;

import com.example.backend.auth.application.LoginOutcome.PasswordRefused;
import com.example.backend.auth.application.LoginOutcome.SignedIn;
import com.example.backend.observability.LogContext;
import com.example.backend.observability.LogEvent;
import com.example.backend.observability.LogEvent.Category;
import com.example.backend.observability.LogEvent.Operation;
import com.example.backend.observability.LogEvent.Type;
import com.example.backend.observability.SessionHash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Service;

/**
 * Records how a Login ended (ADR 0001): the one place each ending's log line and counts are
 * written, so no two endings can disagree about what an ending emits. When each ending is
 * recorded is the web adapter's Login completion's ({@code LoginCompletion}): a success only once
 * its session is signed in (ADR 0001) — save for a refusal, which the login decision
 * ({@link LoginService}) records once its transaction has committed. The browser is answered by
 * the web adapter, as a function of the outcome alone.
 *
 * <p>What a Login did to the login state, and the audit records written with it, are not here:
 * they are the login decision's, in its own transaction. The fail-closed {@code LOGIN_SUCCESS},
 * and a refusal's {@code LOGIN_FAILURE} with the failure it counted, are written there
 * ({@link LoginService}), so a refusal is decided, counted and audited together.
 *
 * <ul>
 *   <li><b>Signed in.</b> The {@code user-authentication} record at {@code INFO}, naming the
 *       User and the login method, and {@code login} {@code outcome=success}.
 *   <li><b>Refused.</b> One {@code WARN} saying only "Login refused", never the reason, which
 *       is the audit trail's alone (Logging §2.2); and {@code login} {@code outcome=refused} with
 *       the reason its {@code LOGIN_FAILURE} was recorded under ({@link PasswordRefused#recorded}):
 *       {@code UNKNOWN_ACCOUNT} when it was recorded against nobody.
 * </ul>
 *
 * <p>Every ending's records carry {@code session.hash} when a session is named — for a refusal the
 * session the Login ran in, for a success the session it signed in — and no user field but
 * a success's: the session the browser held is not whom the attempt was for. No record carries
 * the submitted username.
 */
@Service
public class LoginOutcomeService {

    private static final Logger log = LoggerFactory.getLogger(LoginOutcomeService.class);

    private final LoginCounts counts;

    public LoginOutcomeService(LoginCounts counts) {
        this.counts = counts;
    }

    /**
     * Records {@code outcome}.
     *
     * @param sessionId the id of the session the Login ran in — for a success, the one it signed
     *                  in — which its records name by hash only; {@code null} when the browser
     *                  held none
     */
    public void record(LoginOutcome outcome, String sessionId) {
        String sessionHash = SessionHash.of(sessionId);
        switch (outcome) {
            case SignedIn signedIn -> signedIn(signedIn, sessionHash);
            case PasswordRefused refused -> refused(refused, sessionHash);
        }
    }

    private void signedIn(SignedIn signedIn, String sessionHash) {
        try (LogContext.Scope resolved = LogContext.userId(signedIn.userId())) {
            inSession(LogEvent.success(
                            log, Operation.LOGIN, Category.PROCESS, Type.USER, Type.ALLOWED)
                    .addKeyValue(LogEvent.LOGIN_METHOD, LoginOutcome.loginMethod()),
                    sessionHash).log();
        }
        counts.signedIn();
    }

    /**
     * A refusal, already counted and audited by the login decision that refused it
     * ({@link LoginService}): its {@code WARN}, and its count under the reason it was recorded
     * under. The {@code WARN} says that the Login was refused, in which session — and never why
     * (Logging §2.2).
     */
    private void refused(PasswordRefused refused, String sessionHash) {
        try (LogContext.Scope unresolved = LogContext.userId(null)) {
            inSession(LogEvent.refused(
                            log, Operation.LOGIN, Category.PROCESS, Type.USER, Type.DENIED)
                    .addKeyValue(LogEvent.LOGIN_METHOD, LoginOutcome.loginMethod()),
                    sessionHash).log();
        }
        counts.refused(refused.recorded().audited());
    }

    /**
     * {@code record}, naming the session the Login ran in by its hash — or by nothing, when the
     * browser held no session.
     */
    private static LoggingEventBuilder inSession(LoggingEventBuilder record, String sessionHash) {
        if (sessionHash != null) {
            // Not a secret: a truncated SHA-256 of a random session id (SessionHash), which cannot
            // be turned back into the id. be-log-sensitive-value, which keeps password and bearer
            // hashes out of logs, does not match this call, so nothing here is suppressed.
            record.addKeyValue(LogEvent.SESSION_HASH, sessionHash);
        }
        return record;
    }
}
