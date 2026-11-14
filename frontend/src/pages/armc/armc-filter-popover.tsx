import { ChoiceToggle } from "@/pages/choice-toggle";
import { HistoryFilterPopover } from "@/pages/history-filter-popover";

import {
  ARMC_DATE_COLUMNS,
  ARMC_OUTCOME_LABELS,
  ARMC_OUTCOMES,
  type ArmcFilters,
  countArmcFilters,
  NO_ARMC_FILTERS,
} from "./armc-api";

/** The Outcome's choices: All, which is no filter, then each ARMC Outcome by its label, in its set's order. */
const OUTCOME_CHOICES: readonly { label: string; value: ArmcFilters["outcome"] }[] = [
  { label: "All", value: "all" },
  ...ARMC_OUTCOMES.map((outcome) => ({ label: ARMC_OUTCOME_LABELS[outcome], value: outcome })),
];

/**
 * The ARMC History's filters, behind its Filter button
 * ({@link HistoryFilterPopover}): the ARMC Outcome as an All / FIT / UNFIT
 * toggle, one at a time, as the Search page's Gender filter is, and a from–to
 * range for each date. The ARMC Board Type takes no filter while ACES is its
 * one code.
 */
export function ArmcFilterPopover({
  applied,
  onApply,
}: {
  applied: ArmcFilters;
  onApply: (filters: ArmcFilters) => void;
}) {
  return (
    <HistoryFilterPopover
      applied={applied}
      count={countArmcFilters}
      label="ARMC filters"
      none={NO_ARMC_FILTERS}
      onApply={onApply}
      columns={ARMC_DATE_COLUMNS}
    >
      {({ dateRanges, draft, edit }) => (
        <>
          <ChoiceToggle
            choices={OUTCOME_CHOICES}
            label="Outcome"
            onChange={(outcome) => edit({ outcome })}
            value={draft.outcome}
          />
          {dateRanges}
        </>
      )}
    </HistoryFilterPopover>
  );
}
