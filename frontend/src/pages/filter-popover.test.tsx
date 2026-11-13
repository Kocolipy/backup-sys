import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";

import { FilterPopover } from "./filter-popover";

/** A panel form holding one draft field, and a Done that closes the panel. */
function DraftForm({ close }: { close: () => void }) {
  const [draft, setDraft] = useState("");
  return (
    <form aria-label="Widget filters">
      <label>
        Name
        <input onChange={(event) => setDraft(event.target.value)} value={draft} />
      </label>
      <button onClick={close} type="button">
        Done
      </button>
    </form>
  );
}

function renderPopover(setCount = 0) {
  render(
    <FilterPopover className="w-64" setCount={setCount}>
      {(close) => <DraftForm close={close} />}
    </FilterPopover>,
  );
}

const trigger = () => screen.getByRole("button", { name: /^Filter/ });
const panel = () => screen.queryByRole("form", { name: "Widget filters" });

describe("FilterPopover", () => {
  it.each([
    ["Filter", 0, "Filter"],
    ["Filter, 1 set", 1, "Filter1"],
    ["Filter, 6 set", 6, "Filter6"],
  ])(
    'names its button "%s" with %i set, and shows that count beside it',
    (name, setCount, text) => {
      renderPopover(setCount);

      expect([trigger().getAttribute("aria-label"), trigger().textContent]).toEqual([name, text]);
    },
  );

  it("shows no count badge with no filter set", () => {
    renderPopover(0);

    expect(trigger().querySelector('[data-slot="badge"]')).toBeNull();
  });

  it("keeps the count badge from a screen reader, which reads the count in the button's name", () => {
    renderPopover(2);

    expect(trigger().querySelector('[data-slot="badge"]')).toHaveAttribute("aria-hidden", "true");
  });

  it("is closed until its button is pressed", async () => {
    const user = userEvent.setup();
    renderPopover();
    const closed = panel();

    await user.click(trigger());

    expect([closed, panel()]).toEqual([null, expect.any(HTMLFormElement)]);
  });

  it("closes when its form calls close", async () => {
    const user = userEvent.setup();
    renderPopover();

    await user.click(trigger());
    await user.click(screen.getByRole("button", { name: "Done" }));

    expect([panel(), trigger().getAttribute("aria-expanded")]).toEqual([null, "false"]);
  });

  it("mounts its form afresh on each opening, so an edit left in it is forgotten", async () => {
    const user = userEvent.setup();
    renderPopover();

    await user.click(trigger());
    await user.type(screen.getByLabelText("Name"), "draft");
    await user.keyboard("{Escape}");
    await user.click(trigger());

    expect(screen.getByLabelText("Name")).toHaveValue("");
  });
});
