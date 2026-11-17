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
import {
  anyWarns,
  type MandatoryElement,
  MissingInformationCell,
  MissingInformationHeading,
} from "@/pages/mandatory-elements";
import { type ColumnSorting, PlainHeading, SortHeading } from "@/pages/sort-heading";
import { badged, type BadgeVariant, labelled } from "@/pages/source-code";

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
 * WAIVER REJECTED muted. A code outside the set is on a neutral one.
 */
const OUTCOME_BADGES: Readonly<Record<NumcOutcome, BadgeVariant>> = {
  fit: "success",
  unfit: "danger",
  "waiver-granted": "info",
  "waiver-rejected": "muted",
};

/** The Type column: a Mandatory element, its label the column's heading. */
const TYPE: MandatoryElement<keyof NumcRecord> = { element: "type", label: "Type" };

/** The Outcome column: a Mandatory element, its label the column's heading. */
const OUTCOME: MandatoryElement<keyof NumcRecord> = { element: "outcome", label: "Outcome" };

/** The Issue Date column: a Mandatory element, its label the column's heading. */
const ISSUE_DATE: MandatoryElement<keyof NumcRecord> = {
  element: "issueDate",
  label: "Issue Date",
};

/** The Expiry Date column: a Mandatory element, its label the column's heading. */
const EXPIRY_DATE: MandatoryElement<keyof NumcRecord> = {
  element: "expiryDate",
  label: "Expiry Date",
};

/**
 * The Mandatory elements of a NUMC Record, in the table's column order: a
 * row missing one, or holding one in the wrong format, carries a warning.
 */
const MANDATORY_ELEMENTS: readonly MandatoryElement<keyof NumcRecord>[] = [
  TYPE,
  OUTCOME,
  ISSUE_DATE,
  EXPIRY_DATE,
];

/**
 * The page's NUMC Records, one row each, in the order read, shown as the
 * patient source sent them (ADR 0020): the NUMC Type by its label, the NUMC
 * Outcome as a badge, and both dates as `dd MMM yyyy`; a code outside its
 * closed set as it came, the Outcome's on a neutral badge; a missing element
 * as "—" and one in the wrong format as "Error" (`shown`). A leading column
 * warns on a row whose Mandatory element is missing or in the wrong format
 * ({@link MissingInformationCell}), the row shown in full; a page with no row
 * warning has no leading column ({@link anyWarns}). Issue Date and
 * Expiry Date sort, one at a time, held still while a read is `pending`; the
 * Type and the Outcome do not. Read-only, with no row action; each row is
 * keyed by the record's id, which is never shown, or its place without one.
 * Nothing here works out whether a record has expired.
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
  const warns = anyWarns(MANDATORY_ELEMENTS, records);
  return (
    <Table>
      <TableCaption className="sr-only">NUMC History</TableCaption>
      <TableHeader>
        <TableRow>
          {warns ? <MissingInformationHeading /> : null}
          <PlainHeading>{TYPE.label}</PlainHeading>
          <PlainHeading>{OUTCOME.label}</PlainHeading>
          <SortHeading
            by="issueDate"
            disabled={pending}
            label={ISSUE_DATE.label}
            sorting={sorting}
          />
          <SortHeading
            by="expiryDate"
            disabled={pending}
            label={EXPIRY_DATE.label}
            sorting={sorting}
          />
        </TableRow>
      </TableHeader>
      <TableBody>
        {records.map((record, place) => (
          <TableRow key={orElse(record.id, place)}>
            {warns ? (
              <MissingInformationCell mandatory={MANDATORY_ELEMENTS} record={record} />
            ) : null}
            <TableCell className="font-semibold">
              {shown(record.type, labelled(NUMC_TYPE_LABELS))}
            </TableCell>
            <TableCell>
              {shown(record.outcome, badged(NUMC_OUTCOME_LABELS, OUTCOME_BADGES))}
            </TableCell>
            <TableCell className="tabular-nums">
              {shown(record.issueDate, formatCalendarDateTwoDigitDay)}
            </TableCell>
            <TableCell className="tabular-nums">
              {shown(record.expiryDate, formatCalendarDateTwoDigitDay)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/**
 * The NUMC History of the patient `patientId`, five NUMC Records a page,
 * through the shared filtered history ({@link FilteredHistory}), its rows
 * named records as every paged table's are, and "Couldn't load NUMC records"
 * for a failed read. It sorts by Issue Date or Expiry Date, and the
 * Filter panel narrows it on every column.
 */
function NumcHistory({ patientId }: { patientId: string }) {
  return (
    <FilteredHistory
      copy={{ failure: "Couldn't load NUMC records", pagerLabel: "NUMC History pages" }}
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
 * source, and "Loading records…" while the patient is read
 * ({@link ForPatientInContext}). A new patient in context is a new history,
 * read from its first page.
 */
export function NumcRecords() {
  return (
    <Card>
      <CardContent className="flex flex-col gap-4">
        <ForPatientInContext history={NumcHistory} />
      </CardContent>
    </Card>
  );
}
