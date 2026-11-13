import { expect, test } from "@playwright/test";

import { sidebar } from "./shell.helpers";

// Each page's route is `App.test.tsx`'s and its title `signed-in-shell.test.tsx`'s;
// that every route loads clean is `console-errors.spec.ts`', and a signed-in
// visit to login landing on Patient Search `roles-medical-officer.spec.ts`'.
test.describe("Home", () => {
  // The Superuser opens every page, so the navigation sidebar links each of
  // them; Settings is the top bar's.
  test("links the Superuser to every page from the navigation sidebar", async ({ page }) => {
    await page.goto("/");

    await expect(sidebar(page).getByRole("link")).toHaveText([
      "Search",
      "Profile",
      "FFI",
      "Accounts",
      "Audit logs",
    ]);
  });
});
