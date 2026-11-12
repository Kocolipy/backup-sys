import { expect, test } from "@playwright/test";

/**
 * The Medical Officer's role: a member of the Medical Officer fixture Group,
 * which carries `patient:read` alone. Its landing page is Patient Search, and
 * its sidebar links Search, Profile and FFI with no Admin group. What it holds, and
 * the backend refusing it every administrative read, need no browser
 * (`DevelopmentFixturesIntegrationTests`, `AuthorizationContractTests`).
 *
 * In the `medical-officer` project, replaying the saved `mo1` session, so its
 * login is `auth.setup.ts`', which waits for Patient Search; a signed-in visit
 * to the login page is routed by the same landing rule.
 */
test.describe("Medical Officer pages", () => {
  test("lands on Patient Search, linked to Search, Profile and FFI, with no Admin group", async ({
    page,
  }) => {
    await page.goto("/login");

    await expect(page).toHaveURL("/search");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Patient Search");
    const sidebar = page.locator('[data-slot="sidebar"]');
    await expect(sidebar.getByRole("link")).toHaveText(["Search", "Profile", "FFI"]);
    await expect(sidebar.getByRole("button", { name: "Admin" })).toHaveCount(0);
  });
});
