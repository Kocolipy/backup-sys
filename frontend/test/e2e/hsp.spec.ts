import { expect, test, type Page } from "@playwright/test";

import { expectOnDemographicInContext, search, selectResult } from "./patient.helpers";
import { sidebar } from "./shell.helpers";

/**
 * The HSP page as a Medical Officer meets it, against the patient source's mock
 * (`backend/patient-source-mock`): the patient in context is selected on
 * Patient Search, then HSP opened from the sidebar, headed by the patient's
 * Patient card. Tan Wei Ming has exactly three HSP Records, sorted by each date
 * both ways and filtered by the Status and by a multi-select, and Tan Boon
 * Huat three shown as received (`/docs/domain/hsp.md`, "The patient source's
 * HSP fixtures"). What the page decides without the backend — the labels, the
 * badges, the Status's colour, "—", "Error" and a raw code, the paging, each
 * filter's parameters, an inverted range, no patient in context and a
 * failure — is `hsp.test.tsx`'s; what every history page shares — a patient
 * with no records, a filter matching none, Reset and the backend's real `502`
 * reaching the page — is read once, in `medication.spec.ts`; that a session
 * without `patient:read` is offered no HSP link is `roles-user.spec.ts`', and
 * that it is routed to Home `App.test.tsx`'s.
 *
 * In the `medical-officer` project, replaying the saved `mo1` session. A new
 * page starts with no EHR context: the tab's `sessionStorage` is its own.
 */

/** The HSP table's body rows. */
const hspRows = (page: Page) =>
  page
    .getByRole("table", { name: "HSP History" })
    .getByRole("row")
    .filter({ has: page.getByRole("cell") });

/** Puts the Patient `name`, with NRIC `nric`, into context from Patient Search's `query`. */
async function selectPatient(page: Page, query: string, name: string, nric: string) {
  await page.goto("/search");
  await search(page, query);
  await selectResult(page, name);
  await expectOnDemographicInContext(page, name, nric);
}

/** Opens HSP from the sidebar, titled HSP History and headed by the Patient card of `name`. */
async function openHsp(page: Page, name: string) {
  await sidebar(page).getByRole("link", { exact: true, name: "HSP" }).click();
  await expect(page).toHaveURL("/hsp");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("HSP History");
  await expect(
    page.getByRole("region", { exact: true, name: "Patient" }).getByRole("heading"),
  ).toHaveText(name);
}

const TAN_WEI_MING_ROWS = {
  high: "HIGH22 Nov 202210 Oct 2023Hendon Camp MCInactive",
  low: "LOW11 Mar 202410 Mar 2025Nee Soon Camp MCActive",
  moderate: "MODERATE14 Oct 202313 Oct 2024Mandai Hill Camp MCInactive",
};
const { high, low, moderate } = TAN_WEI_MING_ROWS;

