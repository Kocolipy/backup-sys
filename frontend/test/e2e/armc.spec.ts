import { expect, test, type Page } from "@playwright/test";

import { expectOnHomeInContext, search, selectResult } from "./patient.helpers";
import { USER_SESSION } from "./sessions";
import { sidebar } from "./shell.helpers";

/**
 * The ARMC page as a Medical Officer meets it, against the patient source's
 * mock (`backend/patient-source-mock`): the patient in context is selected on
 * Patient Search, then ARMC opened from the sidebar. Tan Wei Ming has exactly
 * two ARMC Boards, Tan Hui Min seven over two pages, the first still open, and
 * Siti Nurhaliza binte Ahmad none (`/docs/domain/armc.md`, "The patient
 * source's ARMC fixtures"). A failing source is the mock's own
 * `fault-armc-server-error`, the page's read sent there in place of the
 * patient's, so the backend's real `502` reaches the page. What the page
 * decides without the backend — the labels, the badges, a loading patient —
 * is `armc.test.tsx`'s.
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
});
