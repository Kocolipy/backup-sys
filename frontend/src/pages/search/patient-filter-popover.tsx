import { useId, useState } from "react";
import { ListFilterIcon } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";

import {
  checkPatientFilters,
  countPatientFilters,
  NO_PATIENT_FILTERS,
  PATIENT_DATE_RANGES,
  type CheckedPatientFilters,
  type PatientDateRange,
  type PatientFilters,
} from "./search-api";

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
 * One date range's from–to pair of `<input type="date">`, a group named by its
 * label. A refusal of the range marks both ends invalid and describes them by
 * the message shown beneath.
 */
function DateRangeFields({
  draft,
  label,
  onDraft,
  range,
  refusal,
}: {
  draft: PatientFilters;
  label: string;
  onDraft: (filters: PatientFilters) => void;
  range: PatientDateRange;
  refusal: string | null;
}) {
  const labelId = useId();
  const refusalId = useId();
  const field = (end: "From" | "To") => {
    const key = `${range}${end}` as const;
    return (
      <Input
        aria-describedby={refusal === null ? undefined : refusalId}
        aria-invalid={refusal !== null}
        aria-label={end}
        onChange={(event) => onDraft({ ...draft, [key]: event.target.value })}
        type="date"
        value={draft[key]}
      />
    );
  };
  return (
    <div aria-labelledby={labelId} className="flex flex-col gap-1" role="group">
      <Label id={labelId}>{label}</Label>
      <div className="flex items-center gap-2">
        {field("From")}
        <span aria-hidden="true" className="text-muted-foreground">
          –
        </span>
        {field("To")}
      </div>
      {refusal === null ? null : (
        <p className="text-sm text-destructive" id={refusalId}>
          {refusal}
        </p>
      )}
    </div>
  );
}

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
      <GenderToggle onChange={(gender) => setDraft({ ...draft, gender })} value={draft.gender} />
      {PATIENT_DATE_RANGES.map(({ label, range }) => (
        <DateRangeFields
          draft={draft}
          key={range}
          label={label}
          onDraft={setDraft}
          range={range}
          refusal={refusal?.range === range ? refusal.message : null}
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
