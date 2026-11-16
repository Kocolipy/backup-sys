import { HistoryFilterPopover, pickerCopy } from "@/pages/history-filter-popover";
import { MultiSelectPicker } from "@/pages/multi-select-picker";

import {
  countMedicationFilters,
  DISPENSE_STATUS_LABELS,
  DISPENSE_STATUSES,
  MEDICATION_DATE_COLUMNS,
  type MedicationFilters,
  NO_MEDICATION_FILTERS,
} from "./medication-api";

/**
 * The Medication History's filters, behind its Filter button
 * ({@link HistoryFilterPopover}): a multi-select of the Dispense Status,
 * listing it by label in its own order, and a from–to range of date-times for
 * the Start Date and for the End Date. The Dispensary and the free text take
 * no filter.
 */
export function MedicationFilterPopover({
  applied,
  onApply,
}: {
  applied: MedicationFilters;
  onApply: (filters: MedicationFilters) => void;
}) {
  return (
    <HistoryFilterPopover
      applied={applied}
      columns={MEDICATION_DATE_COLUMNS}
      count={countMedicationFilters}
      label="Medication filters"
      none={NO_MEDICATION_FILTERS}
      onApply={onApply}
    >
      {({ dateRanges, draft, edit }) => (
        <>
          <MultiSelectPicker
            copy={pickerCopy("Dispense Status", "Dispense Statuses")}
            labels={DISPENSE_STATUS_LABELS}
            onChange={(dispenseStatuses) => edit({ dispenseStatuses })}
            options={DISPENSE_STATUSES}
            selected={draft.dispenseStatuses}
          />
          {dateRanges}
        </>
      )}
    </HistoryFilterPopover>
  );
}
