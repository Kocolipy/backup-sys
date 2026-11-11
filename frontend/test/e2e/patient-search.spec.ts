import { expect, test, type Page } from "@playwright/test";

import { loginAs } from "./auth.helpers";
import { expectNoPatientInContext } from "./patient.helpers";
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
import { ADMIN_SESSION } from "./sessions";

/**
 * Patient Search as a signed-in User meets it, against the patient source's
 * mock (`backend/patient-source-mock`): its canned name search spanning two
 * pages, sorted by each column both ways and filtered by gender and by each
 * date range, a filtered search spanning two pages, its NRIC search, and its
 * search that matches nothing. Nothing about a search, its filters or its sort
 * reaches the URL. Selecting a result puts its Patient into the tab's EHR
 * context, shown on Home's Patient card and the top bar, and signing out and
 * back in leaves none. What the page decides without the backend — a query
 * too short to send, a date range refused in the Filter panel, keyboard
 * selection, a second selection, Search after one — is `search.test.tsx`'s;
 * the sidebar's links are `roles-user.spec.ts`'.
 *
 * In the `user` project: `patient:read` is a baseline Permission, so the seeded
 * User holding nothing else searches as any signed-in User does. Signing out
 * would end the session the project replays, so the test that signs out and
 * back in does it as a User it provisions, through a connector the saved Admin
 * session creates, and deletes both afterwards.
 */

const box = (page: Page) => page.getByRole("searchbox", { name: "Search by name or NRIC" });

const results = (page: Page) => page.getByRole("table", { name: "Matching patients" });

/** Each body row's cells, as text. */
const rowTexts = (page: Page) =>
  results(page)
    .getByRole("row")
    .filter({ has: page.getByRole("cell") });

/** The rows are the Patients `names`, in order: each row starts with its Patient's name. */
const expectNames = (page: Page, names: readonly string[]) =>
  expect(rowTexts(page)).toHaveText(
    names.map((name) => {
      const literal = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
      // The pattern is built from `literal`, every special character of which is
      // escaped on the line above, so it matches a row starting with `name` alone.
      // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
      return new RegExp(`^${literal}`);
    }),
  );

async function search(page: Page, text: string) {
  await box(page).fill(text);
  await box(page).press("Enter");
}

