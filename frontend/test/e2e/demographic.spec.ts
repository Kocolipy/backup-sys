import { expect, test } from "@playwright/test";

import { openHomeWithPatientInContext, topBarPatient } from "./patient.helpers";

// How the page shows each Demographic detail missing or invalid is
// `demographic-details.test.tsx`'s; here is only that the patient source's
// mock, through the backend, reaches it so, beside a Patient card that renders
// normally.

test.describe("Demographic", () => {
  // The patient source is the truth (ADR 0020): a Patient whose Demographic
  // details are missing and malformed (`received-demographics`) still shows
  // its Patient card and top bar, and each detail as "—" when missing and
  // "Error" when in the wrong format, apart from the others.
  test("shows Demographic details as received: '—' for one missing, 'Error' for one in the wrong format, beside the Patient card", async ({
    page,
  }) => {
    await openHomeWithPatientInContext(page, "received-demographics");
    await page.goto("/demographic");

    const patientCard = page.getByRole("region", { exact: true, name: "Patient" });
    await expect(patientCard.getByRole("heading")).toHaveText("Lim Kok Leong");
    await expect(patientCard.getByText("S8800010A", { exact: true })).toBeVisible();
    await expect(topBarPatient(page)).toHaveText("Lim Kok LeongS8800010A");
    await expect(
      page.getByRole("region", { name: "Contact Details" }).getByRole("definition"),
    ).toHaveText(["Error", "lim.kl@email.com", "—"]);
  });
});
