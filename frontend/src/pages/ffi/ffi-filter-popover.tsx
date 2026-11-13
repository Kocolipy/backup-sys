import { useState } from "react";

import { Button } from "@/components/ui/button";
import { ChoiceToggle } from "@/pages/choice-toggle";
import { DateRangeFields } from "@/pages/date-range-fields";
import { FilterPopover } from "@/pages/filter-popover";
import { MultiSelectPicker } from "@/pages/multi-select-picker";

import {
  checkFfiFilters,
  countFfiFilters,
  FFI_DATE_RANGES,
  FFI_HIGHER_AUTHORITIES,
  FFI_HIGHER_AUTHORITY_LABELS,
  FFI_OUTCOME_LABELS,
  FFI_OUTCOMES,
  FFI_STATUS_LABELS,
  FFI_SUBMITTING_MC_LABELS,
  FFI_SUBMITTING_MCS,
  FFI_TYPE_LABELS,
  FFI_TYPES,
  NO_FFI_FILTERS,
  type CheckedFfiFilters,
  type FfiFilters,
} from "./ffi-api";

const STATUS_CHOICES: readonly { label: string; value: FfiFilters["status"] }[] = [
  { label: "All", value: "all" },
  { label: FFI_STATUS_LABELS.active, value: "active" },
  { label: FFI_STATUS_LABELS.inactive, value: "inactive" },
];

/** A picker's copy, all of it its column's name. */
const pickerCopy = (name: string, plural: string) => ({
  list: plural,
  name,
  noMatch: `No ${name} matches.`,
  search: `Search ${plural}`,
});

/** A range Apply refused, and why. */
type Refusal = Extract<CheckedFfiFilters, { ok: false }>;

/**
 * The panel's form, mounted each time the panel opens, so it starts from the
 * filters applied and with no refusal, and an edit never applied is forgotten
 * when it closes. It offers a filter on every column: a multi-select for each
 * closed set but the Status, listing it by label in its own order; a from–to
 * range for each date; and the Status. Apply — which Enter in a date also
 * asks for — first checks the filters as the backend would
 * ({@link checkFfiFilters}): a refused range is marked beneath it and nothing
 * is applied. Reset applies no filter.
 */
function FfiFilterForm({
  applied,
  onApply,
}: {
  applied: FfiFilters;
  onApply: (filters: FfiFilters) => void;
}) {
  const [draft, setDraft] = useState(applied);
  const [refusal, setRefusal] = useState<Refusal | null>(null);
  const dateRanges = FFI_DATE_RANGES.map(({ label, range }) => (
    <DateRangeFields
      from={draft[`${range}From`]}
      key={range}
      label={label}
      onChange={({ from, to }) =>
        setDraft({ ...draft, [`${range}From`]: from, [`${range}To`]: to })
      }
      refusal={refusal?.range === range ? refusal.message : null}
      to={draft[`${range}To`]}
    />
  ));
  return (
    <form
      aria-label="FFI filters"
      className="flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        const checked = checkFfiFilters(draft);
        if (checked.ok) onApply(draft);
        else setRefusal(checked);
      }}
    >
      <MultiSelectPicker
        copy={pickerCopy("FFI Type", "FFI Types")}
        labels={FFI_TYPE_LABELS}
        onChange={(types) => setDraft({ ...draft, types })}
        options={FFI_TYPES}
        selected={draft.types}
      />
      <MultiSelectPicker
        copy={pickerCopy("Outcome", "Outcomes")}
        labels={FFI_OUTCOME_LABELS}
        onChange={(outcomes) => setDraft({ ...draft, outcomes })}
        options={FFI_OUTCOMES}
        selected={draft.outcomes}
      />
      {dateRanges}
      <MultiSelectPicker
        copy={pickerCopy("Higher Authority", "Higher Authorities")}
        labels={FFI_HIGHER_AUTHORITY_LABELS}
        onChange={(higherAuthorities) => setDraft({ ...draft, higherAuthorities })}
        options={FFI_HIGHER_AUTHORITIES}
        selected={draft.higherAuthorities}
      />
      <MultiSelectPicker
        copy={pickerCopy("Submitting MC", "Submitting MCs")}
        labels={FFI_SUBMITTING_MC_LABELS}
        onChange={(submittingMcs) => setDraft({ ...draft, submittingMcs })}
        options={FFI_SUBMITTING_MCS}
        selected={draft.submittingMcs}
      />
      <ChoiceToggle
        choices={STATUS_CHOICES}
        label="Status"
        onChange={(status) => setDraft({ ...draft, status })}
        value={draft.status}
      />
      <div className="flex justify-end gap-2">
        <Button onClick={() => onApply(NO_FFI_FILTERS)} size="sm" type="button" variant="ghost">
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
 * The FFI History's filters, behind a Filter button that says how many of the
 * filters applied narrow it. Nothing in the panel reaches a request until
 * Apply; an Apply or Reset that applies closes it, and a refused Apply leaves
 * it open on the range to correct.
 */
export function FfiFilterPopover({
  applied,
  onApply,
}: {
  applied: FfiFilters;
  onApply: (filters: FfiFilters) => void;
}) {
  return (
    <FilterPopover className="w-md max-w-screen" setCount={countFfiFilters(applied)}>
      {(close) => (
        <FfiFilterForm
          applied={applied}
          onApply={(filters) => {
            onApply(filters);
            close();
          }}
        />
      )}
    </FilterPopover>
  );
}
