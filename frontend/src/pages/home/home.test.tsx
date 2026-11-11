import { act, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { holding, json, STILL_CHECKING } from "@/auth/session-adapter.testHelpers";
import type { Permission } from "@/auth/api";
import { clearEhrContext, setEhrContext } from "@/auth/ehr-context";

import {
  withPatientInContext,
  page,
  patientReads,
  TAN_WEI_MING,
  TAN_WEI_MING_READ,
} from "./patient.testHelpers";
import { renderSignedIn } from "@/shell/signed-in-shell.testHelpers";

/** The page for a session holding exactly `permissions`, inside the signed-in layout. */
const renderHolding = (permissions: Permission[]) => renderSignedIn("/", holding(permissions), {});

/** A Medical Officer's Permissions: reading patients. */
const PATIENT_ACCESS: Permission[] = ["patient:read"];

describe("Home", () => {
  it("shows no patient in context, and asks for nothing, for a session holding patient:read", async () => {
    const { requests } = await renderHolding(PATIENT_ACCESS);

    expect(screen.getByText("No patient in context")).toBeInTheDocument();
    expect(requests()).toEqual([]);
  });

  it("opens for a session without patient:read, saying it has no access and naming no patient", async () => {
    withPatientInContext(TAN_WEI_MING.id);
    const { requests } = await renderHolding([]);

    expect([
      page().getByText("Your account has no access to patients.").textContent,
      screen.queryByRole("region", { name: "Patient in context" }),
      patientReads(requests()),
    ]).toEqual(["Your account has no access to patients.", null, []]);
  });

  it("shows the patient in the tab's EHR context on the Patient card", async () => {
    withPatientInContext(TAN_WEI_MING.id);

    await renderSignedIn("/", holding(PATIENT_ACCESS), {
      [TAN_WEI_MING_READ]: json(TAN_WEI_MING),
    });

    expect(await page().findByRole("heading", { name: "Tan Wei Ming" })).toBeInTheDocument();
  });

  it("reads the patient in context once, for the Patient card and the top bar both", async () => {
    withPatientInContext(TAN_WEI_MING.id);
    const { requests } = await renderSignedIn("/", holding(PATIENT_ACCESS), {
      [TAN_WEI_MING_READ]: json(TAN_WEI_MING),
    });
    await page().findByText("Tan Wei Ming");

    expect([
      screen.getByRole("region", { name: "Patient in context" }).textContent,
      patientReads(requests()),
    ]).toEqual(["Tan Wei MingS1234567A", [TAN_WEI_MING_READ]]);
  });

  it("asks for no patient for an EHR context that names no patient", async () => {
    setEhrContext({});

    const { requests } = await renderSignedIn("/", holding(PATIENT_ACCESS), {});

    expect(screen.getByText("No patient in context")).toBeInTheDocument();
    expect(patientReads(requests())).toEqual([]);
  });

  it("drops the patient as soon as the tab's EHR context is cleared", async () => {
    withPatientInContext(TAN_WEI_MING.id);
    await renderSignedIn("/", holding(PATIENT_ACCESS), {
      [TAN_WEI_MING_READ]: json(TAN_WEI_MING),
    });
    await page().findByText("Tan Wei Ming");

    act(() => clearEhrContext());

    expect(screen.queryByText("Tan Wei Ming")).toBeNull();
  });

  it("offers no control of its own", async () => {
    await renderHolding(PATIENT_ACCESS);

    // Sign out and the sidebar's controls are the layout's, outside the page.
    expect(page().queryAllByRole("button")).toEqual([]);
  });

  it("shows the page title above the Patient card", async () => {
    await renderHolding(PATIENT_ACCESS);

    expect(screen.getAllByRole("heading").map((heading) => heading.textContent)).toEqual([
      "Profile",
      "Patient",
    ]);
  });

  it("renders safely while the session's user details are unavailable", async () => {
    const { requests } = await renderSignedIn("/", STILL_CHECKING, {});

    // No session details, so no Permission: nothing is asked for.
    expect(requests()).toEqual([]);
  });
});
