import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";

import { ChoiceToggle } from "./choice-toggle";

type Shade = "ANY" | "LIGHT" | "DARK";

const SHADES: readonly { label: string; value: Shade }[] = [
  { label: "Any", value: "ANY" },
  { label: "Light", value: "LIGHT" },
  { label: "Dark", value: "DARK" },
];

/** A "Shade" toggle at `value`, every change it reports recorded and not applied. */
function renderToggle(value: Shade = "ANY") {
  const onChange = vi.fn<(next: Shade) => void>();
  render(<ChoiceToggle choices={SHADES} label="Shade" onChange={onChange} value={value} />);
  return { onChange };
}

/** A "Shade" toggle that starts at Any and takes every change it reports. */
function renderHeldToggle() {
  function Held() {
    const [value, setValue] = useState<Shade>("ANY");
    return <ChoiceToggle choices={SHADES} label="Shade" onChange={setValue} value={value} />;
  }
  render(<Held />);
}

const choice = (name: string) => screen.getByRole("radio", { name });

/** Each choice's label and whether it is pressed, in order. */
const shown = () =>
  within(screen.getByRole("radiogroup", { name: "Shade" }))
    .getAllByRole("radio")
    .map((radio) => [radio.textContent, radio.getAttribute("aria-checked")]);

describe("ChoiceToggle", () => {
  it("offers every choice by its label under its own label, only the value pressed", () => {
    renderToggle("LIGHT");

    expect(shown()).toEqual([
      ["Any", "false"],
      ["Light", "true"],
      ["Dark", "false"],
    ]);
  });

  it.each([
    ["Any", "LIGHT", "ANY"],
    ["Light", "ANY", "LIGHT"],
    ["Dark", "ANY", "DARK"],
  ] as const)("reports pressing %s as its value", async (name, value, reported) => {
    const user = userEvent.setup();
    const { onChange } = renderToggle(value);

    await user.click(choice(name));

    expect(onChange.mock.calls).toEqual([[reported]]);
  });

  it("reports no change when the choice already pressed is pressed", async () => {
    const user = userEvent.setup();
    const { onChange } = renderToggle("DARK");

    await user.click(choice("Dark"));

    expect(onChange).not.toHaveBeenCalled();
  });

  it("keeps exactly one choice pressed through a press of the one pressed", async () => {
    const user = userEvent.setup();
    renderHeldToggle();

    await user.click(choice("Dark"));
    await user.click(choice("Dark"));

    expect(shown()).toEqual([
      ["Any", "false"],
      ["Light", "false"],
      ["Dark", "true"],
    ]);
  });

  it("moves with the arrow keys and chooses with Space", async () => {
    const user = userEvent.setup();
    renderHeldToggle();

    choice("Any").focus();
    await user.keyboard("{ArrowRight}");
    await vi.waitFor(() => expect(choice("Light")).toHaveFocus());
    await user.keyboard(" ");

    expect(choice("Light")).toHaveAttribute("aria-checked", "true");
  });
});
