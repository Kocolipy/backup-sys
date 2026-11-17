/**
 * The paged read's wire vocabulary: what every paged administrative read asks
 * for and answers, by ADR 0017's one set of rules — `startIndex` one-based, a
 * `count`, and an answer of one page beside the `totalResults` and the
 * `startIndex` it starts at.
 *
 * Data alone, so a page's `*-api.ts` module, which declares its endpoints as
 * data and imports no hook, can declare a paged read with it:
 * {@link pagedPath} is the one query string a paged declaration builds, and
 * {@link pageDecoder} the one decoder of the page around its rows, a read
 * that drops entries — a Patient search's — included. The hook
 * that reads such a declaration is `usePagedRead` (`@/pages/paged-read`).
 */

import { readObject } from "./decode";

/** Which page a paged read asks for: from the one-based `startIndex`, `count` rows. */
export interface PageAt {
  startIndex: number;
  count: number;
}

/**
 * One page of a paged read: its rows, where it starts among every row matched,
 * and the total. A page whose read drops entries it cannot show — a Patient
 * search drops a match it cannot select — says how many it dropped, so the
 * page still spans every entry it held: its range and its next page are
 * counted from them as well as its rows.
 */
export interface Page<R> {
  rows: readonly R[];
  startIndex: number;
  totalResults: number;
  /** How many of the page's entries are not among `rows`; none when left out. */
  droppedResults?: number;
}

/**
 * `path` with its query: the listing's own, when it has any, then the page. The
 * backend reads its parameters by name, so their order says nothing.
 */
export const pagedPath = (path: string, { count, startIndex }: PageAt, query = ""): string =>
  `${path}?${query === "" ? "" : `${query}&`}startIndex=${startIndex}&count=${count}`;

/**
 * The decoder of a page whose rows arrive in `rowsField`, each read by
 * `decodeRow`; `what` names the page in its errors. A page holding any field
 * but its three is refused: they say where the page sits, so one this page
 * does not count from would change what they mean. A read that drops entries
 * it cannot show declares `dropsEntries`, and its page then carries a fourth,
 * `droppedResults`, always: how many of the page's entries are not among its
 * rows.
 */
export const pageDecoder =
  <R>(
    what: string,
    rowsField: string,
    decodeRow: (value: unknown) => R,
    { dropsEntries = false }: { dropsEntries?: boolean } = {},
  ) =>
  (value: unknown): Page<R> => {
    const page = readObject(value, what);
    page.onlyFields(
      dropsEntries
        ? ["totalResults", "startIndex", "droppedResults", rowsField]
        : ["totalResults", "startIndex", rowsField],
    );
    const at = {
      rows: page.array(rowsField, decodeRow),
      startIndex: page.integer("startIndex"),
      totalResults: page.integer("totalResults"),
    };
    return dropsEntries ? { ...at, droppedResults: page.integer("droppedResults") } : at;
  };
