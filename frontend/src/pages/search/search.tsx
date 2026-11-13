import { useId, useState } from "react";
import { useNavigate } from "react-router-dom";

import { setEhrContext } from "@/auth/ehr-context";

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
import { PATIENT_GENDER_LABELS, type PatientGender } from "@/lib/patient-gender";
import { PagedListing } from "@/pages/paged-listing";
import { usePagedRead } from "@/pages/paged-read";
import { PlainHeading } from "@/pages/sort-heading";

import { PatientFilterPopover } from "./patient-filter-popover";
import {
  NO_PATIENT_FILTERS,
  parsePatientQuery,
  PATIENT_SEARCH,
  PATIENT_SEARCH_PAGE_SIZE,
  type PatientFilters,
  type PatientMatch,
} from "./search-api";

/** Each gender badge's tint: Male blue, Female pink, any other the plain badge. */
const GENDER_BADGE_TINTS: Readonly<Record<PatientGender, string | undefined>> = {
  female: "bg-female-soft text-female-soft-foreground",
  male: "bg-male-soft text-male-soft-foreground",
  other: undefined,
  unknown: undefined,
};

/**
 * The matching Patients, one row each: the official name, the full NRIC, the
 * gender as a badge (Male blue, Female pink) and both dates as `dd MMM yyyy`,
 * a missing Last visit shown as `—`, in the patient source's own order: no
 * column sorts. Each row is keyed by the Patient's id, which is kept and never
 * shown.
 *
 * Selecting a row puts its Patient into the tab's EHR context, replacing any
 * patient there with no confirmation, and goes to Home. A click anywhere on the
 * row selects it; the Patient Name is a button, so the keyboard and assistive
 * technology select it too, its click reaching the row's. The same Patient
 * selected again leaves the context as it was, so the signed-in layout reads
 * them no second time. No row marks the patient in context.
 */
function ResultsTable({ patients }: { patients: readonly PatientMatch[] }) {
  const navigate = useNavigate();
  const select = (patient: PatientMatch) => {
    setEhrContext({ patient: patient.id });
    void navigate("/");
  };
  return (
    <Table>
      <TableCaption className="sr-only">Matching patients</TableCaption>
      <TableHeader>
        <TableRow>
          <PlainHeading>Patient Name</PlainHeading>
          <PlainHeading>NRIC</PlainHeading>
          <PlainHeading>Gender</PlainHeading>
          <PlainHeading>Date of Birth</PlainHeading>
          <PlainHeading>Last Visit</PlainHeading>
        </TableRow>
      </TableHeader>
      <TableBody>
        {patients.map((patient) => (
          <TableRow className="cursor-pointer" key={patient.id} onClick={() => select(patient)}>
            <TableCell>
              <Button className="h-auto p-0 text-foreground" variant="link">
                {patient.name}
              </Button>
            </TableCell>
            <TableCell className="font-mono">{patient.nric}</TableCell>
            <TableCell>
              <Badge className={GENDER_BADGE_TINTS[patient.gender]} variant="secondary">
                {PATIENT_GENDER_LABELS[patient.gender]}
              </Badge>
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
 * again from its first page whenever its filter changes: the table,
 * the "Showing x to y of N patients" range with the numbered pager whenever
 * there is a match, or "No patients match your search". A failed read is
 * reported on the page's one error line, which the signed-in layout owns, and
 * offers Retry, which reads the same page of the same search again.
 */
function SearchResults({ filters, query }: { filters: PatientFilters; query: string }) {
  const reading = usePagedRead(PATIENT_SEARCH, {
    args: [query, filters],
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
        {({ rows }) => <ResultsTable patients={rows} />}
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
 * The results come in the patient source's own order, and the Filter panel
 * narrows them by gender. The filter narrows the search submitted, and keeps
 * across a new one. A change of it reads the results from their first page.
 *
 * The search and its filter live on the page alone: neither of them,
 * no name or NRIC, reaches the URL or the browser history, so leaving the page
 * forgets them. Before the first search the page reads nothing and shows only
 * the box: the Search Results card, its Filter button with it, appears with the
 * first search submitted.
 */
export function Search() {
  const [typed, setTyped] = useState("");
  const [submitted, setSubmitted] = useState<Submitted | null>(null);
  const [refusal, setRefusal] = useState<string | null>(null);
  const [filters, setFilters] = useState<PatientFilters>(NO_PATIENT_FILTERS);
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
      {submitted === null ? null : (
        <Card>
          <CardHeader className="flex items-center justify-between gap-4">
            <h2 className="font-headline text-headline-sm">Search Results</h2>
            <PatientFilterPopover applied={filters} onApply={setFilters} />
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <SearchResults filters={filters} key={submitted.generation} query={submitted.query} />
          </CardContent>
        </Card>
      )}
    </div>
  );
}
