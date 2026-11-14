import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { HistoryFilterPopover } from "./history-filter-popover";

// The panel over a history declaring one column of each precision. Each
// history's own panel — its filters, their order and its count — is its
// page's (ffi.test.tsx, numc.test.tsx, armc.test.tsx); how a range is checked
// is `checkDateRanges`' (lib/date-range.test.ts).
const COLUMNS = [
  { column: "shipped", label: "Shipped", precision: "date" },
  { column: "takenAt", label: "Taken At", precision: "date-time" },
] as const;

type Filters = Record<"shippedFrom" | "shippedTo" | "takenAtFrom" | "takenAtTo", string>;

const OPEN: Filters = { shippedFrom: "", shippedTo: "", takenAtFrom: "", takenAtTo: "" };

/** The panel over {@link COLUMNS}, open, every Apply it makes recorded. */
async function openPanel() {
  const onApply = vi.fn<(filters: Filters) => void>();
  render(
    <HistoryFilterPopover
      applied={OPEN}
      columns={COLUMNS}
      count={() => 0}
      label="Widget filters"
      none={OPEN}
      onApply={onApply}
    >
      {({ dateRanges }) => dateRanges}
    </HistoryFilterPopover>,
  );
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: /^Filter/ }));
  const panel = screen.getByRole("form", { name: "Widget filters" });
  return { onApply, panel, user };
}

/** The `end` field of the range named `label`, in `panel`. */
const field = (panel: HTMLElement, label: string, end: "From" | "To") =>
  within(within(panel).getByRole("group", { name: label })).getByLabelText<HTMLInputElement>(end);

describe("HistoryFilterPopover", () => {
  it("types each range's ends in its own column's control: a date, or a date and a time", async () => {
    const { panel } = await openPanel();

    expect(
      COLUMNS.map(({ label }) => [
        label,
        field(panel, label, "From").type,
        field(panel, label, "To").type,
      ]),
    ).toEqual([
      ["Shipped", "date", "date"],
      ["Taken At", "datetime-local", "datetime-local"],
    ]);
  });

  it("applies a date-time range of one minute as its two ends hold it", async () => {
    const { onApply, panel, user } = await openPanel();

    fireEvent.change(field(panel, "Taken At", "From"), { target: { value: "2024-01-01T09:30" } });
    fireEvent.change(field(panel, "Taken At", "To"), { target: { value: "2024-01-01T09:30" } });
    await user.click(within(panel).getByRole("button", { name: "Apply" }));

    expect(onApply.mock.calls).toEqual([
      [{ ...OPEN, takenAtFrom: "2024-01-01T09:30", takenAtTo: "2024-01-01T09:30" }],
    ]);
  });

  it("refuses a date-time from-end after its to-end beneath its range, and applies nothing", async () => {
    const { onApply, panel, user } = await openPanel();

    fireEvent.change(field(panel, "Taken At", "From"), { target: { value: "2024-01-01T09:31" } });
    fireEvent.change(field(panel, "Taken At", "To"), { target: { value: "2024-01-01T09:30" } });
    await user.click(within(panel).getByRole("button", { name: "Apply" }));

    const refusal = within(within(panel).getByRole("group", { name: "Taken At" })).getByText(
      "The Taken At from-date must not be after its to-date.",
    );
    expect({
      applied: onApply.mock.calls,
      describedBy: field(panel, "Taken At", "To").getAttribute("aria-describedby"),
    }).toEqual({ applied: [], describedBy: refusal.id });
  });
});
