import { screen, within } from "@testing-library/react";
import type { OperationOutcome } from "fhir/r5";
import { afterEach, describe, expect, it } from "vitest";

import { clearEhrContext } from "@/auth/ehr-context";
import { bare, deferred, holding, json, type WireAnswer } from "@/auth/session-adapter.testHelpers";
import type { Clock } from "@/lib/time";

import { INVALID_FIELD_EXTENSION } from "@/pages/patient-api";
import { PatientCard } from "@/pages/patient-card";
import {
  withPatientInContext,
  page,
  patientReads,
  TAN_WEI_MING,
  TAN_WEI_MING_READ,
} from "@/pages/patient.testHelpers";
import { renderInLayout } from "@/shell/signed-in-shell.testHelpers";

/** An error answer as the backend sends it: one issue, its diagnostics fixed per code. */
const outcome = (code: OperationOutcome["issue"][number]["code"]) =>
  ({
    issue: [{ code, diagnostics: "A fixed sentence.", severity: "error" }],
    resourceType: "OperationOutcome",
  }) satisfies OperationOutcome;

/** The extension naming `element` as received in the wrong format. */
const invalidField = (element: string) => ({ url: INVALID_FIELD_EXTENSION, valueString: element });

/** A clock stopped at `instant`. */
const at =
  (instant: string): Clock =>
  () =>
    new Date(instant);

/** 12 March 2026 in Singapore: Tan Wei Ming's 41st birthday. */
const ON_THE_BIRTHDAY = at("2026-03-12T01:00:00+08:00");

/**
 * The card alone, routed in the signed-in layout that reads the patient in
 * context and renders the page's one error line, as a session holding
 * `patient:read` sees it, with Tan Wei Ming in context and the read of him
 * answered with `answer`.
 */
const renderCard = (answer: WireAnswer | Promise<WireAnswer>, clock: Clock = ON_THE_BIRTHDAY) => {
  withPatientInContext(TAN_WEI_MING.id);
  return renderInLayout(
    [{ element: <PatientCard clock={clock} />, path: "/" }],
    "/",
    holding(["patient:read"]),
    { [TAN_WEI_MING_READ]: answer },
  );
};

/** The card with no patient in context, for a session holding `patient:read`. */
const renderWithoutPatient = () =>
  renderInLayout(
    [{ element: <PatientCard clock={ON_THE_BIRTHDAY} />, path: "/" }],
    "/",
    holding(["patient:read"]),
  );

/** What the card lists, each field's label beside its value. */
const fields = () => {
  const values = screen.getAllByRole("definition").map((value) => value.textContent);
  return Object.fromEntries(
    screen.getAllByRole("term").map((label, index) => [label.textContent, values[index]]),
  );
};

afterEach(() => {
  clearEhrContext();
  window.history.replaceState(null, "", "/");
});

describe("PatientCard with no patient in context", () => {
  it("says no patient is in context and asks nothing", async () => {
    const { requests } = await renderWithoutPatient();

    expect(screen.getByText("No patient in context")).toBeInTheDocument();
    expect(requests()).toEqual([]);
  });

  it("shows its note as muted text, on a card with no heading of its own, and reports nothing", async () => {
    await renderWithoutPatient();

    expect([
      screen.getByText("No patient in context").closest("[data-slot=card]")?.textContent,
      screen.getByText("No patient in context").className,
      page().queryAllByRole("heading", { level: 3 }),
      screen.queryByRole("alert"),
    ]).toEqual(["No patient in context", "text-sm text-muted-foreground", [], null]);
  });
});

