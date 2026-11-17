import { expect, test, type Page } from "@playwright/test";

import {
  expectOnDemographicInContext,
  resultRows,
  search,
  searchResults,
  selectResult,
} from "./patient.helpers";

/**
 * Patient Search as a Medical Officer meets it, against the patient source's
 * mock (`backend/patient-source-mock`): its canned name search spanning two
 * pages in the mock's own order, filtered by gender, its NRIC searches — one
 * matching a Patient whose Last visit is in the wrong format — its received
 * search, which drops two matches with a bad id and shows the rest as
 * received, and its search that matches nothing. Nothing about a search or its filter reaches
 * the URL. Selecting a result puts its Patient into the tab's EHR
 * context, shown on the Demographic page's Patient card and the top bar; that signing out and
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

    await expect(page.getByText("Showing 1 to 5 of 7 records")).toBeVisible();
    await expectNames(page, [
      "Tan Hui Min",
      "Stanley Goh Kok Wee",
      "Tan Wei Ming",
      "Lee Mei Ling",
      "Nur Intan binte Yusof",
    ]);

    await page.getByRole("button", { name: "Page 2" }).click();

    await expect(page.getByText("Showing 6 to 7 of 7 records")).toBeVisible();
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
    await expect(page.getByText("Showing 1 to 1 of 1 record")).toBeVisible();
    await expect(page).toHaveURL("/search");
  });

  // The patient source is the truth (ADR 0020): Rajesh Kumar's Last visit is
  // to the month alone, so his row shows it as "Error", and the search stands.
  test("shows a Last visit in the wrong format as Error, in its row", async ({ page }) => {
    await search(page, "S9912345B");

    await expect(resultRows(page)).toHaveText(["Rajesh KumarS9912345BMale28 May 1999Error"]);
  });

  // The patient source is the truth (ADR 0020), but a match's id is validated:
  // the received search's two Patients with no id or a malformed one are
  // dropped and counted, its Observation is a row of "Error" cells, and the
  // rest show each field as its value, "—" or "Error", beside the source's
  // total.
  test("drops a match with a bad id, with a note, and shows the rest as received", async ({
    page,
  }) => {
    await search(page, "teo");

    await expect(resultRows(page)).toHaveText([
      "Teo Ai LingErrorFError14 Oct 2026",
      "ErrorErrorErrorErrorError",
      "—————",
    ]);
    await expect(page.getByText("2 records couldn't be shown", { exact: true })).toBeVisible();
    await expect(page.getByText("Showing 1 to 5 of 5 records")).toBeVisible();
    await expect(page.getByText(/Teo Boon Kiat|Teo Hock Seng/)).toHaveCount(0);
  });

  test("says when no Patient matches", async ({ page }) => {
    await search(page, "nobody");

    await expect(page.getByText("No records match your search.", { exact: true })).toBeVisible();
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

      await expect(page.getByText("Showing 1 to 3 of 3 records")).toBeVisible();
      await expectNames(page, ["Stanley Goh Kok Wee", "Tan Wei Ming", "Tan Boon Huat"]);
      await expect(filterButton(page)).toHaveAccessibleName("Filter, 1 set");
      await expect(page).toHaveURL("/search");
    });
  });

  test("puts the Patient of a selected row in context, on the Demographic page's Patient card and the top bar", async ({
    page,
  }) => {
    await search(page, "tan");

    await selectResult(page, "Tan Hui Min");

    await expectOnDemographicInContext(page, "Tan Hui Min", "S9012345A");
  });

  test("shows a selected Patient's Demographic details, as the patient source holds them", async ({
    page,
  }) => {
    await search(page, "tan");
    await selectResult(page, "Tan Wei Ming");
    await expectOnDemographicInContext(page, "Tan Wei Ming", "S1234567A");

    await expect(
      page.getByRole("region", { name: "Military Overview" }).getByRole("definition"),
    ).toHaveText([
      "NSman",
      "CPT",
      "3 SIR / Army",
      "Infantry Officer",
      "DXO",
      "ME2",
      "RSS Supreme / Navy",
      "Naval Safety Officer",
      "15 Jan 2018",
      "14 Jan 2020",
      "—",
      "08 Sep 2037",
    ]);
    await expect(
      page.getByRole("region", { name: "Contact Details" }).getByRole("definition"),
    ).toHaveText([
      "+65 9123 4567",
      "tan.hw@email.com",
      "Blk 123 Bishan Street 12 #10-456, Singapore 570123",
    ]);
    await expect(
      page.getByRole("region", { name: "Next of Kin / Emergency" }).getByRole("definition"),
    ).toHaveText(["Mdm Lee Siew Kuen", "Mother", "+65 8234 5678"]);
  });
});
