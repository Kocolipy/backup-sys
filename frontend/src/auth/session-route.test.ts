import { describe, expect, it } from "vitest";

import {
  CREDENTIAL_CHANGE_PATH,
  LOGIN_PATH,
  resolveSessionRoute,
  type RoutedSession,
  type SessionRequirement,
  type SessionRoute,
  type SessionRouteInput,
} from "./session-route";
import { EVERY_PERMISSION } from "./auth-context.testHelpers";
import type { SessionEndReason } from "./sign-in-reason";

const CHECKING: RoutedSession = { status: "checking" };
const AUTHENTICATED: RoutedSession = { status: "authenticated" };
/** A start-up check that came back `403`: the failure travels with the status. */
const UNCHECKED: RoutedSession = { checkFailure: { kind: "forbidden" }, status: "unchecked" };
const guest = (signInReason: SessionEndReason | null = null): RoutedSession => ({
  signInReason,
  status: "guest",
});

const input = (overrides: Partial<SessionRouteInput> = {}): SessionRouteInput => ({
  passwordChangeRequired: false,
  pathname: "/",
  requires: "authenticated",
  session: AUTHENTICATED,
  ...overrides,
});

/** A confined session exactly as the backend reports one: flagged, and holding no Permission. */
const flagged = (overrides: Partial<SessionRouteInput> = {}): SessionRouteInput =>
  input({ passwordChangeRequired: true, permissions: [], ...overrides });

/** The Accounts page's requirement: any one of its views' Permissions. */
const ACCOUNTS: SessionRequirement = { anyOf: ["user:read", "group:read", "connector:read"] };

const toChangePassword: SessionRoute = { kind: "redirect", to: CREDENTIAL_CHANGE_PATH };

/** Where a session holding `patient:read` lands: Patient Search. */
const toSearch: SessionRoute = { kind: "redirect", to: "/search" };
/** Where every other session lands: Home. */
const toHome: SessionRoute = { kind: "redirect", to: "/" };

describe("resolveSessionRoute for the change-required flag", () => {
  const cases: [string, SessionRouteInput, SessionRoute][] = [
    [
      "waits for a flagged session like any other",
      flagged({ session: CHECKING }),
      { kind: "pending" },
    ],
    [
      "renders the change-password route for a flagged session",
      flagged({ pathname: CREDENTIAL_CHANGE_PATH }),
      { kind: "render" },
    ],
    ["confines a flagged session on Home", flagged({ pathname: "/" }), toChangePassword],
    [
      "confines a flagged session on a Permission-guarded route",
      flagged({ pathname: "/accounts", requires: ACCOUNTS }),
      toChangePassword,
    ],
    [
      "confines a flagged session even if Permissions were reported",
      flagged({ pathname: "/accounts", permissions: EVERY_PERMISSION, requires: ACCOUNTS }),
      toChangePassword,
    ],
    [
      "sends a flagged session off the login route to the change",
      flagged({ pathname: LOGIN_PATH, requires: "guest" }),
      toChangePassword,
    ],
    [
      "confines a flagged session on a path with no route of its own",
      flagged({ pathname: "/no-such-page", requires: "landing" }),
      toChangePassword,
    ],
    [
      "ignores a recorded return destination while flagged",
      flagged({ pathname: LOGIN_PATH, requires: "guest", returnTo: "/accounts" }),
      toChangePassword,
    ],
    [
      "renders the change-password route for an unflagged baseline User",
      input({ pathname: CREDENTIAL_CHANGE_PATH, permissions: [] }),
      { kind: "render" },
    ],
    [
      "renders the change-password route for an unflagged Superuser",
      input({ pathname: CREDENTIAL_CHANGE_PATH, permissions: EVERY_PERMISSION }),
      { kind: "render" },
    ],
    [
      "lets an unflagged session holding the Permission reach the guarded route",
      input({ pathname: "/accounts", permissions: EVERY_PERMISSION, requires: ACCOUNTS }),
      { kind: "render" },
    ],
    [
      "sends a Guest on the change-password route to login",
      input({ pathname: CREDENTIAL_CHANGE_PATH, session: guest() }),
      { kind: "redirect", state: { from: CREDENTIAL_CHANGE_PATH }, to: LOGIN_PATH },
    ],
    [
      "returns a User whose change succeeded to login, recording no return destination",
      input({
        pathname: CREDENTIAL_CHANGE_PATH,
        session: guest("password-changed"),
      }),
      { kind: "redirect", state: { reason: "password-changed" }, to: LOGIN_PATH },
    ],
    [
      "returns a User whose change locked the account to login, recording no return destination",
      input({ pathname: CREDENTIAL_CHANGE_PATH, session: guest("locked") }),
      { kind: "redirect", state: { reason: "locked" }, to: LOGIN_PATH },
    ],
    [
      "renders login after a successful change",
      input({
        pathname: LOGIN_PATH,
        requires: "guest",
        session: guest("password-changed"),
      }),
      { kind: "render" },
    ],
  ];

  it.each(cases)("%s", (_name, given, expected) => {
    expect(resolveSessionRoute(given)).toEqual(expected);
  });
});

