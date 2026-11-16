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
import { MEDICAL_CENTRE_LABELS } from "@/pages/medical-centre-api";
import { type ColumnSorting, PlainHeading, SortHeading } from "@/pages/sort-heading";
import { badged, type BadgeVariant, labelled } from "@/pages/source-code";

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

/**
 * Each Outcome's badge: FIT green, REQUEST WAIVER amber, UNFIT red. A code
 * outside the set is on a neutral one.
 */
const OUTCOME_BADGES: Readonly<Record<FfiOutcome, BadgeVariant>> = {
  fit: "success",
  "request-waiver": "warning",
  unfit: "danger",
};

/** Each Status's badge: Active green, Inactive muted. A code outside the set is on a neutral one. */
const STATUS_BADGES: Readonly<Record<FfiStatus, BadgeVariant>> = {
  active: "success",
  inactive: "muted",
};

/** How the history is sorted, by the listing that holds the sort. */
type HistorySorting = ColumnSorting<FfiSortColumn>;

/**
 * The page's FFIs, one row each, in the order read, shown as the patient
 * source sent them (ADR 0020): each closed set's code by its label, the
 * Outcome and the Status as badges, and both dates as `dd MMM yyyy`; a code
 * outside its closed set as it came, the Outcome's and the Status's on a
 * neutral badge; a missing field as "—" and one in the wrong format as
 * "Error" (`shown`). Issue Date and Expiry Date sort, one at a time, held
 * still while a read is `pending`; every other column does not. Read-only,
 * with no row action; each row is keyed by the FFI's id, which is never shown,
 * or its place without one. The Status is the source's, shown as sent:
 * nothing here works out whether an FFI has expired.
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
        {ffis.map((ffi, place) => (
          <TableRow key={orElse(ffi.id, place)}>
            <TableCell>{shown(ffi.type, labelled(FFI_TYPE_LABELS))}</TableCell>
            <TableCell>{shown(ffi.outcome, badged(FFI_OUTCOME_LABELS, OUTCOME_BADGES))}</TableCell>
            <TableCell className="tabular-nums">
              {shown(ffi.issueDate, formatCalendarDateTwoDigitDay)}
            </TableCell>
            <TableCell className="tabular-nums">
              {shown(ffi.expiryDate, formatCalendarDateTwoDigitDay)}
            </TableCell>
            <TableCell>
              {shown(ffi.higherAuthority, labelled(FFI_HIGHER_AUTHORITY_LABELS))}
            </TableCell>
            <TableCell>{shown(ffi.submittingMc, labelled(MEDICAL_CENTRE_LABELS))}</TableCell>
            <TableCell>{shown(ffi.status, badged(FFI_STATUS_LABELS, STATUS_BADGES))}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/**
 * The FFI History of the patient `patientId`, five FFIs a page, through the
 * shared filtered history ({@link FilteredHistory}), its rows named records
 * as every paged table's are, and "Couldn't load FFIs" for a failed read. It
 * sorts by Issue Date or Expiry Date, and the Filter panel narrows it on
 * every column.
 */
function FfiHistory({ patientId }: { patientId: string }) {
  return (
    <FilteredHistory
      copy={{ failure: "Couldn't load FFIs", pagerLabel: "FFI History pages" }}
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
 * source, and "Loading records…" while the patient is read
 * ({@link ForPatientInContext}). A new patient in context is a new history,
 * read from its first page.
 */
export function Ffis() {
  return (
    <Card>
      <CardContent className="flex flex-col gap-4">
        <ForPatientInContext history={FfiHistory} />
      </CardContent>
    </Card>
  );
}
