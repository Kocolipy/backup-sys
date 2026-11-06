import { act, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { bare, holding, json, STILL_CHECKING } from "@/auth/session-adapter.testHelpers";
import type { Permission } from "@/auth/api";
import { adoptLaunchLanding, clearEhrContext } from "@/auth/ehr-context";

import { TAN_WEI_MING, TAN_WEI_MING_READ } from "./patient.testHelpers";
import { renderSignedIn } from "@/shell/signed-in-shell.testHelpers";

/** The tab loaded at an Epic landing with `patient` in context. */
const landedFromEpicWith = (patient: string) => {
  window.history.replaceState(null, "", `/#patient=${patient}`);
  adoptLaunchLanding();
};

/** The page for a session holding exactly `permissions`, inside the signed-in layout. */
const renderHolding = (permissions: Permission[]) => renderSignedIn("/", holding(permissions), {});

/**
 * The FHIRcast card's release, answered "nothing released": an Epic session's
 * Home asks for it beside the patient (`fhircast-card.test.tsx` has the card).
 */
const NO_FHIRCAST_RELEASE = { "GET /api/auth/epic/fhircast": bare(404) };

/** The patient reads among `sent`, the FHIRcast card's release left out. */
const patientReads = (sent: readonly { method: string; path: string }[]) =>
  sent.filter(({ path }) => path.startsWith("/api/patient/"));

/** The page beneath the top bar, which names the patient in context too. */
const page = () => within(screen.getByRole("main"));

/** Every baseline Permission a User holds. */
const BASELINE: Permission[] = ["patient:read"];

describe("Home", () => {
  it("shows no patient in context, and asks for nothing, for a session holding the baseline Permissions", async () => {
    const { requests } = await renderHolding(BASELINE);

    expect(screen.getByText("No patient in context")).toBeInTheDocument();
    expect(requests()).toEqual([]);
  });

  it("shows the patient an EHR launch put in context on the Patient card", async () => {
    landedFromEpicWith(TAN_WEI_MING.id);

    await renderSignedIn("/", holding(BASELINE), {
      [TAN_WEI_MING_READ]: json(TAN_WEI_MING),
      ...NO_FHIRCAST_RELEASE,
    });

    expect(await page().findByRole("heading", { name: "Tan Wei Ming" })).toBeInTheDocument();
  });

  it("asks for no patient after an EHR launch that put none in context", async () => {
    window.history.replaceState(null, "", "/#encounter=enc-1");
    adoptLaunchLanding();

    const { requests } = await renderSignedIn("/", holding(BASELINE), NO_FHIRCAST_RELEASE);

    expect(screen.getByText("No patient in context")).toBeInTheDocument();
    expect(patientReads(requests())).toEqual([]);
  });

  it("shows the FHIRcast card after an EHR launch that put no patient in context", async () => {
    window.history.replaceState(null, "", "/#launch");
    adoptLaunchLanding();

    await renderSignedIn("/", holding(BASELINE), NO_FHIRCAST_RELEASE);

    expect([
      screen.getByText("No patient in context") !== null,
      screen.getByRole("heading", { name: "FHIRcast" }) !== null,
    ]).toEqual([true, true]);
  });

  it("drops the patient and the FHIRcast card as soon as the tab's EHR context is cleared", async () => {
    landedFromEpicWith(TAN_WEI_MING.id);
    await renderSignedIn("/", holding(BASELINE), {
      [TAN_WEI_MING_READ]: json(TAN_WEI_MING),
      ...NO_FHIRCAST_RELEASE,
    });
    await page().findByText("Tan Wei Ming");

    act(() => clearEhrContext());

    expect([
      screen.queryByText("Tan Wei Ming"),
      screen.queryByRole("heading", { name: "FHIRcast" }),
    ]).toEqual([null, null]);
  });

  it("offers no control of its own", async () => {
    await renderHolding(BASELINE);

    // Sign out and the sidebar's controls are the layout's, outside the page.
    expect(page().queryAllByRole("button")).toEqual([]);
  });

  it("shows the page title above the Patient card", async () => {
    await renderHolding(BASELINE);

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
