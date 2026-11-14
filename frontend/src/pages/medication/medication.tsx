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
import { formatDateTime } from "@/lib/time";
import { ForPatientInContext, NoPatientInContext } from "@/pages/for-patient-in-context";
import { MEDICAL_CENTRE_LABELS } from "@/pages/medical-centre-api";
import { PagedListing } from "@/pages/paged-listing";
import { usePagedRead } from "@/pages/paged-read";
import { PlainHeading } from "@/pages/sort-heading";

import {
  DISPENSE_STATUS_LABELS,
  MEDICATION_HISTORY,
  MEDICATION_HISTORY_PAGE_SIZE,
  type DispenseStatus,
  type MedicationRecord,
} from "./medication-api";

/** Each Dispense Status's badge: PENDING muted, DISPENSED green, UNCOLLECTED red. */
const STATUS_BADGES: Readonly<Record<DispenseStatus, "muted" | "success" | "danger">> = {
  dispensed: "success",
  pending: "muted",
  uncollected: "danger",
};

/** An optional element as shown: the source's text, or `—` when it has none. */
const orDash = (text: string | undefined): string => text ?? "—";

/**
 * The page's Medication Records, one row each, in the order read: the
 * Medication in bold, each free-text element exactly as the source gives it
 * and `—` for an optional one it lacks, the Dispense Status as a badge, both
 * date-times in Singapore time as `dd MMM yyyy HH:mm`, and the Dispensary by
 * its Medical Centre's label. Read-only, with no row action and no column
 * that sorts; each row is keyed by the record's id, which is never shown. The
 * table is wider than a card: each cell keeps to one line and the table
 * scrolls sideways inside its card.
 */
function MedicationTable({ records }: { records: readonly MedicationRecord[] }) {
  return (
    <Table>
      <TableCaption className="sr-only">Medication History</TableCaption>
      <TableHeader>
        <TableRow>
          <PlainHeading>Medication</PlainHeading>
          <PlainHeading>Dosage</PlainHeading>
          <PlainHeading>Route</PlainHeading>
          <PlainHeading>Frequency</PlainHeading>
          <PlainHeading>Step</PlainHeading>
          <PlainHeading>Quantity</PlainHeading>
          <PlainHeading>Dispense Status</PlainHeading>
          <PlainHeading>Duration</PlainHeading>
          <PlainHeading>Start Date</PlainHeading>
          <PlainHeading>End Date</PlainHeading>
          <PlainHeading>Dispensary</PlainHeading>
          <PlainHeading>Prescribing Doctor</PlainHeading>
        </TableRow>
      </TableHeader>
      <TableBody>
        {records.map((record) => (
          <TableRow key={record.id}>
            <TableCell className="font-semibold">{record.medication}</TableCell>
            <TableCell>{orDash(record.dosage)}</TableCell>
            <TableCell>{orDash(record.route)}</TableCell>
            <TableCell>{orDash(record.frequency)}</TableCell>
            <TableCell>{orDash(record.step)}</TableCell>
            <TableCell>{orDash(record.quantity)}</TableCell>
            <TableCell>
              <Badge variant={STATUS_BADGES[record.dispenseStatus]}>
                {DISPENSE_STATUS_LABELS[record.dispenseStatus]}
              </Badge>
            </TableCell>
            <TableCell>{orDash(record.duration)}</TableCell>
            <TableCell className="tabular-nums">{formatDateTime(record.startDate)}</TableCell>
            <TableCell className="tabular-nums">{formatDateTime(record.endDate)}</TableCell>
            <TableCell>{MEDICAL_CENTRE_LABELS[record.dispensary]}</TableCell>
            <TableCell>{record.prescribingDoctor}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/**
 * The Medication History of the patient `patientId`, five Medication Records
 * a page, in the source's own order, through the shared paged listing: the
 * numbered pager and "Showing x to y of N medication records", the page shown
 * held while the next loads. A `404` — the source holds no patient under the
 * id — is "No patient in context", an answer rather than a failure; any other
 * failure is "Couldn't load medication records" on the page's one error line,
 * with Try again.
 */
function MedicationHistory({ patientId }: { patientId: string }) {
  const reading = usePagedRead(MEDICATION_HISTORY, {
    args: [patientId],
    failureMessage: "Couldn't load medication records",
    pageSize: MEDICATION_HISTORY_PAGE_SIZE,
  });
  const { view } = reading;
  if (view.kind === "absent") return <NoPatientInContext />;
  return (
    <PagedListing
      copy={{
        empty: "No medication records",
        loading: "Loading medication records…",
        noun: "medication records",
        pagerLabel: "Medication History pages",
      }}
      onePage="shown"
      read={{ ...reading, view }}
    >
      {({ rows }) => <MedicationTable records={rows} />}
    </PagedListing>
  );
}

/**
 * Medication: the Medication History of the patient in context, read from
 * the patient source, and "Loading medication records…" while the patient is
 * read ({@link ForPatientInContext}). A new patient in context is a new
 * history, read from its first page.
 */
export function Medications() {
  return (
    <Card>
      <CardContent className="flex flex-col gap-4">
        <ForPatientInContext history={MedicationHistory} loading="Loading medication records…" />
      </CardContent>
    </Card>
  );
}
