import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { clearEhrContext } from "@/auth/ehr-context";
import { deferred, holding } from "@/auth/session-adapter.testHelpers";
import { renderInLayout } from "@/shell/signed-in-shell.testHelpers";

import { usePatientInContext } from "./patient-in-context";
import { TAN_WEI_MING, TAN_WEI_MING_READ, withPatientInContext } from "./patient.testHelpers";

// What the one read shows, and how often it asks, is held where it is shown:
// `home.test.tsx` (one read per change, for the card and the top bar both),
// `patient-card.test.tsx` and `shell/top-bar.test.tsx`. Held here is what no
// screen shows: the view each render passes on, before the browser paints.

/** A page that records every view of the patient in context it renders with. */
function ViewsSeen({ seen }: { seen: string[] }) {
  seen.push(usePatientInContext().kind);
  return null;
}

afterEach(() => {
  clearEhrContext();
  window.history.replaceState(null, "", "/");
});

describe("usePatientInContext", () => {
  it("has nothing to read outside the signed-in layout's scope", () => {
    const silence = vi.spyOn(console, "error").mockImplementation(() => undefined);
    let thrown: unknown = null;
    try {
      renderHook(() => usePatientInContext());
    } catch (error) {
      thrown = error;
    } finally {
      silence.mockRestore();
    }

    expect(thrown).toEqual(
      new Error("The patient in context is read only inside the signed-in layout."),
    );
  });

  it("shows a session holding patient:read a patient it opens as loading from the first render", async () => {
    const seen: string[] = [];
    await renderInLayout(
      [{ element: <ViewsSeen seen={seen} />, path: "/" }],
      "/",
      holding(["patient:read"]),
      { [TAN_WEI_MING_READ]: deferred().answer },
    );
    seen.length = 0;

    act(() => withPatientInContext(TAN_WEI_MING.id));

    expect(new Set(seen)).toEqual(new Set(["loading"]));
  });
});
