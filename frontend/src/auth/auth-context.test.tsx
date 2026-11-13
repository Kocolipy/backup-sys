import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import {
  bare,
  CURRENT_PASSWORD_REFUSED,
  deferred,
  fakeBackend,
  json,
  UNREACHABLE,
  type Answers,
  requestKey,
  type FakeBackend,
} from "@/lib/fake-backend.testHelpers";
import { AuthProvider } from "./auth-context";
import { AUTH_REQUESTS, testUser } from "./auth-context.testHelpers";
import { useAuthState } from "./auth-context-value";
import { ehrContext, setEhrContext } from "./ehr-context";

// The provider is the subject, so it runs for real over the backend layer: the
// session check, the session actions and the token all go over the wire, and
// the token being forgotten shows there too — the next unsafe request fetches
// a fresh one first.

const { change: CHANGE, login: LOGIN, logout: LOGOUT, me: ME } = AUTH_REQUESTS;

const CONFINED = testUser({ passwordChangeRequired: true });
const USER = testUser();
const CREDENTIALS = ["ada", "chosen-1"] as const;
const [OLD_VALUE, NEW_VALUE] = ["old-value", "new-value"];

/** A signed-in session, and one with the change-required flag. */
const SIGNED_IN: Answers = { [ME]: json(USER) };
const SIGNED_IN_CONFINED: Answers = { [ME]: json(CONFINED) };
const GUEST: Answers = { [ME]: bare(401) };

const wrapper = ({ children }: { children: ReactNode }) => <AuthProvider>{children}</AuthProvider>;

/** Mounts the provider over a backend answering `answers`, and waits for its session check to settle. */
async function mounted(answers: Answers) {
  const backend = fakeBackend(answers);
  const hook = renderHook(() => useAuthState(), { wrapper });
  await waitFor(() => expect(hook.result.current.status).not.toBe("checking"));
  return { backend, result: hook.result };
}

/** How many times the session was checked. */
const checks = (backend: FakeBackend) =>
  backend.requests().filter((sent) => requestKey(sent) === ME).length;

/** The sign-in reason the route guards read, plus the status. */
const signInReasonAndStatus = (state: ReturnType<typeof useAuthState>) => ({
  signInReason: state.signInReason,
  status: state.status,
});

/**
 * Runs one session action and reports what it settled to: the outcome it
 * resolved, the status after it, and whether it forgot the token it was sent
 * with.
 */
async function settle<T>(
  { backend, result }: Awaited<ReturnType<typeof mounted>>,
  action: (state: ReturnType<typeof useAuthState>) => Promise<T>,
) {
  let outcome: T | undefined;
  await act(async () => {
    outcome = await action(result.current);
  });
  return {
    outcome,
    status: result.current.status,
    tokenForgotten: await backend.tokenForgotten(),
  };
}

