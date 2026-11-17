import { expect, test } from "@playwright/test";

import { sidebar } from "./shell.helpers";

// Each page's route is `App.test.tsx`'s and its title `signed-in-shell.test.tsx`'s;
// that every route loads clean is `console-errors.spec.ts`', what Home shows a
// session without `patient:read` `roles-user.spec.ts`', and a signed-in visit
// to login landing on Patient Search `roles-medical-officer.spec.ts`'.

test.describe("Home", () => {
  // The Superuser holds `patient:read`, so Home is refused to it and its guard
  // sends it on to its landing page.
  test("sends the Superuser on to Patient Search", async ({ page }) => {
    await page.goto("/");

    await expect(page).toHaveURL("/search");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Patient Search");
  });

  // The Superuser opens every page, so the navigation sidebar links each of
  // them; Settings is the top bar's.
  test("links the Superuser to every page from the navigation sidebar", async ({ page }) => {
    await page.goto("/search");

    await expect(sidebar(page).getByRole("link")).toHaveText([
      "Search",
      "Demographic",
      "Medication",
      "FFI",
      "NUMC",
      "ARMC",
      "Accounts",
      "Audit logs",
    ]);
  });
});
