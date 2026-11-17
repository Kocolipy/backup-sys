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
import { formatDateTime } from "@/lib/time";
import { FilteredHistory } from "@/pages/filtered-history";
import { ForPatientInContext } from "@/pages/for-patient-in-context";
import {
  type MandatoryElement,
  MissingInformationCell,
  MissingInformationHeading,
} from "@/pages/mandatory-elements";
import { MEDICAL_CENTRE_LABELS } from "@/pages/medical-centre-api";
import { type ColumnSorting, PlainHeading, SortHeading } from "@/pages/sort-heading";
import { badged, type BadgeVariant, labelled } from "@/pages/source-code";

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

/**
 * Each Dispense Status's badge: PENDING muted, DISPENSED green, UNCOLLECTED
 * red. A code outside the set is on a neutral one.
 */
const STATUS_BADGES: Readonly<Record<DispenseStatus, BadgeVariant>> = {
  dispensed: "success",
  pending: "muted",
  uncollected: "danger",
};

/** The Medication column: a Mandatory element, its label the column's heading. */
const MEDICATION: MandatoryElement<keyof MedicationRecord> = {
  element: "medication",
  label: "Medication",
};

/** The Dispense Status column: a Mandatory element, its label the column's heading. */
const DISPENSE_STATUS: MandatoryElement<keyof MedicationRecord> = {
  element: "dispenseStatus",
  label: "Dispense Status",
};

/**
 * The Mandatory elements of a Medication Record, in the table's column order:
 * a row missing one, or holding one in the wrong format, carries a warning.
 */
const MANDATORY_ELEMENTS: readonly MandatoryElement<keyof MedicationRecord>[] = [
  MEDICATION,
  DISPENSE_STATUS,
];

/** Free text as shown: exactly as the source gives it. */
const asGiven = (text: string): string => text;

/**
 * The page's Medication Records, one row each, in the order read, shown as
 * the patient source sent them (ADR 0020): the Medication in bold, each
 * free-text element exactly as the source gives it, the Dispense Status as a
 * badge, both date-times in Singapore time as `dd MMM yyyy HH:mm`, and the
 * Dispensary by its Medical Centre's label; a code outside its closed set as
 * it came, the Dispense Status's on a neutral badge; a missing element as "—"
 * and one in the wrong format as "Error" (`shown`). A leading column warns on
 * a row whose Mandatory element is missing or in the wrong format
 * ({@link MissingInformationCell}), the row shown in full. Nothing checks the
 * dates against each other. Start Date and End Date sort, one at a time, held still
 * while a read is `pending`; no other column does. Read-only, with no row
 * action; each row is keyed by the record's id, which is never shown, or its
 * place without one. The table is wider than a card: each cell keeps to one
 * line and the table scrolls sideways inside its card.
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
          <MissingInformationHeading />
          <PlainHeading>{MEDICATION.label}</PlainHeading>
          <PlainHeading>Dosage</PlainHeading>
          <PlainHeading>Route</PlainHeading>
          <PlainHeading>Frequency</PlainHeading>
          <PlainHeading>Step</PlainHeading>
          <PlainHeading>Quantity</PlainHeading>
          <PlainHeading>{DISPENSE_STATUS.label}</PlainHeading>
          <PlainHeading>Duration</PlainHeading>
          <SortHeading by="startDate" disabled={pending} label="Start Date" sorting={sorting} />
          <SortHeading by="endDate" disabled={pending} label="End Date" sorting={sorting} />
          <PlainHeading>Dispensary</PlainHeading>
          <PlainHeading>Prescribing Doctor</PlainHeading>
        </TableRow>
      </TableHeader>
      <TableBody>
        {records.map((record, place) => (
          <TableRow key={orElse(record.id, place)}>
            <MissingInformationCell mandatory={MANDATORY_ELEMENTS} record={record} />
            <TableCell className="font-semibold">{shown(record.medication, asGiven)}</TableCell>
            <TableCell>{shown(record.dosage, asGiven)}</TableCell>
            <TableCell>{shown(record.route, asGiven)}</TableCell>
            <TableCell>{shown(record.frequency, asGiven)}</TableCell>
            <TableCell>{shown(record.step, asGiven)}</TableCell>
            <TableCell>{shown(record.quantity, asGiven)}</TableCell>
            <TableCell>
              {shown(record.dispenseStatus, badged(DISPENSE_STATUS_LABELS, STATUS_BADGES))}
            </TableCell>
            <TableCell>{shown(record.duration, asGiven)}</TableCell>
            <TableCell className="tabular-nums">
              {shown(record.startDate, formatDateTime)}
            </TableCell>
            <TableCell className="tabular-nums">{shown(record.endDate, formatDateTime)}</TableCell>
            <TableCell>{shown(record.dispensary, labelled(MEDICAL_CENTRE_LABELS))}</TableCell>
            <TableCell>{shown(record.prescribingDoctor, asGiven)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/**
 * The Medication History of the patient `patientId`, five Medication Records
 * a page, through the shared filtered history ({@link FilteredHistory}), its
 * rows named records as every paged table's are, and "Couldn't load
 * medication records" for a failed read. It sorts by Start Date or End Date,
 * each by its whole date-time, and the Filter panel narrows it by the
 * Dispense Status and by a date-time range over either date.
 */
function MedicationHistory({ patientId }: { patientId: string }) {
  return (
    <FilteredHistory
      copy={{ failure: "Couldn't load medication records", pagerLabel: "Medication History pages" }}
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
 * the patient source, and "Loading records…" while the patient is read
 * ({@link ForPatientInContext}). A new patient in context is a new history,
 * read from its first page.
 */
export function Medications() {
  return (
    <Card>
      <CardContent className="flex flex-col gap-4">
        <ForPatientInContext history={MedicationHistory} />
      </CardContent>
    </Card>
  );
}
