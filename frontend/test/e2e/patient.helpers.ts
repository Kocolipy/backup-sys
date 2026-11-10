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
