import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";

import { Table, TableHeader, TableRow } from "@/components/ui/table";
import type { ColumnSort } from "@/lib/sorting";

import { SortHeading, type ColumnSorting } from "./sort-heading";

type Column = "name" | "size";

/** One "Name" heading over a table, sorted as `sorting` says. */
function NameHeadingTable({
  disabled,
  sorting,
}: {
  disabled?: boolean;
  sorting: ColumnSorting<Column>;
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <SortHeading by="name" disabled={disabled} label="Name" sorting={sorting} />
        </TableRow>
      </TableHeader>
    </Table>
  );
}

/** The "Name" heading over a table sorted as `sort` says, its presses recorded. */
function renderHeading(sort: ColumnSort<Column> | null, disabled?: boolean) {
  const onSort = vi.fn<(next: ColumnSort<Column> | null) => void>();
  render(<NameHeadingTable disabled={disabled} sorting={{ onSort, sort }} />);
  return { onSort };
}

/** The "Name" heading over a table that starts unsorted and takes every sort it asks for. */
function renderSortedHeading() {
  function SortedTable() {
    const [sort, setSort] = useState<ColumnSort<Column> | null>(null);
    return <NameHeadingTable sorting={{ onSort: setSort, sort }} />;
  }
  render(<SortedTable />);
}

const heading = () => screen.getByRole("columnheader", { name: "Name" });
const button = () => screen.getByRole("button", { name: "Name" });

/** Which `lucide-react` direction icon the heading shows: its icon's `lucide-<name>` class. */
const directionIcon = () =>
  [...(heading().querySelector("svg")?.classList ?? [])].find(
    (name) => name.startsWith("lucide-") && !name.endsWith("-icon"),
  );

describe("SortHeading", () => {
  it.each([
    ["unsorted", null, "none", "lucide-arrow-up-down"],
    [
      "sorted by another column",
      { by: "size", order: "ascending" },
      "none",
      "lucide-arrow-up-down",
    ],
    ["ascending", { by: "name", order: "ascending" }, "ascending", "lucide-arrow-up"],
    ["descending", { by: "name", order: "descending" }, "descending", "lucide-arrow-down"],
  ] as const)("states its direction %s with aria-sort and its icon", (_, sort, ariaSort, icon) => {
    renderHeading(sort);

    expect({ ariaSort: heading().getAttribute("aria-sort"), icon: directionIcon() }).toEqual({
      ariaSort,
      icon,
    });
  });

  it.each([
    ["unsorted", null, { by: "name", order: "ascending" }],
    [
      "sorted by another column",
      { by: "size", order: "descending" },
      { by: "name", order: "ascending" },
    ],
    ["ascending", { by: "name", order: "ascending" }, { by: "name", order: "descending" }],
    ["descending", { by: "name", order: "descending" }, { by: "name", order: "ascending" }],
  ] as const)("asks, pressed while %s, for the next sort in the cycle", async (_, sort, next) => {
    const { onSort } = renderHeading(sort);

    await userEvent.click(button());

    expect(onSort.mock.calls).toEqual([[next]]);
  });

  it("never returns a sorted column to the default order, a third press sorting it ascending", async () => {
    renderSortedHeading();

    const directions = [heading().getAttribute("aria-sort")];
    for (let press = 0; press < 3; press++) {
      await userEvent.click(button());
      directions.push(heading().getAttribute("aria-sort"));
    }

    expect(directions).toEqual(["none", "ascending", "descending", "ascending"]);
  });

  it("holds the sort still while disabled", () => {
    renderHeading(null, true);

    expect(button()).toBeDisabled();
  });

  it("is a column heading of its column", () => {
    renderHeading(null);

    expect(heading().getAttribute("scope")).toBe("col");
  });
});
