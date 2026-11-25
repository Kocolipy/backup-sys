import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { byCode, orElse, shown } from "@/lib/source-field";
import { formatCalendarDateTwoDigitDay } from "@/lib/time";
import { FilteredHistory } from "@/pages/filtered-history";
import {
  anyWarns,
  type MandatoryElement,
  MissingInformationCell,
  MissingInformationHeading,
} from "@/pages/mandatory-elements";
import { MEDICAL_CENTRE_LABELS } from "@/pages/medical-centre-api";
import { HistoryHeading, PatientHistoryPage } from "@/pages/patient-history-page";
import { type ColumnSorting, PlainHeading, SortHeading } from "@/pages/sort-heading";
import { type BadgeVariant, labelled } from "@/pages/source-code";

import { HspFilterPopover } from "./hsp-filter-popover";
import {
  countHspFilters,
  HSP_HISTORY,
  HSP_HISTORY_PAGE_SIZE,
  HSP_OUTCOME_LABELS,
  HSP_STATUS_LABELS,
  NO_HSP_FILTERS,
  type HspOutcome,
  type HspRecord,
  type HspSortColumn,
  type HspStatus,
} from "./hsp-api";

/**
 * Each Outcome's badge: LOW green, MODERATE amber, HIGH red. A code outside
 * the set is on a white one ({@link outcomeBadge}).
 */
const OUTCOME_BADGES: Readonly<Record<HspOutcome, BadgeVariant>> = {
  high: "danger",
  low: "success",
  moderate: "warning",
};

/**
 * How an Outcome's code is shown, handed to `shown`: its label on its coloured
 * badge, or the code as it came on a white pill — the outline badge filled with
 * the card's white, so no row tint shows through it.
 */
function outcomeBadge(code: string) {
  const variant = byCode(OUTCOME_BADGES, code, "outline");
  return (
    <Badge className={variant === "outline" ? "bg-card" : undefined} variant={variant}>
      {labelled(HSP_OUTCOME_LABELS)(code)}
    </Badge>
  );
}

/**
 * Each Status's text colour: Active green, Inactive muted. A code outside the
 * set is in the table's own colour.
 */
const STATUS_TONES: Readonly<Record<HspStatus, string>> = {
  active: "text-success-soft-foreground",
  inactive: "text-muted-foreground",
};

/** How a Status's code is shown, handed to `shown`: its label in its tone, or as it came. */
function toned(code: string) {
  return (
    <span className={byCode(STATUS_TONES, code, "")}>{labelled(HSP_STATUS_LABELS)(code)}</span>
  );
}

/** How the history is sorted, by the listing that holds the sort. */
type HistorySorting = ColumnSorting<HspSortColumn>;

/** The Outcome column: a Mandatory element, its label the column's heading. */
const OUTCOME: MandatoryElement<keyof HspRecord> = { element: "outcome", label: "Outcome" };

/** The Status column: a Mandatory element, its label the column's heading. */
const STATUS: MandatoryElement<keyof HspRecord> = { element: "status", label: "Status" };

/**
 * The Mandatory elements of an HSP Record, in the table's column order: a row
 * missing one, or holding one in the wrong format, carries a warning.
 */
const MANDATORY_ELEMENTS: readonly MandatoryElement<keyof HspRecord>[] = [OUTCOME, STATUS];

/**
 * The page's HSP Records, one row each, in the order read, shown as the
 * patient source sent them (ADR 0020): the Outcome by its label on its badge,
 * both dates as `dd MMM yyyy`, the Submitting MC by its label and the Status
 * by its label in its tone; a code outside its closed set as it came, the
 * Outcome's on a white pill; a missing field as "—" and one in the wrong
 * format as "Error" (`shown`). A leading column warns on a row whose
 * Mandatory element is missing or in the wrong format
 * ({@link MissingInformationCell}); a page with no row warning has no leading
 * column ({@link anyWarns}). Start Date and End Date sort, one at a time, held
 * still while a read is `pending`; every other column does not. Read-only,
 * with no row action; each row is keyed by the record's id, which is never
 * shown, or its place without one.
 */
function HspTable({
  pending,
  records,
  sorting,
}: {
  pending: boolean;
  records: readonly HspRecord[];
  sorting: HistorySorting;
}) {
  const warns = anyWarns(MANDATORY_ELEMENTS, records);
  return (
    <Table>
      <TableCaption className="sr-only">HSP History</TableCaption>
      <TableHeader>
        <TableRow>
          {warns ? <MissingInformationHeading /> : null}
          <PlainHeading>{OUTCOME.label}</PlainHeading>
          <SortHeading by="startDate" disabled={pending} label="Start Date" sorting={sorting} />
          <SortHeading by="endDate" disabled={pending} label="End Date" sorting={sorting} />
          <PlainHeading>Submitting MC</PlainHeading>
          <PlainHeading>{STATUS.label}</PlainHeading>
        </TableRow>
      </TableHeader>
      <TableBody>
        {records.map((record, place) => (
          <TableRow key={orElse(record.id, place)}>
            {warns ? (
              <MissingInformationCell mandatory={MANDATORY_ELEMENTS} record={record} />
            ) : null}
            <TableCell>{shown(record.outcome, outcomeBadge)}</TableCell>
            <TableCell className="tabular-nums">
              {shown(record.startDate, formatCalendarDateTwoDigitDay)}
            </TableCell>
            <TableCell className="tabular-nums">
              {shown(record.endDate, formatCalendarDateTwoDigitDay)}
            </TableCell>
            <TableCell>{shown(record.submittingMc, labelled(MEDICAL_CENTRE_LABELS))}</TableCell>
            <TableCell>{shown(record.status, toned)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/**
 * The HSP History of the patient `patientId`, five records a page, through the
 * shared filtered history ({@link FilteredHistory}), headed "HSP History" —
 * the page's one `<h1>`, the shell dropping its title while a patient is in
 * context — beside its Filter button, and "Couldn't load HSP records" for a failed read.
 * It sorts by Start Date or End Date, and the Filter panel narrows it on every
 * column.
 */
function HspHistory({ patientId }: { patientId: string }) {
  return (
    <FilteredHistory
      copy={{ failure: "Couldn't load HSP records", pagerLabel: "HSP History pages" }}
      countFilters={countHspFilters}
      declaration={HSP_HISTORY}
      filterPopover={HspFilterPopover}
      heading={<HistoryHeading>HSP History</HistoryHeading>}
      noFilters={NO_HSP_FILTERS}
      pageSize={HSP_HISTORY_PAGE_SIZE}
      patientId={patientId}
      table={({ pending, rows, sorting }) => (
        <HspTable pending={pending} records={rows} sorting={sorting} />
      )}
    />
  );
}

/**
 * HSP: the patient in context's Patient card, then their HSP History, read
 * from the patient source ({@link PatientHistoryPage}).
 */
export function HspRecords() {
  return <PatientHistoryPage history={HspHistory} />;
}
