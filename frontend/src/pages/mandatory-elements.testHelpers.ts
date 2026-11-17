import { within } from "@testing-library/react";

/** The body rows of `table`, its heading row left out. */
const bodyRows = (table: HTMLElement) => within(table).getAllByRole("row").slice(1);

/** Whether `table` shows the warnings' leading column, by its hidden heading. */
const hasLeadingColumn = (table: HTMLElement) =>
  within(table).queryByRole("columnheader", { name: "Missing information" }) !== null;

/**
 * The cells before `table`'s data columns: 1 where it shows the warnings'
 * leading column, 0 where it does not. A data column's cell index is this plus
 * the column's own index.
 */
export const leadingCells = (table: HTMLElement) => (hasLeadingColumn(table) ? 1 : 0);

/** The warning icon of each body row of `table`, by its accessible name, `null` on a row with none. */
export const warnings = (table: HTMLElement) =>
  bodyRows(table).map((row) => within(row).queryByRole("img")?.getAttribute("aria-label") ?? null);

/**
 * What `table` shows in place of the leading column: the text of each body
 * row's first cell, and the "Missing information" heading, `null` when absent.
 * On a page where no row warns, the first cell is the first data column's.
 */
export const leadingColumn = (table: HTMLElement) => ({
  firstCells: bodyRows(table).map((row) => within(row).getAllByRole("cell")[0]!.textContent),
  heading: within(table).queryByRole("columnheader", { name: "Missing information" }),
});
