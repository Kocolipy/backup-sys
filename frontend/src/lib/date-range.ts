/**
 * A from–to range over a date column as a filter panel holds it, checked as
 * the backend checks it, so a panel refuses what the backend would answer
 * `400` and sends nothing. Each column declares its precision, which decides
 * what an end may be:
 *
 * - a **calendar date** (`date`): an `<input type="date">` value, `YYYY-MM-DD`;
 * - a **date-time to the minute** (`date-time`): an
 *   `<input type="datetime-local">` value, `YYYY-MM-DDTHH:mm`, Singapore's
 *   wall clock with no offset.
 *
 * Either end may be `""`, left open.
 */

import { isCalendarDate, isMinuteDateTime } from "./time";

/** How finely a date column is written: a calendar date, or a date-time to the minute. */
export type DatePrecision = "date" | "date-time";

/**
 * A date column a listing declares: its name — the prefix of its two range
 * fields, `<column>From` and `<column>To`, and what the backend's `sortBy`
 * calls it — its label, and its precision.
 */
export interface DateColumn<C extends string> {
  column: C;
  label: string;
  precision: DatePrecision;
}

/** What an end of each precision must be, and what a refusal of one asks for. */
const ENDS: Readonly<Record<DatePrecision, { is: (end: string) => boolean; as: string }>> = {
  date: { as: "dates as real dates", is: isCalendarDate },
  "date-time": { as: "dates and times as real dates and times", is: isMinuteDateTime },
};

/**
 * Why the range from `from` to `to` over the column named `label` cannot be
 * sent, or `null` when it can: each end set is written at the column's
 * `precision`, and the from-end is not after the to-end — both ends are
 * inclusive, so a range of one day, or of one minute, passes.
 */
export function dateRangeRefusal(
  { label, precision }: { label: string; precision: DatePrecision },
  from: string,
  to: string,
): string | null {
  const ends = ENDS[precision];
  if ([from, to].some((end) => end !== "" && !ends.is(end))) {
    return `Enter the ${label} ${ends.as}.`;
  }
  // Both are written to one fixed width, so their text orders as their times
  // do; an open from-end, "", is before every to-end, and an open to-end
  // bounds nothing.
  if (to !== "" && from > to) return `The ${label} from-date must not be after its to-date.`;
  return null;
}

/** Filters either passed, or refused with the column whose range is at fault and why. */
export type CheckedDateRanges<C extends string> =
  { ok: true } | { ok: false; column: C; message: string };

/**
 * Whether `filters` may be sent: the range over each of `columns`, read from
 * its two fields `<column>From` and `<column>To`, passes
 * {@link dateRangeRefusal} at the column's precision. The first range refused,
 * in the order given, is the one named.
 */
export function checkDateRanges<C extends string>(
  columns: readonly DateColumn<C>[],
  filters: Readonly<Record<`${C}From` | `${C}To`, string>>,
): CheckedDateRanges<C> {
  for (const declared of columns) {
    const { column } = declared;
    const message = dateRangeRefusal(declared, filters[`${column}From`], filters[`${column}To`]);
    if (message !== null) return { column, message, ok: false };
  }
  return { ok: true };
}
