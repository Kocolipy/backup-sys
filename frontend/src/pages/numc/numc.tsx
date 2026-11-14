import { useState } from "react";

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
import { ForPatientInContext, NoPatientInContext } from "@/pages/for-patient-in-context";
import type { IssueOrExpirySort, IssueOrExpiryColumn } from "@/pages/history-api";
import { PagedListing } from "@/pages/paged-listing";
import { usePagedRead } from "@/pages/paged-read";
import { type ColumnSorting, PlainHeading, SortHeading } from "@/pages/sort-heading";

import { NumcFilterPopover } from "./numc-filter-popover";
import {
  countNumcFilters,
  NO_NUMC_FILTERS,
  NUMC_HISTORY,
  NUMC_HISTORY_PAGE_SIZE,
  NUMC_OUTCOME_LABELS,
  NUMC_TYPE_LABELS,
  type NumcFilters,
  type NumcOutcome,
  type NumcRecord,
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
  sorting: ColumnSorting<IssueOrExpiryColumn>;
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
 * through the shared paged listing: the numbered pager and "Showing x to y of
 * N NUMC records", the page shown held while the next loads. A `404` — the
 * source holds no patient under the id — is "No patient in context", an
 * answer rather than a failure; any other failure is "Couldn't load NUMC
 * records" on the page's one error line, with Try again.
 *
 * It sorts by Issue Date or Expiry Date, in the source's own order until a
 * heading is pressed, and the Filter panel at its top right narrows it on
 * every column. A change of either reads the history from its first page,
 * the filters combined with AND and with the sort. Both live here alone:
 * neither reaches the URL, and a new patient in context, a new history,
 * starts with neither. With a filter applied, a history with no NUMC Record
 * says none matches, rather than that the patient has none.
 */
function NumcHistory({ patientId }: { patientId: string }) {
  const [filters, setFilters] = useState<NumcFilters>(NO_NUMC_FILTERS);
  const [sort, setSort] = useState<IssueOrExpirySort | null>(null);
  const reading = usePagedRead(NUMC_HISTORY, {
    args: [patientId, filters, sort],
    failureMessage: "Couldn't load NUMC records",
    pageSize: NUMC_HISTORY_PAGE_SIZE,
  });
  const { view } = reading;
  if (view.kind === "absent") return <NoPatientInContext />;
  // Rows are shown only once a page is read, so a read in flight beside them
  // is one the listing holds them still for.
  const pending = view.kind === "loading";
  return (
    <>
      <div className="flex justify-end">
        <NumcFilterPopover applied={filters} onApply={setFilters} />
      </div>
      <PagedListing
        copy={{
          empty:
            countNumcFilters(filters) === 0
              ? "No NUMC records"
              : "No NUMC records match the filters",
          loading: "Loading NUMC records…",
          noun: "NUMC records",
          pagerLabel: "NUMC History pages",
        }}
        onePage="shown"
        read={{ ...reading, view }}
      >
        {({ rows }) => (
          <NumcTable pending={pending} records={rows} sorting={{ onSort: setSort, sort }} />
        )}
      </PagedListing>
    </>
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
