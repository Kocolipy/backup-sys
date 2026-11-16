/**
 * A patient-source field as the SPA shows it: the source is the truth, so its
 * data is shown as received (`/docs/adr/0020-patient-source-data-is-shown-as-received.md`).
 * The backend answers each such field present, `null` when it is missing, or
 * `null` and named in its row's `invalid` when its format was wrong; the
 * lenient field decoder (`readObject(…).sourceFields()`, `./decode.ts`) reads
 * that into a {@link SourceField}, and {@link shown} turns it into the cell.
 * A code outside its closed set is present, and {@link byCode} finds what to
 * show for it.
 */

import type { ReactNode } from "react";

/** A field of a patient-source record: present with its value, missing, or in the wrong format. */
export type SourceField<T> =
  | { readonly state: "present"; readonly value: T }
  | { readonly state: "missing" }
  | { readonly state: "invalid" };

/** What a cell shows for a missing field: the em dash every empty cell shows. */
const MISSING = "—";

/** What a cell shows for a field whose format was wrong. */
const INVALID = "Error";

/** A cell's content for `field`: its value as `show` renders it, "—" when missing, "Error" when invalid. */
export function shown<T>(field: SourceField<T>, show: (value: T) => ReactNode): ReactNode {
  switch (field.state) {
    case "present":
      return show(field.value);
    case "missing":
      return MISSING;
    case "invalid":
      return INVALID;
  }
}

/** `field`'s value when it is present, and `otherwise` when it is missing or invalid. */
export function orElse<T, U>(field: SourceField<T>, otherwise: U): T | U {
  return field.state === "present" ? field.value : otherwise;
}

/**
 * What `table` holds for `code`, one of its own keys, or `otherwise` for any
 * other code — a code outside its closed set, which the source may send: a
 * label falls back to the raw code, and a badge to a neutral one. Only the
 * table's own keys count, so a code spelling an `Object` member is no member.
 */
export function byCode<C extends string, V>(
  table: Readonly<Record<C, V>>,
  code: string,
  otherwise: V,
): V {
  const known = Object.entries<V>(table).find(([key]) => key === code);
  return known === undefined ? otherwise : known[1];
}
