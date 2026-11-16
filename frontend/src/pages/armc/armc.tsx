import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatCalendarDateTwoDigitDay } from "@/lib/time";
import { FilteredHistory } from "@/pages/filtered-history";
import { ForPatientInContext } from "@/pages/for-patient-in-context";
import { type ColumnSorting, PlainHeading, SortHeading } from "@/pages/sort-heading";

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

/** Each ARMC Outcome's badge: FIT green, UNFIT red. */
const OUTCOME_BADGES: Readonly<Record<ArmcOutcome, "success" | "danger">> = {
  fit: "success",
  unfit: "danger",
};

/**
 * The page's ARMC Boards, one row each, in the order read: the ARMC Board
 * Type by its label, in bold, the ARMC Outcome as a badge, both dates as
 * `dd MMM yyyy` — `—` for the End Date of a board still open — and both
 * Medical Officers' names as the source gives them. Start Date and End Date
 * sort, one at a time, held still while a read is `pending`; no other column
 * does. Read-only, with no row action; each row is keyed by the board's id,
 * which is never shown.
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
        {boards.map((board) => (
          <TableRow key={board.id}>
            <TableCell className="font-semibold">
              {ARMC_BOARD_TYPE_LABELS[board.boardType]}
            </TableCell>
            <TableCell>
              <Badge variant={OUTCOME_BADGES[board.outcome]}>
                {ARMC_OUTCOME_LABELS[board.outcome]}
              </Badge>
            </TableCell>
            <TableCell className="tabular-nums">
              {formatCalendarDateTwoDigitDay(board.startDate)}
            </TableCell>
            <TableCell className="tabular-nums">
              {board.endDate === undefined ? "—" : formatCalendarDateTwoDigitDay(board.endDate)}
            </TableCell>
            <TableCell>{board.medicalOfficer1}</TableCell>
            <TableCell>{board.medicalOfficer2}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/**
 * The ARMC History of the patient `patientId`, five ARMC Boards a page,
 * through the shared filtered history ({@link FilteredHistory}): "Showing x
 * to y of N ARMC records", "Couldn't load ARMC records" for a failed read,
 * and "No ARMC records", or with a filter applied "No ARMC records match the
 * filters", for an empty one. It sorts by Start Date or End Date, and the
 * Filter panel narrows it by its Outcome and either date.
 */
function ArmcHistory({ patientId }: { patientId: string }) {
  return (
    <FilteredHistory
      copy={{
        empty: "No ARMC records",
        emptyFiltered: "No ARMC records match the filters",
        failure: "Couldn't load ARMC records",
        loading: "Loading ARMC records…",
        noun: "ARMC records",
        pagerLabel: "ARMC History pages",
      }}
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
 * source, and "Loading ARMC records…" while the patient is read
 * ({@link ForPatientInContext}). A new patient in context is a new history,
 * read from its first page.
 */
export function ArmcBoards() {
  return (
    <Card>
      <CardContent className="flex flex-col gap-4">
        <ForPatientInContext history={ArmcHistory} loading="Loading ARMC records…" />
      </CardContent>
    </Card>
  );
}
