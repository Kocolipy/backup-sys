import { act, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { clearEhrContext, setEhrContext } from "@/auth/ehr-context";
import { bare, deferred, holding, json, type WireAnswer } from "@/auth/session-adapter.testHelpers";
import {
  page,
  patientReads,
  TAN_WEI_MING,
  TAN_WEI_MING_MALFORMED,
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

/** The page's headings, each beside whether it is kept for screen readers only. */
const headingsShown = () =>
  page()
    .getAllByRole("heading")
    .map((heading) => [heading.textContent, heading.classList.contains("sr-only")]);

/** The text of the card holding "No patient in context", or null with none. */
const noPatientCard = () =>
  page().queryByText("No patient in context")?.closest("[data-slot=card]")?.textContent ?? null;

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

  it("shows the Patient card as ever beside Demographic details missing and malformed", async () => {
    withPatientInContext(TAN_WEI_MING.id);

    await renderSignedIn("/demographic", CLINICIAN, {
      [TAN_WEI_MING_READ]: json(TAN_WEI_MING_MALFORMED),
    });
    await page().findByRole("heading", { name: "Tan Wei Ming" });

    expect([
      headings(),
      page().getAllByText("S1234567A").length > 0,
      page().queryByText("Couldn't load patient"),
    ]).toEqual([
      [
        "Demographic",
        "Tan Wei Ming",
        "Military Overview",
        "Contact Details",
        "Next of Kin / Emergency",
      ],
      true,
      null,
    ]);
  });

  it("is headed by its title, shown, above a card saying no patient is in context, with none", async () => {
    const { requests } = await renderSignedIn("/demographic", CLINICIAN, {});

    expect([headingsShown(), noPatientCard(), patientReads(requests())]).toEqual([
      [["Demographic", false]],
      "No patient in context",
      [],
    ]);
  });

  it("is headed by its title, shown, above a card saying no patient is in context, for a 404", async () => {
    withPatientInContext(TAN_WEI_MING.id);
    await renderSignedIn("/demographic", CLINICIAN, { [TAN_WEI_MING_READ]: bare(404) });
    await page().findByText("No patient in context");

    expect([headingsShown(), noPatientCard()]).toEqual([
      [["Demographic", false]],
      "No patient in context",
    ]);
  });

  it("shows its note with no patient in context as muted text", async () => {
    await renderSignedIn("/demographic", CLINICIAN, {});

    expect(page().getByText("No patient in context")).toHaveClass(
      "text-sm",
      "text-muted-foreground",
    );
  });

  it.each([
    ["once they are read", json(TAN_WEI_MING)],
    ["while they are read", deferred().answer],
  ] as [string, WireAnswer | Promise<WireAnswer>][])(
    "keeps its title for screen readers only with a patient in context, %s",
    async (_, answer) => {
      withPatientInContext(TAN_WEI_MING.id);
      await renderSignedIn("/demographic", CLINICIAN, { [TAN_WEI_MING_READ]: answer });

      expect(screen.getByRole("heading", { level: 1, name: "Demographic" })).toHaveClass("sr-only");
    },
  );

  it("reads the patient in context once, for the Patient card and the top bar both", async () => {
    withPatientInContext(TAN_WEI_MING.id);
    const { requests } = await renderSignedIn("/demographic", CLINICIAN, {
      [TAN_WEI_MING_READ]: json(TAN_WEI_MING),
    });
    await page().findByRole("heading", { name: "Tan Wei Ming" });

    expect([
      screen.getByRole("region", { name: "Patient in context" }).textContent,
      patientReads(requests()),
    ]).toEqual(["Tan Wei MingS1234567A", [TAN_WEI_MING_READ]]);
  });

  it("asks for no patient, and says there is none, for an EHR context that names no patient", async () => {
    setEhrContext({});

    const { requests } = await renderSignedIn("/demographic", CLINICIAN, {});

    expect([noPatientCard(), patientReads(requests())]).toEqual(["No patient in context", []]);
  });

  it("drops the patient, saying there is none, as soon as the tab's EHR context is cleared", async () => {
    withPatientInContext(TAN_WEI_MING.id);
    await renderSignedIn("/demographic", CLINICIAN, { [TAN_WEI_MING_READ]: json(TAN_WEI_MING) });
    await page().findByRole("heading", { name: "Tan Wei Ming" });

    act(() => clearEhrContext());

    expect([headingsShown(), noPatientCard()]).toEqual([
      [["Demographic", false]],
      "No patient in context",
    ]);
  });
});
