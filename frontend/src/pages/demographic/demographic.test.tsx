import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { clearEhrContext } from "@/auth/ehr-context";
import { holding, json } from "@/auth/session-adapter.testHelpers";
import {
  page,
  patientReads,
  TAN_WEI_MING,
  TAN_WEI_MING_READ,
  withPatientInContext,
} from "@/pages/patient.testHelpers";
import { renderSignedIn } from "@/shell/signed-in-shell.testHelpers";

const CLINICIAN = holding(["patient:read"]);

/** The page's headings, its hidden title first. */
const headings = () =>
  page()
    .getAllByRole("heading")
    .map((heading) => heading.textContent);

afterEach(() => clearEhrContext());

describe("Demographic", () => {
  it("shows the patient in context's banner, then their Demographic details, read once", async () => {
    withPatientInContext(TAN_WEI_MING.id);

    const { requests } = await renderSignedIn("/demographic", CLINICIAN, {
      [TAN_WEI_MING_READ]: json(TAN_WEI_MING),
    });
    await page().findByRole("heading", { name: "Tan Wei Ming" });

    expect([headings(), patientReads(requests())]).toEqual([
      [
        "Demographic",
        "Tan Wei Ming",
        "Military Overview",
        "Contact Details",
        "Next of Kin / Emergency",
      ],
      [TAN_WEI_MING_READ],
    ]);
  });

  it("shows only the Patient card, saying so, with no patient in context", async () => {
    const { requests } = await renderSignedIn("/demographic", CLINICIAN, {});

    expect([
      headings(),
      screen.getByText("No patient in context") !== null,
      patientReads(requests()),
    ]).toEqual([["Demographic", "Patient"], true, []]);
  });

  it("keeps its title for screen readers only, the patient banner heading the page", async () => {
    await renderSignedIn("/demographic", CLINICIAN, {});

    expect(screen.getByRole("heading", { level: 1, name: "Demographic" })).toHaveClass("sr-only");
  });
});
