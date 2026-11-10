import { ArrowDownIcon, ArrowUpDownIcon, ArrowUpIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { TableHead } from "@/components/ui/table";
import type { ColumnSort } from "@/lib/sorting";

/** How a listing is sorted, by whatever holds the sort: the sort, `null` for the default order, and its change. */
export interface ColumnSorting<B extends string> {
  onSort: (sort: ColumnSort<B> | null) => void;
  sort: ColumnSort<B> | null;
}

/**
 * The sort a press of the heading for `by` asks for next: ascending first,
 * then descending, then the listing's default order. A heading other than
 * the sorted one starts ascending and replaces the sort, since a listing
 * sorts by one column only.
 */
function nextSort<B extends string>(sort: ColumnSort<B> | null, by: B): ColumnSort<B> | null {
  if (sort?.by !== by) return { by, order: "ascending" };
  return sort.order === "ascending" ? { by, order: "descending" } : null;
}

/**
 * A sortable column's heading, its `by` the column the backend sorts the
 * listing by. It states its direction with `aria-sort`, and its label is a
 * ghost icon button, as the design's icon buttons are, showing the direction
 * — up ascending, down descending, both while unsorted. Each press moves
 * ascending → descending → the listing's default order.
 */
export function SortHeading<B extends string>({
  by,
  disabled = false,
  label,
  sorting: { onSort, sort },
}: {
  by: B;
  /** Holds the sort still, as the pager and the search are held. */
  disabled?: boolean;
  label: string;
  sorting: ColumnSorting<B>;
}) {
  const direction = sort?.by === by ? sort.order : "none";
  const Icon =
    direction === "ascending"
      ? ArrowUpIcon
      : direction === "descending"
        ? ArrowDownIcon
        : ArrowUpDownIcon;
  return (
    <TableHead aria-sort={direction} className="text-muted-foreground" scope="col">
      <Button
        className="-ml-2 h-8 px-2 text-muted-foreground"
        disabled={disabled}
        onClick={() => onSort(nextSort(sort, by))}
        size="sm"
        variant="ghost"
      >
        {label}
        <Icon aria-hidden="true" />
      </Button>
    </TableHead>
  );
}

/** A column's heading that cannot sort: its text alone. */
export function PlainHeading({ children }: { children: string }) {
  return (
    <TableHead className="text-muted-foreground" scope="col">
      {children}
    </TableHead>
  );
}
