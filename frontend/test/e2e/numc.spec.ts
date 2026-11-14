import { expect, test, type Page } from "@playwright/test";

import { expectOnHomeInContext, search, selectResult } from "./patient.helpers";
import { USER_SESSION } from "./sessions";
import { sidebar } from "./shell.helpers";

/**
 * The NUMC page as a Medical Officer meets it, against the patient source's
 * mock (`backend/patient-source-mock`): the patient in context is selected on
 * Patient Search, then NUMC opened from the sidebar. Tan Wei Ming has exactly
 * four NUMC Records, Tan Hui Min seven over two pages, and Siti Nurhaliza
 * binte Ahmad none (`/docs/domain/numc.md`, "The patient source's NUMC
 * fixtures"). A failing source is the mock's own `fault-numc-server-error`,
 * the page's read sent there in place of the patient's, so the backend's real
 * `502` reaches the page. What the page decides without the backend — the
 * labels, the badges, a loading patient — is `numc.test.tsx`'s.
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
});
