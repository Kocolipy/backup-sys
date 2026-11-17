import { expect, test, type Page } from "@playwright/test";

import { openWithPatientInContext, topBarPatient } from "./patient.helpers";

// How the page shows each Demographic detail missing or invalid is
// `demographic-details.test.tsx`'s, and how the card shows each field missing
// or invalid, a gender outside the four codes and each failure of the read
// `patient-card.test.tsx`'s; here is only that the patient source's mock,
// through the backend, reaches them so.

/** The Patient card holding a patient: a banner named Patient, headed by their name. */
const patientCard = (page: Page) => page.getByRole("region", { exact: true, name: "Patient" });

test.describe("Demographic", () => {
  // The patient source is the truth (ADR 0020): a Patient with no birth date
  // shows it, and the Age derived from it, as "—"; one whose NRIC, name,
  // gender and birth date are each in the wrong format shows each as "Error",
  // the Age too, on the card and in the top bar.
  test("shows a Patient as received: '—' for a field missing, 'Error' for one in the wrong format, and Age following the date of birth", async ({
    page,
  }) => {
    await openWithPatientInContext(page, "/demographic", "received-no-birth-date");

    await expect(patientCard(page).getByRole("heading")).toHaveText("Daniel Pereira");
    await expect(patientCard(page).getByRole("definition")).toHaveText([
      "S8800003A",
      "Male",
      "—",
      "—",
    ]);

    await openWithPatientInContext(page, "/demographic", "received-wrong-format");

    await expect(patientCard(page).getByRole("heading")).toHaveText("Error");
    await expect(patientCard(page).getByRole("definition")).toHaveText([
      "Error",
      "Error",
      "Error",
      "Error",
    ]);
    await expect(topBarPatient(page)).toHaveText("ErrorError");
  });

  // A Patient whose Demographic details are missing and malformed
  // (`received-demographics`) still shows its Patient card and top bar, and
  // each detail as "—" when missing and "Error" when in the wrong format,
  // apart from the others.
  test("shows Demographic details as received: '—' for one missing, 'Error' for one in the wrong format, beside the Patient card", async ({
    page,
  }) => {
    await openWithPatientInContext(page, "/demographic", "received-demographics");

    await expect(patientCard(page).getByRole("heading")).toHaveText("Lim Kok Leong");
    await expect(patientCard(page).getByText("S8800010A", { exact: true })).toBeVisible();
    await expect(topBarPatient(page)).toHaveText("Lim Kok LeongS8800010A");
    await expect(
      page.getByRole("region", { name: "Contact Details" }).getByRole("definition"),
    ).toHaveText(["Error", "lim.kl@email.com", "—"]);
  });
});
