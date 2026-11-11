import { expect, type Page } from "@playwright/test";

import { cardTitled } from "./card.helpers";

/**
 * Starts recording every read the page sends for one patient by id
 * (`/api/patient/{id}`), and returns what it has recorded so far. A Patient
 * search (`/api/patient-search`) is no such read. Call it before navigating,
 * so the first read is caught.
 */
export function recordPatientRequests(page: Page): () => string[] {
  const sent: string[] = [];
  page.on("request", (request) => {
    const { pathname } = new URL(request.url());
    if (pathname.startsWith("/api/patient/")) sent.push(`${request.method()} ${pathname}`);
  });
  return () => [...sent];
}

/** Home's Patient card with nothing in the tab's EHR context, which says so. */
export async function expectNoPatientInContext(page: Page): Promise<void> {
  await expect(cardTitled(page, "Patient").getByText("No patient in context")).toBeVisible();
}

/** Home's Patient card for a session without `patient:read`, which says it has no access. */
export async function expectNoPatientAccess(page: Page): Promise<void> {
  await expect(
    cardTitled(page, "Patient").getByText("Your account has no access to patients."),
  ).toBeVisible();
}

/** Patient Search's one box. */
export const searchBox = (page: Page) =>
  page.getByRole("searchbox", { name: "Search by name or NRIC" });

/** Patient Search's results table. */
export const searchResults = (page: Page) => page.getByRole("table", { name: "Matching patients" });

/** Each body row of Patient Search's results. */
export const resultRows = (page: Page) =>
  searchResults(page)
    .getByRole("row")
    .filter({ has: page.getByRole("cell") });

/** Submits `text` from Patient Search's box. */
export async function search(page: Page, text: string) {
  await searchBox(page).fill(text);
  await searchBox(page).press("Enter");
}

/** Selects the result row naming `name`, by a click on its NRIC cell. */
export const selectResult = (page: Page, name: string) =>
  resultRows(page)
    .filter({ has: page.getByRole("button", { exact: true, name }) })
    .getByRole("cell")
    .nth(1)
    .click();

/** The top bar's patient in context. */
export const topBarPatient = (page: Page) =>
  page.getByRole("region", { name: "Patient in context" });

/** Home, showing the Patient `name` with NRIC `nric` on its Patient card and the top bar. */
export async function expectOnHomeInContext(page: Page, name: string, nric: string) {
  // Home's Patient card holding a patient: a banner named Patient, headed by their name.
  const patientCard = page.getByRole("region", { exact: true, name: "Patient" });
  await expect(page).toHaveURL("/");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Profile");
  await expect(patientCard.getByRole("heading")).toHaveText(name);
  await expect(patientCard.getByText(nric, { exact: true })).toBeVisible();
  await expect(topBarPatient(page)).toHaveText(`${name}${nric}`);
}
