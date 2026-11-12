import type { ReactNode } from "react";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatCalendarDateTwoDigitDay } from "@/lib/time";
import { PagedListing } from "@/pages/paged-listing";
import { usePagedRead } from "@/pages/paged-read";
import { usePatientInContext } from "@/pages/patient-in-context";

import {
  FFI_HIGHER_AUTHORITY_LABELS,
  FFI_HISTORY,
  FFI_HISTORY_PAGE_SIZE,
  FFI_OUTCOME_LABELS,
  FFI_STATUS_LABELS,
  FFI_SUBMITTING_MC_LABELS,
  FFI_TYPE_LABELS,
  type Ffi,
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
const STATUS_BADGES: Readonly<
  Record<FfiStatus, { variant: "success" | "secondary"; className?: string }>
> = {
  active: { variant: "success" },
  inactive: { className: "bg-muted text-muted-foreground", variant: "secondary" },
};

const HEADINGS = [
  "FFI Type",
  "Outcome",
  "Issue Date",
  "Expiry Date",
  "Higher Authority",
  "Submitting MC",
  "Status",
] as const;

/**
 * The page's FFIs, one row each, in the source's order: each closed set's code
 * by its label, the Outcome and the Status as badges, and both dates as
 * `dd MMM yyyy`. Read-only, with no row action; each row is keyed by the
 * FFI's id, which is never shown. The Status is the source's, shown as sent:
 * nothing here works out whether an FFI has expired.
 */
function FfiTable({ ffis }: { ffis: readonly Ffi[] }) {
  return (
    <Table>
      <TableCaption className="sr-only">FFI History</TableCaption>
      <TableHeader>
        <TableRow>
          {HEADINGS.map((heading) => (
            <TableHead className="text-muted-foreground" key={heading} scope="col">
              {heading}
            </TableHead>
          ))}
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
            <TableCell>{FFI_SUBMITTING_MC_LABELS[ffi.submittingMc]}</TableCell>
            <TableCell>
              <Badge {...STATUS_BADGES[ffi.status]}>{FFI_STATUS_LABELS[ffi.status]}</Badge>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/** What the page says with no patient whose FFIs it could show. */
function NoPatientInContext(): ReactNode {
  return <p className="text-sm text-muted-foreground">No patient in context</p>;
}

/**
 * The FFI History of the patient `patientId`, five FFIs a page, through the
 * shared paged listing: the numbered pager and "Showing x to y of N FFIs",
 * the page shown held while the next loads. A `404` — the source holds no
 * patient under the id — is "No patient in context", an answer rather than a
 * failure; any other failure is "Couldn't load FFIs" on the page's one error
 * line, with Try again.
 */
function FfiHistory({ patientId }: { patientId: string }) {
  const reading = usePagedRead(FFI_HISTORY, {
    args: [patientId],
    failureMessage: "Couldn't load FFIs",
    pageSize: FFI_HISTORY_PAGE_SIZE,
  });
  const { view } = reading;
  if (view.kind === "absent") return <NoPatientInContext />;
  return (
    <PagedListing
      copy={{
        empty: "No FFIs",
        loading: "Loading FFIs…",
        noun: "FFIs",
        pagerLabel: "FFI History pages",
      }}
      onePage="shown"
      read={{ ...reading, view }}
    >
      {({ rows }) => <FfiTable ffis={rows} />}
    </PagedListing>
  );
}

/** What the page says while the patient in context is still being read. */
function LoadingPatient(): ReactNode {
  return (
    <p className="text-sm text-muted-foreground" role="status">
      Loading FFIs…
    </p>
  );
}

/**
 * The part of the page the patient in context decides, from the signed-in
 * layout's one read of them (`usePatientInContext`): with none in context, or
 * a `404` for the id there, "No patient in context", reading no FFIs; while
 * the patient is read, its loading copy; a failed read leaves the page empty,
 * its copy on the page's one error line; and once read, the FFI History of
 * that patient, by its id.
 */
function ForPatientInContext(): ReactNode {
  const view = usePatientInContext();
  switch (view.kind) {
    case "none":
    case "absent":
    case "not-permitted":
      return <NoPatientInContext />;
    case "loading":
      return <LoadingPatient />;
    case "failed":
      return null;
    default:
      return <FfiHistory key={view.data.id} patientId={view.data.id} />;
  }
}

/**
 * FFI: the FFI History of the patient in context, read from the patient
 * source. A new patient in context is a new history, read from its first page.
 */
export function Ffis() {
  return (
    <Card>
      <CardContent className="flex flex-col gap-4">
        <ForPatientInContext />
      </CardContent>
    </Card>
  );
}
