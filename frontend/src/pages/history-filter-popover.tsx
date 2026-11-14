import { type ReactNode, useState } from "react";

import { Button } from "@/components/ui/button";
import { type CheckedDateRanges, checkDateRanges } from "@/lib/date-range";
import { DateRangeFields } from "@/pages/date-range-fields";
import { FilterPopover } from "@/pages/filter-popover";
import type { HistoryDateRanges, HistoryDates } from "@/pages/history-api";

/** A picker's copy for a history's column, all of it the column's name. */
export const pickerCopy = (name: string, plural: string) => ({
  list: plural,
  name,
  noMatch: `No ${name} matches.`,
  search: `Search ${plural}`,
});

/** A range Apply refused, and why. */
type Refusal<R extends string> = Extract<CheckedDateRanges<R>, { ok: false }>;

/** What a history's form lays out: its draft, a change to it, and its date ranges. */
interface HistoryFilterFields<F> {
  /** The date ranges' fields, each refusal beneath its range, for the form to place. */
  dateRanges: ReactNode;
  draft: F;
  edit: (change: Partial<F>) => void;
}

/** `range`'s two fields, named by its prefix, as their draft holds them. */
function datesOf<R extends string>(range: R, from: string, to: string): Partial<HistoryDates<R>> {
  // A computed key over a generic prefix widens to `string`; these two are
  // exactly the range's own fields.
  return { [`${range}From`]: from, [`${range}To`]: to } as Partial<HistoryDates<R>>;
}

/**
 * The panel's form, mounted each time the panel opens, so it starts from the
 * filters applied and with no refusal, and an edit never applied is forgotten
 * when it closes. Apply — which Enter in a date also asks for — first checks
 * the dates of `ranges` as the backend would ({@link checkDateRanges}): a
 * refused range is marked beneath it and nothing is applied. Reset applies
 * `none`.
 */
function HistoryFilterForm<R extends string, F extends HistoryDates<R>>({
  applied,
  children,
  label,
  none,
  onApply,
  ranges,
}: {
  applied: F;
  children: (fields: HistoryFilterFields<F>) => ReactNode;
  label: string;
  none: F;
  onApply: (filters: F) => void;
  ranges: HistoryDateRanges<R>;
}) {
  const [draft, setDraft] = useState(applied);
  const [refusal, setRefusal] = useState<Refusal<R> | null>(null);
  const dateRanges = ranges.map(({ label: rangeLabel, range }) => (
    <DateRangeFields
      from={draft[`${range}From`]}
      key={range}
      label={rangeLabel}
      onChange={({ from, to }) => setDraft({ ...draft, ...datesOf(range, from, to) })}
      refusal={refusal?.range === range ? refusal.message : null}
      to={draft[`${range}To`]}
    />
  ));
  return (
    <form
      aria-label={label}
      className="flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        const checked = checkDateRanges(ranges, draft);
        if (checked.ok) onApply(draft);
        else setRefusal(checked);
      }}
    >
      {children({ dateRanges, draft, edit: (change) => setDraft({ ...draft, ...change }) })}
      <div className="flex justify-end gap-2">
        <Button onClick={() => onApply(none)} size="sm" type="button" variant="ghost">
          Reset
        </Button>
        <Button size="sm" type="submit">
          Apply
        </Button>
      </div>
    </form>
  );
}

/**
 * A history's filters — the FFI, NUMC or ARMC page's — behind a Filter button
 * that says how many of the filters applied narrow it (`count`). The form,
 * named `label`, lays out its own fields through `children`, the date ranges
 * of `ranges` among them, above Reset and Apply. Nothing in the panel reaches
 * a request until Apply; an Apply or Reset that applies closes it, and a
 * refused Apply leaves it open on the range to correct.
 */
export function HistoryFilterPopover<R extends string, F extends HistoryDates<R>>({
  applied,
  children,
  count,
  label,
  none,
  onApply,
  ranges,
}: {
  applied: F;
  children: (fields: HistoryFilterFields<F>) => ReactNode;
  count: (filters: F) => number;
  label: string;
  /** No filter, which Reset applies. */
  none: F;
  onApply: (filters: F) => void;
  /** The history's date ranges, in the order the form lists them. */
  ranges: HistoryDateRanges<R>;
}) {
  return (
    <FilterPopover className="w-md max-w-screen" setCount={count(applied)}>
      {(close) => (
        <HistoryFilterForm
          applied={applied}
          label={label}
          none={none}
          onApply={(filters) => {
            onApply(filters);
            close();
          }}
          ranges={ranges}
        >
          {children}
        </HistoryFilterForm>
      )}
    </FilterPopover>
  );
}
