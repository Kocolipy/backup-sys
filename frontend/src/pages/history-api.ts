/**
 * What the histories' pages share in what they ask the backend for: each
 * history's date columns, as the history declares them ({@link DateColumn}:
 * name, label and precision), and how a range over each is counted and sent —
 * `checkDateRanges` (`@/lib/date-range`) checks them — and the names a
 * history's sort (`ColumnSort`, `@/lib/sorting`) takes from them, as
 * `sortBy=<column>` sends one; a date-time column sorts by its whole
 * date-time. Shared by the pages' API modules, so it
 * sits beside them in `src/pages/`; each page's `*-api.ts` declares its own
 * columns and adds its own closed sets' filters. Nothing here names a
 * history's column.
 */

import type { DateColumn } from "@/lib/date-range";

/** A history's date columns, in the order its Filter panel lists them and their parameters are sent. */
export type HistoryDateColumns<C extends string> = readonly DateColumn<C>[];

/** The name of each of a history's declared date columns. */
export type DateColumnOf<D extends HistoryDateColumns<string>> = D[number]["column"];

/**
 * A history's date ranges, each end as its control holds it, `<column>From`
 * and `<column>To`: written at the column's precision — `YYYY-MM-DD`, or
 * `YYYY-MM-DDTHH:mm` for a date-time — or `""` for an end left open.
 */
export type HistoryDates<C extends string> = Record<`${C}From` | `${C}To`, string>;

/** Each date field of `columns`, in the order its parameter is sent: each range's from, then its to. */
const dateFields = <C extends string>(columns: HistoryDateColumns<C>) =>
  columns.flatMap(({ column }) => [`${column}From` as const, `${column}To` as const]);

/** No date range over any of `columns`: every end open. */
export function noHistoryDates<C extends string>(columns: HistoryDateColumns<C>): HistoryDates<C> {
  // `Object.fromEntries` widens its keys to `string`; these are exactly the
  // columns' own fields.
  return Object.fromEntries(dateFields(columns).map((field) => [field, ""])) as HistoryDates<C>;
}

/** How many ends of `dates`' ranges over `columns` are set: each end counts as one filter. */
export function countHistoryDates<C extends string>(
  columns: HistoryDateColumns<C>,
  dates: HistoryDates<C>,
): number {
  return dateFields(columns).filter((field) => dates[field] !== "").length;
}

/**
 * The query parameters `dates` are sent as: one for each end of `columns` set,
 * by its field's name, its value as its control holds it — a date-time as its
 * Singapore minute, with no offset.
 */
export function historyDateParameters<C extends string>(
  columns: HistoryDateColumns<C>,
  dates: HistoryDates<C>,
): string[] {
  return dateFields(columns)
    .filter((field) => dates[field] !== "")
    .map((field) => `${field}=${dates[field]}`);
}
