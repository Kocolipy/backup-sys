import { expect, test, type Page } from "@playwright/test";

import { openHomeWithPatientInContext, topBarPatient } from "./patient.helpers";
import { sidebar } from "./shell.helpers";

// Each page's route is `App.test.tsx`'s and its title `signed-in-shell.test.tsx`'s;
// that every route loads clean is `console-errors.spec.ts`', and a signed-in
// visit to login landing on Patient Search `roles-medical-officer.spec.ts`'.
// How the card shows each field missing or invalid, a gender outside the four
// codes and each failure of the read is `patient-card.test.tsx`'s; here is
// only that the patient source's mock, through the backend, reaches it so.

/** The Patient card holding a patient: a banner named Patient, headed by their name. */
const patientCard = (page: Page) => page.getByRole("region", { exact: true, name: "Patient" });

test.describe("Home", () => {
  // The Superuser opens every page, so the navigation sidebar links each of
  // them; Settings is the top bar's.
  test("links the Superuser to every page from the navigation sidebar", async ({ page }) => {
    await page.goto("/");

    await expect(sidebar(page).getByRole("link")).toHaveText([
      "Search",
      "Demographic",
      "Medication",
      "FFI",
      "NUMC",
      "ARMC",
      "Accounts",
      "Audit logs",
    ]);
  });

  // The patient source is the truth (ADR 0020): a Patient with no birth date
  // shows it, and the Age derived from it, as "—"; one whose NRIC, name,
  // gender and birth date are each in the wrong format shows each as "Error",
  // the Age too, on the card and in the top bar.
  test("shows a Patient as received: '—' for a field missing, 'Error' for one in the wrong format, and Age following the date of birth", async ({
    page,
  }) => {
    await openHomeWithPatientInContext(page, "received-no-birth-date");

    await expect(patientCard(page).getByRole("heading")).toHaveText("Daniel Pereira");
    await expect(patientCard(page).getByRole("definition")).toHaveText([
      "S8800003A",
      "Male",
      "—",
      "—",
    ]);

    await openHomeWithPatientInContext(page, "received-wrong-format");

    await expect(patientCard(page).getByRole("heading")).toHaveText("Error");
    await expect(patientCard(page).getByRole("definition")).toHaveText([
      "Error",
      "Error",
      "Error",
      "Error",
    ]);
    await expect(topBarPatient(page)).toHaveText("ErrorError");
  });
});
