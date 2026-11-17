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

/** The leading column's heading, over the warnings: hidden from sight, read as "Missing information". */
export function MissingInformationHeading() {
  return (
    <TableHead scope="col">
      <span className="sr-only">Missing information</span>
    </TableHead>
  );
}

/**
 * The leading cell of `record`'s row: a warning icon when any of its
 * Mandatory elements `mandatory` is missing or in the wrong format, as the
 * backend classified it (ADR 0020) — no value is read — and empty otherwise.
 * The icon is a tab stop whose tooltip, on hover and on focus, is its
 * accessible name: "Critical information missing: " and each such element's
 * column, in `mandatory`'s order. It only informs: the row is shown in full.
 */
export function MissingInformationCell<K extends string>({
  mandatory,
  record,
}: {
  mandatory: readonly MandatoryElement<K>[];
  record: Readonly<Record<K, SourceField<unknown>>>;
}) {
  const absent = mandatory
    .filter(({ element }) => record[element].state !== "present")
    .map(({ label }) => label);
  if (absent.length === 0) return <TableCell />;
  const warning = `Critical information missing: ${absent.join(", ")}`;
  return (
    <TableCell>
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
