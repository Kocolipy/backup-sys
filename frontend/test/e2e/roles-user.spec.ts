import { expect, test } from "@playwright/test";

import { expectNoPatientAccess, recordPatientRequests } from "./patient.helpers";
import { sidebar } from "./shell.helpers";

// The seeded `guest`: a User in no Group, holding baseline access and no
// Permission, so no patient access either. What it sees, and the pages the SPA
// routes it away from, are asserted here. What it holds, its self-service, and
// the backend refusing it every administrative read, the Patient read and the
// Patient search need no browser, and are the backend's suites' to prove
// (`DevelopmentFixturesIntegrationTests`, `AuthorizationContractTests`); Logout
// is `login.spec.ts`', the top bar naming a User in no Group `top-bar.test.tsx`'s,
// and every other page it is routed away from `App.test.tsx`'s. In the `user` project, replaying the saved session, so
// its login is `auth.setup.ts`', which waits for Home; a signed-in visit to the
// login page is routed by the same landing rule.

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

  test("is offered no Search or FFI link, only Profile", async ({ page }) => {
    await page.goto("/");

    await expect(sidebar(page).getByRole("link")).toHaveText(["Profile"]);
  });

  test("is routed from Patient Search to Home", async ({ page }) => {
    await page.goto("/search");

    await expect(page.getByRole("heading", { name: "Profile" })).toBeVisible();
    await expect(page).toHaveURL("/");
  });
});
