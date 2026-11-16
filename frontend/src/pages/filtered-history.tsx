/**
 * The filtered, sorted history listing every history page renders for the
 * patient in context: the FFI, NUMC, ARMC and Medication Histories. Shared by
 * those pages, so it sits beside them in `src/pages/`; each page hands it its
 * read, its Filter panel, its copy and its table, and nothing here names a
 * history's column.
 */

import { type ComponentType, type ReactNode, useState } from "react";

import type { Permission } from "@/auth/api";
import type { ColumnSort } from "@/lib/sorting";
import { NoPatientInContext } from "@/pages/for-patient-in-context";
import { PagedListing } from "@/pages/paged-listing";
import { type PagedDeclaration, usePagedRead } from "@/pages/paged-read";
import type { ColumnSorting } from "@/pages/sort-heading";

/** The copy a history shows, all of it the page's own. */
interface FilteredHistoryCopy {
  /** The empty line with no filter applied: the patient has none. */
  empty: string;
  /** The empty line with a filter applied: none matches. */
  emptyFiltered: string;
  /** The error line's copy for a read that failed. */
  failure: string;
  /** The line shown while the first read is in flight. */
  loading: string;
  /** What the rows are, lower-case and plural, as the pager's position line names them. */
  noun: string;
  /** The pager's accessible name. */
  pagerLabel: string;
}

/** What a history's table renders: the page's rows, its sort, and whether a read is pending. */
interface HistoryTableProps<R, C extends string> {
  /** Whether a read is in flight beside the rows, so the sort headings are held still. */
  pending: boolean;
  rows: readonly R[];
  sorting: ColumnSorting<C>;
}

/**
 * The history `declaration` reads of the patient `patientId`, `pageSize`
 * rows a page, through the shared paged listing, naming the range on a single
 * page too, the page shown held while the next loads. A `404` — the source
 * holds no patient under the id — is "No patient in context", an answer
 * rather than a failure; any other failure is the page's `failure` copy on
 * the page's one error line, with Try again.
 *
 * It sorts by the columns `table` offers a sort heading for, in the source's
 * own order until a heading is pressed, and `filterPopover`, at its top
 * right, narrows it. A change of either reads the history from its first
 * page, the filters combined with AND and with the sort. Both live here
 * alone: neither reaches the URL, and a new patient in context, a new
 * history, starts with neither — `noFilters` and no sort. With a filter
 * applied, as `countFilters` counts them, an empty history says
 * `emptyFiltered`, that none matches, rather than `empty`, that the patient
 * has none.
 */
export function FilteredHistory<R, F, C extends string>({
  copy,
  countFilters,
  declaration,
  filterPopover: FilterPopover,
  noFilters,
  pageSize,
  patientId,
  table,
}: {
  copy: FilteredHistoryCopy;
  countFilters: (filters: F) => number;
  declaration: PagedDeclaration<
    R,
    [patientId: string, filters: F, sort: ColumnSort<C> | null],
    Permission,
    true
  >;
  filterPopover: ComponentType<{ applied: F; onApply: (filters: F) => void }>;
  noFilters: F;
  pageSize: number;
  patientId: string;
  table: (props: HistoryTableProps<R, C>) => ReactNode;
}): ReactNode {
  const [filters, setFilters] = useState<F>(noFilters);
  const [sort, setSort] = useState<ColumnSort<C> | null>(null);
  const reading = usePagedRead(declaration, {
    args: [patientId, filters, sort],
    failureMessage: copy.failure,
    pageSize,
  });
  const { view } = reading;
  if (view.kind === "absent") return <NoPatientInContext />;
  // Rows are shown only once a page is read, so a read in flight beside them
  // is one the listing holds them still for.
  const pending = view.kind === "loading";
  return (
    <>
      <div className="flex justify-end">
        <FilterPopover applied={filters} onApply={setFilters} />
      </div>
      <PagedListing
        copy={{
          empty: countFilters(filters) === 0 ? copy.empty : copy.emptyFiltered,
          loading: copy.loading,
          noun: copy.noun,
          pagerLabel: copy.pagerLabel,
        }}
        onePage="shown"
        read={{ ...reading, view }}
      >
        {({ rows }) => table({ pending, rows, sorting: { onSort: setSort, sort } })}
      </PagedListing>
    </>
  );
}