describe("AuthProvider", () => {
  it("refuses to read the session outside the provider", () => {
    // React reports the render error on the console as well as throwing it.
    const quiet = vi.spyOn(console, "error").mockImplementation(() => undefined);

    try {
      expect(() => renderHook(() => useAuthState())).toThrow(
        /^useAuth must be used inside AuthProvider\.$/,
      );
    } finally {
      quiet.mockRestore();
    }
  });

  it("starts as a cold guest when there is no session", async () => {
    const { result } = await mounted(GUEST);

    expect(signInReasonAndStatus(result.current)).toEqual({ signInReason: null, status: "guest" });
    expect(result.current.user).toBeNull();
  });

  // A check that did not answer says nothing about whether a live session
  // stands: only a 401 makes a Guest, so this one ends nothing and forgets no
  // token, and no login route is taken. (A check is a GET, so it never comes
  // back `csrf-expired`: no token is fetched for it.)
  it.each([
    ["a 403", bare(403), "forbidden"],
    ["a 500", bare(500), "failed"],
    ["no answer at all", UNREACHABLE, "failed"],
  ] as const)(
    "leaves the session unknown, not a Guest, when its check meets %s",
    async (_name, answer, kind) => {
      const backend = fakeBackend({ [ME]: answer });
      await backend.holdToken();
      const { result } = renderHook(() => useAuthState(), { wrapper });
      await waitFor(() => expect(result.current.status).not.toBe("checking"));

      expect({
        checkFailure: result.current.checkFailure,
        status: result.current.status,
        tokenForgotten: await backend.tokenForgotten(),
      }).toEqual({ checkFailure: { kind }, status: "unchecked", tokenForgotten: false });
    },
  );

  it.each([
    ["signs in", json(USER), "authenticated"],
    ["makes a Guest", bare(401), "guest"],
    ["leaves the session unknown", bare(500), "unchecked"],
  ] as const)(
    "checking again after an unanswered check %s by the same rules",
    async (_name, answer, status) => {
      const backend = fakeBackend({ [ME]: [bare(500), answer] });
      const { result } = renderHook(() => useAuthState(), { wrapper });
      await waitFor(() => expect(result.current.status).toBe("unchecked"));

      act(() => result.current.retrySessionCheck());
      await waitFor(() => expect(checks(backend)).toBe(2));
      await waitFor(() => expect(result.current.status).not.toBe("checking"));

      expect(result.current.status).toBe(status);
    },
  );

  it("checks again on every retry, not only the first", async () => {
    const backend = fakeBackend({ [ME]: bare(500) });
    const { result } = renderHook(() => useAuthState(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe("unchecked"));

    for (const run of [2, 3]) {
      act(() => result.current.retrySessionCheck());
      await waitFor(() => expect(result.current.status).toBe("unchecked"));
      await waitFor(() => expect(checks(backend)).toBe(run));
    }

    expect(checks(backend)).toBe(3);
  });

  it.each([
    ["finds a session", json(USER)],
    ["fails", bare(500)],
  ])(
    "changes nothing, the token included, when its check %s after the provider unmounted",
    async (_name, answer) => {
      const check = deferred();
      const backend = fakeBackend({ [ME]: check.answer });
      await backend.holdToken();
      const { unmount } = renderHook(() => useAuthState(), { wrapper });

      unmount();
      await check.settle(answer);

      expect(await backend.tokenForgotten()).toBe(false);
    },
  );

  it("ends the session on a successful change and records why, forgetting the token", async () => {
    const session = await mounted({ ...SIGNED_IN_CONFINED, [CHANGE]: bare(204) });

    const settled = await settle(session, (auth) => auth.changePassword(OLD_VALUE, NEW_VALUE));

    expect(settled.outcome).toEqual({ kind: "changed" });
    expect(session.backend.requests()).toContainEqual({
      body: { currentPassword: OLD_VALUE, newPassword: NEW_VALUE },
      method: "POST",
      path: "/api/auth/change-password",
    });
    expect({
      ...signInReasonAndStatus(session.result.current),
      tokenForgotten: settled.tokenForgotten,
      user: session.result.current.user,
    }).toEqual({
      signInReason: "password-changed",
      status: "guest",
      tokenForgotten: true,
      user: null,
    });
  });

  // The session survives the 401, so the check that follows it still answers.
  it("keeps a flagged session, its user and its token on a refused current password", async () => {
    const session = await mounted({ ...SIGNED_IN_CONFINED, [CHANGE]: CURRENT_PASSWORD_REFUSED });

    const settled = await settle(session, (auth) => auth.changePassword(OLD_VALUE, NEW_VALUE));

    expect(settled).toEqual({
      outcome: { kind: "current-password-rejected" },
      status: "authenticated",
      tokenForgotten: false,
    });
    expect(session.result.current.user).toEqual(CONFINED);
    expect(session.result.current.signInReason).toBeNull();
  });

  it("signs in, forgetting the pre-login session's token", async () => {
    const session = await mounted({ ...GUEST, [LOGIN]: json(USER) });

    expect(await settle(session, (auth) => auth.login(...CREDENTIALS))).toEqual({
      outcome: { kind: "signed-in", user: USER },
      status: "authenticated",
      tokenForgotten: true,
    });
  });

  it("refuses wrong credentials, forgetting the token of the session the backend ended", async () => {
    const session = await mounted({ ...GUEST, [LOGIN]: bare(401) });

    expect(await settle(session, (auth) => auth.login(...CREDENTIALS))).toEqual({
      outcome: { kind: "refused" },
      status: "guest",
      tokenForgotten: true,
    });
  });

  it("resolves a failed login without a verdict, keeping the session and its token", async () => {
    const session = await mounted({ ...GUEST, [LOGIN]: bare(500) });

    expect(await settle(session, (auth) => auth.login(...CREDENTIALS))).toEqual({
      outcome: { kind: "failed" },
      status: "guest",
      tokenForgotten: false,
    });
  });

  it("logs out, forgetting the ended session's token", async () => {
    const session = await mounted({ ...SIGNED_IN, [LOGOUT]: bare(204) });

    expect(await settle(session, (auth) => auth.logout())).toEqual({
      outcome: { kind: "signed-out" },
      status: "guest",
      tokenForgotten: true,
    });
  });

  it("resolves a failed logout, keeping the session and its token", async () => {
    const session = await mounted({ ...SIGNED_IN, [LOGOUT]: bare(500) });

    expect(await settle(session, (auth) => auth.logout())).toEqual({
      outcome: { kind: "failed" },
      status: "authenticated",
      tokenForgotten: false,
    });
  });

  it("forgets the CSRF token of a session the backend has ended", async () => {
    const { backend, result } = await mounted(SIGNED_IN);
    await backend.holdToken();

    act(() => result.current.expireSession(result.current.sessionGeneration()));

    expect(await backend.tokenForgotten()).toBe(true);
  });

  /** Each way a session ends with a reason, and the answers that let it end so. */
  const ENDINGS = [
    [
      "a successful change",
      { ...SIGNED_IN_CONFINED, [CHANGE]: bare(204) },
      (auth: ReturnType<typeof useAuthState>) => auth.changePassword(OLD_VALUE, NEW_VALUE),
      "password-changed",
    ],
    [
      "an expiry",
      SIGNED_IN,
      (auth: ReturnType<typeof useAuthState>) => {
        auth.expireSession(auth.sessionGeneration());
        return Promise.resolve();
      },
      "expired",
    ],
    [
      "an Idle sign-out",
      { ...SIGNED_IN, [LOGOUT]: bare(204) },
      (auth: ReturnType<typeof useAuthState>) => auth.signOutForInactivity(),
      "inactive",
    ],
  ] as const;

  it.each(ENDINGS)(
    "clears the reason %s recorded on the next login",
    async (_name, answers, end, reason) => {
      const { result } = await mounted({ ...answers, [LOGIN]: json(USER) });

      await act(async () => {
        await end(result.current);
      });
      expect(result.current.signInReason).toBe(reason);

      await act(async () => {
        await result.current.login(...CREDENTIALS);
      });
      expect({ ...signInReasonAndStatus(result.current), user: result.current.user }).toEqual({
        signInReason: null,
        status: "authenticated",
        user: USER,
      });
    },
  );

  it.each(ENDINGS.filter(([name]) => name !== "an expiry"))(
    "says an expiry after %s ended the session by expiry",
    async (_name, answers, end) => {
      const { result } = await mounted(answers);

      await act(async () => {
        await end(result.current);
      });
      act(() => result.current.expireSession(result.current.sessionGeneration()));

      expect(signInReasonAndStatus(result.current)).toEqual({
        signInReason: "expired",
        status: "guest",
      });
    },
  );

  it("logs out to a cold guest, clearing the sign-in reason", async () => {
    const { result } = await mounted({
      ...SIGNED_IN_CONFINED,
      [CHANGE]: bare(204),
      [LOGOUT]: bare(204),
    });
    await act(async () => {
      await result.current.changePassword(OLD_VALUE, NEW_VALUE);
    });

    await act(async () => {
      await result.current.logout();
    });

    expect(signInReasonAndStatus(result.current)).toEqual({ signInReason: null, status: "guest" });
  });

  it("signs an idle session out: logs out, forgets the token and records why", async () => {
    const session = await mounted({ ...SIGNED_IN, [LOGOUT]: bare(204) });

    const { tokenForgotten: forgotten } = await settle(session, (auth) =>
      auth.signOutForInactivity(),
    );

    expect(session.backend.requests()).toContainEqual({
      method: "DELETE",
      path: "/api/auth/logout",
    });
    expect(forgotten).toBe(true);
    expect(signInReasonAndStatus(session.result.current)).toEqual({
      signInReason: "inactive",
      status: "guest",
    });
    expect(session.result.current.user).toBeNull();
  });

  it("ends an idle session and forgets its token even when its logout fails", async () => {
    const session = await mounted({ ...SIGNED_IN, [LOGOUT]: bare(500) });

    const { tokenForgotten } = await settle(session, (auth) => auth.signOutForInactivity());

    expect({
      ...signInReasonAndStatus(session.result.current),
      tokenForgotten,
      user: session.result.current.user,
    }).toEqual({ signInReason: "inactive", status: "guest", tokenForgotten: true, user: null });
  });
});

describe("AuthProvider and the EHR context", () => {
  const PATIENT = "eq081-VQEgP8drUUqCWzHfw3";

  /** The tab holds `PATIENT` in context, as the page loads. */
  const withPatientInContext = () => setEhrContext({ patient: PATIENT });

  type Auth = ReturnType<typeof useAuthState>;

  it.each([
    [
      "keeps",
      "the start-up check finding the session signed in, as on a reload",
      SIGNED_IN,
      () => Promise.resolve(),
      { patient: PATIENT },
    ],
    [
      "clears",
      "a sign-out",
      { ...SIGNED_IN, [LOGOUT]: bare(204) },
      (auth: Auth) => auth.logout(),
      null,
    ],
    [
      "clears",
      "a session expiry",
      SIGNED_IN,
      (auth: Auth) => {
        auth.expireSession(auth.sessionGeneration());
        return Promise.resolve();
      },
      null,
    ],
    [
      "clears",
      "an Idle sign-out",
      { ...SIGNED_IN, [LOGOUT]: bare(204) },
      (auth: Auth) => auth.signOutForInactivity(),
      null,
    ],
    [
      "clears",
      "a password change",
      { ...SIGNED_IN, [CHANGE]: bare(204) },
      (auth: Auth) => auth.changePassword(OLD_VALUE, NEW_VALUE),
      null,
    ],
    [
      "clears",
      "a password Login",
      { ...SIGNED_IN, [LOGIN]: json(USER) },
      (auth: Auth) => auth.login(...CREDENTIALS),
      null,
    ],
    [
      "keeps",
      "a login that reached no verdict",
      { ...SIGNED_IN, [LOGIN]: bare(500) },
      (auth: Auth) => auth.login(...CREDENTIALS),
      { patient: PATIENT },
    ],
  ] as [string, string, Answers, (auth: Auth) => Promise<unknown>, unknown][])(
    "%s it through %s",
    async (_verb, _action, answers, action, expected) => {
      withPatientInContext();
      const session = await mounted(answers);

      await settle(session, action);

      expect(ehrContext()).toEqual(expected);
    },
  );
});
