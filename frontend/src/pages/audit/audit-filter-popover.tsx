import { useId, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ChoiceToggle } from "@/pages/choice-toggle";
import { FilterPopover } from "@/pages/filter-popover";
import { MultiSelectPicker } from "@/pages/multi-select-picker";

import {
  AUDIT_OPERATIONS,
  EMPTY_AUDIT_FILTERS,
  parseAuditFilters,
  type AuditFilters,
  type AuditQuery,
  type AuditRefusedField,
  type ParsedAuditFilters,
} from "./audit-api";
import { AUDIT_OPERATION_LABELS } from "./audit-operation-labels";

/** The operation picker's copy: the audit operations, every one by its label. */
const OPERATION_COPY = {
  list: "Operations",
  name: "Operation",
  noMatch: "No operation matches.",
  search: "Search operations",
};

const OUTCOME_CHOICES: readonly { label: string; value: AuditFilters["outcome"] }[] = [
  { label: "Any", value: "ANY" },
  { label: "Success", value: "SUCCESS" },
  { label: "Failure", value: "FAILURE" },
];

const ID_FILTERS = [
  { key: "actorId", label: "Actor id" },
  { key: "resourceId", label: "Resource id" },
] as const;

type TypedFilter = "actorId" | "resourceId" | "from" | "to";

/** A filter set Apply refused, and why. */
type Refusal = Extract<ParsedAuditFilters, { ok: false }>;

/**
 * How many of `filters` narrow the listing: the operations, as one, the
 * outcome, and each typed field that is set.
 */
function countSet(filters: AuditFilters): number {
  return [
    filters.operations.length > 0,
    filters.outcome !== "ANY",
    filters.actorId !== "",
    filters.resourceId !== "",
    filters.from !== "",
    filters.to !== "",
  ].filter(Boolean).length;
}

/**
 * The panel's form, mounted each time the panel opens, so it starts from the
 * filters applied and with no refusal, and an edit never applied is forgotten
 * when it closes. No control reaches a request until Apply — which Enter in a
 * typed field also asks for — and Apply first parses the whole set
 * ({@link parseAuditFilters}): a refusal is marked beside the field it is
 * about, an id field or the Occurred range, and nothing is applied. Reset
 * applies no filter.
 */
function AuditFilterForm({
  applied,
  onApply,
}: {
  applied: AuditFilters;
  onApply: (filters: AuditFilters, query: AuditQuery) => void;
}) {
  const baseId = useId();
  const [draft, setDraft] = useState(applied);
  const [refusal, setRefusal] = useState<Refusal | null>(null);
  const refusalId = (field: AuditRefusedField) => `${baseId}-${field}-refusal`;
  const refusalOf = (field: AuditRefusedField) =>
    refusal?.field === field ? refusal.message : null;
  const parseAndApply = (filters: AuditFilters) => {
    const parsed = parseAuditFilters(filters);
    if (parsed.ok) onApply(filters, parsed.query);
    else setRefusal(parsed);
  };
  /** A refusal's message beneath the field it is about, the id its field is described by. */
  const refusalNote = (field: AuditRefusedField) => {
    const message = refusalOf(field);
    return message === null ? null : (
      <p className="text-sm text-destructive" id={refusalId(field)}>
        {message}
      </p>
    );
  };
  /**
   * A typed field's input: named by a `<Label>` of its own, or by `ariaLabel`
   * inside a labelled group. A refusal of `field` marks it invalid and
   * describes it by the message shown beneath.
   */
  const typedField = (
    key: TypedFilter,
    field: AuditRefusedField,
    type: string,
    ariaLabel?: string,
  ) => {
    const refused = refusalOf(field) !== null;
    return (
      <Input
        aria-describedby={refused ? refusalId(field) : undefined}
        aria-invalid={refused}
        aria-label={ariaLabel}
        id={`${baseId}-${key}`}
        onChange={(event) => setDraft({ ...draft, [key]: event.target.value })}
        type={type}
        value={draft[key]}
      />
    );
  };
  return (
    <form
      aria-label="Audit filters"
      className="flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        parseAndApply(draft);
      }}
    >
      <MultiSelectPicker
        copy={OPERATION_COPY}
        labels={AUDIT_OPERATION_LABELS}
        onChange={(operations) => setDraft({ ...draft, operations })}
        options={AUDIT_OPERATIONS}
        selected={draft.operations}
      />
      <ChoiceToggle
        choices={OUTCOME_CHOICES}
        label="Outcome"
        onChange={(outcome) => setDraft({ ...draft, outcome })}
        value={draft.outcome}
      />
      {ID_FILTERS.map(({ key, label }) => (
        <div className="flex flex-col gap-1" key={key}>
          <Label htmlFor={`${baseId}-${key}`}>{label}</Label>
          {typedField(key, key, "text")}
          {refusalNote(key)}
        </div>
      ))}
      <div aria-labelledby={`${baseId}-occurred`} className="flex flex-col gap-1" role="group">
        <Label id={`${baseId}-occurred`}>Occurred</Label>
        <div className="flex items-center gap-2">
          {typedField("from", "occurred", "datetime-local", "From")}
          <span aria-hidden="true" className="text-muted-foreground">
            –
          </span>
          {typedField("to", "occurred", "datetime-local", "To")}
        </div>
        {refusalNote("occurred")}
      </div>
      <div className="flex justify-end gap-2">
        <Button
          onClick={() => parseAndApply(EMPTY_AUDIT_FILTERS)}
          size="sm"
          type="button"
          variant="ghost"
        >
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
 * The Audit page's filters, behind a Filter button that says how many of the
 * filters applied narrow the listing. Nothing in the panel reaches a request
 * until Apply; an Apply or Reset that applies closes it, and a refused Apply
 * leaves it open on the field to correct.
 */
export function AuditFilterPopover({
  applied,
  onApply,
}: {
  applied: AuditFilters;
  onApply: (filters: AuditFilters, query: AuditQuery) => void;
}) {
  return (
    <FilterPopover className="w-xl max-w-screen" setCount={countSet(applied)}>
      {(close) => (
        <AuditFilterForm
          applied={applied}
          onApply={(filters, query) => {
            onApply(filters, query);
            close();
          }}
        />
      )}
    </FilterPopover>
  );
}
