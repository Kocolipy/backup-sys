/**
 * A listing's sort: the data a sortable heading sets, and the query
 * parameters an API module sends it as. It sits in `lib/` so a page's
 * `*-api.ts`, which imports no component (`lb-api-modules-are-data`), can name
 * the sort its read takes and send it.
 */

/** A listing's sort: one column, in one direction. */
export interface ColumnSort<B extends string> {
  by: B;
  order: "ascending" | "descending";
}

/**
 * The query parameters `sort` is sent as: `sortBy` and `sortOrder`, always
 * together and each encoded as one value, or none for the listing's default
 * order. A listing joins them to its own search and filter parameters.
 */
export const sortParameters = <B extends string>(sort: ColumnSort<B> | null): string[] =>
  sort === null
    ? []
    : [`sortBy=${encodeURIComponent(sort.by)}`, `sortOrder=${encodeURIComponent(sort.order)}`];
