import { expect, test, type Response } from "@playwright/test";

import { submitLogin } from "./auth.helpers";
import {
  deprovisionUser,
  E2E_PREFIX,
  freshBrowser,
  OWN_PASSWORD,
  provisionUser,
  runId,
  settlePassword,
  workerConnector,
} from "./scim.helpers";

/**
 * The SPA's own sign-out for inactivity, against the real backend and a
 * real browser: the idle limit is the one `GET /api/auth/me` reports, the
 * warning is a native modal `alertdialog` the keyboard can answer, staying is a
 * real request, the sign-out at the limit really logs out, and an active User's
 * input renews the session with real requests.
 *
 * The page's clock is Playwright's, so fifteen minutes pass in an instant. The
 * backend's clock is not faked, so its session is still live when the SPA's
 * limit arrives and the logout is a real `204`.
 *
 * The User is provisioned here and deleted in a `finally`: an idle sign-out
 * ends its session, which must never be a seeded identity's the other projects
 * replay.
 */

const RUN = runId();
const connector = workerConnector("idle");

const INACTIVE_NOTICE = "You were signed out because you were inactive. Please sign in again.";

/** A renewal the backend accepted: a `GET /api/auth/me` answered `200`. */
const isRenewal = (response: Response) =>
  response.url().endsWith("/api/auth/me") && response.status() === 200;

test("warns before the idle limit, answers the keyboard, stays on Escape, and signs out at the limit", async ({
  browser,
}) => {
  test.setTimeout(90_000);
  const userName = `${E2E_PREFIX}idle-${RUN}`;
  const id = await provisionUser(connector.scim, userName);
  try {
    await settlePassword(userName);
    const context = await freshBrowser(browser);
    try {
      const page = await context.newPage();
      await page.clock.install();
      await submitLogin(page, userName, OWN_PASSWORD);
      await expect(page).toHaveURL("/");

      const me = await page.request.get("/api/auth/me");
      const { idleTimeoutSeconds } = (await me.json()) as { idleTimeoutSeconds: number };
      expect(idleTimeoutSeconds, "the backend reports its idle bound").toBeGreaterThan(120);

      // The page's clock also runs at wall speed between the sign-in and each
      // jump, so the closed check stands half a minute clear of the warning's
      // minute: only a stall that long could open it early.
      await page.clock.fastForward((idleTimeoutSeconds - 90) * 1000);
      await expect(page.getByRole("alertdialog")).toHaveCount(0);
      // Past a minute before the limit, the warning opens, holding focus.
      await page.clock.fastForward(30_000);
      const warning = page.getByRole("alertdialog", { name: "Are you still there?" });
      await expect(warning).toBeVisible();
      await expect(warning).toBeFocused();

      // Tab moves through the dialog's own buttons alone.
      await page.keyboard.press("Tab");
      await expect(warning.getByRole("button", { name: "Sign out" })).toBeFocused();
      await page.keyboard.press("Tab");
      await expect(warning.getByRole("button", { name: "Stay signed in" })).toBeFocused();

      // Escape stays signed in, with one real request that renews the session.
      const stayed = page.waitForResponse(isRenewal);
      await page.keyboard.press("Escape");
      await stayed;
      await expect(warning).toHaveCount(0);

      // A full limit from the stay, untouched: signed out, and told why.
      await page.clock.fastForward(idleTimeoutSeconds * 1000);
      await expect(page.getByRole("heading", { name: "Welcome back" })).toBeVisible();
      await expect(page).toHaveURL(/\/login$/);
      await expect(page.getByRole("status")).toHaveText(INACTIVE_NOTICE);
      expect((await page.request.get("/api/auth/me")).status(), "logged out on the backend").toBe(
        401,
      );
    } finally {
      await context.close();
    }
  } finally {
    await deprovisionUser(connector.scim, id);
  }
});

test("keeps an active User signed in past the idle limit, renewing the session at most once a minute", async ({
  browser,
}) => {
  test.setTimeout(90_000);
  const userName = `${E2E_PREFIX}idle-active-${RUN}`;
  const id = await provisionUser(connector.scim, userName);
  try {
    await settlePassword(userName);
    const context = await freshBrowser(browser);
    try {
      const page = await context.newPage();
      await page.clock.install();
      await submitLogin(page, userName, OWN_PASSWORD);
      await expect(page).toHaveURL("/");

      const me = await page.request.get("/api/auth/me");
      const { idleTimeoutSeconds } = (await me.json()) as { idleTimeoutSeconds: number };
      let renewals = 0;
      page.on("response", (response) => {
        if (isRenewal(response)) renewals += 1;
      });

      // Twice the limit on the page's clock, moving the pointer every half
      // minute: only that input can renew the session, since nothing polls.
      // Every second move finds the last renewal a minute old and renews; the
      // page's minutes pass in milliseconds here, so each renewal is let land
      // before the next is due, as a real minute would. The backend's own
      // fifteen minutes never pass here, so this proves real renewals the
      // backend accepted; that they keep its session past its Idle timeout is
      // `src/auth/idle-sign-out.test.tsx`'s.
      const steps = Math.ceil((2 * idleTimeoutSeconds) / 30);
      for (let step = 1; step <= steps; step += 1) {
        await page.clock.fastForward(30_000);
        const renewed = step % 2 === 0 ? page.waitForResponse(isRenewal) : null;
        await page.mouse.move(10 + (step % 2) * 20, 10);
        await renewed;
        await expect(page.getByRole("alertdialog")).toHaveCount(0);
      }

      await expect(page).toHaveURL("/");
      expect(renewals, "one renewal a minute, no more").toBe(Math.floor(steps / 2));
      expect((await page.request.get("/api/auth/me")).status(), "the session is still live").toBe(
        200,
      );
    } finally {
      await context.close();
    }
  } finally {
    await deprovisionUser(connector.scim, id);
  }
});
