import { expect, test, type Page } from "@playwright/test";

import { expectOnHomeInContext, search, selectResult } from "./patient.helpers";
import { USER_SESSION } from "./sessions";
import { sidebar } from "./shell.helpers";

/**
 * The NUMC page as a Medical Officer meets it, against the patient source's
 * mock (`backend/patient-source-mock`): the patient in context is selected on
 * Patient Search, then NUMC opened from the sidebar. Tan Wei Ming has exactly
 * four NUMC Records, sorted by each date both ways, Tan Hui Min seven over two
 * pages, filtered by each multi-select, each date range, two filters combined
 * and one matching nothing, and Siti Nurhaliza binte Ahmad none
 * (`/docs/domain/numc.md`, "The patient source's NUMC fixtures"). A failing
 * source is the mock's own `fault-numc-server-error`, the page's read sent
 * there in place of the patient's, so the backend's real `502` reaches the
 * page. What the page decides without the backend — the labels, the badges, a
 * loading patient, each filter's parameters, an inverted range — is
 * `numc.test.tsx`'s.
 *
 * In the `medical-officer` project, replaying the saved `mo1` session; the
 * case without `patient:read` replays the `guest` session in a context of its
 * own. A new page starts with no EHR context: the tab's `sessionStorage` is
 * its own.
 */

/** The NUMC table's body rows. */
const numcRows = (page: Page) =>
  page
    .getByRole("table", { name: "NUMC History" })
    .getByRole("row")
    .filter({ has: page.getByRole("cell") });

/** Puts the Patient `name`, with NRIC `nric`, into context from Patient Search's `query`. */
async function selectPatient(page: Page, query: string, name: string, nric: string) {
  await page.goto("/search");
  await search(page, query);
  await selectResult(page, name);
  await expectOnHomeInContext(page, name, nric);
}

/** Opens NUMC from the sidebar, headed NUMC. */
async function openNumc(page: Page) {
  await sidebar(page).getByRole("link", { exact: true, name: "NUMC" }).click();
  await expect(page).toHaveURL("/numc");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("NUMC");
}

