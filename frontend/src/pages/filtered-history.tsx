/**
 * The filtered, sorted history listing every history page renders for the
 * patient in context: the FFI, NUMC, ARMC, HSP and Medication Histories. Shared by
 * those pages, so it sits beside them in `src/pages/`; each page hands it its
 * read, its Filter panel, its failure copy and its table, and nothing here
 * names a history's column or its rows.
 */

import { type ComponentType, type ReactNode, useState } from "react";

import type { Permission } from "@/auth/api";
import type { ColumnSort } from "@/lib/sorting";
import { NoPatientInContext } from "@/pages/for-patient-in-context";
import { PagedListing } from "@/pages/paged-listing";
import { type PagedDeclaration, usePagedRead } from "@/pages/paged-read";
import type { ColumnSorting } from "@/pages/sort-heading";

/** The copy a history names itself by; what it says of its rows is the shared listing's. */
interface FilteredHistoryCopy {
  /** The error line's copy for a read that failed. */
  failure: string;
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
 * right — beside `heading` on its left, when the page hands one — narrows it. A change of either reads the history from its first
 * page, the filters combined with AND and with the sort. Both live here
 * alone: neither reaches the URL, and a new patient in context, a new
 * history, starts with neither — `noFilters` and no sort. With a filter
 * applied, as `countFilters` counts them, the listing is narrowed by
 * filters, so an empty history says no records match the filters rather
 * than that none is found.
 */
export function FilteredHistory<R, F, C extends string>({
  copy,
  countFilters,
  declaration,
  filterPopover: FilterPopover,
  heading,
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
  /** The history's own heading, on the Filter button's row; none leaves the button alone. */
  heading?: ReactNode;
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
      <div className={heading ? "flex items-center justify-between gap-4" : "flex justify-end"}>
        {heading}
        <FilterPopover applied={filters} onApply={setFilters} />
      </div>
      <PagedListing
        narrowing={countFilters(filters) === 0 ? "none" : "filters"}
        onePage="shown"
        pagerLabel={copy.pagerLabel}
        read={{ ...reading, view }}
      >
        {({ rows }) => table({ pending, rows, sorting: { onSort: setSort, sort } })}
      </PagedListing>
    </>
  );
}
