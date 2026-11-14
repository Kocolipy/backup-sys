/**
 * What the FFI and NUMC pages' histories share in what they ask the backend
 * for: the two dates each history sorts by and narrows by a range of, and how
 * those ranges are checked, counted and sent. Shared by both pages' API
 * modules, so it sits beside them in `src/pages/`; each page's `*-api.ts`
 * adds its own closed sets' filters.
 */

import { type CheckedDateRanges, checkDateRanges } from "@/lib/date-range";
import type { ColumnSort } from "@/lib/sorting";

/** A column a history sorts by, as the backend's `sortBy` names it. */
export type HistorySortColumn = "issueDate" | "expiryDate";

/** How a history is sorted: one date, in one direction. */
export type HistorySort = ColumnSort<HistorySortColumn>;

/**
 * A history's two date ranges, each end as its `<input type="date">` holds
 * it: `YYYY-MM-DD`, or `""` for an end left open.
 */
export interface HistoryDates {
  issueDateFrom: string;
  issueDateTo: string;
  expiryDateFrom: string;
  expiryDateTo: string;
}

/** No date range: every date. */
export const NO_HISTORY_DATES: HistoryDates = {
  expiryDateFrom: "",
  expiryDateTo: "",
  issueDateFrom: "",
  issueDateTo: "",
};

/** A date range a history's Filter panel offers, by the prefix of its two fields, and its label. */
export const HISTORY_DATE_RANGES = [
  { label: "Issue Date", range: "issueDate" },
  { label: "Expiry Date", range: "expiryDate" },
] as const;

/** The dates either passed, or refused with the range at fault and why. */
export type CheckedHistoryDates = CheckedDateRanges<(typeof HISTORY_DATE_RANGES)[number]["range"]>;

/** Each date field, in the order its parameter is sent. */
const DATE_FIELDS = ["issueDateFrom", "issueDateTo", "expiryDateFrom", "expiryDateTo"] as const;

/**
 * Whether `dates` may be sent: each date set is one the calendar has, and no
 * from-date is after its to-date ({@link checkDateRanges}). The backend
 * refuses either with a `400`, so the panel refuses it first and sends
 * nothing.
 */
export function checkHistoryDates(dates: HistoryDates): CheckedHistoryDates {
  return checkDateRanges(HISTORY_DATE_RANGES, dates);
}

/** How many ends of `dates`' ranges are set: each end counts as one filter. */
export function countHistoryDates(dates: HistoryDates): number {
  return DATE_FIELDS.filter((field) => dates[field] !== "").length;
}

/** The query parameters `dates` are sent as: one for each end set, by its field's name. */
export function historyDateParameters(dates: HistoryDates): string[] {
  return DATE_FIELDS.filter((field) => dates[field] !== "").map(
    (field) => `${field}=${dates[field]}`,
  );
}
