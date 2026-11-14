/**
 * A filter on a closed set's codes, as the query parameters an API module
 * sends it as. It sits in `lib/` beside `sorting.ts` so every listing's
 * `*-api.ts` (`lb-api-modules-are-data`) sends a set of codes alike, keeping
 * only which parameter names which set.
 */

/**
 * `parameter` once for each of `set`'s codes `chosen` holds, in the set's own
 * order, however they were chosen. It walks the closed set and asks `chosen`
 * of each code, so it reads nothing of `chosen` but whether it holds a code.
 */
export const codeParameters = <C extends string>(
  parameter: string,
  set: readonly C[],
  chosen: readonly C[],
): string[] => set.filter((code) => chosen.includes(code)).map((code) => `${parameter}=${code}`);
