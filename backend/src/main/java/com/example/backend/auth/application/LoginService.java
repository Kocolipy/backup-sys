package com.example.backend.auth.application;

import com.example.backend.audit.domain.AuditLoginMethod;
import com.example.backend.audit.domain.AuditLoginStateAttribute;
import com.example.backend.audit.domain.AuditTrail;
import com.example.backend.audit.domain.Audited;
import com.example.backend.auth.application.LoginOutcome.PasswordRefused;
import com.example.backend.auth.application.LoginOutcome.SignedIn;
import com.example.backend.auth.application.LoginStateService.LoginVerdict;
import com.example.backend.auth.application.LoginStateService.VerifiedPassword;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Logging in: submitted credentials become either an authentication or a refusal, and the attempt
 * is counted against the account either way. The one module a Login runs through — it
 * authenticates, decides under the User's lock, counts a failure and writes the Login's audit
 * record.
 *
 * <p>The counting is here rather than at the call site because it is part of what logging in
 * <em>is</em> — an entry point that authenticated credentials without it would silently have no
 * lockout. A caller therefore cannot obtain an authentication from submitted credentials and skip
 * the failure run; it gets both or neither.
 *
 * <p>What an attempt does to the login state — the failure run, the Lockout, the dormancy basis —
 * is not decided here: each attempt is reported to {@link LoginStateService} by the User's stable
 * id, and the module reads the User under its lock, decides the new state, writes it and, on a
 * newly imposed Lockout, audits it and schedules the revocation of every Session. A User the
 * module finds gone there is reported back as gone, and this decides only what that Login is
 * recorded as. What this records is the Login's own: a refusal's
 * {@code LOGIN_FAILURE}, an acceptance's fail-closed {@code LOGIN_SUCCESS}, and an acceptance's
 * one-session-per-User sweep, through {@link SessionRevocationService}. Enforcement is elsewhere
 * too: {@link LoginIdentityService} reports a locked identity to Spring Security, which refuses
 * it whatever the password, after comparing that password exactly as it would compare a wrong
 * one; the rule for what counts as locked lives in
 * {@link com.example.backend.scim.domain.ScimLoginState}.
 *
 * <h2>One transaction after the comparison</h2>
 *
 * <p>A password Login's Argon2id comparison runs in no transaction: it is slow by design, and a
 * transaction open across it would hold a pooled connection for its whole length. So
 * {@link #logIn} opens none around it. Once the authentication has answered, everything else runs
 * in one transaction, which {@link #logIn} opens itself — a {@code @Transactional} helper it
 * called would never pass through the proxy:
 *
 * <ul>
 *   <li>a refusal by the authentication is counted under the User's lock
 *       ({@link LoginStateService#passwordLoginRefused}), and its {@code LOGIN_FAILURE} written;
 *   <li>an acceptance is decided under the User's lock, by {@link LoginStateService#passwordLogin}, and either
 *       recorded — the cleared run, the {@code LOGIN_SUCCESS}, the sweep scheduled for after the
 *       commit — or refused there, that refusal counted under the same lock, and its
 *       {@code LOGIN_FAILURE} written.
 * </ul>
 *
 * <p>A refusal is therefore decided and counted in one transaction, never decided in one and
 * counted in a second that takes the lock again. Every {@code LOGIN_FAILURE} is fail-open, as
 * everything on a refusal's path is: the caller is already being answered a bare {@code 401}, and
 * a trail that cannot be written must not change that answer. The {@code LOGIN_SUCCESS} is
 * fail-closed and joins the transaction, so a success the trail cannot record takes the cleared
 * run down with it, and is not a success ({@link AuditTrail}; ADR 0004). The transaction is not
 * retried on a deadlock: a Login is neither a SCIM nor an administrative write, and the person
 * signing in tries again.
 */
@Service
public class LoginService {

    private final AuthenticationManager authenticationManager;
    private final LoginIdentityService identities;
    private final LoginStateService loginState;
    private final SessionRevocationService sessions;
    private final AuditTrail audit;
    private final LoginOutcomeService outcomes;

    /**
     * A password Login's one transaction, once the authentication has answered: the template's
     * default propagation, {@code PROPAGATION_REQUIRED}, which opens it, since {@link #logIn} runs
     * in none.
     */
    private final TransactionTemplate decision;

    /**
     * @param transactionManager the primary transaction manager, which commits a password Login's
     *     decision, its count and its audit record together
     */
    public LoginService(
            AuthenticationManager authenticationManager,
            LoginIdentityService identities,
            LoginStateService loginState,
            SessionRevocationService sessions,
            AuditTrail audit,
            LoginOutcomeService outcomes,
            PlatformTransactionManager transactionManager) {
        this.authenticationManager = authenticationManager;
        this.identities = identities;
        this.loginState = loginState;
        this.sessions = sessions;
        this.audit = audit;
        this.outcomes = outcomes;
        this.decision = new TransactionTemplate(transactionManager);
    }

    /**
     * The login decision for a password Login: the submitted credentials become an accepted
     * Login or a refusal, and the attempt is counted against the account either way.
     *
     * <p>Every refusal — wrong password, unknown username, locked account, disabled account —
     * ends in the same kind of decision, a {@link PasswordRefused}, told apart only by the reason
     * the audit trail records, so the web adapter answers each with the same bare {@code 401}. A
     * fault in the lookup — {@link LoginIdentityService#loadUserByUsername} meeting a
     * {@code DataAccessException}, say — is not among them: Spring reports it as an
     * {@code InternalAuthenticationServiceException}, which is rethrown as its own cause rather
     * than decided here, so nothing is counted or audited as a refusal, and the application's
     * fault handling answers it exactly as any other database fault.
     *
     * <p>The run of failures an accepted login ends is cleared, and its fail-closed
     * {@code LOGIN_SUCCESS} written, before this returns, so a caller holding an authentication
     * is by definition one whose account was not refused, whatever it does with the
     * authentication next. Every other session of the User ends once that commits — one
     * concurrent session per User, so a credential cannot be in use from two places at once — on
     * the path a Lockout's revocation takes and for the same reason
     * ({@code /docs/adr/0002-revoke-sessions-after-commit.md}): a rolled-back Login must not have
     * signed its owner out everywhere else. The rule holds for a Login confined by a required
     * change as for any other.
     *
     * <p>Spring Security's check of a locked or deactivated account reads the User before the
     * login-state lock, and the password comparison after it takes long enough for another
     * attempt's Lockout, a connector's deactivation, or a replacement of the very password being
     * compared, to commit in between. The success is therefore decided once more on the state
     * read under the lock ({@link LoginStateService#passwordLogin}); a Login refused there
     * ends as the refusal the check would have made a moment later — {@code ACCOUNT_LOCKED} or
     * {@code ACCOUNT_DISABLED}, recorded and counted exactly as that refusal is — so it never
     * lifts the Lockout it lost the race to (ADR 0007), or, for a replaced password, as the wrong
     * password it is by then: {@code BAD_CREDENTIALS}, counted on the failure run. It is counted
     * under the lock it was decided under, in the same transaction. A User deleted at any point
     * before the lock is gone under it, and the Login is refused and recorded against nobody
     * ({@code UNKNOWN_ACCOUNT}), with nothing else written or counted. The authority the session
     * it issues holds — whether it is confined, and otherwise its Superuser, baseline and Group
     * Permissions — is read in that same decision, under the same lock, never from the read
     * before it: a Group membership removed, or a Group's Permissions reduced, during the
     * comparison has had its revocation run before this Login's session existed, so only that
     * read keeps the removed power out of it (ADR 0002).
     *
     * <p>Every refusal is counted and audited against the User the authentication's one read
     * resolved, by stable id — the authentication tells this which, as it reads
     * ({@link PasswordLoginAttempt}) — or against nobody when that read found no User, or the
     * login-state module finds the User it found gone under its lock; the submitted name is never
     * looked up again. A name that resolved nobody is recorded rather than counted: nothing is
     * created for it, so a caller cannot learn from stored state whether the name exists, and its
     * {@code LOGIN_FAILURE} carries no subject, because the submitted username is the one thing
     * that must not be recorded in its place. An administrator reading a run of subject-less
     * failures is seeing attempts against names that do not exist, which is exactly the
     * distinction worth having.
     *
     * <p>A refusal's log line and counts are written through {@link LoginOutcomeService}, the one
     * module that logs and counts how a Login ended, once its transaction has
     * committed. An acceptance's are not: the Login has not ended until the caller has signed the
     * session in, so the caller records the {@link SignedIn} it is handed once it has, naming the
     * session the User goes on to use ({@code LoginCompletion}; ADR 0001). Neither record carries
     * the submitted {@code username}. It is
     * the single most sensitive value passing through here — it is half a credential, and on a
     * failed attempt it is very often a mistyped password — so it stays out of the log, in the
     * message and in the context alike. The accepted Login's record carries the identity's stable
     * id as {@code user.id}; the refused one carries no user field at all, because the identity it
     * named is unresolved, and no reason either: it says only that the Login was refused (Logging
     * §2.2). Whether the password was wrong, the name unknown, or the account locked or
     * deactivated tells whether an account exists, so that is the audit trail's
     * {@code LOGIN_FAILURE} alone, read by an administrator, and the {@code login} counter's
     * {@code reason} tag, which names no account. A run of wrong passwords is still told from a
     * run against names that do not exist, there.
     *
     * @return how the decision ended — a {@link SignedIn} with the accepted Login, or a
     *     {@link PasswordRefused}, already recorded
     */
    public LoginDecision logIn(String username, String password) {
        return logIn(username, password, null);
    }

    /**
     * {@link #logIn(String, String)} from a caller that may already hold a session: an accepted
     * login ends every other session of the identity once the cleared failure run commits, and
     * keeps this one, which the caller goes on to rotate and sign in. A refused one names this
     * session by hash on its record, and leaves ending it to the caller, the web adapter.
     *
     * @param retainedSessionId the id the caller's session is stored under, or {@code null} when
     *     it holds none
     */
    public LoginDecision logIn(String username, String password, String retainedSessionId) {
        PasswordLoginAttempt attempt = new PasswordLoginAttempt(username, password);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(attempt);
        } catch (InternalAuthenticationServiceException fault) {
                   // A fault in the lookup, not a refusal: nothing is counted or audited. The cause is
            // rethrown, never this wrapper, whose message Spring copied from the cause and may
            // carry SQL detail, so the fault handling classifies and redacts it as any other
            // database fault. Spring's constructor does not rule out a checked cause.
     throw fault.getCause() instanceof RuntimeException cause ? cause : fault;
        } catch (AuthenticationException refused) {
            // Against the User the authentication's one read resolved, by stable id, or nobody:
            // the submitted name is never looked up again, so a rename landing since the read
            // cannot move the refusal, or the Lockout it may impose, onto another User.
            UUID resolved = attempt.resolvedUserId();
            PasswordRefusalReason reason = PasswordRefusalReason.reportedFor(refused);
            return ended(decision.execute(status -> refusalCounted(resolved, reason)),
                    retainedSessionId);
        }

        // The User was read once, to authenticate; everything after works from the stable id
        // that read put on the principal, never from the name, which a directory rename can
        // move to another User in between. Outside the catch above on purpose: a failure
        // recording the success is not a refusal, and must not be reported to the caller as one.
        UUID userId = LoginPrincipal.userIdOf(authentication);
        VerifiedPassword credential = attempt.verifiedCredential();
        return ended(decision.execute(status ->
                        decided(authentication, userId, credential, retainedSessionId)),
                retainedSessionId);
    }

    /**
     * A refusal by the authentication: counted against the User {@code resolvedUserId} names,
     * under its lock, and recorded — against nobody when the authentication resolved no User, or
     * the module finds it gone under the lock. In the Login's one transaction.
     */
    private LoginDecision refusalCounted(UUID resolvedUserId, PasswordRefusalReason reason) {
        return Optional.ofNullable(resolvedUserId)
                .flatMap(userId -> loginState.passwordLoginRefused(userId, reason))
                .map(counted -> refusalRecorded(resolvedUserId, counted))
                .orElseGet(() -> refusalRecorded(new PasswordRefused(null, reason), Set.of()));
    }

    /**
     * An accepted authentication of the User {@code userId}, decided under its lock: recorded as
     * a success and issued the authority the decision read there, or refused there and that
     * refusal counted under the same lock. In the Login's one transaction.
     */
    private LoginDecision decided(Authentication authentication, UUID userId,
            VerifiedPassword credential, String retainedSessionId) {
        return loginState.passwordLogin(userId, credential)
                .map(verdict -> switch (verdict) {
                    // A Lockout or a deactivation committed while the password was being
                    // compared — the refusal the authentication would have made a moment later —
                    // or a password replaced meanwhile, which makes the submitted one wrong:
                    // counted already, as that refusal is, on the state it was decided from.
                    case LoginVerdict.Refused refused ->
                            refusalRecorded(userId, refused);
                    case LoginVerdict.Accepted granted -> {
                        signedInRecorded(new Audited.LoginSuccess(userId, AuditLoginMethod.PASSWORD),
                                userId, retainedSessionId);
                        yield LoginDecision.signedIn(accepted(
                                identities.issued(authentication, granted.authorities()),
                                new SignedIn(userId)));
                    }
                })
                // Deleted since the authentication read it, before or after the comparison: gone
                // under the lock, so the name it was found by names nobody now. Refused as a name
                // that resolves no User is — the wrong password Spring Security reports it as —
                // and recorded against nobody, so settled as UNKNOWN_ACCOUNT.
                .orElseGet(() -> refusalRecorded(
                        new PasswordRefused(null, PasswordRefusalReason.BAD_CREDENTIALS),
                        Set.of()));
    }

    /** The User {@code userId}'s Login, refused under its lock as {@code refused} says. */
    private LoginDecision refusalRecorded(
            UUID userId, LoginVerdict.Refused refused) {
        return refusalRecorded(
                new PasswordRefused(userId, refused.reason()), refused.change().moved());
    }

    /**
     * Writes {@code refusal}'s {@code LOGIN_FAILURE}, fail-open, naming what counting it
     * {@code moved}, and hands back the decision that ends in it.
     */
    private LoginDecision refusalRecorded(
            PasswordRefused refusal, Set<AuditLoginStateAttribute> moved) {
        audit.append(new Audited.LoginFailure(refusal.subjectId(), refusal.recorded().audited(),
                AuditLoginMethod.PASSWORD, moved));
        return new LoginDecision(refusal, Optional.empty());
    }

    /**
     * A password Login's decision once its transaction has committed: a refusal's log line and
     * counts written, an acceptance handed back for its caller to record once signed in.
     */
    private LoginDecision ended(LoginDecision decided, String retainedSessionId) {
        if (decided.outcome() instanceof PasswordRefused refusal) {
            outcomes.record(refusal, retainedSessionId);
        }
        return decided;
    }

    /**
     * Records an accepted Login, decided under the User's lock, in its
     * transaction: its {@code LOGIN_SUCCESS}, fail-closed, so a success the trail cannot record
     * rolls back with the cleared run; and the end of every other session of {@code userId} but
     * {@code retainedSessionId}, once that commits.
     */
    private void signedInRecorded(
            Audited.LoginSuccess success, UUID userId, String retainedSessionId) {
        audit.append(success);
        sessions.revokeOtherSessionsAfterCommit(userId, retainedSessionId);
    }

    /**
     * What the caller establishes the session from, once the success is recorded: the
     * authentication, and {@code signedIn}, naming the User by the stable id its principal
     * carries.
     */
    private AcceptedLogin accepted(Authentication authentication, SignedIn signedIn) {
        return new AcceptedLogin(authentication, signedIn);
    }

    /**
     * A successful login, carrying both what Spring Security needs to place in
     * the security context and the identity's stable id — the SCIM resource id the
     * web adapter writes into the session index, so application-owned session
     * lookups survive a later {@code userName} change instead of following
     * {@code authentication.getName()}. Its {@link SignedIn} is the outcome the caller records
     * once the session is signed in.
     */
    public record AcceptedLogin(Authentication authentication, SignedIn signedIn) {

        /** The identity's stable id, which the session is indexed and logged by. */
        public UUID userId() {
            return signedIn.userId();
        }
    }

    /**
     * How a login decision ended: its {@link LoginOutcome}, and the
     * accepted Login the caller establishes the session from — present exactly when the outcome
     * is {@link SignedIn}, and then that accepted Login's own. A refusal it carries is already
     * recorded; a {@link SignedIn} is the caller's to record, once the session is signed in.
     */
    public record LoginDecision(LoginOutcome outcome, Optional<AcceptedLogin> accepted) {

        public LoginDecision {
            if (accepted.map(login -> !login.signedIn().equals(outcome))
                    .orElse(outcome instanceof SignedIn)) {
                throw new IllegalArgumentException(
                        "exactly a signed-in Login carries the accepted Login it signed in");
            }
        }

        /** The decision that accepted {@code accepted}, ending in its {@link SignedIn}. */
        public static LoginDecision signedIn(AcceptedLogin accepted) {
            return new LoginDecision(accepted.signedIn(), Optional.of(accepted));
        }
    }
}
