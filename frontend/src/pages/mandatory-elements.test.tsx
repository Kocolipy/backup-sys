import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Table, TableBody, TableCell, TableHeader, TableRow } from "@/components/ui/table";
import { TooltipProvider } from "@/components/ui/tooltip";
import type { SourceField } from "@/lib/source-field";
import {
  anyWarns,
  type MandatoryElement,
  MissingInformationCell,
  MissingInformationHeading,
} from "@/pages/mandatory-elements";
import { warnings } from "@/pages/mandatory-elements.testHelpers";

/** A record of three elements, two of them Mandatory. */
interface Sample {
  readonly first: SourceField<string>;
  readonly second: SourceField<string>;
  readonly optional: SourceField<string>;
}

const MANDATORY: readonly MandatoryElement<keyof Sample>[] = [
  { element: "first", label: "First" },
  { element: "second", label: "Second" },
];

const present: SourceField<string> = { state: "present", value: "x" };
const missing: SourceField<string> = { state: "missing" };
const invalid: SourceField<string> = { state: "invalid" };

const sample = (fields: Partial<Sample> = {}): Sample => ({
  first: present,
  optional: present,
  second: present,
  ...fields,
});

/** A table of `records` as a history lays one out: the leading column only when a row warns. */
function renderTable(records: readonly Sample[]) {
  const warns = anyWarns(MANDATORY, records);
  render(
    <TooltipProvider>
      <Table>
        <TableHeader>
          <TableRow>
            {warns ? <MissingInformationHeading /> : null}
            <TableCell>Data</TableCell>
          </TableRow>
        </TableHeader>
        <TableBody>
          {records.map((record, place) => (
            <TableRow key={place}>
              {warns ? <MissingInformationCell mandatory={MANDATORY} record={record} /> : null}
              <TableCell>data</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TooltipProvider>,
  );
}

const table = () => screen.getByRole("table");

describe("Mandatory elements", () => {
  it.each([
    ["missing", missing],
    ["in the wrong format", invalid],
  ] as const)("warns on a row whose Mandatory element is %s, naming its column", (_, field) => {
    renderTable([sample({ second: field })]);

    expect(warnings(table())).toEqual(["Critical information missing: Second"]);
  });

  it("does not warn on a row whose only absent element is not Mandatory", () => {
    renderTable([sample({ optional: missing }), sample({ first: missing })]);

    expect(warnings(table())).toEqual([null, "Critical information missing: First"]);
  });

  it("names each absent Mandatory element in the declared order, whatever the record's", () => {
    renderTable([sample({ first: invalid, second: missing })]);

    expect(warnings(table())).toEqual(["Critical information missing: First, Second"]);
  });

  it("leaves the leading cell of a row that does not warn empty, beside one that does", () => {
    renderTable([sample({ first: missing }), sample()]);
    const secondRow = screen.getAllByRole("row")[2]!;

    expect(within(secondRow).getAllByRole("cell")[0]!.childNodes).toHaveLength(0);
  });

  it("shows no leading column when no row warns", () => {
    renderTable([sample({ optional: invalid }), sample()]);

    expect({
      cells: screen
        .getAllByRole("row")
        .slice(1)
        .map((row) => within(row).getAllByRole("cell").length),
      heading: screen.queryByRole("columnheader", { name: "Missing information" }),
    }).toEqual({ cells: [1, 1], heading: null });
  });

  it("heads the leading column with a hidden heading, Missing information", () => {
    renderTable([sample({ first: missing })]);
    const heading = screen.getByRole("columnheader", { name: "Missing information" });

    expect({
      hidden: heading.querySelector(".sr-only")?.textContent,
      scope: heading.getAttribute("scope"),
    }).toEqual({ hidden: "Missing information", scope: "col" });
  });

  it("draws the warning as a triangle in the warning colour, a tab stop", () => {
    renderTable([sample({ first: missing })]);
    const warning = screen.getByRole("img");

    const icon = warning.querySelector("svg.lucide-triangle-alert");
    expect({
      colour: icon?.classList.contains("text-warning-soft-foreground"),
      hidden: icon?.getAttribute("aria-hidden"),
      tabIndex: warning.tabIndex,
    }).toEqual({ colour: true, hidden: "true", tabIndex: 0 });
  });

  it("opens the warning's tooltip on focus, naming the columns as its accessible name does", async () => {
    renderTable([sample({ first: missing, second: invalid })]);

    fireEvent.focus(
      screen.getByRole("img", { name: "Critical information missing: First, Second" }),
    );

    expect(await screen.findByRole("tooltip")).toHaveTextContent(
      /^Critical information missing: First, Second$/,
    );
  });
});
