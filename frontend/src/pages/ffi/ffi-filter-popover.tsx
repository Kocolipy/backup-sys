import { ChoiceToggle } from "@/pages/choice-toggle";
import { HistoryFilterPopover, pickerCopy } from "@/pages/history-filter-popover";
import { MEDICAL_CENTRE_LABELS, MEDICAL_CENTRES } from "@/pages/medical-centre-api";
import { MultiSelectPicker } from "@/pages/multi-select-picker";

import {
  countFfiFilters,
  FFI_HIGHER_AUTHORITIES,
  FFI_HIGHER_AUTHORITY_LABELS,
  FFI_OUTCOME_LABELS,
  FFI_OUTCOMES,
  FFI_STATUS_LABELS,
  FFI_TYPE_LABELS,
  FFI_TYPES,
  NO_FFI_FILTERS,
  type FfiFilters,
} from "./ffi-api";

const STATUS_CHOICES: readonly { label: string; value: FfiFilters["status"] }[] = [
  { label: "All", value: "all" },
  { label: FFI_STATUS_LABELS.active, value: "active" },
  { label: FFI_STATUS_LABELS.inactive, value: "inactive" },
];

/**
 * The FFI History's filters, behind its Filter button
 * ({@link HistoryFilterPopover}). The panel offers a filter on every column:
 * a multi-select for each closed set but the Status, listing it by label in
 * its own order; a from–to range for each date; and the Status.
 */
export function FfiFilterPopover({
  applied,
  onApply,
}: {
  applied: FfiFilters;
  onApply: (filters: FfiFilters) => void;
}) {
  return (
    <HistoryFilterPopover
      applied={applied}
      count={countFfiFilters}
      label="FFI filters"
      none={NO_FFI_FILTERS}
      onApply={onApply}
    >
      {({ dateRanges, draft, edit }) => (
        <>
          <MultiSelectPicker
            copy={pickerCopy("FFI Type", "FFI Types")}
            labels={FFI_TYPE_LABELS}
            onChange={(types) => edit({ types })}
            options={FFI_TYPES}
            selected={draft.types}
          />
          <MultiSelectPicker
            copy={pickerCopy("Outcome", "Outcomes")}
            labels={FFI_OUTCOME_LABELS}
            onChange={(outcomes) => edit({ outcomes })}
            options={FFI_OUTCOMES}
            selected={draft.outcomes}
          />
          {dateRanges}
          <MultiSelectPicker
            copy={pickerCopy("Higher Authority", "Higher Authorities")}
            labels={FFI_HIGHER_AUTHORITY_LABELS}
            onChange={(higherAuthorities) => edit({ higherAuthorities })}
            options={FFI_HIGHER_AUTHORITIES}
            selected={draft.higherAuthorities}
          />
          <MultiSelectPicker
            copy={pickerCopy("Submitting MC", "Submitting MCs")}
            labels={MEDICAL_CENTRE_LABELS}
            onChange={(submittingMcs) => edit({ submittingMcs })}
            options={MEDICAL_CENTRES}
            selected={draft.submittingMcs}
          />
          <ChoiceToggle
            choices={STATUS_CHOICES}
            label="Status"
            onChange={(status) => edit({ status })}
            value={draft.status}
          />
        </>
      )}
    </HistoryFilterPopover>
  );
}
