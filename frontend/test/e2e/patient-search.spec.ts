import { expect, test, type Page } from "@playwright/test";

import {
  expectOnHomeInContext,
  resultRows,
  search,
  searchResults,
  selectResult,
} from "./patient.helpers";

/**
 * Patient Search as a Medical Officer meets it, against the patient source's
 * mock (`backend/patient-source-mock`): its canned name search spanning two
 * pages in the mock's own order, filtered by gender, its NRIC search, and its
 * search that matches nothing. Nothing about a search or its filter reaches
 * the URL. Selecting a result puts its Patient into the tab's EHR
 * context, shown on Home's Patient card and the top bar; that signing out and
 * back in leaves none is `patient-sign-out.spec.ts`'. What the page decides
 * without the backend — a query too short to send, keyboard selection, a
 * second selection, Search after one — is
 * `search.test.tsx`'s; the sidebar's links are `roles-medical-officer.spec.ts`'.
 *
 * In the `medical-officer` project, replaying the saved `mo1` session: a member
 * of the Medical Officer fixture Group, which carries `patient:read`.
 */

/** The rows are the Patients `names`, in order: each row starts with its Patient's name. */
const expectNames = (page: Page, names: readonly string[]) =>
  expect(resultRows(page)).toHaveText(
    names.map((name) => {
      const literal = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
      // The pattern is built from `literal`, every special character of which is
      // escaped on the line above, so it matches a row starting with `name` alone.
      // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
      return new RegExp(`^${literal}`);
    }),
  );

test.describe("Patient Search", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/search");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Patient Search");
  });

  test("pages a name search to its second page, in the patient source's order", async ({
    page,
  }) => {
    await search(page, "tan");

    await expect(page.getByText("Showing 1 to 5 of 7 patients")).toBeVisible();
    await expectNames(page, [
      "Tan Hui Min",
      "Stanley Goh Kok Wee",
      "Tan Wei Ming",
      "Lee Mei Ling",
      "Nur Intan binte Yusof",
    ]);

    await page.getByRole("button", { name: "Page 2" }).click();

    await expect(page.getByText("Showing 6 to 7 of 7 patients")).toBeVisible();
    await expect(resultRows(page)).toHaveText([
      "Kavitha TanabalanS9512345AFemale09 Sep 199507 May 2026",
      "Tan Boon HuatS5512345AMale23 Jan 1955—",
    ]);
    await expect(page).toHaveURL("/search");
  });

  test("finds the one Patient holding a full NRIC", async ({ page }) => {
    await search(page, "s9212345a");

    await expect(resultRows(page)).toHaveText([
      "Siti Nurhaliza binte AhmadS9212345AFemale21 Aug 199211 Jul 2026",
    ]);
    await expect(page.getByText("Showing 1 to 1 of 1 patients")).toBeVisible();
    await expect(page).toHaveURL("/search");
  });

  test("says when no Patient matches", async ({ page }) => {
    await search(page, "nobody");

    await expect(page.getByText("No patients match your search")).toBeVisible();
    await expect(searchResults(page)).toHaveCount(0);
    await expect(page.getByText(/^Showing/)).toHaveCount(0);
  });

  test.describe("filtered", () => {
    const filterButton = (page: Page) => page.getByRole("button", { name: /^Filter/ });

    const panel = (page: Page) => page.getByRole("form", { name: "Patient filters" });

    test("filters by gender, and counts the filter", async ({ page }) => {
      await search(page, "tan");
      await filterButton(page).click();

      await panel(page).getByRole("radio", { exact: true, name: "Male" }).click();
      await panel(page).getByRole("button", { name: "Apply" }).click();

      await expect(page.getByText("Showing 1 to 3 of 3 patients")).toBeVisible();
      await expectNames(page, ["Stanley Goh Kok Wee", "Tan Wei Ming", "Tan Boon Huat"]);
      await expect(filterButton(page)).toHaveAccessibleName("Filter, 1 set");
      await expect(page).toHaveURL("/search");
    });
  });

  test("puts the Patient of a selected row in context, on Home's Patient card and the top bar", async ({
    page,
  }) => {
    await search(page, "tan");

    await selectResult(page, "Tan Hui Min");

    await expectOnHomeInContext(page, "Tan Hui Min", "S9012345A");
  });
});
