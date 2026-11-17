import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { holding, STILL_CHECKING } from "@/auth/session-adapter.testHelpers";
import { page } from "@/pages/patient.testHelpers";
import { renderSignedIn } from "@/shell/signed-in-shell.testHelpers";

// Who is routed to Home and who on to Patient Search is `App.test.tsx`'s; the
// patient in context, which Home never shows, is the Demographic page's.

/** Home for a session holding no Permission, inside the signed-in layout. */
const renderHome = () => renderSignedIn("/", holding([]), {});

describe("Home", () => {
  it("is headed by its title alone, shown", async () => {
    await renderHome();

    expect(
      page()
        .getAllByRole("heading")
        .map((heading) => [heading.textContent, heading.classList.contains("sr-only")]),
    ).toEqual([["Home", false]]);
  });

  it("says, on a card of its own, that the account has no access to patients", async () => {
    await renderHome();

    expect(
      screen.getByText("Your account has no access to patients.").closest("[data-slot=card]")
        ?.textContent,
    ).toBe("Your account has no access to patients.");
  });

  it("shows its note as muted text", async () => {
    await renderHome();

    expect(screen.getByText("Your account has no access to patients.")).toHaveClass(
      "text-sm",
      "text-muted-foreground",
    );
  });

  it("renders safely while the session's user details are unavailable", async () => {
    const { requests } = await renderSignedIn("/", STILL_CHECKING, {});

    // No session details, so no Permission: nothing is asked for.
    expect(requests()).toEqual([]);
  });
});
