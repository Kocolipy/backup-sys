import { expect, test, type Page } from "@playwright/test";

import { expectOnHomeInContext, search, selectResult } from "./patient.helpers";

/**
 * The FFI page as a Medical Officer meets it, against the patient source's mock
 * (`backend/patient-source-mock`): the patient in context is selected on
 * Patient Search, then FFI opened from the sidebar. Tan Wei Ming has exactly
 * four FFIs, Tan Hui Min seven over two pages, and Siti Nurhaliza binte Ahmad
 * none (`/docs/domain/ffi.md`, "The patient source's FFI fixtures"). What the
 * page decides without the backend — the labels, the badges, a 404 and a
 * failure — is `ffi.test.tsx`'s; that a session without `patient:read` is
 * offered no FFI link is `roles-user.spec.ts`'.
 *
 * In the `medical-officer` project, replaying the saved `mo1` session. A new
 * page starts with no EHR context: the tab's `sessionStorage` is its own.
 */

const sidebar = (page: Page) => page.locator('[data-slot="sidebar"]');

/** The FFI table's body rows. */
const ffiRows = (page: Page) =>
  page
    .getByRole("table", { name: "FFI History" })
    .getByRole("row")
    .filter({ has: page.getByRole("cell") });

/** Puts the Patient `name`, with NRIC `nric`, into context from Patient Search's `query`. */
async function selectPatient(page: Page, query: string, name: string, nric: string) {
  await page.goto("/search");
  await search(page, query);
  await selectResult(page, name);
  await expectOnHomeInContext(page, name, nric);
}

/** Opens FFI from the sidebar, headed FFI History. */
async function openFfi(page: Page) {
  await sidebar(page).getByRole("link", { exact: true, name: "FFI" }).click();
  await expect(page).toHaveURL("/ffi");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("FFI History");
}

test.describe("FFI", () => {
  test("shows exactly Tan Wei Ming's four FFIs once he is selected on Patient Search", async ({
    page,
  }) => {
    await selectPatient(page, "tan", "Tan Wei Ming", "S1234567A");

    await openFfi(page);

    await expect(ffiRows(page)).toHaveText([
      "Overseas ExercisesFIT14 Oct 202313 Oct 2024CAMONee Soon Camp MCInactive",
      "IPPT FFIREQUEST WAIVER10 Jan 202409 Jan 2025CNMOKranji Camp MCActive",
      "Dental FFIFIT22 Nov 202321 Nov 2024CARMOHendon Camp MCActive",
      "Vocational FFIUNFIT05 Aug 202304 Aug 2024CAMOMandai Hill Camp MCInactive",
    ]);
    await expect(page.getByText("Showing 1 to 4 of 4 FFIs")).toBeVisible();
  });

  test("pages Tan Hui Min's seven FFIs to the second page", async ({ page }) => {
    await selectPatient(page, "tan", "Tan Hui Min", "S9012345A");
    await openFfi(page);
    await expect(page.getByText("Showing 1 to 5 of 7 FFIs")).toBeVisible();
    await expect(ffiRows(page)).toHaveCount(5);

    await page.getByRole("button", { name: "Page 2" }).click();

    await expect(page.getByText("Showing 6 to 7 of 7 FFIs")).toBeVisible();
    await expect(ffiRows(page)).toHaveText([
      "Vocational FFIREQUEST WAIVER17 Apr 202316 Apr 2024CNMOKranji Camp MCInactive",
      "IPPT FFIUNFIT05 Dec 202204 Dec 2023CAMOHendon Camp MCInactive",
    ]);
  });

  test("says No FFIs for a patient with none", async ({ page }) => {
    await selectPatient(page, "s9212345a", "Siti Nurhaliza binte Ahmad", "S9212345A");

    await openFfi(page);

    await expect(page.getByText("No FFIs")).toBeVisible();
    await expect(page.getByRole("table", { name: "FFI History" })).toHaveCount(0);
  });

  test("says No patient in context with no patient in context, and reads nothing", async ({
    page,
  }) => {
    const reads: string[] = [];
    page.on("request", (request) => {
      if (new URL(request.url()).pathname.startsWith("/api/patient-ffi/"))
        reads.push(request.url());
    });

    await page.goto("/ffi");

    await expect(page.getByRole("heading", { level: 1 })).toHaveText("FFI History");
    await expect(page.getByRole("main").getByText("No patient in context")).toBeVisible();
    expect(reads).toEqual([]);
  });
});
