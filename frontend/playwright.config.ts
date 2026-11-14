import { defineConfig, devices } from "@playwright/test";

import { ADMIN_SESSION, MEDICAL_OFFICER_SESSION, USER_SESSION } from "./test/e2e/sessions";

/**
 * The projects only `make integration-test` declares (frontend/AGENTS.md), each
 * when that run's environment names what it needs, so a bare `npm run
 * test:e2e` against a backend started some other way never sees them. Each
 * replays the Admin session `setup` saves, as the `admin` project does, so they
 * run ahead of `guest`, whose sign-in as the seeded Admin ends that session,
 * and after `admin`.
 */
const adminSessionExtras = [
  // The packaged SPA, named by the origin of the packaged JAR, which serves the
  // built SPA under the backend's CSP. The Admin session's cookie is scoped to
  // the host, not the port, so it replays there too; it signs no one in.
  ...(process.env.E2E_PACKAGED_BASE_URL
    ? [
        {
          name: "packaged",
          testMatch: /packaged-(?:csp|type)\.spec\.ts/,
          // After every project that writes connectors (`connectorWriters`),
          // as `routes` runs: its route walk loads Accounts.
          dependencies: ["setup", ...connectorWriters()],
          use: {
            ...devices["Desktop Chrome"],
            baseURL: process.env.E2E_PACKAGED_BASE_URL,
            storageState: ADMIN_SESSION,
          },
        },
      ]
    : []),
];

/**
 * The projects whose specs create and delete connectors of their own: `admin`.
 * A spec asserting that Accounts loads with no alert runs after them, not
 * beside them — Accounts reads each listed connector's tokens, and a connector
 * another worker deletes between the listing and that read fails it with an
 * alert. A function declaration, so `packaged`, declared above it, can name
 * them too.
 */
function connectorWriters(): string[] {
  return ["admin"];
}

export default defineConfig({
  testDir: "./test/e2e",
  fullyParallel: true,
  // Unconditional, not `!!process.env.CI`: this repo has no CI, so gating it
  // on that variable left a stray `test.only` silently narrowing the E2E run
  // to one spec while still exiting zero. `test/arch/skippedTests.test.ts`
  // keeps the marker out of the tree; this keeps one that reached a working
  // copy from passing the run (/backend/AGENTS.md, "Test suite policy").
  forbidOnly: true,
  retries: process.env.CI ? 2 : 0,
  // Capped at 2 locally: the dev box runs tight on memory, and one Chromium
  // per worker is the biggest cost in this suite. CI stays serial.
  workers: process.env.CI ? 1 : 2,
  reporter: "html",
  use: {
    baseURL: "http://localhost:5173",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },

  projects: [
    {
      name: "setup",
      testMatch: /auth\.setup\.ts/,
      use: { ...devices["Desktop Chrome"] },
    },

    // `testMatch` names its specs explicitly rather than globbing `*.spec.ts`,
    // which is what gives test/arch/e2eSpecRouting.test.ts something to
    // enforce: a new spec that nobody routed here fails that arch test instead
    // of being silently skipped at runtime.
    {
      name: "guest",
      testMatch: /(?:smoke|login|dev-roles)\.spec\.ts/,
      // After every project replaying `admin.json`: `routes`, which runs after
      // `admin`, and `adminSessionExtras`. The backend keeps one session per
      // User and a successful login ends every other one, so `login.spec.ts` signing
      // in as the seeded Admin revokes the session `admin.json` replays. Run
      // beside the `admin` project, that turned its specs into 401s.
      dependencies: ["routes", ...adminSessionExtras.map(({ name }) => name)],
      use: {
        ...devices["Desktop Chrome"],
        storageState: { cookies: [], origins: [] },
      },
    },
    // A session without patient:read: the `guest` fixture, in no Group.
    {
      name: "user",
      testMatch: /roles-user\.spec\.ts/,
      dependencies: ["setup"],
      use: {
        ...devices["Desktop Chrome"],
        storageState: USER_SESSION,
      },
    },
    // A session holding patient:read: the `mo1` Medical Officer, whose session
    // `setup` saves and no other project signs in as, so it runs beside the rest.
    {
      name: "medical-officer",
      testMatch: /(?:roles-medical-officer|patient-search|ffi|numc|armc)\.spec\.ts/,
      dependencies: ["setup"],
      use: {
        ...devices["Desktop Chrome"],
        storageState: MEDICAL_OFFICER_SESSION,
      },
    },
    // Signing out and back in as `mo2`, the Medical Officer no other project
    // signs in as: no saved session to keep, so it waits on no project.
    {
      name: "patient-sign-out",
      testMatch: /patient-sign-out\.spec\.ts/,
      use: {
        ...devices["Desktop Chrome"],
        storageState: { cookies: [], origins: [] },
      },
    },
    {
      name: "admin",
      testMatch:
        /(?:home|session|session-revocation|idle-sign-out|login-lockout|accounts-admin|change-password|audit|group-permissions|group-members|accounts-paging|stale-401)\.spec\.ts/,
      // After the `user` project, not beside it: this project drives the
      // administration surface, and ordering it last keeps any future spec that
      // acts on a seeded identity from pulling a session out from under the
      // `user` project. `accounts-admin.spec.ts` itself only acts on Users it
      // provisions over SCIM, never on the seeded `user`.
      dependencies: ["setup", "user"],
      use: {
        ...devices["Desktop Chrome"],
        storageState: ADMIN_SESSION,
      },
    },
    // Every signed-in route loading with an empty console and no alert. After
    // every project that writes connectors (`connectorWriters`), and ahead of
    // `guest`, which ends the Admin session it replays.
    {
      name: "routes",
      testMatch: /console-errors\.spec\.ts/,
      dependencies: ["setup", ...connectorWriters()],
      use: {
        ...devices["Desktop Chrome"],
        storageState: ADMIN_SESSION,
      },
    },
    {
      name: "dormancy",
      testMatch: /dormancy\.spec\.ts/,
      // Last and alone: it signs in as the Helpdesk and Auditor fixture Users,
      // the Auditor as `dev-roles.spec.ts` does, and consumes the `dormant`
      // fixture's locked state, which only a backend restart re-arms.
      dependencies: ["guest"],
      use: {
        ...devices["Desktop Chrome"],
        storageState: { cookies: [], origins: [] },
      },
    },
    ...adminSessionExtras,
  ],

  webServer: {
    command: "npm run dev",
    // No watching and no HMR socket, so no page reloads mid-spec (vite.config.ts). A
    // server you started yourself and Playwright reuses keeps both.
    env: { E2E_DEV_SERVER: "1" },
    url: "http://localhost:5173",
    reuseExistingServer: !process.env.CI,
    timeout: 120 * 1000,
  },
});
