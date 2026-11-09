import { describe, expect, it } from "vitest";

import type { PasswordChangeOutcome } from "./api";
import { sessionEndReasonFor, signInNoticeFor, type SignInReasonState } from "./sign-in-reason";

const EXPIRED = "Your session ended. Please sign in again.";
const INACTIVE = "You were signed out because you were inactive. Please sign in again.";
const CHANGED = "Your password was changed. Sign in with your new password.";
const LOCKED =
  "Too many incorrect passwords: the account is now locked and your session has ended. An Admin must Unlock the account before you can sign in again.";

const carrying = (state: SignInReasonState & { from?: string }) => ({ state });

describe("signInNoticeFor", () => {
  it.each([
    // Each reason, carried in router state.
    [
      "an Expired session, carried in router state",
      carrying({ from: "/a", reason: "expired" }),
      EXPIRED,
    ],
    [
      "an Idle sign-out, carried in router state",
      carrying({ from: "/a", reason: "inactive" }),
      INACTIVE,
    ],
    [
      "a password change, carried in router state",
      carrying({ reason: "password-changed" }),
      CHANGED,
    ],
    [
      "a lockout during a password change, carried in router state",
      carrying({ reason: "locked" }),
      LOCKED,
    ],
    // Nothing to say.
    ["a cold visit", { state: null }, null],
    ["a return destination with no reason", carrying({ from: "/accounts" }), null],
    // Router state is whatever the history entry holds, so it is decoded, not trusted.
    ["a carried reason it does not know", { state: { reason: "stale" } }, null],
    ["a carried reason naming an object property", { state: { reason: "constructor" } }, null],
    ["router state that is not an object", { state: "expired" }, null],
  ])("says the right thing for %s", (_name, location, notice) => {
    expect(signInNoticeFor(location)).toBe(notice);
  });
});

describe("sessionEndReasonFor", () => {
  it.each<[PasswordChangeOutcome, string | null]>([
    // The two outcomes after which the backend holds no session for the User.
    [{ kind: "changed" }, "password-changed"],
    [{ kind: "locked" }, "locked"],
    // The session had ended before the request, which found none.
    [{ kind: "expired" }, "expired"],
    // Every refusal leaves the session standing, so it records no reason.
    [{ kind: "policy-violation", message: "Too short." }, null],
    [{ kind: "current-password-rejected" }, null],
    [{ kind: "forbidden" }, null],
    [{ kind: "csrf-expired" }, null],
    [{ kind: "failed" }, null],
  ])("a password change that came to %o ends the session for %s", (outcome, reason) => {
    expect(sessionEndReasonFor(outcome)).toBe(reason);
  });
});
