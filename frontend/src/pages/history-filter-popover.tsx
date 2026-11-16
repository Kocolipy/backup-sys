import { type ReactNode, useState } from "react";

import { Button } from "@/components/ui/button";
import { type CheckedDateRanges, checkDateRanges } from "@/lib/date-range";
import { DateRangeFields } from "@/pages/date-range-fields";
import { FilterPopover } from "@/pages/filter-popover";
import type { HistoryDateColumns, HistoryDates } from "@/pages/history-api";

/** A picker's copy for a history's column, all of it the column's name. */
export const pickerCopy = (name: string, plural: string) => ({
  list: plural,
  name,
  noMatch: `No ${name} matches.`,
  search: `Search ${plural}`,
});

/** A range Apply refused, and why. */
type Refusal<C extends string> = Extract<CheckedDateRanges<C>, { ok: false }>;

/** What a history's form lays out: its draft, a change to it, and its date ranges. */
interface HistoryFilterFields<F> {
  /** The date ranges' fields, each refusal beneath its range, for the form to place. */
  dateRanges: ReactNode;
  draft: F;
  edit: (change: Partial<F>) => void;
}

/** The two fields of the range over `column`, named by it, as their draft holds them. */
function datesOf<C extends string>(column: C, from: string, to: string): Partial<HistoryDates<C>> {
  // A computed key over a generic prefix widens to `string`; these two are
  // exactly the range's own fields.
  return { [`${column}From`]: from, [`${column}To`]: to } as Partial<HistoryDates<C>>;
}

/**
 * The panel's form, mounted each time the panel opens, so it starts from the
 * filters applied and with no refusal, and an edit never applied is forgotten
 * when it closes. Apply — which Enter in a date also asks for — first checks
 * the range over each of `columns` at its precision, as the backend would
 * ({@link checkDateRanges}): a refused range is marked beneath it and nothing
 * is applied. Reset applies `none`.
 */
function HistoryFilterForm<C extends string, F extends HistoryDates<C>>({
  applied,
  children,
  columns,
  label,
  none,
  onApply,
}: {
  applied: F;
  children: (fields: HistoryFilterFields<F>) => ReactNode;
  columns: HistoryDateColumns<C>;
  label: string;
  none: F;
  onApply: (filters: F) => void;
}) {
  const [draft, setDraft] = useState(applied);
  const [refusal, setRefusal] = useState<Refusal<C> | null>(null);
  const dateRanges = columns.map(({ column, label: columnLabel, precision }) => (
    <DateRangeFields
      from={draft[`${column}From`]}
      key={column}
      label={columnLabel}
      onChange={({ from, to }) => setDraft({ ...draft, ...datesOf(column, from, to) })}
      precision={precision}
      refusal={refusal?.column === column ? refusal.message : null}
      to={draft[`${column}To`]}
    />
  ));
  return (
    <form
      aria-label={label}
      className="flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        const checked = checkDateRanges(columns, draft);
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
 * A history's filters behind a Filter button that says how many of the
 * filters applied narrow it (`count`). The form, named `label`, lays out its
 * own fields through `children`, among them a range over each of the
 * history's date `columns`, each end typed in its column's precision's control
 * ({@link DateRangeFields}), above Reset and Apply. Nothing in the panel
 * reaches a request until Apply; an Apply or Reset that applies closes it, and
 * a refused Apply leaves it open on the range to correct.
 */
export function HistoryFilterPopover<C extends string, F extends HistoryDates<C>>({
  applied,
  children,
  columns,
  count,
  label,
  none,
  onApply,
}: {
  applied: F;
  children: (fields: HistoryFilterFields<F>) => ReactNode;
  /** The history's date columns, in the order the form lists their ranges. */
  columns: HistoryDateColumns<C>;
  count: (filters: F) => number;
  label: string;
  /** No filter, which Reset applies. */
  none: F;
  onApply: (filters: F) => void;
}) {
  return (
    <FilterPopover className="w-md max-w-screen" setCount={count(applied)}>
      {(close) => (
        <HistoryFilterForm
          applied={applied}
          columns={columns}
          label={label}
          none={none}
          onApply={(filters) => {
            onApply(filters);
            close();
          }}
        >
          {children}
        </HistoryFilterForm>
      )}
    </FilterPopover>
  );
}
