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
import { FilteredHistory } from "@/pages/filtered-history";
import { ForPatientInContext } from "@/pages/for-patient-in-context";
import { MEDICAL_CENTRE_LABELS } from "@/pages/medical-centre-api";
import { type ColumnSorting, PlainHeading, SortHeading } from "@/pages/sort-heading";

import {
  countMedicationFilters,
  DISPENSE_STATUS_LABELS,
  MEDICATION_HISTORY,
  MEDICATION_HISTORY_PAGE_SIZE,
  NO_MEDICATION_FILTERS,
  type DispenseStatus,
  type MedicationRecord,
  type MedicationSortColumn,
} from "./medication-api";
import { MedicationFilterPopover } from "./medication-filter-popover";

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
 * its Medical Centre's label. Start Date and End Date sort, one at a time,
 * held still while a read is `pending`; no other column does. Read-only, with
 * no row action; each row is keyed by the record's id, which is never shown.
 * The table is wider than a card: each cell keeps to one line and the table
 * scrolls sideways inside its card.
 */
function MedicationTable({
  pending,
  records,
  sorting,
}: {
  pending: boolean;
  records: readonly MedicationRecord[];
  sorting: ColumnSorting<MedicationSortColumn>;
}) {
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
          <SortHeading by="startDate" disabled={pending} label="Start Date" sorting={sorting} />
          <SortHeading by="endDate" disabled={pending} label="End Date" sorting={sorting} />
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
 * a page, through the shared filtered history ({@link FilteredHistory}):
 * "Showing x to y of N medication records", "Couldn't load medication
 * records" for a failed read, and "No medication records", or with a filter
 * applied "No medication records match the filters", for an empty one. It
 * sorts by Start Date or End Date, each by its whole date-time, and the
 * Filter panel narrows it by the Dispense Status and by a date-time range
 * over either date.
 */
function MedicationHistory({ patientId }: { patientId: string }) {
  return (
    <FilteredHistory
      copy={{
        empty: "No medication records",
        emptyFiltered: "No medication records match the filters",
        failure: "Couldn't load medication records",
        loading: "Loading medication records…",
        noun: "medication records",
        pagerLabel: "Medication History pages",
      }}
      countFilters={countMedicationFilters}
      declaration={MEDICATION_HISTORY}
      filterPopover={MedicationFilterPopover}
      noFilters={NO_MEDICATION_FILTERS}
      pageSize={MEDICATION_HISTORY_PAGE_SIZE}
      patientId={patientId}
      table={({ pending, rows, sorting }) => (
        <MedicationTable pending={pending} records={rows} sorting={sorting} />
      )}
    />
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
