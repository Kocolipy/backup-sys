import { useId } from "react";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { DatePrecision } from "@/lib/date-range";

/** The native control each precision's ends are typed in. */
const INPUT_TYPE: Readonly<Record<DatePrecision, "date" | "datetime-local">> = {
  date: "date",
  "date-time": "datetime-local",
};

/**
 * One date range's from–to pair, a group named by its label, each end typed in
 * the native control of the column's `precision`: an `<input type="date">`,
 * `YYYY-MM-DD`, for a calendar date, and an `<input type="datetime-local">`,
 * `YYYY-MM-DDTHH:mm`, for a date-time, which holds no value until a time is
 * chosen beside the date. Either end is `""` when left open. A refusal of the
 * range marks both ends invalid and describes them by the message shown
 * beneath.
 */
export function DateRangeFields({
  from,
  label,
  onChange,
  precision,
  refusal,
  to,
}: {
  from: string;
  label: string;
  onChange: (range: { from: string; to: string }) => void;
  precision: DatePrecision;
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
      type={INPUT_TYPE[precision]}
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
