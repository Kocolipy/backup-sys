import { expect, test, type Page } from "@playwright/test";

import { expectOnHomeInContext, search, selectResult } from "./patient.helpers";
import { USER_SESSION } from "./sessions";
import { sidebar } from "./shell.helpers";

/**
 * The Medication page as a Medical Officer meets it, against the patient
 * source's mock (`backend/patient-source-mock`): the patient in context is
 * selected on Patient Search, then Medication opened from the sidebar. Tan Wei
 * Ming has six Medication Records over two pages, Tan Hui Min four covering
 * every Dispense Status, the last with no optional element, and Siti Nurhaliza
 * binte Ahmad none (`/docs/domain/medication.md`, "The patient source's
 * Medication fixtures"), each sort of his and each filter of hers a canned
 * stub of its own ("Sort and filter stubs"). A failing source is the mock's own
 * `fault-medication-server-error`, the page's read sent there in place of the
 * patient's, so the backend's real `502` reaches the page. What the page
 * decides without the backend — the labels, the badges, a loading patient —
 * is `medication.test.tsx`'s.
 *
 * In the `medical-officer` project, replaying the saved `mo1` session; the
 * case without `patient:read` replays the `guest` session in a context of its
 * own. A new page starts with no EHR context: the tab's `sessionStorage` is
 * its own.
 */

/** The Medication table's body rows. */
const medicationRows = (page: Page) =>
  page
    .getByRole("table", { name: "Medication History" })
    .getByRole("row")
    .filter({ has: page.getByRole("cell") });

/** Puts the Patient `name`, with NRIC `nric`, into context from Patient Search's `query`. */
async function selectPatient(page: Page, query: string, name: string, nric: string) {
  await page.goto("/search");
  await search(page, query);
  await selectResult(page, name);
  await expectOnHomeInContext(page, name, nric);
}

/** Opens Medication from the sidebar, headed Medication. */
async function openMedication(page: Page) {
  await sidebar(page).getByRole("link", { exact: true, name: "Medication" }).click();
  await expect(page).toHaveURL("/medication");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Medication");
}

