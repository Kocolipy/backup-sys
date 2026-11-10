import { useId, useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatCalendarDateTwoDigitDay } from "@/lib/time";
import { PATIENT_GENDER_LABELS } from "@/lib/patient-gender";
import { PagedListing } from "@/pages/paged-listing";
import { usePagedRead } from "@/pages/paged-read";
import { type ColumnSorting, PlainHeading, SortHeading } from "@/pages/sort-heading";

import { PatientFilterPopover } from "./patient-filter-popover";
import {
  NO_PATIENT_FILTERS,
  parsePatientQuery,
  PATIENT_SEARCH,
  PATIENT_SEARCH_PAGE_SIZE,
  type PatientFilters,
  type PatientMatch,
  type PatientSort,
  type PatientSortColumn,
} from "./search-api";

/** How the results are sorted, by the page that holds the sort. */
type ResultsSorting = ColumnSorting<PatientSortColumn>;

/**
 * The matching Patients, one row each: the official name, the full NRIC, the
 * gender as a badge and both dates as `dd MMM yyyy`, a missing Last visit
 * shown as `—`. Date of Birth and Last Visit sort, one at a time; the name,
 * NRIC and gender do not. A row is not a control: nothing happens on clicking
 * it. Each row is keyed by the Patient's id, which is kept and never shown.
 */
function ResultsTable({
  patients,
  sorting,
}: {
  patients: readonly PatientMatch[];
  sorting: ResultsSorting;
}) {
  return (
    <Table>
      <TableCaption className="sr-only">Matching patients</TableCaption>
      <TableHeader>
        <TableRow>
          <PlainHeading>Patient Name</PlainHeading>
          <PlainHeading>NRIC</PlainHeading>
          <PlainHeading>Gender</PlainHeading>
          <SortHeading by="birthDate" label="Date of Birth" sorting={sorting} />
          <SortHeading by="lastVisit" label="Last Visit" sorting={sorting} />
        </TableRow>
      </TableHeader>
      <TableBody>
        {patients.map((patient) => (
          <TableRow key={patient.id}>
            <TableCell className="font-medium">{patient.name}</TableCell>
            <TableCell className="font-mono">{patient.nric}</TableCell>
            <TableCell>
              <Badge variant="secondary">{PATIENT_GENDER_LABELS[patient.gender]}</Badge>
            </TableCell>
            <TableCell className="tabular-nums">
              {formatCalendarDateTwoDigitDay(patient.birthDate)}
            </TableCell>
            <TableCell className="tabular-nums">
              {patient.lastVisit === null ? "—" : formatCalendarDateTwoDigitDay(patient.lastVisit)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/**
 * One submitted search's results, read from its first page on mounting and
 * again from its first page whenever its filters or sort change: the table,
 * the "Showing x to y of N patients" range with the numbered pager whenever
 * there is a match, or "No patients match your search". A failed read is
 * reported on the page's one error line, which the signed-in layout owns, and
 * offers Retry, which reads the same page of the same search again.
 */
function SearchResults({
  filters,
  query,
  sorting,
}: {
  filters: PatientFilters;
  query: string;
  sorting: ResultsSorting;
}) {
  const reading = usePagedRead(PATIENT_SEARCH, {
    args: [query, filters, sorting.sort],
    failureMessage: "Unable to search for patients. Please try again.",
    pageSize: PATIENT_SEARCH_PAGE_SIZE,
  });
  // A move reads its page afresh, keeping nothing of the page before it, and
  // Retry is offered only once a read has failed, so a failure never keeps a
  // page (`stale`): it is always one with nothing read.
  const failed = reading.view.kind === "failed";
  return (
    <>
      <PagedListing
        copy={{
          empty: "No patients match your search",
          loading: "Searching for patients…",
          noun: "patients",
          pagerLabel: "Search result pages",
        }}
        onePage="shown"
        read={reading}
        readAgain={false}
      >
        {({ rows }) => <ResultsTable patients={rows} sorting={sorting} />}
      </PagedListing>
      {failed ? (
        <Button
          className="self-start"
          onClick={() => void reading.reload()}
          size="sm"
          variant="outline"
        >
          Retry
        </Button>
      ) : null}
    </>
  );
}

/**
 * The search a submit asks for. `generation` counts the submits, so submitting
 * the same text again is a new search, read afresh from its first page.
 */
interface Submitted {
  generation: number;
  query: string;
}

/**
 * Patient Search: one box taking a name or a full NRIC, searched only on
 * submit (Enter or the button). A full NRIC runs an NRIC search, anything else
 * a name search, which needs at least three characters; a shorter query is
 * refused beside the box and nothing is read, and the results last read stay.
 *
 * The results sort by Date of Birth or Last Visit, newest Last visit first by
 * default, and the Filter panel narrows them by gender and by a date range for
 * each date. Both narrow or order the search submitted, and keep across a new
 * one; a filter applied before any search reads nothing until one is
 * submitted. A change of either reads the results from their first page.
 *
 * The search, its filters and its sort live on the page alone: none of them,
 * no name or NRIC, reaches the URL or the browser history, so leaving the page
 * forgets them. Before the first search the page reads nothing and shows only
 * a prompt.
 */
export function Search() {
  const [typed, setTyped] = useState("");
  const [submitted, setSubmitted] = useState<Submitted | null>(null);
  const [refusal, setRefusal] = useState<string | null>(null);
  const [filters, setFilters] = useState<PatientFilters>(NO_PATIENT_FILTERS);
  const [sort, setSort] = useState<PatientSort | null>(null);
  const refusalId = useId();

  const submit = () => {
    const parsed = parsePatientQuery(typed);
    if (!parsed.ok) {
      setRefusal(parsed.message);
      return;
    }
    setRefusal(null);
    setSubmitted((last) => ({ generation: (last?.generation ?? 0) + 1, query: parsed.query }));
  };

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardContent>
          <form
            aria-label="Patient search"
            className="flex flex-col gap-2"
            onSubmit={(event) => {
              event.preventDefault();
              submit();
            }}
            role="search"
          >
            <div className="flex items-center gap-2">
              <Input
                aria-describedby={refusal === null ? undefined : refusalId}
                aria-invalid={refusal !== null}
                aria-label="Search by name or NRIC"
                onChange={(event) => setTyped(event.target.value)}
                placeholder="Search by name or NRIC"
                type="search"
                value={typed}
              />
              <Button type="submit">Search</Button>
            </div>
            {refusal === null ? null : (
              <p className="text-sm text-destructive" id={refusalId}>
                {refusal}
              </p>
            )}
          </form>
        </CardContent>
      </Card>
      <Card>
        <CardHeader className="flex items-center justify-between gap-4">
          <h2 className="font-headline text-headline-sm">Search Results</h2>
          <PatientFilterPopover applied={filters} onApply={setFilters} />
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {submitted === null ? (
            <p className="text-sm text-muted-foreground">
              Search for a patient by name or full NRIC.
            </p>
          ) : (
            <SearchResults
              filters={filters}
              key={submitted.generation}
              query={submitted.query}
              sorting={{ onSort: setSort, sort }}
            />
          )}
        </CardContent>
      </Card>
    </div>
  );
}
