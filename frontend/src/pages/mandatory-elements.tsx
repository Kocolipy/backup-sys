import { TriangleAlertIcon } from "lucide-react";

import { TableCell, TableHead } from "@/components/ui/table";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import type { SourceField } from "@/lib/source-field";

/**
 * A Mandatory element of a history's record: the record's field `element`,
 * named by `label`, the exact heading of its column. Each history declares its
 * own beside its table, in the table's column order.
 */
export interface MandatoryElement<K extends string> {
  readonly element: K;
  readonly label: string;
}

/**
 * The leading column hugs the first data column: no padding of its own on that
 * side, and that column's cell — the next sibling — padded 10px from it.
 */
const LEADING_COLUMN = "pr-0 [&+td]:pl-2.5 [&+th]:pl-2.5";

/** The leading column's heading, over the warnings: hidden from sight, read as "Missing information". */
export function MissingInformationHeading() {
  return (
    <TableHead className={LEADING_COLUMN} scope="col">
      <span className="sr-only">Missing information</span>
    </TableHead>
  );
}

type MandatoryRecord<K extends string> = Readonly<Record<K, SourceField<unknown>>>;

/**
 * The columns of `record`'s Mandatory elements `mandatory` that are missing or
 * in the wrong format, as the backend classified them (ADR 0020) — no value is
 * read — in `mandatory`'s order.
 */
function absentFrom<K extends string>(
  mandatory: readonly MandatoryElement<K>[],
  record: MandatoryRecord<K>,
): string[] {
  return mandatory
    .filter(({ element }) => record[element].state !== "present")
    .map(({ label }) => label);
}

/**
 * Whether any of `records` warns, so the table shows the leading column: a
 * page with no row warning has none, its heading included.
 */
export function anyWarns<K extends string>(
  mandatory: readonly MandatoryElement<K>[],
  records: readonly MandatoryRecord<K>[],
): boolean {
  return records.some((record) => absentFrom(mandatory, record).length > 0);
}

/**
 * The leading cell of `record`'s row, on a page where some row warns
 * ({@link anyWarns}): a warning icon when any of its Mandatory elements
 * `mandatory` is missing or in the wrong format, and empty otherwise.
 * The icon is a tab stop whose tooltip, on hover and on focus, is its
 * accessible name: "Critical information missing: " and each such element's
 * column, in `mandatory`'s order. It only informs: the row is shown in full.
 */
export function MissingInformationCell<K extends string>({
  mandatory,
  record,
}: {
  mandatory: readonly MandatoryElement<K>[];
  record: MandatoryRecord<K>;
}) {
  const absent = absentFrom(mandatory, record);
  if (absent.length === 0) return <TableCell className={LEADING_COLUMN} />;
  const warning = `Critical information missing: ${absent.join(", ")}`;
  return (
    <TableCell className={LEADING_COLUMN}>
      <Tooltip>
        <TooltipTrigger asChild>
          <span aria-label={warning} role="img" tabIndex={0}>
            <TriangleAlertIcon
              aria-hidden="true"
              className="text-warning-soft-foreground"
              size={16}
            />
          </span>
        </TooltipTrigger>
        <TooltipContent>{warning}</TooltipContent>
      </Tooltip>
    </TableCell>
  );
}
