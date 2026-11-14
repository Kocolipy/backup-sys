/**
 * What the FFI, NUMC and ARMC pages' histories share in what they ask the
 * backend for: the date ranges each history narrows by, and how those ranges
 * are counted and sent — `checkDateRanges` (`@/lib/date-range`) checks them —
 * and the two dates the FFI and NUMC Histories sort by. Shared by the pages' API modules, so it sits beside them in
 * `src/pages/`; each page's `*-api.ts` adds its own closed sets' filters, and
 * the ARMC page its own two dates.
 */

import type { ColumnSort } from "@/lib/sorting";

/** A column the FFI or NUMC History sorts by, as the backend's `sortBy` names it. */
export type IssueOrExpiryColumn = "issueDate" | "expiryDate";

/** How the FFI or NUMC History is sorted: one date, in one direction. */
export type IssueOrExpirySort = ColumnSort<IssueOrExpiryColumn>;

/**
 * The date ranges a history's Filter panel offers, each by the prefix `R` of
 * its two fields and its label, in the order the panel lists them and their
 * parameters are sent.
 */
export type HistoryDateRanges<R extends string> = readonly { label: string; range: R }[];

/**
 * A history's date ranges, each end as its `<input type="date">` holds it,
 * `<range>From` and `<range>To`: `YYYY-MM-DD`, or `""` for an end left open.
 */
export type HistoryDates<R extends string> = Record<`${R}From` | `${R}To`, string>;

/** The FFI and NUMC Histories' two date ranges. */
export const ISSUE_AND_EXPIRY_DATES = [
  { label: "Issue Date", range: "issueDate" },
  { label: "Expiry Date", range: "expiryDate" },
] as const;

/** A date range of the FFI or NUMC History, by the prefix of its two fields. */
type IssueOrExpiry = (typeof ISSUE_AND_EXPIRY_DATES)[number]["range"];

/** The FFI or NUMC History's two date ranges, as its Filter panel holds them. */
export type IssueAndExpiryDates = HistoryDates<IssueOrExpiry>;

/** No date range on the FFI or NUMC History: every date. */
export const NO_ISSUE_AND_EXPIRY_DATES: IssueAndExpiryDates = {
  expiryDateFrom: "",
  expiryDateTo: "",
  issueDateFrom: "",
  issueDateTo: "",
};

/** Each date field of `ranges`, in the order its parameter is sent: each range's from, then its to. */
const dateFields = <R extends string>(ranges: HistoryDateRanges<R>) =>
  ranges.flatMap(({ range }) => [`${range}From` as const, `${range}To` as const]);

/** How many ends of `dates`' `ranges` are set: each end counts as one filter. */
export function countHistoryDates<R extends string>(
  ranges: HistoryDateRanges<R>,
  dates: HistoryDates<R>,
): number {
  return dateFields(ranges).filter((field) => dates[field] !== "").length;
}

/** The query parameters `dates` are sent as: one for each end of `ranges` set, by its field's name. */
export function historyDateParameters<R extends string>(
  ranges: HistoryDateRanges<R>,
  dates: HistoryDates<R>,
): string[] {
  return dateFields(ranges)
    .filter((field) => dates[field] !== "")
    .map((field) => `${field}=${dates[field]}`);
}