test.describe("HSP", () => {
  test("shows exactly Tan Wei Ming's three HSP Records beneath his Patient card once he is selected on Patient Search", async ({
    page,
  }) => {
    await selectPatient(page, "tan", "Tan Wei Ming", "S1234567A");

    await openHsp(page, "Tan Wei Ming");

    await expect(hspRows(page)).toHaveText([moderate, low, high]);
    await expect(page.getByText("Showing 1 to 3 of 3 records")).toBeVisible();
  });

  // The patient source is the truth (ADR 0020): Tan Boon Huat's HSP Records
  // hold an outcome left out and a Submitting MC null, a Start Date of the
  // wrong JSON type, an End Date not to the day and a Status of the wrong JSON
  // type, and a record with no id whose codes are outside their sets.
  test('shows Tan Boon Huat\'s HSP Records as received: "—", "Error" and raw codes in one table', async ({
    page,
  }) => {
    await page.goto("/search");
    await search(page, "tan");
    await page.getByRole("button", { name: "Page 2" }).click();
    await selectResult(page, "Tan Boon Huat");
    await expectOnDemographicInContext(page, "Tan Boon Huat", "S5512345A");

    await openHsp(page, "Tan Boon Huat");

    await expect(hspRows(page)).toHaveText([
      "—16 Mar 202215 Mar 2023—Active",
      "LOWErrorErrorKranji Camp MCError",
      "critical16 Mar 202015 Mar 2021tengah-air-baseexpired",
    ]);
    await expect(page.getByText("Showing 1 to 3 of 3 records")).toBeVisible();
    // The first misses its Outcome and the second holds its Status in the
    // wrong format, Mandatory elements of an HSP Record: those rows warn, its
    // invalid Start Date naming nothing, and the third, whose codes are
    // outside their sets, does not.
    for (const [row, warns] of [1, 1, 0].entries()) {
      await expect(hspRows(page).nth(row).getByRole("img")).toHaveCount(warns);
    }
    const warning = hspRows(page).nth(1).getByRole("img");
    await expect(warning).toHaveAccessibleName("Critical information missing: Status");
    await warning.hover();
    await expect(page.getByRole("tooltip")).toHaveText("Critical information missing: Status");
  });

  test.describe("sorted", () => {
    const header = (page: Page, heading: string) =>
      page.getByRole("table", { name: "HSP History" }).getByRole("columnheader", { name: heading });

    const sortBy = (page: Page, heading: string) =>
      header(page, heading).getByRole("button").click();

    // His records' own order is by neither date, in either direction, so each
    // sorted order below is the mock's stub of that sort and no fallback to
    // the source's own order.
    test("sorts by Start Date, then by End Date, both ways, in the stubbed orders", async ({
      page,
    }) => {
      await selectPatient(page, "tan", "Tan Wei Ming", "S1234567A");
      await openHsp(page, "Tan Wei Ming");
      await expect(hspRows(page)).toHaveText([moderate, low, high]);

      await sortBy(page, "Start Date");
      await expect(header(page, "Start Date")).toHaveAttribute("aria-sort", "ascending");
      await expect(hspRows(page)).toHaveText([high, moderate, low]);

      await sortBy(page, "Start Date");
      await expect(header(page, "Start Date")).toHaveAttribute("aria-sort", "descending");
      await expect(hspRows(page)).toHaveText([low, moderate, high]);

      await sortBy(page, "End Date");
      await expect(header(page, "End Date")).toHaveAttribute("aria-sort", "ascending");
      await expect(header(page, "Start Date")).toHaveAttribute("aria-sort", "none");
      await expect(hspRows(page)).toHaveText([high, moderate, low]);

      await sortBy(page, "End Date");
      await expect(header(page, "End Date")).toHaveAttribute("aria-sort", "descending");
      await expect(hspRows(page)).toHaveText([low, moderate, high]);
      await expect(page).toHaveURL("/hsp");
    });
  });

  test.describe("filtered", () => {
    const filterButton = (page: Page) => page.getByRole("button", { name: /^Filter/ });

    const panel = (page: Page) => page.getByRole("form", { name: "HSP filters" });

    const apply = (page: Page) => panel(page).getByRole("button", { name: "Apply" }).click();

    /** Opens Tan Wei Ming's history, then the Filter panel. */
    async function filterTanWeiMing(page: Page) {
      await selectPatient(page, "tan", "Tan Wei Ming", "S1234567A");
      await openHsp(page, "Tan Wei Ming");
      await expect(hspRows(page)).toHaveCount(3);
      await filterButton(page).click();
    }

    test("applies the Status toggle, reading its stubbed row", async ({ page }) => {
      await filterTanWeiMing(page);

      await panel(page).getByRole("radio", { exact: true, name: "Active" }).click();
      await apply(page);

      await expect(page.getByText("Showing 1 to 1 of 1 record")).toBeVisible();
      await expect(hspRows(page)).toHaveText([low]);
      await expect(filterButton(page)).toHaveAccessibleName("Filter, 1 set");
    });

    test("applies a multi-select filter, reading its stubbed rows", async ({ page }) => {
      await filterTanWeiMing(page);

      await panel(page)
        .getByRole("button", { name: /^Outcome: / })
        .click();
      for (const label of ["LOW", "HIGH"]) {
        await page.getByRole("option", { exact: true, name: label }).click();
      }
      await page.keyboard.press("Escape");
      await apply(page);

      await expect(page.getByText("Showing 1 to 2 of 2 records")).toBeVisible();
      await expect(hspRows(page)).toHaveText([low, high]);
      await expect(filterButton(page)).toHaveAccessibleName("Filter, 1 set");
      await expect(page).toHaveURL("/hsp");
    });
  });
});
