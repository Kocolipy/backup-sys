import { useId } from "react";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

/**
 * One date range's from–to pair of `<input type="date">`, a group named by its
 * label, each end `YYYY-MM-DD` or `""` for an end left open. A refusal of the
 * range marks both ends invalid and describes them by the message shown
 * beneath.
 */
export function DateRangeFields({
  from,
  label,
  onChange,
  refusal,
  to,
}: {
  from: string;
  label: string;
  onChange: (range: { from: string; to: string }) => void;
  refusal: string | null;
  to: string;
}) {
  const labelId = useId();
  const refusalId = useId();
  const field = (end: "From" | "To") => (
    <Input
      aria-describedby={refusal === null ? undefined : refusalId}
      aria-invalid={refusal !== null}
      aria-label={end}
      onChange={(event) =>
        onChange(
          end === "From" ? { from: event.target.value, to } : { from, to: event.target.value },
        )
      }
      type="date"
      value={end === "From" ? from : to}
    />
  );
  return (
    <div aria-labelledby={labelId} className="flex flex-col gap-1" role="group">
      <Label id={labelId}>{label}</Label>
      <div className="flex items-center gap-2">
        {field("From")}
        <span aria-hidden="true" className="text-muted-foreground">
          –
        </span>
        {field("To")}
      </div>
      {refusal === null ? null : (
        <p className="text-sm text-destructive" id={refusalId}>
          {refusal}
        </p>
      )}
    </div>
  );
}
