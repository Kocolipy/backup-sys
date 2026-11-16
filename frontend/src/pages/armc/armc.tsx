import { Card, CardContent } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { orElse, shown } from "@/lib/source-field";
import { formatCalendarDateTwoDigitDay } from "@/lib/time";
import { FilteredHistory } from "@/pages/filtered-history";
import { ForPatientInContext } from "@/pages/for-patient-in-context";
import { type ColumnSorting, PlainHeading, SortHeading } from "@/pages/sort-heading";
import { badged, type BadgeVariant, labelled } from "@/pages/source-code";

import { ArmcFilterPopover } from "./armc-filter-popover";
import {
  ARMC_BOARD_TYPE_LABELS,
  ARMC_HISTORY,
  ARMC_HISTORY_PAGE_SIZE,
  ARMC_OUTCOME_LABELS,
  type ArmcBoard,
  type ArmcOutcome,
  type ArmcSortColumn,
  countArmcFilters,
  NO_ARMC_FILTERS,
} from "./armc-api";

/** Each ARMC Outcome's badge: FIT green, UNFIT red. A code outside the set is on a neutral one. */
const OUTCOME_BADGES: Readonly<Record<ArmcOutcome, BadgeVariant>> = {
  fit: "success",
  unfit: "danger",
};

/**
 * The page's ARMC Boards, one row each, in the order read, shown as the
 * patient source sent them (ADR 0020): the ARMC Board Type by its label, in
 * bold, the ARMC Outcome as a badge, both dates as `dd MMM yyyy` and both
 * Medical Officers' names as the source gives them; a code outside its closed
 * set as it came, the Outcome's on a neutral badge; a missing element as "—" —
 * the End Date of a board still open among them — and one in the wrong format
 * as "Error" (`shown`). Nothing checks a board's dates against each other.
 * Start Date and End Date sort, one at a time, held still while a read is
 * `pending`; no other column does. Read-only, with no row action; each row is
 * keyed by the board's id, which is never shown, or its place without one.
 */
function ArmcTable({
  boards,
  pending,
  sorting,
}: {
  boards: readonly ArmcBoard[];
  pending: boolean;
  sorting: ColumnSorting<ArmcSortColumn>;
}) {
  return (
    <Table>
      <TableCaption className="sr-only">ARMC History</TableCaption>
      <TableHeader>
        <TableRow>
          <PlainHeading>Board Type</PlainHeading>
          <PlainHeading>Outcome</PlainHeading>
          <SortHeading by="startDate" disabled={pending} label="Start Date" sorting={sorting} />
          <SortHeading by="endDate" disabled={pending} label="End Date" sorting={sorting} />
          <PlainHeading>Medical Officer #1</PlainHeading>
          <PlainHeading>Medical Officer #2</PlainHeading>
        </TableRow>
      </TableHeader>
      <TableBody>
        {boards.map((board, place) => (
          <TableRow key={orElse(board.id, place)}>
            <TableCell className="font-semibold">
              {shown(board.boardType, labelled(ARMC_BOARD_TYPE_LABELS))}
            </TableCell>
            <TableCell>
              {shown(board.outcome, badged(ARMC_OUTCOME_LABELS, OUTCOME_BADGES))}
            </TableCell>
            <TableCell className="tabular-nums">
              {shown(board.startDate, formatCalendarDateTwoDigitDay)}
            </TableCell>
            <TableCell className="tabular-nums">
              {shown(board.endDate, formatCalendarDateTwoDigitDay)}
            </TableCell>
            <TableCell>{shown(board.medicalOfficer1, (name) => name)}</TableCell>
            <TableCell>{shown(board.medicalOfficer2, (name) => name)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/**
 * The ARMC History of the patient `patientId`, five ARMC Boards a page,
 * through the shared filtered history ({@link FilteredHistory}), its rows
 * named records as every paged table's are, and "Couldn't load ARMC records"
 * for a failed read. It sorts by Start Date or End Date, and the
 * Filter panel narrows it by its Outcome and either date.
 */
function ArmcHistory({ patientId }: { patientId: string }) {
  return (
    <FilteredHistory
      copy={{ failure: "Couldn't load ARMC records", pagerLabel: "ARMC History pages" }}
      countFilters={countArmcFilters}
      declaration={ARMC_HISTORY}
      filterPopover={ArmcFilterPopover}
      noFilters={NO_ARMC_FILTERS}
      pageSize={ARMC_HISTORY_PAGE_SIZE}
      patientId={patientId}
      table={({ pending, rows, sorting }) => (
        <ArmcTable boards={rows} pending={pending} sorting={sorting} />
      )}
    />
  );
}

/**
 * ARMC: the ARMC History of the patient in context, read from the patient
 * source, and "Loading records…" while the patient is read
 * ({@link ForPatientInContext}). A new patient in context is a new history,
 * read from its first page.
 */
export function ArmcBoards() {
  return (
    <Card>
      <CardContent className="flex flex-col gap-4">
        <ForPatientInContext history={ArmcHistory} />
      </CardContent>
    </Card>
  );
}
