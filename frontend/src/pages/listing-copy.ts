/**
 * The words every paged table says about its rows, in one place: each names
 * its rows "records", in its pager, its empty line and its loading line, and
 * no table has a noun of its own.
 */

/**
 * Whether a listing's rows are narrowed, and how — the one thing its empty
 * and loading copy are chosen from:
 *
 * - `"none"`: nothing narrows it — a table with no filter or search, or one
 *   with none set.
 * - `"filters"`: a filter is set.
 * - `"search"`: a search term is entered.
 * - `"patient-search"`: the Search page's results, which a search always
 *   narrows, and which say they are searching while read.
 */
export type Narrowing = "none" | "filters" | "search" | "patient-search";

/** What a listing says with no rows, and while its first read is in flight. */
interface ListingCopy {
  empty: string;
  loading: string;
}

const LOADING = "Loading records…";
const NO_SEARCH_MATCH = "No records match your search.";

const LISTING_COPY: Readonly<Record<Narrowing, ListingCopy>> = {
  filters: { empty: "No records match the filters.", loading: LOADING },
  none: { empty: "No records found.", loading: LOADING },
  "patient-search": { empty: NO_SEARCH_MATCH, loading: "Searching records…" },
  search: { empty: NO_SEARCH_MATCH, loading: LOADING },
};

/** The empty and loading copy of a listing narrowed as `narrowing` says. */
export function listingCopyOf(narrowing: Narrowing): ListingCopy {
  return LISTING_COPY[narrowing];
}

/** `total` records, as a pager's position line counts them: "1 record", "45 records". */
export function recordsCounted(total: number): string {
  return `${total} ${total === 1 ? "record" : "records"}`;
}
