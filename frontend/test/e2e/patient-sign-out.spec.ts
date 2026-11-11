import { expect, test } from "@playwright/test";

import {
  FIXTURE_PASSWORD,
  HOME_PATH,
  loginAs,
  MEDICAL_OFFICERS,
  PATIENT_SEARCH_PATH,
} from "./auth.helpers";
import {
  expectNoPatientInContext,
  expectOnHomeInContext,
  search,
  selectResult,
  topBarPatient,
} from "./patient.helpers";

/**
 * The patient in context is the session's: a Medical Officer who puts a
 * Patient in context, signs out and signs back in has none.
 *
 * In the `patient-sign-out` project, which starts with no session and depends
 * on no other project. It signs in and out as `mo2`, the Medical Officer no
 * other spec signs in as, so the `mo1` session the `medical-officer` project
 * replays beside it is never ended.
 */
test("leaves no patient in context after signing out and back in", async ({ page }) => {
  const officer = MEDICAL_OFFICERS.signsOut;
  await loginAs(page, officer, FIXTURE_PASSWORD, PATIENT_SEARCH_PATH);
  await search(page, "tan");
  await selectResult(page, "Tan Hui Min");
  await expectOnHomeInContext(page, "Tan Hui Min", "S9012345A");

  // Signed out from Home, which the next sign-in replays as its return
  // destination, ahead of the landing page.
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page.getByRole("heading", { name: "Welcome back" })).toBeVisible();
  await loginAs(page, officer, FIXTURE_PASSWORD, HOME_PATH);

  await expectNoPatientInContext(page);
  await expect(topBarPatient(page)).toHaveCount(0);
});
