import { act, fireEvent, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";

import type { AuthUser, Permission } from "@/auth/api";
import { clearEhrContext } from "@/auth/ehr-context";
import { bare, holding, json, type Answers } from "@/auth/session-adapter.testHelpers";
import {
  withPatientInContext,
  patientReads,
  TAN_WEI_MING,
  TAN_WEI_MING_READ,
} from "@/pages/patient.testHelpers";

import { renderInLayout, renderSignedIn } from "./signed-in-shell.testHelpers";

/** The tab holds Tan Wei Ming in its EHR context. */
const withTanWeiMingInContext = () => withPatientInContext(TAN_WEI_MING.id);

/** The layout over a page that reads nothing, for a session holding `permissions`. */
const renderBar = (permissions: Permission[], answers: Answers = {}) =>
  renderInLayout([{ element: <p>page body</p>, path: "/" }], "/", holding(permissions), answers);

/** The layout over a page that reads nothing, for a session signed in as `user`. */
const renderBarFor = (user: Partial<Omit<AuthUser, "permissions">>) =>
  renderInLayout([{ element: <p>page body</p>, path: "/" }], "/", holding([], user));

const topBar = () => within(screen.getByRole("banner"));

/** The lines naming who is signed in, top to bottom, as the bar shows them. */
const signedInLines = () => topBar().getAllByTestId("signed-in-line");

const textOf = (lines: HTMLElement[]) => lines.map((line) => line.textContent);

const patientInContext = () => screen.queryByRole("region", { name: "Patient in context" });

afterEach(() => {
  clearEhrContext();
  window.history.replaceState(null, "", "/");
});

describe("TopBar", () => {
  it("names who is signed in exactly as the backend sends the name, in the primary colour", async () => {
    await renderBarFor({ username: "Ada.Lovelace" });

    const [name] = signedInLines();
    expect([
      name?.textContent,
      name?.classList.contains("text-primary"),
      name?.classList.contains("uppercase"),
    ]).toEqual(["Ada.Lovelace", true, false]);
  });

  it("lists two Groups in full beneath the name, in the order sent", async () => {
    await renderBarFor({ groups: ["Helpdesk", "auditor"] });

    expect(textOf(signedInLines())).toEqual(["ada", "Helpdesk", "auditor"]);
  });

  it("styles every line beneath the name as a muted subtitle, upper-cased by style alone", async () => {
    await renderBarFor({ groups: ["auditor", "Helpdesk", "Ward 7B"] });

    expect(
      signedInLines()
        .slice(1)
        .map((line) => [
          line.textContent,
          line.classList.contains("uppercase"),
          line.classList.contains("text-muted-foreground"),
        ]),
    ).toEqual([
      ["auditor", true, true],
      ["Helpdesk", true, true],
      ["+1 more", true, true],
    ]);
  });

  it("shows the first two of more Groups, then how many more there are", async () => {
    await renderBarFor({ groups: ["Admin", "Auditor", "Helpdesk", "Ward 7B"] });

    expect(textOf(signedInLines())).toEqual(["ada", "Admin", "Auditor", "+2 more"]);
  });

  it("lists every Group in the tooltip of the line saying how many more there are", async () => {
    await renderBarFor({ groups: ["Admin", "Auditor", "Helpdesk"] });

    fireEvent.focus(topBar().getByText("+1 more"));

    expect(textOf(within(await screen.findByRole("tooltip")).getAllByRole("listitem"))).toEqual([
      "Admin",
      "Auditor",
      "Helpdesk",
    ]);
  });

  it("names the line saying how many more there are for the Groups it opens", async () => {
    await renderBarFor({ groups: ["Admin", "Auditor", "Helpdesk"] });

    expect(topBar().getByText("+1 more")).toBe(
      topBar().getByRole("button", { name: "Show all 3 Groups" }),
    );
  });

  it("shows only the name for a User in no Group", async () => {
    await renderBarFor({ groups: [] });

    expect(textOf(signedInLines())).toEqual(["ada"]);
  });

  it("tells a screen reader who is signed in and every Group they are in", async () => {
    await renderBarFor({ groups: ["Admin", "Auditor", "Helpdesk"] });

    expect(topBar().getByText(/^Signed in as/).textContent).toBe(
      "Signed in as ada, in Admin, Auditor, Helpdesk",
    );
  });

  it("tells a screen reader only who is signed in for a User in no Group", async () => {
    await renderBarFor({ groups: [] });

    expect(topBar().getByText(/^Signed in as/).textContent).toBe("Signed in as ada");
  });

  it("links Settings", async () => {
    await renderBar([]);

    expect(topBar().getByRole("link", { name: "Open settings" })).toHaveAttribute(
      "href",
      "/settings",
    );
  });

  it.each([
    ["Notifications", "No notifications."],
    ["Help", "Press Ctrl+B (⌘B on a Mac) to collapse or expand the sidebar."],
  ])("opens %s to say %s", async (control, says) => {
    await renderBar([]);

    await userEvent.setup().click(topBar().getByRole("button", { name: control }));

    expect(await screen.findByRole("dialog")).toHaveTextContent(says);
  });

  it("names the patient in context by name and NRIC", async () => {
    withTanWeiMingInContext();
    await renderBar(["patient:read"], { [TAN_WEI_MING_READ]: json(TAN_WEI_MING) });

    expect((await screen.findByRole("region", { name: "Patient in context" })).textContent).toBe(
      "Tan Wei MingS1234567A",
    );
  });

  it("reads the patient in context once on a page that shows no patient", async () => {
    withTanWeiMingInContext();
    const { requests } = await renderSignedIn("/settings", holding(["patient:read"]), {
      [TAN_WEI_MING_READ]: json(TAN_WEI_MING),
    });
    await topBar().findByText("Tan Wei Ming");

    expect(patientReads(requests())).toEqual([TAN_WEI_MING_READ]);
  });

  it("names no patient, and asks nothing, with no patient in context", async () => {
    const { requests } = await renderBar(["patient:read"]);

    expect([patientInContext(), patientReads(requests())]).toEqual([null, []]);
  });

  it("names no patient, and asks nothing, for a session without patient:read", async () => {
    withTanWeiMingInContext();
    const { requests } = await renderBar([]);

    expect([patientInContext(), patientReads(requests())]).toEqual([null, []]);
  });

  it("names no patient, and reports nothing, for a patient that is not there", async () => {
    withTanWeiMingInContext();
    const { requests } = await renderBar(["patient:read"], { [TAN_WEI_MING_READ]: bare(404) });
    await act(async () => {});

    expect([
      patientInContext(),
      screen.queryByRole("alert"),
      patientReads(requests()).length,
    ]).toEqual([null, null, 1]);
  });

  it("names no patient when the read of the patient in context fails", async () => {
    withTanWeiMingInContext();
    const { requests } = await renderBar(["patient:read"], { [TAN_WEI_MING_READ]: bare(503) });
    await act(async () => {});

    expect([patientInContext(), patientReads(requests()).length]).toEqual([null, 1]);
  });

  it("drops the patient as soon as the tab's EHR context is cleared", async () => {
    withTanWeiMingInContext();
    await renderBar(["patient:read"], { [TAN_WEI_MING_READ]: json(TAN_WEI_MING) });
    await screen.findByRole("region", { name: "Patient in context" });

    act(() => clearEhrContext());

    expect(patientInContext()).toBeNull();
  });
});