describe("PatientCard with a patient in context", () => {
  it("reads that patient, and nothing else", async () => {
    const { requests } = await renderCard(json(TAN_WEI_MING));

    await page().findByText("Tan Wei Ming");
    expect(patientReads(requests())).toEqual([TAN_WEI_MING_READ]);
  });

  it("is a region named Patient, headed by the patient's name", async () => {
    await renderCard(json(TAN_WEI_MING));

    const banner = await screen.findByRole("region", { name: "Patient" });
    expect(within(banner).getByRole("heading", { name: "Tan Wei Ming" })).toBeInTheDocument();
  });

  it("shows the loaded patient's NRIC in full, gender, age and date of birth", async () => {
    await renderCard(json(TAN_WEI_MING));
    await page().findByText("Tan Wei Ming");

    expect(fields()).toEqual({
      Age: "41",
      "Date of birth": "12 Mar 1985",
      Gender: "Male",
      NRIC: "S1234567A",
    });
  });

  it("shows nothing of a patient while the read is in flight", async () => {
    await renderCard(deferred().answer);

    expect(screen.getByText("Loading the patient…")).toBeInTheDocument();
    expect(screen.queryAllByRole("definition")).toEqual([]);
  });

  it("says no patient is in context for a 404, and reports nothing", async () => {
    await renderCard(bare(404));

    expect(await screen.findByText("No patient in context")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.queryAllByRole("definition")).toEqual([]);
  });

  it("reports any other failure on the page's error line, with no patient and no retry", async () => {
    await renderCard(bare(503));

    expect(await screen.findByRole("alert")).toHaveTextContent(/^Couldn't load patient$/);
    expect(screen.queryAllByRole("definition")).toEqual([]);
    expect(screen.queryByText("No patient in context")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /retry|try again/i })).not.toBeInTheDocument();
  });

  it("shows its failed state for a 403 OperationOutcome, never the absent one", async () => {
    await renderCard(json(outcome("forbidden"), 403));

    expect(await screen.findByRole("alert")).toHaveTextContent(/^Couldn't load patient$/);
    expect(screen.queryAllByRole("definition")).toEqual([]);
    expect(screen.queryByText("No patient in context")).not.toBeInTheDocument();
  });

  it("says no patient is in context for the 404's OperationOutcome, and reports nothing", async () => {
    await renderCard(json(outcome("not-found"), 404));

    expect(await screen.findByText("No patient in context")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("reports a body that is not a Patient as a failure", async () => {
    await renderCard(json({ ...TAN_WEI_MING, resourceType: "Practitioner" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/^Couldn't load patient$/);
    expect(screen.queryAllByRole("definition")).toEqual([]);
  });

  it("does not count the year whose birthday is tomorrow in Singapore", async () => {
    // 23:59 on 11 March in Singapore, though already 12 March nowhere east of it.
    await renderCard(json(TAN_WEI_MING), at("2026-03-11T23:59:00+08:00"));
    await page().findByText("Tan Wei Ming");

    expect(fields().Age).toBe("40");
  });

  it.each([
    ["female", "Female"],
    ["other", "Other"],
    ["unknown", "Unknown"],
  ] as const)("shows the gender %s as %s", async (gender, label) => {
    await renderCard(json({ ...TAN_WEI_MING, gender }));
    await page().findByText("Tan Wei Ming");

    expect(fields().Gender).toBe(label);
  });

  it("shows a gender outside the four codes as the code it came as", async () => {
    await renderCard(json({ ...TAN_WEI_MING, gender: "M" }));
    await page().findByText("Tan Wei Ming");

    expect(fields().Gender).toBe("M");
  });

  // Shown as received (ADR 0020): each field is its value, "—" when the
  // backend left it out, and "Error" when an invalid-field extension names it,
  // each independently of the others; Age follows the date of birth.
  it.each([
    ["the NRIC", { identifier: undefined }, { NRIC: "—" }],
    ["the gender", { gender: undefined }, { Gender: "—" }],
    ["the date of birth", { birthDate: undefined }, { Age: "—", "Date of birth": "—" }],
  ])("shows %s as — when missing, and the rest as they came", async (_, change, shown) => {
    await renderCard(json({ ...TAN_WEI_MING, ...change }));
    await page().findByText("Tan Wei Ming");

    expect(fields()).toEqual({
      Age: "41",
      "Date of birth": "12 Mar 1985",
      Gender: "Male",
      NRIC: "S1234567A",
      ...shown,
    });
  });

  it.each([
    [
      "the NRIC",
      "identifier[0].value",
      { identifier: [{ system: "https://fhir.synapxe.sg/NamingSystem/nric" }] },
      { NRIC: "Error" },
    ],
    ["the gender", "gender", { gender: undefined }, { Gender: "Error" }],
    [
      "the date of birth",
      "birthDate",
      { birthDate: undefined },
      { Age: "Error", "Date of birth": "Error" },
    ],
  ])(
    "shows %s as Error when its format was wrong, and the rest as they came",
    async (_, element, change, shown) => {
      await renderCard(json({ ...TAN_WEI_MING, ...change, extension: [invalidField(element)] }));
      await page().findByText("Tan Wei Ming");

      expect(fields()).toEqual({
        Age: "41",
        "Date of birth": "12 Mar 1985",
        Gender: "Male",
        NRIC: "S1234567A",
        ...shown,
      });
    },
  );

  it.each([
    ["—, when the Patient has no name", { name: undefined }, "—"],
    [
      "Error, when its format was wrong",
      { extension: [invalidField("name[0].text")], name: [{ use: "official" }] },
      "Error",
    ],
  ])("is headed by %s, its other fields as they came", async (_, change, heading) => {
    await renderCard(json({ ...TAN_WEI_MING, ...change }));

    const banner = await screen.findByRole("region", { name: "Patient" });
    expect([within(banner).getByRole("heading").textContent, fields().NRIC]).toEqual([
      heading,
      "S1234567A",
    ]);
  });

  it("shows markup in a field as the text it is, never as markup", async () => {
    const markup = "<b>Tan</b> <img src=x onerror=alert(1)>";
    await renderCard(json({ ...TAN_WEI_MING, name: [{ text: markup, use: "official" }] }));

    expect(await page().findByText(markup)).toBeInTheDocument();
    expect(document.querySelector("b, img")).toBeNull();
  });

  it.each([
    ["while loading", deferred().answer, "Loading the patient…"],
    ["for a 404", bare(404), "No patient in context"],
  ])("shows its note %s as muted text", async (_, answer, note) => {
    await renderCard(answer);

    expect(await screen.findByText(note)).toHaveClass("text-sm", "text-muted-foreground");
  });
});
