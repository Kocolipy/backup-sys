/**
 * A listing's sort, as data: what a sortable heading sets and an API module
 * sends. It sits in `lib/` so a page's `*-api.ts`, which imports no component
 * (`lb-api-modules-are-data`), can name the sort its read takes.
 */

/** A listing's sort: one column, in one direction. */
export interface ColumnSort<B extends string> {
  by: B;
  order: "ascending" | "descending";
}
