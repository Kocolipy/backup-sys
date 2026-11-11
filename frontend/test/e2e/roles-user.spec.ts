import { expect, test, type Page } from "@playwright/test";

import { expectNoPatientAccess, recordPatientRequests } from "./patient.helpers";

// The seeded `guest`: a User in no Group, holding baseline access and no
// Permission, so no patient access either. What it sees, and the pages the SPA
// routes it away from, are asserted here. What it holds, its self-service, and
// the backend refusing it every administrative read, the Patient read and the
// Patient search need no browser, and are the backend's suites' to prove
// (`DevelopmentFixturesIntegrationTests`, `AuthorizationContractTests`); Logout
// is `login.spec.ts`'. In the `user` project, replaying the saved session, so
// its login is `auth.setup.ts`', which waits for Home; a signed-in visit to the
// login page is routed by the same landing rule.

/** The navigation sidebar, which the layout renders beside every signed-in page. */
const sidebar = (page: Page) => page.locator('[data-slot="sidebar"]');

test.describe("baseline User pages", () => {
  test("lands on Home, whose Patient card says it has no access and reads nothing", async ({
    page,
  }) => {
    const patientRequests = recordPatientRequests(page);
    await page.goto("/login");

    await expect(page).toHaveURL("/");
    await expect(page.getByRole("heading", { name: "Profile" })).toBeVisible();
    await expectNoPatientAccess(page);
    await expect(page.getByRole("alert")).toHaveCount(0);
    expect(patientRequests()).toEqual([]);
  });

  test("is offered no Search link, only Profile", async ({ page }) => {
    await page.goto("/");

    await expect(sidebar(page).getByRole("link")).toHaveText(["Profile"]);
  });

  for (const path of ["/search", "/accounts"]) {
    test(`is routed from ${path} to Home`, async ({ page }) => {
      await page.goto(path);

      await expect(page.getByRole("heading", { name: "Profile" })).toBeVisible();
      await expect(page).toHaveURL("/");
    });
  }
});
