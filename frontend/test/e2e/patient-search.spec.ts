import { expect, test, type Page } from "@playwright/test";

/**
 * Patient Search as a signed-in User meets it, against the patient source's
 * mock (`backend/patient-source-mock`): its canned name search spanning two
 * pages, sorted by each column both ways and filtered by gender and by each
 * date range, a filtered search spanning two pages, its NRIC search, its
 * search that matches nothing, a query too short to send, a from-date after
 * its to-date refused in the Filter panel, and the Search link first in the
 * sidebar. Nothing about a search, its filters or its sort reaches the URL.
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

  test("refuses a query under three characters, and sends nothing", async ({ page }) => {
    const searches: string[] = [];
    page.on("request", (request) => {
      if (new URL(request.url()).pathname === "/api/patient-search") searches.push(request.url());
    });

    await search(page, "ta");

    await expect(page.getByText("Enter at least 3 characters to search by name.")).toBeVisible();
    expect(searches).toEqual([]);
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

    test("refuses a from-date after its to-date in the form, and sends nothing", async ({
      page,
    }) => {
      await search(page, "tan");
      await expect(page.getByText("Showing 1 to 5 of 7 patients")).toBeVisible();
      const searches: string[] = [];
      page.on("request", (request) => {
        if (new URL(request.url()).pathname === "/api/patient-search") searches.push(request.url());
      });
      await filterButton(page).click();

      await dateField(page, "Last Visit", "From").fill("2026-10-31");
      await dateField(page, "Last Visit", "To").fill("2026-09-01");
      await panel(page).getByRole("button", { name: "Apply" }).click();

      await expect(
        panel(page).getByText("The Last Visit from-date must not be after its to-date."),
      ).toBeVisible();
      await expect(dateField(page, "Last Visit", "From")).toHaveAttribute("aria-invalid", "true");
      await expect(filterButton(page)).toHaveAccessibleName("Filter");
      expect(searches).toEqual([]);
    });
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
