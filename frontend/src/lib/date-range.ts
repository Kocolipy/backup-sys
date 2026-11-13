/**
 * A from–to range of calendar dates as a filter panel holds it — each end an
 * `<input type="date">` value, `YYYY-MM-DD` or `""` for an end left open —
 * checked as the backend checks it, so a panel refuses what the backend would
 * answer `400` and sends nothing.
 */

import { isCalendarDate } from "./time";

/**
 * Why the range from `from` to `to`, named `label`, cannot be sent, or `null`
 * when it can: each end set is a day the calendar has, and the from-date is
 * not after the to-date — both ends are inclusive, so a range of one day
 * passes.
 */
export function dateRangeRefusal(label: string, from: string, to: string): string | null {
  if ([from, to].some((end) => end !== "" && !isCalendarDate(end))) {
    return `Enter the ${label} dates as real dates.`;
  }
  // Both are `YYYY-MM-DD`, so their text orders as their days do; an open
  // from-date, "", is before every to-date, and an open to-date bounds nothing.
  if (to !== "" && from > to) return `The ${label} from-date must not be after its to-date.`;
  return null;
}

/** A date range a panel offers: the prefix of its two fields, and its label. */
interface DateRangeField<R extends string> {
  label: string;
  range: R;
}

/** Filters either passed, or refused with the range at fault and why. */
export type CheckedDateRanges<R extends string> =
  { ok: true } | { ok: false; range: R; message: string };

/**
 * Whether `filters` may be sent: each of `ranges`, read from its two fields
 * `<range>From` and `<range>To`, passes {@link dateRangeRefusal}. The first
 * range refused, in the order given, is the one named.
 */
export function checkDateRanges<R extends string>(
  ranges: readonly DateRangeField<R>[],
  filters: Readonly<Record<`${R}From` | `${R}To`, string>>,
): CheckedDateRanges<R> {
  for (const { label, range } of ranges) {
    const message = dateRangeRefusal(label, filters[`${range}From`], filters[`${range}To`]);
    if (message !== null) return { message, ok: false, range };
  }
  return { ok: true };
}
