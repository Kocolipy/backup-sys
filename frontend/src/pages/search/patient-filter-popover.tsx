import { useId, useState } from "react";
import { ListFilterIcon } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";

import { countPatientFilters, NO_PATIENT_FILTERS, type PatientFilters } from "./search-api";

const GENDER_CHOICES: readonly { label: string; value: PatientFilters["gender"] }[] = [
  { label: "All", value: "all" },
  { label: "Male", value: "male" },
  { label: "Female", value: "female" },
];

function GenderToggle({
  onChange,
  value,
}: {
  onChange: (next: PatientFilters["gender"]) => void;
  value: PatientFilters["gender"];
}) {
  const labelId = useId();
  return (
    <div className="flex flex-col gap-1">
      <Label id={labelId}>Gender</Label>
      <ToggleGroup
        aria-labelledby={labelId}
        onValueChange={(next) => {
          // Pressing the position already chosen reports "": no position, and no change.
          if (next !== "") onChange(next as PatientFilters["gender"]);
        }}
        size="sm"
        type="single"
        value={value}
        variant="outline"
      >
        {GENDER_CHOICES.map((choice) => (
          <ToggleGroupItem key={choice.value} value={choice.value}>
            {choice.label}
          </ToggleGroupItem>
        ))}
      </ToggleGroup>
    </div>
  );
}

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
      <GenderToggle onChange={(gender) => setDraft({ ...draft, gender })} value={draft.gender} />
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
  const [open, setOpen] = useState(false);
  const set = countPatientFilters(applied);
  return (
    <Popover onOpenChange={setOpen} open={open}>
      <PopoverTrigger asChild>
        <Button
          aria-label={set === 0 ? "Filter" : `Filter, ${set} set`}
          size="sm"
          variant="outline"
        >
          <ListFilterIcon aria-hidden="true" />
          Filter
          {set === 0 ? null : (
            <Badge aria-hidden="true" variant="secondary">
              {set}
            </Badge>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-md max-w-screen">
        <PatientFilterForm
          applied={applied}
          onApply={(filters) => {
            onApply(filters);
            setOpen(false);
          }}
        />
      </PopoverContent>
    </Popover>
  );
}
