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
import type { HistorySort, HistorySortColumn } from "@/pages/history-api";
import { MEDICAL_CENTRE_LABELS } from "@/pages/medical-centre-api";
import { PagedListing } from "@/pages/paged-listing";
import { usePagedRead } from "@/pages/paged-read";
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
  type FfiFilters,
  type FfiOutcome,
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
type HistorySorting = ColumnSorting<HistorySortColumn>;

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
 * shared paged listing: the numbered pager and "Showing x to y of N FFIs",
 * the page shown held while the next loads. A `404` — the source holds no
 * patient under the id — is "No patient in context", an answer rather than a
 * failure; any other failure is "Couldn't load FFIs" on the page's one error
 * line, with Try again.
 *
 * It sorts by Issue Date or Expiry Date, in the source's own order until a
 * heading is pressed, and the Filter panel at its top right narrows it on
 * every column. A change of either reads the history from its first page,
 * the filters combined with AND and with the sort. Both live here alone:
 * neither reaches the URL, and a new patient in context, a new history,
 * starts with neither. With a filter applied, a history with no FFI says
 * none matches, rather than that the patient has none.
 */
function FfiHistory({ patientId }: { patientId: string }) {
  const [filters, setFilters] = useState<FfiFilters>(NO_FFI_FILTERS);
  const [sort, setSort] = useState<HistorySort | null>(null);
  const reading = usePagedRead(FFI_HISTORY, {
    args: [patientId, filters, sort],
    failureMessage: "Couldn't load FFIs",
    pageSize: FFI_HISTORY_PAGE_SIZE,
  });
  const { view } = reading;
  if (view.kind === "absent") return <NoPatientInContext />;
  // Rows are shown only once a page is read, so a read in flight beside them
  // is one the listing holds them still for.
  const pending = view.kind === "loading";
  return (
    <>
      <div className="flex justify-end">
        <FfiFilterPopover applied={filters} onApply={setFilters} />
      </div>
      <PagedListing
        copy={{
          empty: countFfiFilters(filters) === 0 ? "No FFIs" : "No FFIs match the filters",
          loading: "Loading FFIs…",
          noun: "FFIs",
          pagerLabel: "FFI History pages",
        }}
        onePage="shown"
        read={{ ...reading, view }}
      >
        {({ rows }) => (
          <FfiTable ffis={rows} pending={pending} sorting={{ onSort: setSort, sort }} />
        )}
      </PagedListing>
    </>
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
