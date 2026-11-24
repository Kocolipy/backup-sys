import { ChoiceToggle } from "@/pages/choice-toggle";
import { HistoryFilterPopover, pickerCopy } from "@/pages/history-filter-popover";
import { MEDICAL_CENTRE_LABELS, MEDICAL_CENTRES } from "@/pages/medical-centre-api";
import { MultiSelectPicker } from "@/pages/multi-select-picker";

import {
  countHspFilters,
  HSP_DATE_COLUMNS,
  HSP_OUTCOME_LABELS,
  HSP_OUTCOMES,
  HSP_STATUS_LABELS,
  HSP_STATUSES,
  NO_HSP_FILTERS,
  type HspFilters,
} from "./hsp-api";

/** All, then each Status of {@link HSP_STATUSES} by its label, so a new Status is offered too. */
const STATUS_CHOICES: readonly { label: string; value: HspFilters["status"] }[] = [
  { label: "All", value: "all" },
  ...HSP_STATUSES.map((status) => ({ label: HSP_STATUS_LABELS[status], value: status })),
];

/**
 * The HSP History's filters, behind its Filter button
 * ({@link HistoryFilterPopover}). The panel offers a filter on every column:
 * a multi-select for the Outcome and for the Submitting MC, each listing its
 * closed set by label in its own order; a from–to range for each date; and
 * the Status.
 */
export function HspFilterPopover({
  applied,
  onApply,
}: {
  applied: HspFilters;
  onApply: (filters: HspFilters) => void;
}) {
  return (
    <HistoryFilterPopover
      applied={applied}
      count={countHspFilters}
      label="HSP filters"
      none={NO_HSP_FILTERS}
      onApply={onApply}
      columns={HSP_DATE_COLUMNS}
    >
      {({ dateRanges, draft, edit }) => (
        <>
          <MultiSelectPicker
            copy={pickerCopy("Outcome", "Outcomes")}
            labels={HSP_OUTCOME_LABELS}
            onChange={(outcomes) => edit({ outcomes })}
            options={HSP_OUTCOMES}
            selected={draft.outcomes}
          />
          {dateRanges}
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
