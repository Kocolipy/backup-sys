import { useState } from "react";

import { Button } from "@/components/ui/button";
import { ChoiceToggle } from "@/pages/choice-toggle";
import { DateRangeFields } from "@/pages/date-range-fields";
import { FilterPopover } from "@/pages/filter-popover";

import {
  checkPatientFilters,
  countPatientFilters,
  NO_PATIENT_FILTERS,
  PATIENT_DATE_RANGES,
  type CheckedPatientFilters,
  type PatientFilters,
} from "./search-api";

const GENDER_CHOICES: readonly { label: string; value: PatientFilters["gender"] }[] = [
  { label: "All", value: "all" },
  { label: "Male", value: "male" },
  { label: "Female", value: "female" },
];

/** A range Apply refused, and why. */
type Refusal = Extract<CheckedPatientFilters, { ok: false }>;

/**
 * The panel's form, mounted each time the panel opens, so it starts from the
 * filters applied and with no refusal, and an edit never applied is forgotten
 * when it closes. Apply — which Enter in a date also asks for — first checks
 * the filters as the backend would ({@link checkPatientFilters}): a refused
 * range is marked beside it and nothing is applied. Reset applies no filter.
 */
function PatientFilterForm({
  applied,
  onApply,
}: {
  applied: PatientFilters;
  onApply: (filters: PatientFilters) => void;
}) {
  const [draft, setDraft] = useState(applied);
  const [refusal, setRefusal] = useState<Refusal | null>(null);
  return (
    <form
      aria-label="Patient filters"
      className="flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        const checked = checkPatientFilters(draft);
        if (checked.ok) onApply(draft);
        else setRefusal(checked);
      }}
    >
      <ChoiceToggle
        choices={GENDER_CHOICES}
        label="Gender"
        onChange={(gender) => setDraft({ ...draft, gender })}
        value={draft.gender}
      />
      {PATIENT_DATE_RANGES.map(({ label, range }) => (
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
      ))}
      <div className="flex justify-end gap-2">
        <Button onClick={() => onApply(NO_PATIENT_FILTERS)} size="sm" type="button" variant="ghost">
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
 * The Search page's filters, behind a Filter button that says how many of the
 * filters applied narrow the results. Nothing in the panel reaches a request
 * until Apply; an Apply or Reset that applies closes it, and a refused Apply
 * leaves it open on the range to correct.
 */
export function PatientFilterPopover({
  applied,
  onApply,
}: {
  applied: PatientFilters;
  onApply: (filters: PatientFilters) => void;
}) {
  return (
    <FilterPopover className="w-md max-w-screen" setCount={countPatientFilters(applied)}>
      {(close) => (
        <PatientFilterForm
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
