import { expect, test, type Page } from "@playwright/test";

import { expectOnHomeInContext, search, selectResult } from "./patient.helpers";
import { USER_SESSION } from "./sessions";
import { sidebar } from "./shell.helpers";

/**
 * The ARMC page as a Medical Officer meets it, against the patient source's
 * mock (`backend/patient-source-mock`): the patient in context is selected on
 * Patient Search, then ARMC opened from the sidebar. Tan Wei Ming has exactly
 * two ARMC Boards, sorted by each date both ways, Tan Hui Min seven over two
 * pages, the first still open, filtered by each Outcome, each date range — an
 * End Date range leaving out the open board — two filters combined and one
 * matching nothing, and Siti Nurhaliza binte Ahmad none
 * (`/docs/domain/armc.md`, "The patient source's ARMC fixtures"). A failing
 * source is the mock's own `fault-armc-server-error`, the page's read sent
 * there in place of the patient's, so the backend's real `502` reaches the
 * page. What the page decides without the backend — the labels, the badges, a
 * loading patient, each filter's parameters, an inverted range — is
 * `armc.test.tsx`'s.
 *
 * In the `medical-officer` project, replaying the saved `mo1` session; the
 * case without `patient:read` replays the `guest` session in a context of its
 * own. A new page starts with no EHR context: the tab's `sessionStorage` is
 * its own.
 */

/** The ARMC table's body rows. */
const armcRows = (page: Page) =>
  page
    .getByRole("table", { name: "ARMC History" })
    .getByRole("row")
    .filter({ has: page.getByRole("cell") });

/** Puts the Patient `name`, with NRIC `nric`, into context from Patient Search's `query`. */
async function selectPatient(page: Page, query: string, name: string, nric: string) {
  await page.goto("/search");
  await search(page, query);
  await selectResult(page, name);
  await expectOnHomeInContext(page, name, nric);
}

/** Opens ARMC from the sidebar, headed ARMC. */
async function openArmc(page: Page) {
  await sidebar(page).getByRole("link", { exact: true, name: "ARMC" }).click();
  await expect(page).toHaveURL("/armc");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("ARMC");
}

