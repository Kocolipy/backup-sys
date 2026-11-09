/**
 * The **sign-in reason**: why a Guest is at the login page, and what it says.
 *
 * The one owner of the closed set of reasons, how each one reaches the login
 * page, and the copy for each. Nothing else
 * in the SPA decides any of those: the provider records a reason, the routing
 * contract carries it, and the login page renders the notice this module gives.
 */

import type { PasswordChangeOutcome } from "./api";

/**
 * The reasons the SPA records when it ends a session: an Expired session, an
 * Idle sign-out, the User's own successful password change, and a password
 * change that locked the account instead. Exactly one or none at a time, so no
 * impossible combination can be represented. They reach the login page in
 * router state, as `SignInReasonState`.
 */
export type SessionEndReason = "expired" | "inactive" | "locked" | "password-changed";

/**
 * The reason a password change records, or `null` for an outcome that leaves
 * the session standing. A change replaces the password and a lockout revokes
 * every session of the User, so both end this one; a change that found the
 * session already gone records the Expired session it met, as any other
 * request's `401` does; every refusal keeps it.
 *
 * Every outcome is named, with no `default`: an outcome added to
 * `PasswordChangeOutcome` fails to compile here until someone decides whether
 * it ends the session.
 */
export function sessionEndReasonFor(outcome: PasswordChangeOutcome): SessionEndReason | null {
  switch (outcome.kind) {
    case "changed":
      return "password-changed";
    case "locked":
      return "locked";
    case "expired":
      return "expired";
    case "policy-violation":
    case "current-password-rejected":
    case "forbidden":
    case "csrf-expired":
    case "failed":
      return null;
  }
}

/** Every `SessionEndReason`, so router state can be checked against the set. */
const SESSION_END_REASONS: ReadonlySet<unknown> = new Set<SessionEndReason>([
  "expired",
  "inactive",
  "locked",
  "password-changed",
]);

/** The router-state field a session-ending redirect carries its reason in. */
export interface SignInReasonState {
  reason?: SessionEndReason;
}

/** The copy for each reason; exhaustive, so a new reason cannot ship without its own. */
function noticeFor(reason: SessionEndReason): string {
  switch (reason) {
    case "expired":
      return "Your session ended. Please sign in again.";
    case "inactive":
      return "You were signed out because you were inactive. Please sign in again.";
    case "locked":
      // Said only here, after the User's own session ended: a refused login
      // never says "locked", which would tell a guesser the name is real.
      return "Too many incorrect passwords: the account is now locked and your session has ended. An Admin must Unlock the account before you can sign in again.";
    case "password-changed":
      return "Your password was changed. Sign in with your new password.";
  }
}

/** Where the login page is, as far as the notice is concerned: its router state. */
interface LoginLocation {
  state: unknown;
}

/**
 * The reason router state carries, if it carries one of the closed set. The
 * history entry can hold anything, a stale or foreign shape included, so it is
 * decoded rather than cast.
 */
function carriedReason(state: unknown): SessionEndReason | undefined {
  const reason = (state as { reason?: unknown } | null)?.reason;
  return SESSION_END_REASONS.has(reason) ? (reason as SessionEndReason) : undefined;
}

/** The notice the login page shows for how it was reached, or `null` for none. */
export function signInNoticeFor({ state }: LoginLocation): string | null {
  const reason = carriedReason(state);
  return reason ? noticeFor(reason) : null;
}