test.describe("NUMC", () => {
  test("shows exactly Tan Wei Ming's four NUMC Records once he is selected on Patient Search", async ({
    page,
  }) => {
    await selectPatient(page, "tan", "Tan Wei Ming", "S1234567A");

    await openNumc(page);

    await expect(numcRows(page)).toHaveText([
      "Hyperbaric Chamber DutiesFIT14 Oct 202313 Oct 2024",
      "Submariner DutiesWAIVER REJECTED10 Jan 202409 Jan 2025",
      "Diving DutiesWAIVER GRANTED22 Nov 202321 Nov 2024",
      "Hyperbaric Chamber DutiesUNFIT05 Aug 202304 Aug 2024",
    ]);
    await expect(page.getByText("Showing 1 to 4 of 4 NUMC records")).toBeVisible();
  });

  test("pages Tan Hui Min's seven NUMC Records to the second page", async ({ page }) => {
    await selectPatient(page, "tan", "Tan Hui Min", "S9012345A");
    await openNumc(page);
    await expect(page.getByText("Showing 1 to 5 of 7 NUMC records")).toBeVisible();
    await expect(numcRows(page)).toHaveCount(5);

    await page.getByRole("button", { name: "Page 2" }).click();

    await expect(page.getByText("Showing 6 to 7 of 7 NUMC records")).toBeVisible();
    await expect(numcRows(page)).toHaveText([
      "Hyperbaric Chamber DutiesUNFIT17 Apr 202316 Apr 2024",
      "Diving DutiesWAIVER GRANTED05 Dec 202204 Dec 2023",
    ]);
  });

  test("says No NUMC records for a patient with none", async ({ page }) => {
    await selectPatient(page, "s9212345a", "Siti Nurhaliza binte Ahmad", "S9212345A");

    await openNumc(page);

    await expect(page.getByText("No NUMC records")).toBeVisible();
    await expect(page.getByRole("table", { name: "NUMC History" })).toHaveCount(0);
  });

  test("says No patient in context with none in context, and reads no NUMC Records", async ({
    page,
  }) => {
    const reads: string[] = [];
    page.on("request", (request) => {
      if (new URL(request.url()).pathname.startsWith("/api/patient-numc/"))
        reads.push(request.url());
    });

    await page.goto("/numc");

    await expect(page.getByRole("main").getByText("No patient in context")).toBeVisible();
    expect(reads).toEqual([]);
  });

  test("says it couldn't load NUMC records when the source fails, offering Try again", async ({
    page,
  }) => {
    await selectPatient(page, "tan", "Tan Wei Ming", "S1234567A");
    // Each read of the history goes to the patient id the mock fails for.
    await page.route("**/api/patient-numc/**", (route) =>
      route.continue({
        url: route
          .request()
          .url()
          .replace(/\/api\/patient-numc\/[^?]+/, "/api/patient-numc/fault-numc-server-error"),
      }),
    );

    await openNumc(page);

    await expect(page.getByRole("alert")).toHaveText("Couldn't load NUMC records");
    await expect(page.getByRole("button", { name: "Try again" })).toBeVisible();
    await expect(page.getByRole("table", { name: "NUMC History" })).toHaveCount(0);
  });

  test.describe("without patient:read", () => {
    test.use({ storageState: USER_SESSION });

    test("is offered no NUMC link, and is routed from NUMC to Home", async ({ page }) => {
      await page.goto("/numc");

      await expect(page).toHaveURL("/");
      await expect(page.getByRole("heading", { name: "Profile" })).toBeVisible();
      await expect(sidebar(page).getByRole("link", { exact: true, name: "NUMC" })).toHaveCount(0);
    });
  });

  test.describe("sorted", () => {
    const TAN_WEI_MING_ROWS = {
      diving: "Diving DutiesWAIVER GRANTED22 Nov 202321 Nov 2024",
      fit: "Hyperbaric Chamber DutiesFIT14 Oct 202313 Oct 2024",
      submariner: "Submariner DutiesWAIVER REJECTED10 Jan 202409 Jan 2025",
      unfit: "Hyperbaric Chamber DutiesUNFIT05 Aug 202304 Aug 2024",
    };
    const { diving, fit, submariner, unfit } = TAN_WEI_MING_ROWS;

    const header = (page: Page, heading: string) =>
      page
        .getByRole("table", { name: "NUMC History" })
        .getByRole("columnheader", { name: heading });

    const sortBy = (page: Page, heading: string) =>
      header(page, heading).getByRole("button").click();

    // His records' own order is by neither date; each sort is the mock's stub of it.
    test("sorts by Issue Date both ways, then by Expiry Date both ways, in the stubbed orders", async ({
      page,
    }) => {
      await selectPatient(page, "tan", "Tan Wei Ming", "S1234567A");
      await openNumc(page);
      await expect(numcRows(page)).toHaveText([fit, submariner, diving, unfit]);

      await sortBy(page, "Issue Date");
      await expect(header(page, "Issue Date")).toHaveAttribute("aria-sort", "ascending");
      await expect(numcRows(page)).toHaveText([unfit, fit, diving, submariner]);

      await sortBy(page, "Issue Date");
      await expect(header(page, "Issue Date")).toHaveAttribute("aria-sort", "descending");
      await expect(numcRows(page)).toHaveText([submariner, diving, fit, unfit]);

      await sortBy(page, "Expiry Date");
      await expect(header(page, "Expiry Date")).toHaveAttribute("aria-sort", "ascending");
      await expect(header(page, "Issue Date")).toHaveAttribute("aria-sort", "none");
      await expect(numcRows(page)).toHaveText([unfit, fit, diving, submariner]);

      await sortBy(page, "Expiry Date");
      await expect(header(page, "Expiry Date")).toHaveAttribute("aria-sort", "descending");
      await expect(numcRows(page)).toHaveText([submariner, diving, fit, unfit]);
      await expect(page).toHaveURL("/numc");
    });
  });

  test.describe("filtered", () => {
    const filterButton = (page: Page) => page.getByRole("button", { name: /^Filter/ });

    const panel = (page: Page) => page.getByRole("form", { name: "NUMC filters" });

    const dateField = (page: Page, range: string, end: "From" | "To") =>
      panel(page).getByRole("group", { name: range }).getByLabel(end);

    const apply = (page: Page) => panel(page).getByRole("button", { name: "Apply" }).click();

    /** Picks each of `labels` in the open panel's multi-select `name`, then closes it. */
    async function pick(page: Page, name: string, ...labels: string[]) {
      // A trigger is named "<name>: Any" or "<name>: N selected".
      await panel(page)
        .getByRole("button", { name: `${name}: ` })
        .click();
      for (const label of labels) {
        await page.getByRole("option", { exact: true, name: label }).click();
      }
      await page.keyboard.press("Escape");
    }

    /** Opens Tan Hui Min's history at its second page, then the Filter panel. */
    async function filterTanHuiMin(page: Page) {
      await selectPatient(page, "tan", "Tan Hui Min", "S9012345A");
      await openNumc(page);
      await page.getByRole("button", { name: "Page 2" }).click();
      await expect(page.getByText("Showing 6 to 7 of 7 NUMC records")).toBeVisible();
      await filterButton(page).click();
    }

    const TAN_HUI_MIN_ROWS = [
      "Diving DutiesFIT03 Mar 202502 Mar 2026",
      "Submariner DutiesUNFIT18 Nov 202417 Nov 2025",
      "Hyperbaric Chamber DutiesWAIVER GRANTED10 Jun 202409 Jun 2025",
      "Diving DutiesWAIVER REJECTED26 Feb 202425 Feb 2025",
      "Submariner DutiesFIT04 Sep 202303 Sep 2024",
      "Hyperbaric Chamber DutiesUNFIT17 Apr 202316 Apr 2024",
      "Diving DutiesWAIVER GRANTED05 Dec 202204 Dec 2023",
    ];
    /** Tan Hui Min's NUMC Records `numbers`, one-based in the source's order. */
    const rowsOf = (...numbers: number[]) => numbers.map((n) => TAN_HUI_MIN_ROWS[n - 1]);

    test("filters by Type, reading its stubbed rows from page 1", async ({ page }) => {
      await filterTanHuiMin(page);

      await pick(page, "Type", "Hyperbaric Chamber Duties", "Submariner Duties");
      await apply(page);

      await expect(page.getByText("Showing 1 to 4 of 4 NUMC records")).toBeVisible();
      await expect(numcRows(page)).toHaveText(rowsOf(2, 3, 5, 6));
      await expect(filterButton(page)).toHaveAccessibleName("Filter, 1 set");
      await expect(page).toHaveURL("/numc");
    });

    test("filters by Outcome, reading its stubbed rows from page 1", async ({ page }) => {
      await filterTanHuiMin(page);

      await pick(page, "Outcome", "WAIVER GRANTED", "WAIVER REJECTED");
      await apply(page);

      await expect(page.getByText("Showing 1 to 3 of 3 NUMC records")).toBeVisible();
      await expect(numcRows(page)).toHaveText(rowsOf(3, 4, 7));
      await expect(filterButton(page)).toHaveAccessibleName("Filter, 1 set");
    });

    test("filters by an Issue Date range, counting each end", async ({ page }) => {
      await filterTanHuiMin(page);

      await dateField(page, "Issue Date", "From").fill("2024-01-01");
      await dateField(page, "Issue Date", "To").fill("2024-12-31");
      await apply(page);

      await expect(page.getByText("Showing 1 to 3 of 3 NUMC records")).toBeVisible();
      await expect(numcRows(page)).toHaveText(rowsOf(2, 3, 4));
      await expect(filterButton(page)).toHaveAccessibleName("Filter, 2 set");
    });

    test("filters by an Expiry Date range", async ({ page }) => {
      await filterTanHuiMin(page);

      await dateField(page, "Expiry Date", "To").fill("2024-12-31");
      await apply(page);

      await expect(page.getByText("Showing 1 to 3 of 3 NUMC records")).toBeVisible();
      await expect(numcRows(page)).toHaveText(rowsOf(5, 6, 7));
    });

    test("combines the Type with an Issue Date from-date", async ({ page }) => {
      await filterTanHuiMin(page);

      await pick(page, "Type", "Diving Duties");
      await dateField(page, "Issue Date", "From").fill("2024-01-01");
      await apply(page);

      await expect(page.getByText("Showing 1 to 2 of 2 NUMC records")).toBeVisible();
      await expect(numcRows(page)).toHaveText(rowsOf(1, 4));
      await expect(filterButton(page)).toHaveAccessibleName("Filter, 2 set");
    });

    test("clears the filters on Reset, reading the whole history from page 1", async ({ page }) => {
      await filterTanHuiMin(page);
      await pick(page, "Outcome", "WAIVER GRANTED", "WAIVER REJECTED");
      await apply(page);
      await expect(page.getByText("Showing 1 to 3 of 3 NUMC records")).toBeVisible();

      await filterButton(page).click();
      await panel(page).getByRole("button", { name: "Reset" }).click();

      await expect(page.getByText("Showing 1 to 5 of 7 NUMC records")).toBeVisible();
      await expect(numcRows(page)).toHaveText(rowsOf(1, 2, 3, 4, 5));
      await expect(filterButton(page)).toHaveAccessibleName("Filter");
    });

    test("says No NUMC records match the filters for a filter matching none", async ({ page }) => {
      await filterTanHuiMin(page);

      await dateField(page, "Expiry Date", "To").fill("2022-12-31");
      await apply(page);

      await expect(
        page.getByText("No NUMC records match the filters", { exact: true }),
      ).toBeVisible();
      await expect(page.getByRole("table", { name: "NUMC History" })).toHaveCount(0);
    });
  });
});
