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
import { PagedListing } from "@/pages/paged-listing";
import { usePagedRead } from "@/pages/paged-read";
import { PlainHeading } from "@/pages/sort-heading";

import {
  NUMC_HISTORY,
  NUMC_HISTORY_PAGE_SIZE,
  NUMC_OUTCOME_LABELS,
  NUMC_TYPE_LABELS,
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
 * Read-only, with no row action and no column that sorts; each row is keyed
 * by the record's id, which is never shown. Nothing here works out whether a
 * record has expired.
 */
function NumcTable({ records }: { records: readonly NumcRecord[] }) {
  return (
    <Table>
      <TableCaption className="sr-only">NUMC History</TableCaption>
      <TableHeader>
        <TableRow>
          <PlainHeading>Type</PlainHeading>
          <PlainHeading>Outcome</PlainHeading>
          <PlainHeading>Issue Date</PlainHeading>
          <PlainHeading>Expiry Date</PlainHeading>
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
 * The NUMC History of the patient `patientId`, five NUMC Records a page, in
 * the source's own order, through the shared paged listing: the numbered pager
 * and "Showing x to y of N NUMC records", the page shown held while the next
 * loads. A `404` — the source holds no patient under the id — is "No patient
 * in context", an answer rather than a failure; any other failure is
 * "Couldn't load NUMC records" on the page's one error line, with Try again.
 */
function NumcHistory({ patientId }: { patientId: string }) {
  const reading = usePagedRead(NUMC_HISTORY, {
    args: [patientId],
    failureMessage: "Couldn't load NUMC records",
    pageSize: NUMC_HISTORY_PAGE_SIZE,
  });
  const { view } = reading;
  if (view.kind === "absent") return <NoPatientInContext />;
  return (
    <PagedListing
      copy={{
        empty: "No NUMC records",
        loading: "Loading NUMC records…",
        noun: "NUMC records",
        pagerLabel: "NUMC History pages",
      }}
      onePage="shown"
      read={{ ...reading, view }}
    >
      {({ rows }) => <NumcTable records={rows} />}
    </PagedListing>
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
