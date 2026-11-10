import { expect, test, type Page } from "@playwright/test";

/**
 * Patient Search as a signed-in User meets it, against the patient source's
 * mock (`backend/patient-source-mock`): its canned name search spanning two
 * pages, its NRIC search, its search that matches nothing, a query too short to
 * send, and the Search link first in the sidebar. Nothing about a search
 * reaches the URL.
 *
 * In the `user` project: `patient:read` is a baseline Permission, so the seeded
 * User holding nothing else searches as any signed-in User does.
 */

const box = (page: Page) => page.getByRole("searchbox", { name: "Search by name or NRIC" });

const results = (page: Page) => page.getByRole("table", { name: "Matching patients" });

/** Each body row's cells, as text. */
const rowTexts = (page: Page) =>
  results(page)
    .getByRole("row")
    .filter({ has: page.getByRole("cell") });

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

  test("refuses a query under three characters, and sends nothing", async ({ page }) => {
    const searches: string[] = [];
    page.on("request", (request) => {
      if (new URL(request.url()).pathname === "/api/patient-search") searches.push(request.url());
    });

    await search(page, "ta");

    await expect(page.getByText("Enter at least 3 characters to search by name.")).toBeVisible();
    await expect(page.getByText("Search for a patient by name or full NRIC.")).toBeVisible();
    expect(searches).toEqual([]);
  });

  test("is the first link in the sidebar", async ({ page }) => {
    await page.goto("/");
    const links = page.locator('[data-slot="sidebar"]').getByRole("link");
    await expect(links).toHaveText(["Search", "Profile"]);

    await links.first().click();

    await expect(page).toHaveURL("/search");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Patient Search");
  });
});