test.describe("Patient Search", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/search");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Patient Search");
  });

  test("pages a name search to its second page, newest Last visit first", async ({ page }) => {
    await search(page, "tan");

    await expect(page.getByText("Showing 1 to 5 of 7 patients")).toBeVisible();
    await expect(rowTexts(page).getByRole("cell").first()).toHaveText("Tan Hui Min");
    await expect(rowTexts(page)).toHaveCount(5);

    await page.getByRole("button", { name: "Page 2" }).click();

    await expect(page.getByText("Showing 6 to 7 of 7 patients")).toBeVisible();
    await expect(rowTexts(page)).toHaveText([
      "Kavitha TanabalanS9512345AFemale09 Sep 199507 May 2026",
      "Tan Boon HuatS5512345AMale23 Jan 1955—",
    ]);
    await expect(page).toHaveURL("/search");
  });

  test("finds the one Patient holding a full NRIC", async ({ page }) => {
    await search(page, "s9212345a");

    await expect(rowTexts(page)).toHaveText([
      "Siti Nurhaliza binte AhmadS9212345AFemale21 Aug 199211 Jul 2026",
    ]);
    await expect(page.getByText("Showing 1 to 1 of 1 patients")).toBeVisible();
    await expect(page).toHaveURL("/search");
  });

  test("says when no Patient matches", async ({ page }) => {
    await search(page, "nobody");

    await expect(page.getByText("No patients match your search")).toBeVisible();
    await expect(results(page)).toHaveCount(0);
    await expect(page.getByText(/^Showing/)).toHaveCount(0);
  });

  test.describe("sorted", () => {
    const sortBy = (page: Page, heading: string) =>
      results(page).getByRole("columnheader", { name: heading }).getByRole("button").click();

    for (const { heading, ascending, descending } of [
      {
        heading: "Date of Birth",
        ascending: [
          "Tan Boon Huat",
          "Lee Mei Ling",
          "Stanley Goh Kok Wee",
          "Tan Wei Ming",
          "Tan Hui Min",
        ],
        descending: [
          "Nur Intan binte Yusof",
          "Kavitha Tanabalan",
          "Tan Hui Min",
          "Tan Wei Ming",
          "Stanley Goh Kok Wee",
        ],
      },
      {
        heading: "Last Visit",
        ascending: [
          "Kavitha Tanabalan",
          "Nur Intan binte Yusof",
          "Lee Mei Ling",
          "Tan Wei Ming",
          "Stanley Goh Kok Wee",
        ],
        descending: [
          "Tan Hui Min",
          "Stanley Goh Kok Wee",
          "Tan Wei Ming",
          "Lee Mei Ling",
          "Nur Intan binte Yusof",
        ],
      },
    ]) {
      test(`sorts by ${heading}, ascending then descending`, async ({ page }) => {
        await search(page, "tan");
        await expect(page.getByText("Showing 1 to 5 of 7 patients")).toBeVisible();
        const header = results(page).getByRole("columnheader", { name: heading });

        await sortBy(page, heading);
        await expect(header).toHaveAttribute("aria-sort", "ascending");
        await expectNames(page, ascending);

        await sortBy(page, heading);
        await expect(header).toHaveAttribute("aria-sort", "descending");
        await expectNames(page, descending);
        await expect(page.getByText("Showing 1 to 5 of 7 patients")).toBeVisible();
        await expect(page).toHaveURL("/search");
      });
    }

    test("puts the Patient with no Last visit last, oldest Last visit first", async ({ page }) => {
      await search(page, "tan");
      await sortBy(page, "Last Visit");
      await expect(rowTexts(page).first()).toHaveText(/^Kavitha Tanabalan/);

      await page.getByRole("button", { name: "Page 2" }).click();

      await expect(rowTexts(page)).toHaveText([
        "Tan Hui MinS9012345AFemale04 Feb 199002 Nov 2026",
        "Tan Boon HuatS5512345AMale23 Jan 1955—",
      ]);
    });
  });

  test.describe("filtered", () => {
    const filterButton = (page: Page) => page.getByRole("button", { name: /^Filter/ });

    const panel = (page: Page) => page.getByRole("form", { name: "Patient filters" });

    const dateField = (page: Page, range: string, end: "From" | "To") =>
      panel(page).getByRole("group", { name: range }).getByLabel(end);

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

    for (const { range, from, to, matched } of [
      {
        range: "Date of Birth",
        from: "1980-01-01",
        to: "1999-12-31",
        matched: ["Tan Hui Min", "Tan Wei Ming", "Kavitha Tanabalan"],
      },
      {
        range: "Last Visit",
        from: "2026-08-01",
        to: "2026-10-20",
        matched: ["Tan Wei Ming", "Lee Mei Ling", "Nur Intan binte Yusof"],
      },
    ]) {
      test(`filters by a ${range} range`, async ({ page }) => {
        await search(page, "tan");
        await filterButton(page).click();

        await dateField(page, range, "From").fill(from);
        await dateField(page, range, "To").fill(to);
        await panel(page).getByRole("button", { name: "Apply" }).click();

        await expect(page.getByText("Showing 1 to 3 of 3 patients")).toBeVisible();
        await expectNames(page, matched);
        await expect(filterButton(page)).toHaveAccessibleName("Filter, 2 set");
        await expect(page).toHaveURL("/search");
      });
    }

    test("pages a filtered search to its second page", async ({ page }) => {
      await search(page, "tan");
      await filterButton(page).click();
      await dateField(page, "Date of Birth", "From").fill("1960-01-01");
      await panel(page).getByRole("button", { name: "Apply" }).click();
      await expect(page.getByText("Showing 1 to 5 of 6 patients")).toBeVisible();

      await page.getByRole("button", { name: "Page 2" }).click();

      await expect(page.getByText("Showing 6 to 6 of 6 patients")).toBeVisible();
      await expect(rowTexts(page)).toHaveText([
        "Kavitha TanabalanS9512345AFemale09 Sep 199507 May 2026",
      ]);
    });
  });

  test.describe("selecting a result", () => {
    /** Home's Patient card holding a patient: a banner named Patient, headed by their name. */
    const patientCard = (page: Page) => page.getByRole("region", { exact: true, name: "Patient" });

    const topBarPatient = (page: Page) => page.getByRole("region", { name: "Patient in context" });

    /** Selects the result row naming `name`, by a click on its NRIC cell. */
    const select = (page: Page, name: string) =>
      rowTexts(page)
        .filter({ has: page.getByRole("button", { exact: true, name }) })
        .getByRole("cell")
        .nth(1)
        .click();

    /** Home, showing the Patient `name` with NRIC `nric` on its Patient card and the top bar. */
    async function expectOnHomeInContext(page: Page, name: string, nric: string) {
      await expect(page).toHaveURL("/");
      await expect(page.getByRole("heading", { level: 1 })).toHaveText("Profile");
      await expect(patientCard(page).getByRole("heading")).toHaveText(name);
      await expect(patientCard(page).getByText(nric, { exact: true })).toBeVisible();
      await expect(topBarPatient(page)).toHaveText(`${name}${nric}`);
    }

    test("puts the Patient of a selected row in context, on Home's Patient card and the top bar", async ({
      page,
    }) => {
      await search(page, "tan");

      await select(page, "Tan Hui Min");

      await expectOnHomeInContext(page, "Tan Hui Min", "S9012345A");
    });

    test.describe("signing out", () => {
      const connector = workerConnector("patient-search", { session: ADMIN_SESSION });

      test("leaves no patient in context after signing out and back in", async ({ browser }) => {
        const userName = `${E2E_PREFIX}patient-search-${runId()}`;
        const id = await provisionUser(connector.scim, userName);
        const context = await freshBrowser(browser);
        try {
          await settlePassword(userName);
          const page = await context.newPage();
          await loginAs(page, userName, OWN_PASSWORD);
          await page.goto("/search");
          await search(page, "tan");
          await select(page, "Tan Hui Min");
          await expectOnHomeInContext(page, "Tan Hui Min", "S9012345A");

          await page.getByRole("button", { name: "Sign out" }).click();
          await expect(page.getByRole("heading", { name: "Welcome back" })).toBeVisible();
          await loginAs(page, userName, OWN_PASSWORD);

          await expectNoPatientInContext(page);
          await expect(topBarPatient(page)).toHaveCount(0);
        } finally {
          await context.close();
          await deprovisionUser(connector.scim, id);
        }
      });
    });
  });
});