describe("resolveSessionRoute", () => {
  const cases: [string, SessionRouteInput, SessionRoute][] = [
    [
      "waits on a protected route while the session status is unknown",
      input({ session: CHECKING }),
      { kind: "pending" },
    ],
    [
      "waits on a guest route while the session status is unknown",
      input({ pathname: LOGIN_PATH, requires: "guest", session: CHECKING }),
      { kind: "pending" },
    ],
    // A check that did not answer makes nobody a Guest: no route to login, no
    // return destination recorded, and no render of what the session may hold.
    [
      "neither renders nor redirects a protected route when the session could not be checked",
      input({ session: UNCHECKED }),
      { failure: { kind: "forbidden" }, kind: "unchecked" },
    ],
    [
      "neither renders nor redirects a guest route when the session could not be checked",
      input({ pathname: LOGIN_PATH, requires: "guest", session: UNCHECKED }),
      { failure: { kind: "forbidden" }, kind: "unchecked" },
    ],
    [
      "renders a protected route for an authenticated visitor",
      input({ session: AUTHENTICATED }),
      { kind: "render" },
    ],
    [
      "sends a guest to login, recording the return destination",
      input({ pathname: "/", session: guest() }),
      { kind: "redirect", state: { from: "/" }, to: LOGIN_PATH },
    ],
    [
      "marks the redirect as an Expired session, replaying the page it left",
      input({ pathname: "/", session: guest("expired") }),
      { kind: "redirect", state: { from: "/", reason: "expired" }, to: LOGIN_PATH },
    ],
    [
      "marks the redirect as an Idle sign-out, replaying the page it left",
      input({ pathname: "/accounts", session: guest("inactive") }),
      { kind: "redirect", state: { from: "/accounts", reason: "inactive" }, to: LOGIN_PATH },
    ],
    [
      "renders a guest route for a guest",
      input({ pathname: LOGIN_PATH, requires: "guest", session: guest() }),
      { kind: "render" },
    ],
    [
      "lands a session without patient:read off a guest route on Home",
      input({ pathname: LOGIN_PATH, permissions: ["audit:read"], requires: "guest" }),
      toHome,
    ],
    [
      "lands a session holding patient:read off a guest route on Patient Search",
      input({ pathname: LOGIN_PATH, permissions: ["patient:read"], requires: "guest" }),
      toSearch,
    ],
    [
      "replays the recorded return destination instead of the landing page",
      input({
        pathname: LOGIN_PATH,
        permissions: ["patient:read"],
        requires: "guest",
        returnTo: "/accounts/settings",
        session: AUTHENTICATED,
      }),
      { kind: "redirect", to: "/accounts/settings" },
    ],
    [
      "lands a session without patient:read on Home from a path with no route of its own",
      input({ pathname: "/no-such-page", permissions: [], requires: "landing" }),
      toHome,
    ],
    [
      "lands a session holding patient:read on Patient Search from a path with no route of its own",
      input({ pathname: "/no-such-page", permissions: ["patient:read"], requires: "landing" }),
      toSearch,
    ],
    [
      "sends a guest on a path with no route of its own to login, recording it",
      input({ pathname: "/no-such-page", requires: "landing", session: guest() }),
      { kind: "redirect", state: { from: "/no-such-page" }, to: LOGIN_PATH },
    ],
    [
      "waits on a path with no route of its own while the session status is unknown",
      input({ pathname: "/no-such-page", requires: "landing", session: CHECKING }),
      { kind: "pending" },
    ],
    [
      "renders login for a Guest whose session expired",
      input({ pathname: LOGIN_PATH, requires: "guest", session: guest("expired") }),
      { kind: "render" },
    ],
  ];

  it.each(cases)("%s", (_name, given, expected) => {
    expect(resolveSessionRoute(given)).toEqual(expected);
  });

  it("records the visited path, not a fixed one, as the return destination", () => {
    const route = resolveSessionRoute(input({ pathname: "/reports/42", session: guest() }));
    expect(route).toEqual({
      kind: "redirect",
      state: { from: "/reports/42" },
      to: LOGIN_PATH,
    });
  });

  it("renders a Permission-guarded route for a session holding its Permission", () => {
    expect(
      resolveSessionRoute(
        input({ pathname: "/accounts", permissions: ["user:read"], requires: ACCOUNTS }),
      ),
    ).toEqual({ kind: "render" });
  });

  it("routes a session holding none of its Permissions, and no patient:read, to Home", () => {
    expect(
      resolveSessionRoute(
        input({
          pathname: "/accounts",
          // Real Permissions, just not this route's: an Auditor and a Monitoring account.
          permissions: ["audit:read", "ops:read"],
          requires: ACCOUNTS,
        }),
      ),
    ).toEqual(toHome);
  });

  it("routes a session holding none of its Permissions but patient:read to Patient Search", () => {
    expect(
      resolveSessionRoute(
        input({ pathname: "/accounts", permissions: ["patient:read"], requires: ACCOUNTS }),
      ),
    ).toEqual(toSearch);
  });

  it("routes a session without patient:read off Patient Search to Home", () => {
    expect(
      resolveSessionRoute(
        input({ pathname: "/search", permissions: [], requires: { anyOf: ["patient:read"] } }),
      ),
    ).toEqual(toHome);
  });

  it("sends a guest on a Permission-guarded route to login with its return destination", () => {
    expect(
      resolveSessionRoute(input({ pathname: "/accounts", requires: ACCOUNTS, session: guest() })),
    ).toEqual({
      kind: "redirect",
      state: { from: "/accounts" },
      to: LOGIN_PATH,
    });
  });
});
