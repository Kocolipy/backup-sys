import { expect, test, type Page } from "@playwright/test";

import { expectOnHomeInContext, search, selectResult } from "./patient.helpers";

/**
 * The FFI page as a Medical Officer meets it, against the patient source's mock
 * (`backend/patient-source-mock`): the patient in context is selected on
 * Patient Search, then FFI opened from the sidebar. Tan Wei Ming has exactly
 * four FFIs, sorted by each date both ways, Tan Hui Min seven over two pages,
 * filtered by a multi-select, a date range — one spanning two pages, one
 * matching nothing — and the Status, and Siti Nurhaliza binte Ahmad none
 * (`/docs/domain/ffi.md`, "The patient source's FFI fixtures"). What the page
 * decides without the backend — the labels, the badges, each filter's
 * parameters, a 404 and a failure — is `ffi.test.tsx`'s; that a session
 * without `patient:read` is offered no FFI link is `roles-user.spec.ts`'.
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

  test.describe("sorted", () => {
    const TAN_WEI_MING_ROWS = {
      dental: "Dental FFIFIT22 Nov 202321 Nov 2024CARMOHendon Camp MCActive",
      ippt: "IPPT FFIREQUEST WAIVER10 Jan 202409 Jan 2025CNMOKranji Camp MCActive",
      overseas: "Overseas ExercisesFIT14 Oct 202313 Oct 2024CAMONee Soon Camp MCInactive",
      vocational: "Vocational FFIUNFIT05 Aug 202304 Aug 2024CAMOMandai Hill Camp MCInactive",
    };
    const { dental, ippt, overseas, vocational } = TAN_WEI_MING_ROWS;

    const header = (page: Page, heading: string) =>
      page.getByRole("table", { name: "FFI History" }).getByRole("columnheader", { name: heading });

    const sortBy = (page: Page, heading: string) =>
      header(page, heading).getByRole("button").click();

    // His FFIs' own order is by neither date; each sort is the mock's stub of it.
    test("sorts by Issue Date, then by Expiry Date both ways, in the stubbed orders", async ({
      page,
    }) => {
      await selectPatient(page, "tan", "Tan Wei Ming", "S1234567A");
      await openFfi(page);
      await expect(ffiRows(page)).toHaveText([overseas, ippt, dental, vocational]);

      await sortBy(page, "Issue Date");
      await expect(header(page, "Issue Date")).toHaveAttribute("aria-sort", "ascending");
      await expect(ffiRows(page)).toHaveText([vocational, overseas, dental, ippt]);

      await sortBy(page, "Expiry Date");
      await expect(header(page, "Expiry Date")).toHaveAttribute("aria-sort", "ascending");
      await expect(header(page, "Issue Date")).toHaveAttribute("aria-sort", "none");
      await expect(ffiRows(page)).toHaveText([vocational, overseas, dental, ippt]);

      await sortBy(page, "Expiry Date");
      await expect(header(page, "Expiry Date")).toHaveAttribute("aria-sort", "descending");
      await expect(ffiRows(page)).toHaveText([ippt, dental, overseas, vocational]);
      await expect(page).toHaveURL("/ffi");
    });
  });

  test.describe("filtered", () => {
    const filterButton = (page: Page) => page.getByRole("button", { name: /^Filter/ });

    const panel = (page: Page) => page.getByRole("form", { name: "FFI filters" });

    const dateField = (page: Page, range: string, end: "From" | "To") =>
      panel(page).getByRole("group", { name: range }).getByLabel(end);

    const apply = (page: Page) => panel(page).getByRole("button", { name: "Apply" }).click();

    /** Opens Tan Hui Min's history at its second page, then the Filter panel. */
    async function filterTanHuiMin(page: Page) {
      await selectPatient(page, "tan", "Tan Hui Min", "S9012345A");
      await openFfi(page);
      await page.getByRole("button", { name: "Page 2" }).click();
      await expect(page.getByText("Showing 6 to 7 of 7 FFIs")).toBeVisible();
      await filterButton(page).click();
    }

    const TAN_HUI_MIN_ROWS = [
      "ORD / Release from ServiceFIT03 Mar 202502 Mar 2026DISNee Soon Camp MCActive",
      "CAT A - OCS/SCSUNFIT18 Nov 202417 Nov 2025Comd MMIKranji Camp MCInactive",
      "IPPT FFIREQUEST WAIVER10 Jun 202409 Jun 2025CNMOHendon Camp MCActive",
      "Overseas ExercisesFIT26 Feb 202425 Feb 2025CARMOMandai Hill Camp MCActive",
      "Dental FFIFIT04 Sep 202303 Sep 2024CAMONee Soon Camp MCInactive",
      "Vocational FFIREQUEST WAIVER17 Apr 202316 Apr 2024CNMOKranji Camp MCInactive",
      "IPPT FFIUNFIT05 Dec 202204 Dec 2023CAMOHendon Camp MCInactive",
    ];
    /** Tan Hui Min's FFIs `numbers`, one-based in the source's order. */
    const rowsOf = (...numbers: number[]) => numbers.map((n) => TAN_HUI_MIN_ROWS[n - 1]);

    test("applies a multi-select filter, reading its stubbed rows from page 1", async ({
      page,
    }) => {
      await filterTanHuiMin(page);

      await panel(page)
        .getByRole("button", { name: /^Higher Authority: / })
        .click();
      for (const label of ["CAMO", "CNMO"]) {
        await page.getByRole("option", { exact: true, name: label }).click();
      }
      await page.keyboard.press("Escape");
      await apply(page);

      await expect(page.getByText("Showing 1 to 4 of 4 FFIs")).toBeVisible();
      await expect(ffiRows(page)).toHaveText(rowsOf(3, 5, 6, 7));
      await expect(filterButton(page)).toHaveAccessibleName("Filter, 1 set");
      await expect(page).toHaveURL("/ffi");
    });

    test("applies a date range, reading its stubbed rows from page 1 over two pages", async ({
      page,
    }) => {
      await filterTanHuiMin(page);

      await dateField(page, "Issue Date", "From").fill("2023-01-01");
      await apply(page);

      await expect(page.getByText("Showing 1 to 5 of 6 FFIs")).toBeVisible();
      await expect(ffiRows(page)).toHaveText(rowsOf(1, 2, 3, 4, 5));
      await page.getByRole("button", { name: "Page 2" }).click();
      await expect(page.getByText("Showing 6 to 6 of 6 FFIs")).toBeVisible();
      await expect(ffiRows(page)).toHaveText(rowsOf(6));
    });

    test("applies the Status toggle, reading its stubbed rows from page 1", async ({ page }) => {
      await filterTanHuiMin(page);

      await panel(page).getByRole("radio", { exact: true, name: "Active" }).click();
      await apply(page);

      await expect(page.getByText("Showing 1 to 3 of 3 FFIs")).toBeVisible();
      await expect(ffiRows(page)).toHaveText(rowsOf(1, 3, 4));
    });

    test("refuses an inverted range in the panel, and reads nothing", async ({ page }) => {
      await filterTanHuiMin(page);
      const reads: string[] = [];
      page.on("request", (request) => {
        if (new URL(request.url()).pathname.startsWith("/api/patient-ffi/"))
          reads.push(request.url());
      });

      await dateField(page, "Expiry Date", "From").fill("2025-12-31");
      await dateField(page, "Expiry Date", "To").fill("2025-01-01");
      await apply(page);

      await expect(
        panel(page)
          .getByRole("group", { name: "Expiry Date" })
          .getByText("The Expiry Date from-date must not be after its to-date."),
      ).toBeVisible();
      await expect(dateField(page, "Expiry Date", "From")).toHaveAttribute("aria-invalid", "true");
      await expect(page.getByText("Showing 6 to 7 of 7 FFIs")).toBeVisible();
      expect(reads).toEqual([]);
    });

    test("says No FFIs match the filters for a filter matching none", async ({ page }) => {
      await filterTanHuiMin(page);

      await dateField(page, "Expiry Date", "To").fill("2022-12-31");
      await apply(page);

      await expect(page.getByText("No FFIs match the filters", { exact: true })).toBeVisible();
      await expect(page.getByRole("table", { name: "FFI History" })).toHaveCount(0);
    });
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
