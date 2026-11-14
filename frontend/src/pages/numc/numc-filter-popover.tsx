import { HistoryFilterPopover, pickerCopy } from "@/pages/history-filter-popover";
import { MultiSelectPicker } from "@/pages/multi-select-picker";

import {
  countNumcFilters,
  NO_NUMC_FILTERS,
  NUMC_OUTCOME_LABELS,
  NUMC_OUTCOMES,
  NUMC_TYPE_LABELS,
  NUMC_TYPES,
  type NumcFilters,
} from "./numc-api";

/**
 * The NUMC History's filters, behind its Filter button
 * ({@link HistoryFilterPopover}). The panel offers a filter on every column:
 * a multi-select for each closed set, the NUMC Type and the NUMC Outcome,
 * listing it by label in its own order, and a from–to range for each date.
 */
export function NumcFilterPopover({
  applied,
  onApply,
}: {
  applied: NumcFilters;
  onApply: (filters: NumcFilters) => void;
}) {
  return (
    <HistoryFilterPopover
      applied={applied}
      count={countNumcFilters}
      label="NUMC filters"
      none={NO_NUMC_FILTERS}
      onApply={onApply}
    >
      {({ dateRanges, draft, edit }) => (
        <>
          <MultiSelectPicker
            copy={pickerCopy("Type", "Types")}
            labels={NUMC_TYPE_LABELS}
            onChange={(types) => edit({ types })}
            options={NUMC_TYPES}
            selected={draft.types}
          />
          <MultiSelectPicker
            copy={pickerCopy("Outcome", "Outcomes")}
            labels={NUMC_OUTCOME_LABELS}
            onChange={(outcomes) => edit({ outcomes })}
            options={NUMC_OUTCOMES}
            selected={draft.outcomes}
          />
          {dateRanges}
        </>
      )}
    </HistoryFilterPopover>
  );
}
