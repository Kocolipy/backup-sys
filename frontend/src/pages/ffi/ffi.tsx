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
import { MEDICAL_CENTRE_LABELS } from "@/pages/medical-centre-api";
import { type ColumnSorting, PlainHeading, SortHeading } from "@/pages/sort-heading";

import { FfiFilterPopover } from "./ffi-filter-popover";
import {
  countFfiFilters,
  FFI_HIGHER_AUTHORITY_LABELS,
  FFI_HISTORY,
  FFI_HISTORY_PAGE_SIZE,
  FFI_OUTCOME_LABELS,
  FFI_STATUS_LABELS,
  FFI_TYPE_LABELS,
  NO_FFI_FILTERS,
  type Ffi,
  type FfiOutcome,
  type FfiSortColumn,
  type FfiStatus,
} from "./ffi-api";

/** Each Outcome's badge: FIT green, REQUEST WAIVER amber, UNFIT red. */
const OUTCOME_BADGES: Readonly<Record<FfiOutcome, "success" | "warning" | "danger">> = {
  fit: "success",
  "request-waiver": "warning",
  unfit: "danger",
};

/** Each Status's badge: Active green, Inactive muted. */
const STATUS_BADGES: Readonly<Record<FfiStatus, "success" | "muted">> = {
  active: "success",
  inactive: "muted",
};

/** How the history is sorted, by the listing that holds the sort. */
type HistorySorting = ColumnSorting<FfiSortColumn>;

/**
 * The page's FFIs, one row each, in the order read: each closed set's code
 * by its label, the Outcome and the Status as badges, and both dates as
 * `dd MMM yyyy`. Issue Date and Expiry Date sort, one at a time, held still
 * while a read is `pending`; every other column does not. Read-only, with no
 * row action; each row is keyed by the FFI's id, which is never shown. The
 * Status is the source's, shown as sent: nothing here works out whether an FFI
 * has expired.
 */
function FfiTable({
  ffis,
  pending,
  sorting,
}: {
  ffis: readonly Ffi[];
  pending: boolean;
  sorting: HistorySorting;
}) {
  return (
    <Table>
      <TableCaption className="sr-only">FFI History</TableCaption>
      <TableHeader>
        <TableRow>
          <PlainHeading>FFI Type</PlainHeading>
          <PlainHeading>Outcome</PlainHeading>
          <SortHeading by="issueDate" disabled={pending} label="Issue Date" sorting={sorting} />
          <SortHeading by="expiryDate" disabled={pending} label="Expiry Date" sorting={sorting} />
          <PlainHeading>Higher Authority</PlainHeading>
          <PlainHeading>Submitting MC</PlainHeading>
          <PlainHeading>Status</PlainHeading>
        </TableRow>
      </TableHeader>
      <TableBody>
        {ffis.map((ffi) => (
          <TableRow key={ffi.id}>
            <TableCell>{FFI_TYPE_LABELS[ffi.type]}</TableCell>
            <TableCell>
              <Badge variant={OUTCOME_BADGES[ffi.outcome]}>{FFI_OUTCOME_LABELS[ffi.outcome]}</Badge>
            </TableCell>
            <TableCell className="tabular-nums">
              {formatCalendarDateTwoDigitDay(ffi.issueDate)}
            </TableCell>
            <TableCell className="tabular-nums">
              {formatCalendarDateTwoDigitDay(ffi.expiryDate)}
            </TableCell>
            <TableCell>{FFI_HIGHER_AUTHORITY_LABELS[ffi.higherAuthority]}</TableCell>
            <TableCell>{MEDICAL_CENTRE_LABELS[ffi.submittingMc]}</TableCell>
            <TableCell>
              <Badge variant={STATUS_BADGES[ffi.status]}>{FFI_STATUS_LABELS[ffi.status]}</Badge>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/**
 * The FFI History of the patient `patientId`, five FFIs a page, through the
 * shared filtered history ({@link FilteredHistory}): "Showing x to y of N
 * FFIs", "Couldn't load FFIs" for a failed read, and "No FFIs", or with a
 * filter applied "No FFIs match the filters", for an empty one. It sorts by
 * Issue Date or Expiry Date, and the Filter panel narrows it on every column.
 */
function FfiHistory({ patientId }: { patientId: string }) {
  return (
    <FilteredHistory
      copy={{
        empty: "No FFIs",
        emptyFiltered: "No FFIs match the filters",
        failure: "Couldn't load FFIs",
        loading: "Loading FFIs…",
        noun: "FFIs",
        pagerLabel: "FFI History pages",
      }}
      countFilters={countFfiFilters}
      declaration={FFI_HISTORY}
      filterPopover={FfiFilterPopover}
      noFilters={NO_FFI_FILTERS}
      pageSize={FFI_HISTORY_PAGE_SIZE}
      patientId={patientId}
      table={({ pending, rows, sorting }) => (
        <FfiTable ffis={rows} pending={pending} sorting={sorting} />
      )}
    />
  );
}

/**
 * FFI: the FFI History of the patient in context, read from the patient
 * source, and "Loading FFIs…" while the patient is read
 * ({@link ForPatientInContext}). A new patient in context is a new history,
 * read from its first page.
 */
export function Ffis() {
  return (
    <Card>
      <CardContent className="flex flex-col gap-4">
        <ForPatientInContext history={FfiHistory} loading="Loading FFIs…" />
      </CardContent>
    </Card>
  );
}
