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

import { NumcFilterPopover } from "./numc-filter-popover";
import {
  countNumcFilters,
  NO_NUMC_FILTERS,
  NUMC_HISTORY,
  NUMC_HISTORY_PAGE_SIZE,
  NUMC_OUTCOME_LABELS,
  NUMC_TYPE_LABELS,
  type NumcOutcome,
  type NumcRecord,
  type NumcSortColumn,
} from "./numc-api";

/**
 * Each NUMC Outcome's badge: FIT green, UNFIT red, WAIVER GRANTED blue and
 * WAIVER REJECTED muted.
 */
const OUTCOME_BADGES: Readonly<Record<NumcOutcome, "success" | "danger" | "info" | "muted">> = {
  fit: "success",
  unfit: "danger",
  "waiver-granted": "info",
  "waiver-rejected": "muted",
};

/**
 * The page's NUMC Records, one row each, in the order read: the NUMC Type by
 * its label, the NUMC Outcome as a badge, and both dates as `dd MMM yyyy`.
 * Issue Date and Expiry Date sort, one at a time, held still while a read is
 * `pending`; the Type and the Outcome do not. Read-only, with no row action;
 * each row is keyed by the record's id, which is never shown. Nothing here
 * works out whether a record has expired.
 */
function NumcTable({
  pending,
  records,
  sorting,
}: {
  pending: boolean;
  records: readonly NumcRecord[];
  sorting: ColumnSorting<NumcSortColumn>;
}) {
  return (
    <Table>
      <TableCaption className="sr-only">NUMC History</TableCaption>
      <TableHeader>
        <TableRow>
          <PlainHeading>Type</PlainHeading>
          <PlainHeading>Outcome</PlainHeading>
          <SortHeading by="issueDate" disabled={pending} label="Issue Date" sorting={sorting} />
          <SortHeading by="expiryDate" disabled={pending} label="Expiry Date" sorting={sorting} />
        </TableRow>
      </TableHeader>
      <TableBody>
        {records.map((record) => (
          <TableRow key={record.id}>
            <TableCell>{NUMC_TYPE_LABELS[record.type]}</TableCell>
            <TableCell>
              <Badge variant={OUTCOME_BADGES[record.outcome]}>
                {NUMC_OUTCOME_LABELS[record.outcome]}
              </Badge>
            </TableCell>
            <TableCell className="tabular-nums">
              {formatCalendarDateTwoDigitDay(record.issueDate)}
            </TableCell>
            <TableCell className="tabular-nums">
              {formatCalendarDateTwoDigitDay(record.expiryDate)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/**
 * The NUMC History of the patient `patientId`, five NUMC Records a page,
 * through the shared filtered history ({@link FilteredHistory}): "Showing x
 * to y of N NUMC records", "Couldn't load NUMC records" for a failed read,
 * and "No NUMC records", or with a filter applied "No NUMC records match the
 * filters", for an empty one. It sorts by Issue Date or Expiry Date, and the
 * Filter panel narrows it on every column.
 */
function NumcHistory({ patientId }: { patientId: string }) {
  return (
    <FilteredHistory
      copy={{
        empty: "No NUMC records",
        emptyFiltered: "No NUMC records match the filters",
        failure: "Couldn't load NUMC records",
        loading: "Loading NUMC records…",
        noun: "NUMC records",
        pagerLabel: "NUMC History pages",
      }}
      countFilters={countNumcFilters}
      declaration={NUMC_HISTORY}
      filterPopover={NumcFilterPopover}
      noFilters={NO_NUMC_FILTERS}
      pageSize={NUMC_HISTORY_PAGE_SIZE}
      patientId={patientId}
      table={({ pending, rows, sorting }) => (
        <NumcTable pending={pending} records={rows} sorting={sorting} />
      )}
    />
  );
}

/**
 * NUMC: the NUMC History of the patient in context, read from the patient
 * source, and "Loading NUMC records…" while the patient is read
 * ({@link ForPatientInContext}). A new patient in context is a new history,
 * read from its first page.
 */
export function NumcRecords() {
  return (
    <Card>
      <CardContent className="flex flex-col gap-4">
        <ForPatientInContext history={NumcHistory} loading="Loading NUMC records…" />
      </CardContent>
    </Card>
  );
}