test.describe("ARMC", () => {
  test("shows exactly Tan Wei Ming's two ARMC Boards once he is selected on Patient Search", async ({
    page,
  }) => {
    await selectPatient(page, "tan", "Tan Wei Ming", "S1234567A");

    await openArmc(page);

    await expect(armcRows(page)).toHaveText([
      "ACESFIT01 Jan 202410 Feb 2024Medical Officer 1Medical Officer 2",
      "ACESUNFIT21 Nov 202002 Apr 2021Medical Officer 3Medical Officer 4",
    ]);
    await expect(page.getByText("Showing 1 to 2 of 2 ARMC records")).toBeVisible();
  });

  test("shows Tan Hui Min's open board with — for its End Date, and pages to the second page", async ({
    page,
  }) => {
    await selectPatient(page, "tan", "Tan Hui Min", "S9012345A");
    await openArmc(page);
    await expect(page.getByText("Showing 1 to 5 of 7 ARMC records")).toBeVisible();
    await expect(armcRows(page)).toHaveCount(5);
    await expect(armcRows(page).first()).toHaveText(
      "ACESFIT02 Jun 2025—Dr Lim Wei JieDr Nur Aisyah binte Rahman",
    );

    await page.getByRole("button", { name: "Page 2" }).click();

    await expect(page.getByText("Showing 6 to 7 of 7 ARMC records")).toBeVisible();
    await expect(armcRows(page)).toHaveText([
      "ACESFIT11 Oct 202120 Dec 2021Dr Rajesh KumarDr Nur Aisyah binte Rahman",
      "ACESUNFIT18 May 202024 Aug 2020Dr Chen Mei LingDr Lim Wei Jie",
    ]);
  });

  test("says No ARMC records for a patient with none", async ({ page }) => {
    await selectPatient(page, "s9212345a", "Siti Nurhaliza binte Ahmad", "S9212345A");

    await openArmc(page);

    await expect(page.getByText("No ARMC records")).toBeVisible();
    await expect(page.getByRole("table", { name: "ARMC History" })).toHaveCount(0);
  });

  test("says No patient in context with none in context, and reads no ARMC Boards", async ({
    page,
  }) => {
    const reads: string[] = [];
    page.on("request", (request) => {
      if (new URL(request.url()).pathname.startsWith("/api/patient-armc/"))
        reads.push(request.url());
    });

    await page.goto("/armc");

    await expect(page.getByRole("main").getByText("No patient in context")).toBeVisible();
    expect(reads).toEqual([]);
  });

  test("says it couldn't load ARMC records when the source fails, offering Try again", async ({
    page,
  }) => {
    await selectPatient(page, "tan", "Tan Wei Ming", "S1234567A");
    // Each read of the history goes to the patient id the mock fails for.
    await page.route("**/api/patient-armc/**", (route) =>
      route.continue({
        url: route
          .request()
          .url()
          .replace(/\/api\/patient-armc\/[^?]+/, "/api/patient-armc/fault-armc-server-error"),
      }),
    );

    await openArmc(page);

    await expect(page.getByRole("alert")).toHaveText("Couldn't load ARMC records");
    await expect(page.getByRole("button", { name: "Try again" })).toBeVisible();
    await expect(page.getByRole("table", { name: "ARMC History" })).toHaveCount(0);
  });

  test.describe("without patient:read", () => {
    test.use({ storageState: USER_SESSION });

    test("is offered no ARMC link, and is routed from ARMC to Home", async ({ page }) => {
      await page.goto("/armc");

      await expect(page).toHaveURL("/");
      await expect(page.getByRole("heading", { name: "Profile" })).toBeVisible();
      await expect(sidebar(page).getByRole("link", { exact: true, name: "ARMC" })).toHaveCount(0);
    });
  });

  test.describe("sorted", () => {
    const LATER = "ACESFIT01 Jan 202410 Feb 2024Medical Officer 1Medical Officer 2";
    const EARLIER = "ACESUNFIT21 Nov 202002 Apr 2021Medical Officer 3Medical Officer 4";

    const header = (page: Page, heading: string) =>
      page
        .getByRole("table", { name: "ARMC History" })
        .getByRole("columnheader", { name: heading });

    const sortBy = (page: Page, heading: string) =>
      header(page, heading).getByRole("button").click();

    // His boards' own order is the later first; each sort is the mock's stub of
    // it, which a read sent with an `_id` tie-break would miss.
    test("sorts by Start Date both ways, then by End Date both ways, in the stubbed orders", async ({
      page,
    }) => {
      await selectPatient(page, "tan", "Tan Wei Ming", "S1234567A");
      await openArmc(page);
      await expect(armcRows(page)).toHaveText([LATER, EARLIER]);

      await sortBy(page, "Start Date");
      await expect(header(page, "Start Date")).toHaveAttribute("aria-sort", "ascending");
      await expect(armcRows(page)).toHaveText([EARLIER, LATER]);

      await sortBy(page, "Start Date");
      await expect(header(page, "Start Date")).toHaveAttribute("aria-sort", "descending");
      await expect(armcRows(page)).toHaveText([LATER, EARLIER]);

      await sortBy(page, "End Date");
      await expect(header(page, "End Date")).toHaveAttribute("aria-sort", "ascending");
      await expect(header(page, "Start Date")).toHaveAttribute("aria-sort", "none");
      await expect(armcRows(page)).toHaveText([EARLIER, LATER]);

      await sortBy(page, "End Date");
      await expect(header(page, "End Date")).toHaveAttribute("aria-sort", "descending");
      await expect(armcRows(page)).toHaveText([LATER, EARLIER]);
      await expect(page.getByText("Showing 1 to 2 of 2 ARMC records")).toBeVisible();
      await expect(page).toHaveURL("/armc");
    });
  });

  test.describe("filtered", () => {
    const filterButton = (page: Page) => page.getByRole("button", { name: /^Filter/ });

    const panel = (page: Page) => page.getByRole("form", { name: "ARMC filters" });

    const dateField = (page: Page, range: string, end: "From" | "To") =>
      panel(page).getByRole("group", { name: range }).getByLabel(end);

    const outcome = (page: Page, label: "All" | "FIT" | "UNFIT") =>
      panel(page).getByRole("radio", { exact: true, name: label }).click();

    const apply = (page: Page) => panel(page).getByRole("button", { name: "Apply" }).click();

    /** Opens Tan Hui Min's history at its second page, then the Filter panel. */
    async function filterTanHuiMin(page: Page) {
      await selectPatient(page, "tan", "Tan Hui Min", "S9012345A");
      await openArmc(page);
      await page.getByRole("button", { name: "Page 2" }).click();
      await expect(page.getByText("Showing 6 to 7 of 7 ARMC records")).toBeVisible();
      await filterButton(page).click();
    }

    const TAN_HUI_MIN_ROWS = [
      "ACESFIT02 Jun 2025—Dr Lim Wei JieDr Nur Aisyah binte Rahman",
      "ACESUNFIT16 Sep 202429 Nov 2024Dr Rajesh KumarDr Lim Wei Jie",
      "ACESFIT04 Dec 202304 Dec 2023Dr Chen Mei LingDr Rajesh Kumar",
      "ACESFIT13 Mar 202308 May 2023Dr Nur Aisyah binte RahmanDr Chen Mei Ling",
      "ACESUNFIT25 Jul 202203 Oct 2022Dr Lim Wei JieDr Chen Mei Ling",
      "ACESFIT11 Oct 202120 Dec 2021Dr Rajesh KumarDr Nur Aisyah binte Rahman",
      "ACESUNFIT18 May 202024 Aug 2020Dr Chen Mei LingDr Lim Wei Jie",
    ];
    /** Tan Hui Min's ARMC Boards `numbers`, one-based in the source's order. */
    const rowsOf = (...numbers: number[]) => numbers.map((n) => TAN_HUI_MIN_ROWS[n - 1]);

    test("filters by the Outcome FIT, reading its stubbed rows from page 1, the open board among them", async ({
      page,
    }) => {
      await filterTanHuiMin(page);

      await outcome(page, "FIT");
      await apply(page);

      await expect(page.getByText("Showing 1 to 4 of 4 ARMC records")).toBeVisible();
      await expect(armcRows(page)).toHaveText(rowsOf(1, 3, 4, 6));
      await expect(filterButton(page)).toHaveAccessibleName("Filter, 1 set");
      await expect(page).toHaveURL("/armc");
    });

    test("filters by the Outcome UNFIT, then All reads the whole history again", async ({
      page,
    }) => {
      await filterTanHuiMin(page);
      await outcome(page, "UNFIT");
      await apply(page);
      await expect(page.getByText("Showing 1 to 3 of 3 ARMC records")).toBeVisible();
      await expect(armcRows(page)).toHaveText(rowsOf(2, 5, 7));

      await filterButton(page).click();
      await outcome(page, "All");
      await apply(page);

      await expect(page.getByText("Showing 1 to 5 of 7 ARMC records")).toBeVisible();
      await expect(armcRows(page)).toHaveText(rowsOf(1, 2, 3, 4, 5));
      await expect(filterButton(page)).toHaveAccessibleName("Filter");
    });

    test("filters by a Start Date range, counting each end", async ({ page }) => {
      await filterTanHuiMin(page);

      await dateField(page, "Start Date", "From").fill("2023-01-01");
      await dateField(page, "Start Date", "To").fill("2023-12-31");
      await apply(page);

      await expect(page.getByText("Showing 1 to 2 of 2 ARMC records")).toBeVisible();
      await expect(armcRows(page)).toHaveText(rowsOf(3, 4));
      await expect(filterButton(page)).toHaveAccessibleName("Filter, 2 set");
    });

    test("filters by an End Date from-date, leaving out the board still open", async ({ page }) => {
      await filterTanHuiMin(page);

      await dateField(page, "End Date", "From").fill("2023-01-01");
      await apply(page);

      await expect(page.getByText("Showing 1 to 3 of 3 ARMC records")).toBeVisible();
      await expect(armcRows(page)).toHaveText(rowsOf(2, 3, 4));
      await expect(armcRows(page).filter({ hasText: "—" })).toHaveCount(0);
    });

    test("combines the Outcome with a Start Date from-date", async ({ page }) => {
      await filterTanHuiMin(page);

      await outcome(page, "FIT");
      await dateField(page, "Start Date", "From").fill("2023-01-01");
      await apply(page);

      await expect(page.getByText("Showing 1 to 3 of 3 ARMC records")).toBeVisible();
      await expect(armcRows(page)).toHaveText(rowsOf(1, 3, 4));
      await expect(filterButton(page)).toHaveAccessibleName("Filter, 2 set");
    });

    test("clears the filters on Reset, reading the whole history from page 1", async ({ page }) => {
      await filterTanHuiMin(page);
      await outcome(page, "FIT");
      await dateField(page, "Start Date", "From").fill("2023-01-01");
      await apply(page);
      await expect(page.getByText("Showing 1 to 3 of 3 ARMC records")).toBeVisible();

      await filterButton(page).click();
      await panel(page).getByRole("button", { name: "Reset" }).click();

      await expect(page.getByText("Showing 1 to 5 of 7 ARMC records")).toBeVisible();
      await expect(armcRows(page)).toHaveText(rowsOf(1, 2, 3, 4, 5));
      await expect(filterButton(page)).toHaveAccessibleName("Filter");
    });

    test("says No ARMC records match the filters for a filter matching none", async ({ page }) => {
      await filterTanHuiMin(page);

      await dateField(page, "Start Date", "To").fill("2019-12-31");
      await apply(page);

      await expect(
        page.getByText("No ARMC records match the filters", { exact: true }),
      ).toBeVisible();
      await expect(page.getByRole("table", { name: "ARMC History" })).toHaveCount(0);
    });
  });
});
