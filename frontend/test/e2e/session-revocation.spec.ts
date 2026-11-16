import { expect, test, type Browser, type Page } from "@playwright/test";

import { adminRequest, EXPIRED_NOTICE, submitLogin, submitPasswordChange } from "./auth.helpers";
import {
  deprovisionGroup,
  deprovisionUser,
  E2E_PREFIX,
  freshBrowser,
  OWN_PASSWORD,
  provisionGroup,
  provisionUser,
  runId,
  settlePassword,
  workerConnector,
} from "./scim.helpers";

/**
 * Sessions ended from somewhere other than the browser holding them, seen from
 * that browser: a second sign-in as the same User (one session per User), and
 * a SCIM write the backend revokes sessions for after commit — the deletion of
 * a Group carrying Permissions.
 *
 * The backend's integration tests prove which writes delete the sessions —
 * deactivation, deletion, a password replace, a rename and removal from a Group
 * of power (`ScimConditionalWriteIntegrationTests`, `ScimDeletionIntegrationTests`),
 * and a second sign-in (`LoginSessionHardeningIntegrationTests`). What only a
 * browser can show is the SPA's reaction, the same whatever ended the session:
 * the next request the page makes is refused, and the User lands on the login
 * page TOLD their session ended, rather than on a broken page or a silent
 * sign-in form. A Group's deletion is the SCIM write here because no backend
 * integration test revokes through it against the real session store, and a
 * deactivated User's next sign-in is refused, and answered as every refused
 * sign-in is, by the backend's own suites (`LoginLockoutTests`,
 * `AuthControllerTests`).
 *
 * Every User is provisioned here and deleted in a `finally`; the seeded
 * identities, whose sessions the other projects replay, are never touched.
 */

const RUN = runId();
const connector = workerConnector("revocation");

/** The new password a revoked session's change asks for, which is never applied. */
const UNAPPLIED_PASSWORD = "Never-Applied-Change-4k";

/** A provisioned User, settled on {@link OWN_PASSWORD}; deleted again by `withUser`. */
async function withUser(label: string, body: (userName: string, id: string) => Promise<void>) {
  const userName = `${E2E_PREFIX}rev-${label}-${RUN}`;
  const id = await provisionUser(connector.scim, userName);
  try {
    await settlePassword(userName);
    await body(userName, id);
  } finally {
    await deprovisionUser(connector.scim, id);
  }
}

/** Signs `userName` in through the login page of a browser of its own. */
async function signedInBrowser(browser: Browser, userName: string) {
  const context = await freshBrowser(browser);
  const page = await context.newPage();
  await submitLogin(page, userName, OWN_PASSWORD);
  await expect(page).toHaveURL("/");
  await expect(page.getByText(`Signed in as ${userName}`)).toBeVisible();
  return page;
}

/**
 * Park the page on Settings, which holds the password change, reached by an
 * in-app link so the SPA keeps its authenticated state, and nothing there
 * writes anything.
 */
async function park(page: Page) {
  await page.getByRole("link", { name: "Open settings", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Change your password" })).toBeVisible();
}

/**
 * The page's next request, after the session was ended elsewhere: the parked
 * page's password change is refused for want of a session, and the SPA sends
 * the User to sign in with the expired-session notice. Home is no use here: with
 * no patient in context it sends nothing. The refusal comes before the password
 * check, so nothing is written; a session still standing would change the
 * password instead, and the sign-ins with `OWN_PASSWORD` after it would fail.
 * Signing in again returns the User to Settings, where it ended.
 */
async function expectNextRequestEndsSession(page: Page) {
  await submitPasswordChange(page, OWN_PASSWORD, UNAPPLIED_PASSWORD);
  await expect(page.getByRole("heading", { name: "Welcome back" })).toBeVisible();
  await expect(page.getByRole("status")).toHaveText(EXPIRED_NOTICE);
  expect((await page.request.get("/api/auth/me")).status()).toBe(401);
}

test("a second sign-in as the same User ends the first browser's session", async ({ browser }) => {
  test.setTimeout(90_000);
  await withUser("single", async (userName) => {
    const first = await signedInBrowser(browser, userName);
    try {
      await park(first);

      const second = await signedInBrowser(browser, userName);
      try {
        await expectNextRequestEndsSession(first);

        // The session kept is the newer one: it goes on working.
        await second.reload();
        await expect(second.getByText(`Signed in as ${userName}`)).toBeVisible();
        expect((await second.request.get("/api/auth/me")).status()).toBe(200);
      } finally {
        await second.context().close();
      }
    } finally {
      await first.context().close();
    }
  });
});

test("deleting a Group carrying Permissions over SCIM signs its member's open SPA session out", async ({
  browser,
  page: adminPage,
}) => {
  test.setTimeout(90_000);

  await withUser("group-deleted", async (userName, id) => {
    // A Group of this test's own, carrying audit:read: the Superuser sets what it confers.
    const groupName = `${E2E_PREFIX}disposable-${RUN}`;
    const groupId = await provisionGroup(connector.scim, groupName, [id]);
    const groupPath = `/scim/v2/Groups/${groupId}`;
    const granted = await adminRequest(
      adminPage,
      "PUT",
      `/api/admin/groups/${groupId}/permissions`,
      ["audit:read"],
    );
    expect(granted.status(), "granting the disposable Group audit:read").toBe(200);

    const page = await signedInBrowser(browser, userName);
    try {
      // Precondition: the Group's Permission reached the session.
      await page.goto("/audit");
      await expect(page.getByRole("heading", { name: "Audit", exact: true })).toBeVisible();
      await page.goto("/");
      await park(page);

      const current = await connector.scim.get(groupPath);
      expect(current.status(), `GET ${groupPath}`).toBe(200);
      const deleted = await connector.scim.delete(groupPath, {
        headers: { "If-Match": current.headers()["etag"] ?? "" },
      });
      expect(deleted.status(), "deleting the Group over SCIM").toBe(204);

      await expectNextRequestEndsSession(page);

      // Signed in again, the deleted Group confers nothing: the audit page routes
      // away and the backend refuses the trail.
      await submitLogin(page, userName, OWN_PASSWORD);
      await expect(page).toHaveURL("/settings");
      await page.goto("/audit");
      await expect(page).toHaveURL("/");
      expect((await page.request.get("/api/admin/audit-events")).status()).toBe(403);
    } finally {
      await page.context().close();
      await deprovisionGroup(connector.scim, groupId);
    }
  });
});
