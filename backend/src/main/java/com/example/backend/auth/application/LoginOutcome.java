package com.example.backend.auth.application;

import com.example.backend.audit.domain.AuditLoginMethod;
import java.util.Objects;
import java.util.UUID;

/**
 * How a Login ended: signed in, or refused (ADR 0001, "One module records every ending").
 * {@link LoginOutcomeService} records each one; this is the whole of what it needs.
 */
public sealed interface LoginOutcome {

    /**
     * The login method every Login's records name — its log lines and its {@code login} count:
     * password Login is the one there is.
     */
    static String loginMethod() {
        return AuditLoginMethod.PASSWORD.value();
    }

    /** The Login signed {@code userId} in. */
    record SignedIn(UUID userId) implements LoginOutcome {

        public SignedIn {
            Objects.requireNonNull(userId, "a signed-in Login names its User");
        }
    }

    /**
     * The Login was refused for {@code reason}, as the login decision reported it, and recorded
     * against {@code subjectId}. The browser is told only that, by a bare {@code 401}, and the
     * reason is the audit trail's and the {@code login} counter's alone.
     *
     * <p>It names the User the refusal was counted and audited against: the one the
     * authentication's one read resolved, by stable id, whether the authentication refused it or
     * the decision under the User's lock did — or nobody, when that read found no User or the User
     * it found was gone by the time the refusal was recorded. Never the submitted name: looking
     * that up again would follow whichever User holds it by then, and a directory rename can land
     * in between.
     *
     * @param subjectId the stable id of the User the refusal was recorded against; {@code null}
     *     when there was none, which settles the refusal as {@code UNKNOWN_ACCOUNT}
     *     ({@link #recorded})
     * @param reason    the refusal as reported — never {@code UNKNOWN_ACCOUNT}, which Spring
     *     Security hides behind a wrong password, and which recording the refusal settles
     *     ({@link PasswordRefusalReason#reported})
     */
    record PasswordRefused(UUID subjectId, PasswordRefusalReason reason) implements LoginOutcome {

        public PasswordRefused {
            Objects.requireNonNull(reason, "a refusal has a reason");
            if (!reason.reported()) {
                throw new IllegalArgumentException("not a password Login's refusal: " + reason);
            }
        }

        /**
         * The reason the refusal is recorded and counted under: {@link #reason} as
         * {@link PasswordRefusalReason#settled} settles it — {@code UNKNOWN_ACCOUNT} when it was
         * recorded against nobody.
         */
        public PasswordRefusalReason recorded() {
            return reason.settled(subjectId != null);
        }
    }
}
