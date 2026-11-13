import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { DateRangeFields } from "./date-range-fields";

type Range = { from: string; to: string };

/** A "Shipped" range at `range`, refused with `refusal`, every change it reports recorded. */
function renderRange(range: Range = { from: "", to: "" }, refusal: string | null = null) {
  const onChange = vi.fn<(next: Range) => void>();
  render(
    <DateRangeFields
      from={range.from}
      label="Shipped"
      onChange={onChange}
      refusal={refusal}
      to={range.to}
    />,
  );
  return { onChange };
}

const group = () => within(screen.getByRole("group", { name: "Shipped" }));
const end = (name: "From" | "To") => group().getByLabelText<HTMLInputElement>(name);

describe("DateRangeFields", () => {
  it("is a group named by its label, holding a From and a To date, each showing its end", () => {
    renderRange({ from: "2024-01-01", to: "2024-12-31" });

    expect((["From", "To"] as const).map((name) => [end(name).type, end(name).value])).toEqual([
      ["date", "2024-01-01"],
      ["date", "2024-12-31"],
    ]);
  });

  it.each([
    ["From", { from: "2024-02-03", to: "2024-12-31" }],
    ["To", { from: "2024-01-01", to: "2024-02-03" }],
  ] as const)("reports a change to %s with the other end as it stands", (name, reported) => {
    const { onChange } = renderRange({ from: "2024-01-01", to: "2024-12-31" });

    fireEvent.change(end(name), { target: { value: "2024-02-03" } });

    expect(onChange.mock.calls).toEqual([[reported]]);
  });

  it("marks neither end invalid, and shows no message, with no refusal", () => {
    renderRange();

    expect({
      ends: (["From", "To"] as const).map((name) => [
        end(name).getAttribute("aria-invalid"),
        end(name).getAttribute("aria-describedby"),
      ]),
      notes: group().queryAllByRole("paragraph"),
    }).toEqual({
      ends: [
        ["false", null],
        ["false", null],
      ],
      notes: [],
    });
  });

  it("marks both ends invalid, each described by the refusal shown beneath the range", () => {
    renderRange({ from: "2024-02-01", to: "2024-01-01" }, "From must not be after To.");

    const refusal = group().getByText(/^From must not be after To\.$/);
    expect(
      (["From", "To"] as const).map((name) => [
        end(name).getAttribute("aria-invalid"),
        end(name).getAttribute("aria-describedby"),
      ]),
    ).toEqual([
      ["true", refusal.id],
      ["true", refusal.id],
    ]);
  });
});
