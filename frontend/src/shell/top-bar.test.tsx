import { act, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";

import type { Permission } from "@/auth/api";
import { clearEhrContext } from "@/auth/ehr-context";
import { bare, holding, json, type Answers } from "@/auth/session-adapter.testHelpers";
import {
  withPatientInContext,
  patientReads,
  TAN_WEI_MING,
  TAN_WEI_MING_READ,
} from "@/pages/home/patient.testHelpers";

import { renderInLayout, renderSignedIn } from "./signed-in-shell.testHelpers";

/** The tab holds Tan Wei Ming in its EHR context. */
const withTanWeiMingInContext = () => withPatientInContext(TAN_WEI_MING.id);

/** The layout over a page that reads nothing, for a session holding `permissions`. */
const renderBar = (permissions: Permission[], answers: Answers = {}) =>
  renderInLayout([{ element: <p>page body</p>, path: "/" }], "/", holding(permissions), answers);

const topBar = () => within(screen.getByRole("banner"));

const patientInContext = () => screen.queryByRole("region", { name: "Patient in context" });

afterEach(() => {
  clearEhrContext();
  window.history.replaceState(null, "", "/");
});

describe("TopBar", () => {
  it("shows who is signed in, by name beneath their avatar", async () => {
    await renderBar([]);

    expect([
      topBar().getByText("Signed in as ada") !== null,
      topBar().getByText("ada", { selector: "span" }) !== null,
    ]).toEqual([true, true]);
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

  it.each([403, 503])(
    "reports a %i on the page's error line as a failed read, naming no patient",
    async (status) => {
      withTanWeiMingInContext();
      await renderBar(["patient:read"], { [TAN_WEI_MING_READ]: bare(status) });

      expect(await screen.findByRole("alert")).toHaveTextContent(/^Couldn't load patient$/);
      expect(patientInContext()).toBeNull();
    },
  );

  it("drops the patient as soon as the tab's EHR context is cleared", async () => {
    withTanWeiMingInContext();
    await renderBar(["patient:read"], { [TAN_WEI_MING_READ]: json(TAN_WEI_MING) });
    await screen.findByRole("region", { name: "Patient in context" });

    act(() => clearEhrContext());

    expect(patientInContext()).toBeNull();
  });
});
