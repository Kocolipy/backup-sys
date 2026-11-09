import { screen, within } from "@testing-library/react";
import type { OperationOutcome } from "fhir/r5";
import { afterEach, describe, expect, it } from "vitest";

import type { Permission } from "@/auth/api";
import { clearEhrContext } from "@/auth/ehr-context";
import { bare, deferred, holding, json, type WireAnswer } from "@/auth/session-adapter.testHelpers";
import type { Clock } from "@/lib/time";

import { PatientCard } from "./patient-card";
import {
  landedFromEpicWith,
  page,
  patientReads,
  TAN_WEI_MING,
  TAN_WEI_MING_READ,
} from "./patient.testHelpers";
import { renderInLayout } from "@/shell/signed-in-shell.testHelpers";

/** An error answer as the backend sends it: one issue, its diagnostics fixed per code. */
const outcome = (code: OperationOutcome["issue"][number]["code"]) =>
  ({
    issue: [{ code, diagnostics: "A fixed sentence.", severity: "error" }],
    resourceType: "OperationOutcome",
  }) satisfies OperationOutcome;

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
 * `permissions` sees it, with Tan Wei Ming in context and the read of him
 * answered with `answer`.
 */
const renderCard = (
  answer: WireAnswer | Promise<WireAnswer>,
  clock: Clock = ON_THE_BIRTHDAY,
  permissions: Permission[] = ["patient:read"],
) => {
  landedFromEpicWith(TAN_WEI_MING.id);
  return renderInLayout(
    [{ element: <PatientCard clock={clock} />, path: "/" }],
    "/",
    holding(permissions),
    { [TAN_WEI_MING_READ]: answer },
  );
};

/** The card with no patient in context, for a session holding exactly `permissions`. */
const renderWithoutPatient = (permissions: Permission[]) =>
  renderInLayout(
    [{ element: <PatientCard clock={ON_THE_BIRTHDAY} />, path: "/" }],
    "/",
    holding(permissions),
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
  it.each([[["patient:read"]], [[]]] as Permission[][][])(
    "says no patient is in context and asks nothing, for a session holding %j",
    async (permissions) => {
      const { requests } = await renderWithoutPatient(permissions);

      expect(screen.getByText("No patient in context")).toBeInTheDocument();
      expect(requests()).toEqual([]);
    },
  );

  it("shows its note as muted text, under the heading Patient, and reports nothing", async () => {
    await renderWithoutPatient(["patient:read"]);

    expect(screen.getByText("No patient in context")).toHaveClass(
      "text-sm",
      "text-muted-foreground",
    );
    expect(screen.getByRole("heading", { name: "Patient" })).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
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

  it("asks nothing and says so for a session without patient:read", async () => {
    const { requests } = await renderCard(json(TAN_WEI_MING), ON_THE_BIRTHDAY, []);

    expect(screen.getByText("Your account has no access to patients.")).toBeInTheDocument();
    expect(patientReads(requests())).toEqual([]);
  });
});
