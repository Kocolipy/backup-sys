import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { clearEhrContext } from "@/auth/ehr-context";
import { bare, deferred, holding, json } from "@/auth/session-adapter.testHelpers";
import { renderInLayout } from "@/shell/signed-in-shell.testHelpers";

import { ForPatientInContext } from "./for-patient-in-context";
import { TAN_WEI_MING, TAN_WEI_MING_READ, withPatientInContext } from "./patient.testHelpers";

/** A session holding `patient:read`, which the read of the patient in context requires. */
const CLINICIAN = holding(["patient:read"]);

/** A history standing in for a page's own, naming the patient it is of. */
function History({ patientId }: { patientId: string }) {
  return <p>History of {patientId}</p>;
}

/** The wrapper at `/`, inside the signed-in layout, as `session` sees it. */
const renderWrapper = (
  answers: Parameters<typeof renderInLayout>[3] = {},
  session: Parameters<typeof renderInLayout>[2] = CLINICIAN,
) =>
  renderInLayout(
    [{ element: <ForPatientInContext history={History} loading="Loading visits…" />, path: "/" }],
    "/",
    session,
    answers,
  );

const history = () => screen.queryByText(/^History of /);

afterEach(() => {
  clearEhrContext();
});

describe("ForPatientInContext", () => {
  it("says No patient in context, and shows no history, with no patient in context", async () => {
    await renderWrapper();

    expect({
      history: history(),
      note: screen.getByText("No patient in context"),
    }).toEqual({ history: null, note: expect.anything() as unknown });
  });

  it("says No patient in context, and shows no history, when the patient in context reads as none", async () => {
    withPatientInContext(TAN_WEI_MING.id);
    await renderWrapper({ [TAN_WEI_MING_READ]: bare(404) });

    expect({
      history: history(),
      note: await screen.findByText("No patient in context"),
    }).toEqual({ history: null, note: expect.anything() as unknown });
  });

  it("says No patient in context to a session that may not read patients", async () => {
    withPatientInContext(TAN_WEI_MING.id);
    await renderWrapper({}, holding([]));

    expect({
      history: history(),
      note: screen.getByText("No patient in context"),
    }).toEqual({ history: null, note: expect.anything() as unknown });
  });

  it("says its loading copy while the patient in context is read", async () => {
    const patient = deferred();
    withPatientInContext(TAN_WEI_MING.id);
    await renderWrapper({ [TAN_WEI_MING_READ]: patient.answer });

    expect(screen.getByRole("main").querySelector("[role=status]")).toHaveTextContent(
      /^Loading visits…$/,
    );
    await patient.settle(json(TAN_WEI_MING));
  });

  it("shows nothing but the patient's failure when the patient in context cannot be read", async () => {
    withPatientInContext(TAN_WEI_MING.id);
    await renderWrapper({ [TAN_WEI_MING_READ]: bare(502) });

    expect({
      alert: (await screen.findByRole("alert")).textContent,
      history: history(),
      noPatient: screen.queryByText("No patient in context"),
    }).toEqual({ alert: "Couldn't load patient", history: null, noPatient: null });
  });

  it("shows the history of the patient in context once they are read, by their id", async () => {
    withPatientInContext(TAN_WEI_MING.id);
    await renderWrapper({ [TAN_WEI_MING_READ]: json(TAN_WEI_MING) });

    expect(await screen.findByText(`History of ${TAN_WEI_MING.id}`)).toBeInTheDocument();
  });
});