test.describe("Medication", () => {
  test("shows Tan Wei Ming's first five Medication Records, every column, once he is selected", async ({
    page,
  }) => {
    await selectPatient(page, "tan", "Tan Wei Ming", "S1234567A");

    await openMedication(page);

    await expect(medicationRows(page)).toHaveText([
      "Paracetamol750 mgPOQDS—30 tabletsPENDING5 days18 Oct 2026 17:3022 Oct 2026 17:30Nee Soon Camp MCDr Lim Wei Jie",
      "Amoxicillin300 mgPOTDS—20 tabletsPENDING5 days18 Oct 2026 17:3122 Oct 2026 17:31Khatib Camp MCDr Lim Wei Jie",
      "Paracetamol750 mgPOQDS—30 tabletsPENDING5 days18 Oct 2026 17:3022 Oct 2026 17:30Nee Soon Camp MCDr Chen Mei Ling",
      "Paracetamol500 mgPOQDS—20 tabletsDISPENSED3 days13 Oct 2026 14:3015 Oct 2026 14:30Khatib Camp MCDr Rajesh Kumar",
      "Amoxicillin300 mgPOTDS—20 tabletsUNCOLLECTED3 days22 Apr 2025 10:0024 Apr 2025 10:00Kranji Camp MCDr Nur Aisyah binte Rahman",
    ]);
    await expect(page.getByText("Showing 1 to 5 of 6 medication records")).toBeVisible();
  });

  test("pages Tan Wei Ming's six Medication Records to the second page", async ({ page }) => {
    await selectPatient(page, "tan", "Tan Wei Ming", "S1234567A");
    await openMedication(page);
    await expect(medicationRows(page)).toHaveCount(5);

    await page.getByRole("button", { name: "Page 2" }).click();

    await expect(page.getByText("Showing 6 to 6 of 6 medication records")).toBeVisible();
    await expect(medicationRows(page)).toHaveText([
      "Prednisolone30 mgPOOM114 tabletsDISPENSED7 days02 Dec 2024 09:1509 Dec 2024 09:15Hendon Camp MCDr Chen Mei Ling",
    ]);
  });

  test("shows Tan Hui Min's records with — for each absent element, a UTC date-time in Singapore time, and each badge", async ({
    page,
  }) => {
    await selectPatient(page, "tan", "Tan Hui Min", "S9012345A");

    await openMedication(page);

    await expect(medicationRows(page)).toHaveText([
      "Ibuprofen400 mgPOTDS—15 tabletsDISPENSED5 days01 Mar 2026 10:0006 Mar 2026 10:00Hendon Camp MCDr Rajesh Kumar",
      "Lignocaine 2% gel—Topical——1 tubeUNCOLLECTED—14 Jul 2025 16:2021 Jul 2025 16:20Mandai Hill Camp MCDr Nur Aisyah binte Rahman",
      "Prednisolone20 mgPOOM27 tabletsPENDING7 days10 Feb 2025 08:4517 Feb 2025 08:45Mandai Hill Camp MCDr Chen Mei Ling",
      "Oral rehydration salts—————DISPENSED—05 Aug 2024 11:0505 Aug 2024 11:05Kranji Camp MCDr Lim Wei Jie",
    ]);
    const badges = page
      .getByRole("table", { name: "Medication History" })
      .locator("[data-slot=badge]");
    await expect(badges).toHaveText(["DISPENSED", "UNCOLLECTED", "PENDING", "DISPENSED"]);
    expect(
      await badges.evaluateAll((all) => all.map((badge) => badge.getAttribute("data-variant"))),
    ).toEqual(["success", "danger", "muted", "success"]);
  });

  test("says No medication records for a patient with none", async ({ page }) => {
    await selectPatient(page, "s9212345a", "Siti Nurhaliza binte Ahmad", "S9212345A");

    await openMedication(page);

    await expect(page.getByText("No medication records")).toBeVisible();
    await expect(page.getByRole("table", { name: "Medication History" })).toHaveCount(0);
  });

  test("says No patient in context with none in context, and reads no Medication Records", async ({
    page,
  }) => {
    const reads: string[] = [];
    page.on("request", (request) => {
      if (new URL(request.url()).pathname.startsWith("/api/patient-medication/"))
        reads.push(request.url());
    });

    await page.goto("/medication");

    await expect(page.getByRole("main").getByText("No patient in context")).toBeVisible();
    expect(reads).toEqual([]);
  });

  test("says it couldn't load medication records when the source fails, offering Try again", async ({
    page,
  }) => {
    await selectPatient(page, "tan", "Tan Wei Ming", "S1234567A");
    // Each read of the history goes to the patient id the mock fails for.
    await page.route("**/api/patient-medication/**", (route) =>
      route.continue({
        url: route
          .request()
          .url()
          .replace(
            /\/api\/patient-medication\/[^?]+/,
            "/api/patient-medication/fault-medication-server-error",
          ),
      }),
    );

    await openMedication(page);

    await expect(page.getByRole("alert")).toHaveText("Couldn't load medication records");
    await expect(page.getByRole("button", { name: "Try again" })).toBeVisible();
    await expect(page.getByRole("table", { name: "Medication History" })).toHaveCount(0);
  });

  test.describe("without patient:read", () => {
    test.use({ storageState: USER_SESSION });

    test("is offered no Medication link, and is routed from Medication to Home", async ({
      page,
    }) => {
      await page.goto("/medication");

      await expect(page).toHaveURL("/");
      await expect(page.getByRole("heading", { name: "Profile" })).toBeVisible();
      await expect(
        sidebar(page).getByRole("link", { exact: true, name: "Medication" }),
      ).toHaveCount(0);
    });
  });

  test.describe("sorted", () => {
    const TAN_WEI_MING_ROWS = [
      "Paracetamol750 mgPOQDS—30 tabletsPENDING5 days18 Oct 2026 17:3022 Oct 2026 17:30Nee Soon Camp MCDr Lim Wei Jie",
      "Amoxicillin300 mgPOTDS—20 tabletsPENDING5 days18 Oct 2026 17:3122 Oct 2026 17:31Khatib Camp MCDr Lim Wei Jie",
      "Paracetamol750 mgPOQDS—30 tabletsPENDING5 days18 Oct 2026 17:3022 Oct 2026 17:30Nee Soon Camp MCDr Chen Mei Ling",
      "Paracetamol500 mgPOQDS—20 tabletsDISPENSED3 days13 Oct 2026 14:3015 Oct 2026 14:30Khatib Camp MCDr Rajesh Kumar",
      "Amoxicillin300 mgPOTDS—20 tabletsUNCOLLECTED3 days22 Apr 2025 10:0024 Apr 2025 10:00Kranji Camp MCDr Nur Aisyah binte Rahman",
      "Prednisolone30 mgPOOM114 tabletsDISPENSED7 days02 Dec 2024 09:1509 Dec 2024 09:15Hendon Camp MCDr Chen Mei Ling",
    ];
    /** Tan Wei Ming's Medication Records `numbers`, one-based in the source's order. */
    const rowsOf = (...numbers: number[]) => numbers.map((n) => TAN_WEI_MING_ROWS[n - 1]);

    const header = (page: Page, heading: string) =>
      page
        .getByRole("table", { name: "Medication History" })
        .getByRole("columnheader", { name: heading });

    const sortBy = (page: Page, heading: string) =>
      header(page, heading).getByRole("button").click();

    // His records' own order is by neither date-time; each sort is the mock's
    // stub of it, by the whole date-time: his second starts a minute after his
    // first and third, which tie and keep the source's order.
    test("sorts by Start Date both ways, then by End Date both ways, from the first page, in the stubbed orders", async ({
      page,
    }) => {
      await selectPatient(page, "tan", "Tan Wei Ming", "S1234567A");
      await openMedication(page);
      await page.getByRole("button", { name: "Page 2" }).click();
      await expect(page.getByText("Showing 6 to 6 of 6 medication records")).toBeVisible();

      await sortBy(page, "Start Date");
      await expect(header(page, "Start Date")).toHaveAttribute("aria-sort", "ascending");
      await expect(page.getByText("Showing 1 to 5 of 6 medication records")).toBeVisible();
      await expect(medicationRows(page)).toHaveText(rowsOf(6, 5, 4, 1, 3));

      await sortBy(page, "Start Date");
      await expect(header(page, "Start Date")).toHaveAttribute("aria-sort", "descending");
      await expect(medicationRows(page)).toHaveText(rowsOf(2, 1, 3, 4, 5));

      await sortBy(page, "End Date");
      await expect(header(page, "End Date")).toHaveAttribute("aria-sort", "ascending");
      await expect(header(page, "Start Date")).toHaveAttribute("aria-sort", "none");
      await expect(medicationRows(page)).toHaveText(rowsOf(6, 5, 4, 1, 3));

      await sortBy(page, "End Date");
      await expect(header(page, "End Date")).toHaveAttribute("aria-sort", "descending");
      await expect(medicationRows(page)).toHaveText(rowsOf(2, 1, 3, 4, 5));
      await expect(page).toHaveURL("/medication");
    });
  });

  test.describe("filtered", () => {
    const filterButton = (page: Page) => page.getByRole("button", { name: /^Filter/ });

    const panel = (page: Page) => page.getByRole("form", { name: "Medication filters" });

    const dateField = (page: Page, range: string, end: "From" | "To") =>
      panel(page).getByRole("group", { name: range }).getByLabel(end);

    const apply = (page: Page) => panel(page).getByRole("button", { name: "Apply" }).click();

    /** Picks each of `labels` in the open panel's Dispense Status multi-select, then closes it. */
    async function pickStatuses(page: Page, ...labels: string[]) {
      // The trigger is named "Dispense Status: Any" or "Dispense Status: N selected".
      await panel(page).getByRole("button", { name: "Dispense Status: " }).click();
      for (const label of labels) {
        await page.getByRole("option", { exact: true, name: label }).click();
      }
      await page.keyboard.press("Escape");
    }

    /** Opens Tan Hui Min's history, then the Filter panel. */
    async function filterTanHuiMin(page: Page) {
      await selectPatient(page, "tan", "Tan Hui Min", "S9012345A");
      await openMedication(page);
      await expect(page.getByText("Showing 1 to 4 of 4 medication records")).toBeVisible();
      await filterButton(page).click();
    }

    const TAN_HUI_MIN_ROWS = [
      "Ibuprofen400 mgPOTDS—15 tabletsDISPENSED5 days01 Mar 2026 10:0006 Mar 2026 10:00Hendon Camp MCDr Rajesh Kumar",
      "Lignocaine 2% gel—Topical——1 tubeUNCOLLECTED—14 Jul 2025 16:2021 Jul 2025 16:20Mandai Hill Camp MCDr Nur Aisyah binte Rahman",
      "Prednisolone20 mgPOOM27 tabletsPENDING7 days10 Feb 2025 08:4517 Feb 2025 08:45Mandai Hill Camp MCDr Chen Mei Ling",
      "Oral rehydration salts—————DISPENSED—05 Aug 2024 11:0505 Aug 2024 11:05Kranji Camp MCDr Lim Wei Jie",
    ];
    /** Tan Hui Min's Medication Records `numbers`, one-based in the source's order. */
    const rowsOf = (...numbers: number[]) => numbers.map((n) => TAN_HUI_MIN_ROWS[n - 1]);

    for (const [labels, rows] of [
      [["PENDING"], [3]],
      [["DISPENSED"], [1, 4]],
      [["UNCOLLECTED"], [2]],
      [
        ["PENDING", "UNCOLLECTED"],
        [2, 3],
      ],
    ] as const) {
      test(`filters by the Dispense Status ${labels.join(" or ")}, reading its stubbed rows`, async ({
        page,
      }) => {
        await filterTanHuiMin(page);

        await pickStatuses(page, ...labels);
        await apply(page);

        await expect(
          page.getByText(`Showing 1 to ${rows.length} of ${rows.length} medication records`),
        ).toBeVisible();
        await expect(medicationRows(page)).toHaveText(rowsOf(...rows));
        await expect(filterButton(page)).toHaveAccessibleName("Filter, 1 set");
        await expect(page).toHaveURL("/medication");
      });
    }

    // Her second record starts at 16:20 on 14 Jul 2025: a to-bound on that
    // minute keeps it.
    test("filters by a Start Date to-bound, inclusive of the record on its exact minute", async ({
      page,
    }) => {
      await filterTanHuiMin(page);

      await dateField(page, "Start Date", "To").fill("2025-07-14T16:20");
      await apply(page);

      await expect(page.getByText("Showing 1 to 3 of 3 medication records")).toBeVisible();
      await expect(medicationRows(page)).toHaveText(rowsOf(2, 3, 4));
      await expect(filterButton(page)).toHaveAccessibleName("Filter, 1 set");
    });

    // Her first record starts at 10:00 Singapore time, given in UTC: a
    // from-bound on that minute keeps it.
    test("filters by a Start Date from-bound, inclusive of the record on its exact minute", async ({
      page,
    }) => {
      await filterTanHuiMin(page);

      await dateField(page, "Start Date", "From").fill("2026-03-01T10:00");
      await apply(page);

      await expect(page.getByText("Showing 1 to 1 of 1 medication records")).toBeVisible();
      await expect(medicationRows(page)).toHaveText(rowsOf(1));
    });

    test("filters by an End Date range, counting each end", async ({ page }) => {
      await filterTanHuiMin(page);

      await dateField(page, "End Date", "From").fill("2025-01-01T00:00");
      await dateField(page, "End Date", "To").fill("2025-12-31T23:59");
      await apply(page);

      await expect(page.getByText("Showing 1 to 2 of 2 medication records")).toBeVisible();
      await expect(medicationRows(page)).toHaveText(rowsOf(2, 3));
      await expect(filterButton(page)).toHaveAccessibleName("Filter, 2 set");
    });

    test("combines the Dispense Status with a Start Date from-bound", async ({ page }) => {
      await filterTanHuiMin(page);

      await pickStatuses(page, "DISPENSED");
      await dateField(page, "Start Date", "From").fill("2025-01-01T00:00");
      await apply(page);

      await expect(page.getByText("Showing 1 to 1 of 1 medication records")).toBeVisible();
      await expect(medicationRows(page)).toHaveText(rowsOf(1));
      await expect(filterButton(page)).toHaveAccessibleName("Filter, 2 set");
    });

    test("clears the filters on Reset, reading the whole history again", async ({ page }) => {
      await filterTanHuiMin(page);
      await pickStatuses(page, "PENDING", "UNCOLLECTED");
      await apply(page);
      await expect(page.getByText("Showing 1 to 2 of 2 medication records")).toBeVisible();

      await filterButton(page).click();
      await panel(page).getByRole("button", { name: "Reset" }).click();

      await expect(page.getByText("Showing 1 to 4 of 4 medication records")).toBeVisible();
      await expect(medicationRows(page)).toHaveText(rowsOf(1, 2, 3, 4));
      await expect(filterButton(page)).toHaveAccessibleName("Filter");
    });

    test("says No medication records match the filters for a filter matching none", async ({
      page,
    }) => {
      await filterTanHuiMin(page);

      await dateField(page, "End Date", "To").fill("2023-12-31T23:59");
      await apply(page);

      await expect(
        page.getByText("No medication records match the filters", { exact: true }),
      ).toBeVisible();
      await expect(page.getByRole("table", { name: "Medication History" })).toHaveCount(0);
    });
  });
});
