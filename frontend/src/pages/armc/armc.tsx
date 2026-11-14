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
  ARMC_BOARD_TYPE_LABELS,
  ARMC_HISTORY,
  ARMC_HISTORY_PAGE_SIZE,
  ARMC_OUTCOME_LABELS,
  type ArmcBoard,
  type ArmcOutcome,
} from "./armc-api";

/** Each ARMC Outcome's badge: FIT green, UNFIT red. */
const OUTCOME_BADGES: Readonly<Record<ArmcOutcome, "success" | "danger">> = {
  fit: "success",
  unfit: "danger",
};

/**
 * The page's ARMC Boards, one row each, in the order read: the ARMC Board
 * Type by its label, in bold, the ARMC Outcome as a badge, both dates as
 * `dd MMM yyyy` — `—` for the End Date of a board still open — and both
 * Medical Officers' names as the source gives them. Read-only, with no row
 * action and no column that sorts; each row is keyed by the board's id, which
 * is never shown.
 */
function ArmcTable({ boards }: { boards: readonly ArmcBoard[] }) {
  return (
    <Table>
      <TableCaption className="sr-only">ARMC History</TableCaption>
      <TableHeader>
        <TableRow>
          <PlainHeading>Board Type</PlainHeading>
          <PlainHeading>Outcome</PlainHeading>
          <PlainHeading>Start Date</PlainHeading>
          <PlainHeading>End Date</PlainHeading>
          <PlainHeading>Medical Officer #1</PlainHeading>
          <PlainHeading>Medical Officer #2</PlainHeading>
        </TableRow>
      </TableHeader>
      <TableBody>
        {boards.map((board) => (
          <TableRow key={board.id}>
            <TableCell className="font-semibold">
              {ARMC_BOARD_TYPE_LABELS[board.boardType]}
            </TableCell>
            <TableCell>
              <Badge variant={OUTCOME_BADGES[board.outcome]}>
                {ARMC_OUTCOME_LABELS[board.outcome]}
              </Badge>
            </TableCell>
            <TableCell className="tabular-nums">
              {formatCalendarDateTwoDigitDay(board.startDate)}
            </TableCell>
            <TableCell className="tabular-nums">
              {board.endDate === undefined ? "—" : formatCalendarDateTwoDigitDay(board.endDate)}
            </TableCell>
            <TableCell>{board.medicalOfficer1}</TableCell>
            <TableCell>{board.medicalOfficer2}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/**
 * The ARMC History of the patient `patientId`, five ARMC Boards a page, in
 * the source's own order, through the shared paged listing: the numbered pager
 * and "Showing x to y of N ARMC records", the page shown held while the next
 * loads. A `404` — the source holds no patient under the id — is "No patient
 * in context", an answer rather than a failure; any other failure is
 * "Couldn't load ARMC records" on the page's one error line, with Try again.
 */
function ArmcHistory({ patientId }: { patientId: string }) {
  const reading = usePagedRead(ARMC_HISTORY, {
    args: [patientId],
    failureMessage: "Couldn't load ARMC records",
    pageSize: ARMC_HISTORY_PAGE_SIZE,
  });
  const { view } = reading;
  if (view.kind === "absent") return <NoPatientInContext />;
  return (
    <PagedListing
      copy={{
        empty: "No ARMC records",
        loading: "Loading ARMC records…",
        noun: "ARMC records",
        pagerLabel: "ARMC History pages",
      }}
      onePage="shown"
      read={{ ...reading, view }}
    >
      {({ rows }) => <ArmcTable boards={rows} />}
    </PagedListing>
  );
}

/**
 * ARMC: the ARMC History of the patient in context, read from the patient
 * source, and "Loading ARMC records…" while the patient is read
 * ({@link ForPatientInContext}). A new patient in context is a new history,
 * read from its first page.
 */
export function ArmcBoards() {
  return (
    <Card>
      <CardContent className="flex flex-col gap-4">
        <ForPatientInContext history={ArmcHistory} loading="Loading ARMC records…" />
      </CardContent>
    </Card>
  );
}
