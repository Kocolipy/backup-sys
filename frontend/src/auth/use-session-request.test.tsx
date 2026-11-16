import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { bare, deferred, fakeBackend, json, type Answers } from "@/lib/fake-backend.testHelpers";

import { AuthProvider } from "./auth-context";
import { AuthContext, useAuthState, type AuthContextState } from "./auth-context-value";
import { AUTH_REQUESTS, authState, signedIn, testUser } from "./auth-context.testHelpers";
import { ehrContext, setEhrContext } from "./ehr-context";
import { useSessionRequest } from "./use-session-request";

// The hook reads one thing from the session — `expireSession` — so it runs
// against a hand-built auth state; the backend it requests from is the
// backend layer's.

const state: AuthContextState = authState(signedIn());

const wrapper = ({ children }: { children: ReactNode }) => (
  <AuthContext.Provider value={state}>{children}</AuthContext.Provider>
);

/** The hook's request, over a backend answering `answers`. */
function request(answers: Answers) {
  const backend = fakeBackend(answers);
  const send = renderHook(() => useSessionRequest(), { wrapper }).result.current;
  return { requests: backend.requests, send };
}

const decode = (response: Response) => response.json() as Promise<number>;

describe("useSessionRequest", () => {
  beforeEach(() => {
    vi.mocked(state.expireSession).mockReset();
  });

  it("sends the path and request it is handed, passing the decoded success through untouched", async () => {
    const { requests, send } = request({ "POST /api/example/change": json(1) });

    const result = await send("/api/example/change", { method: "POST" }, decode);

    expect({
      expired: vi.mocked(state.expireSession).mock.calls.length,
      requests: requests(),
      result,
    }).toEqual({
      expired: 0,
      requests: [{ method: "POST", path: "/api/example/change" }],
      result: { data: 1, kind: "ok" },
    });
  });

  it("reads no body for a request sent without a decoder", async () => {
    const { send } = request({ "GET /api/ping": json(1) });

    await expect(send("/api/ping")).resolves.toEqual({ data: undefined, kind: "ok" });
  });

  it("ends the session itself on a 401, handing the caller a plain failure", async () => {
    const { send } = request({ "GET /api/example": bare(401) });

    const result = await send("/api/example", {}, decode);

    expect(state.expireSession).toHaveBeenCalledOnce();
    expect(result).toEqual({ kind: "failed", status: 401 });
  });

  it.each([
    [
      "csrf-expired",
      { "GET /api/auth/csrf": bare(500) },
      { method: "POST" },
      { kind: "csrf-expired" },
    ],
    ["forbidden", { "GET /api/example": bare(403) }, {}, { kind: "forbidden" }],
    ["failed", { "GET /api/example": bare(503) }, {}, { kind: "failed", status: 503 }],
  ] as const)(
    "leaves a %s result for the caller to report",
    async (_kind, answers, init, result) => {
      const { send } = request(answers);

      await expect(send("/api/example", init, decode)).resolves.toEqual(result);
      expect(state.expireSession).not.toHaveBeenCalled();
    },
  );
});

// Whose `401` it is: the hook runs inside the real provider here, over the
// backend layer, so a request is held in flight while the session changes
// under it, and its `401` lands on whatever session stands by then.
describe("useSessionRequest, as the session changes under a request", () => {
  const { login: LOGIN, logout: LOGOUT, me: ME } = AUTH_REQUESTS;
  const USER = testUser();
  const PATIENT = "eq081-VQEgP8drUUqCWzHfw3";

  const providerWrapper = ({ children }: { children: ReactNode }) => (
    <AuthProvider>{children}</AuthProvider>
  );

  /** Signed in as `USER`, with a read of `/api/example` held until the test answers it. */
  async function signedInWithReadHeld() {
    const read = deferred();
    fakeBackend({
      [LOGIN]: json(USER),
      [LOGOUT]: bare(204),
      [ME]: json(USER),
      "GET /api/example": read.answer,
    });
    const { result } = renderHook(() => ({ auth: useAuthState(), send: useSessionRequest() }), {
      wrapper: providerWrapper,
    });
    await waitFor(() => expect(result.current.auth.status).toBe("authenticated"));
    const sent = result.current.send("/api/example");
    return { read, result, sent };
  }

  it("ends the session it was sent under with expired, on a 401 while it stands", async () => {
    const { read, result, sent } = await signedInWithReadHeld();

    await read.settle(bare(401));

    expect({
      outcome: await sent,
      signInReason: result.current.auth.signInReason,
      status: result.current.auth.status,
    }).toEqual({
      outcome: { kind: "failed", status: 401 },
      signInReason: "expired",
      status: "guest",
    });
  });

  it("leaves no expired reason on a 401 landing after the user signed out", async () => {
    const { read, result, sent } = await signedInWithReadHeld();
    await act(async () => {
      await result.current.auth.logout();
    });

    await read.settle(bare(401));

    expect({
      outcome: await sent,
      signInReason: result.current.auth.signInReason,
      status: result.current.auth.status,
    }).toEqual({ outcome: { kind: "failed", status: 401 }, signInReason: null, status: "guest" });
  });

  it("leaves a fresh sign-in standing, its EHR context too, on an old session's 401", async () => {
    const { read, result } = await signedInWithReadHeld();
    await act(async () => {
      await result.current.auth.logout();
    });
    await act(async () => {
      await result.current.auth.login("ada", "chosen-1");
    });
    setEhrContext({ patient: PATIENT });

    await read.settle(bare(401));

    expect({
      ehrContext: ehrContext(),
      status: result.current.auth.status,
    }).toEqual({
      ehrContext: { patient: PATIENT },
      status: "authenticated",
    });
  });
});
