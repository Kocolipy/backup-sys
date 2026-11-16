import { useState } from "react";

import { Button } from "@/components/ui/button";
import { ChoiceToggle } from "@/pages/choice-toggle";
import { FilterPopover } from "@/pages/filter-popover";

import { countPatientFilters, NO_PATIENT_FILTERS, type PatientFilters } from "./search-api";

const GENDER_CHOICES: readonly { label: string; value: PatientFilters["gender"] }[] = [
  { label: "All", value: "all" },
  { label: "Male", value: "male" },
  { label: "Female", value: "female" },
];

/**
 * The panel's form, mounted each time the panel opens, so it starts from the
 * filter applied, and an edit never applied is forgotten when it closes. Apply
 * applies the gender chosen; Reset applies no filter.
 */
function PatientFilterForm({
  applied,
  onApply,
}: {
  applied: PatientFilters;
  onApply: (filters: PatientFilters) => void;
}) {
  const [draft, setDraft] = useState(applied);
  return (
    <form
      aria-label="Patient filters"
      className="flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        onApply(draft);
      }}
    >
      <ChoiceToggle
        choices={GENDER_CHOICES}
        label="Gender"
        onChange={(gender) => setDraft({ ...draft, gender })}
        value={draft.gender}
      />
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
 * The Search page's filter, behind a Filter button that says how many of the
 * filters applied narrow the results. Nothing in the panel reaches a request
 * until Apply; an Apply or Reset closes it.
 */
export function PatientFilterPopover({
  applied,
  onApply,
}: {
  applied: PatientFilters;
  onApply: (filters: PatientFilters) => void;
}) {
  return (
    <FilterPopover className="w-64 max-w-screen" setCount={countPatientFilters(applied)}>
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
