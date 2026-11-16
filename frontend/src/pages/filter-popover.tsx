import { type ReactNode, useState } from "react";
import { ListFilterIcon } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";

/**
 * A listing's filter panel behind its Filter button, which says how many of
 * the filters applied narrow the listing: "Filter" with none, "Filter, 2 set"
 * and a badge of 2 with two. The panel's form, `children`, is mounted each
 * time the panel opens, so it starts from the filters applied and an edit
 * never applied is forgotten when it closes; it calls `close` once an Apply or
 * Reset has applied, and a refused Apply leaves the panel open on the field to
 * correct.
 */
export function FilterPopover({
  children,
  className,
  setCount,
}: {
  children: (close: () => void) => ReactNode;
  /** The panel's width, as the form it holds needs. */
  className: string;
  /** How many of the filters applied narrow the listing. */
  setCount: number;
}) {
  const [open, setOpen] = useState(false);
  return (
    <Popover onOpenChange={setOpen} open={open}>
      <PopoverTrigger asChild>
        <Button
          aria-label={setCount === 0 ? "Filter" : `Filter, ${setCount} set`}
          size="sm"
          variant="outline"
        >
          <ListFilterIcon aria-hidden="true" />
          Filter
          {setCount === 0 ? null : (
            <Badge aria-hidden="true" variant="secondary">
              {setCount}
            </Badge>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className={className}>
        {children(() => setOpen(false))}
      </PopoverContent>
    </Popover>
  );
}
