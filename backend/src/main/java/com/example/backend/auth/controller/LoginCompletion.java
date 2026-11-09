package com.example.backend.auth.controller;

import com.example.backend.auth.application.LoginOutcomeService;
import com.example.backend.auth.application.LoginService;
import com.example.backend.auth.application.LoginService.AcceptedLogin;
import com.example.backend.auth.application.LoginService.LoginDecision;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * How every Login ends: the one place the steps that end a Login run, in one order (ADR 0001).
 *
 * <ol>
 *   <li><b>Decide.</b> The login decision is {@link LoginService}'s, handed the id of the session
 *       the browser holds, which the Login continues in. It counts the attempt (ADR 0001), writes
 *       an accepted Login's fail-closed {@code LOGIN_SUCCESS} in its own transaction (ADR 0004),
 *       and records a refusal it reached, so no caller can refuse a Login without the record.
 *   <li><b>Establish.</b> An accepted Login's session is signed in by {@link SessionEstablishment}:
 *       rotated, its security context saved, indexed, the pre-login CSRF token dropped.
 *   <li><b>Record</b> the ending, once, through {@link LoginOutcomeService}: the
 *       {@code user-authentication} record and the {@code login} success count, naming the session
 *       the User goes on to use. Only now, so a Login that never got its session — the session
 *       step failing — is not logged or counted a success.
 *   <li><b>On a refusal, end the browser's session</b>, whoever it belonged to, so a shared browser is never left signed in as the previous
 *       User after a Login that signed nobody in. {@link SessionEnding} ends it, for
 *       {@link SessionEnding.Reason#LOGIN_SIGNED_NOBODY_IN}: invalidated, the security context
 *       cleared, the cookie expired. A refused Login that arrived without a session creates none.
 * </ol>
 *
 * <p>What the browser is answered is left to the web adapter, as a function of the ending alone:
 * a {@code UserResponse} or a bare {@code 401}. A web adapter rather than an application service,
 * because establishing and ending a session are servlet work.
 */
@Component
public class LoginCompletion {

    private final SessionEstablishment sessionEstablishment;

    private final LoginOutcomeService outcomes;

    private final SessionEnding sessionEnding;

    LoginCompletion(SessionEstablishment sessionEstablishment, LoginOutcomeService outcomes,
            SessionEnding sessionEnding) {
        this.sessionEstablishment = sessionEstablishment;
        this.outcomes = outcomes;
        this.sessionEnding = sessionEnding;
    }

    /**
     * Completes a Login: decides it, then signs its session in and records it, or ends the
     * browser's session.
     *
     * @param decide     the login decision, handed the id of the session the browser holds — the
     *                   one the Login continues in — or {@code null} when it holds none
     * @return the session the Login signed in, or empty when it was refused — a refusal its
     *     decision has recorded already
     */
    public Optional<EstablishedLogin> complete(Function<String, LoginDecision> decide,
            HttpServletRequest request, HttpServletResponse response) {
        HttpSession existing = request.getSession(false);
        Optional<AcceptedLogin> decided =
                decide.apply(existing == null ? null : existing.getId()).accepted();
        if (decided.isEmpty()) {
            // Recorded by the decision already, which counted it; ended here.
            sessionEnding.end(SessionEnding.Reason.LOGIN_SIGNED_NOBODY_IN, request, response);
            return Optional.empty();
        }
        AcceptedLogin accepted = decided.get();
        HttpSession session = sessionEstablishment.establish(accepted, request, response);
        outcomes.record(accepted.signedIn(), session.getId());
        return Optional.of(new EstablishedLogin(accepted.authentication(), session));
    }

    /**
     * The session an accepted Login signed in, under its rotated id, and the authentication it was
     * signed in as.
     */
    public record EstablishedLogin(Authentication authentication, HttpSession session) {
    }
}
