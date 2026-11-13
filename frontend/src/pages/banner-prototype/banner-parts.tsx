import { ChevronDown } from "lucide-react";
import type { ReactNode } from "react";

import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { formatCalendarDate, formatInstant } from "@/lib/time";
import { cn } from "@/lib/utils";

import type { ServiceRecord } from "./mock-patient";

/** The pieces every banner layout shares: chips, labelled fields, alert groups. */

/** How many allergies show as chips before the rest fold into "+N more". */
const VISIBLE_ALLERGIES = 3;

export const CHIP = "rounded-xs px-3 py-0.5 text-xs font-bold tracking-wide uppercase";
export const ALERT_CHIP = cn(CHIP, "bg-destructive text-destructive-foreground");
export const BANNER_LABEL = "text-xs tracking-widest text-primary-foreground/70 uppercase";

const SERVICE_CHIP: Record<ServiceRecord["service"], string> = {
  "AIR FORCE": "bg-ring text-primary-foreground",
  ARMY: "bg-secondary text-secondary-foreground",
  NAVY: "bg-card text-primary",
};

export function ServiceChip({ service }: { service: ServiceRecord["service"] }) {
  return <span className={cn(CHIP, "inline-block px-4", SERVICE_CHIP[service])}>{service}</span>;
}

/** A label stacked over its value, inside a `<dl>`. */
export function Field({
  children,
  className,
  label,
}: {
  children: ReactNode;
  className?: string;
  label: string;
}) {
  return (
    <div className={cn("flex flex-col gap-1", className)}>
      <dt className={BANNER_LABEL}>{label}</dt>
      <dd className="font-semibold">{children}</dd>
    </div>
  );
}

/** A label beside its value on one line, inside a `<dl>`. */
export function InlineField({
  children,
  className,
  label,
}: {
  children: ReactNode;
  className?: string;
  label: string;
}) {
  return (
    <div className={cn("flex items-baseline gap-3", className)}>
      <dt className={cn(BANNER_LABEL, "w-20 shrink-0")}>{label}</dt>
      <dd className="font-semibold">{children}</dd>
    </div>
  );
}

export function AlertGroup({ label, children }: { children: ReactNode; label: string }) {
  return (
    <div className="flex items-center gap-2">
      <span className={BANNER_LABEL}>{label}:</span>
      {children}
    </div>
  );
}

export function AlertChips({ items, label }: { items: readonly string[]; label: string }) {
  return (
    <AlertGroup label={label}>
      {items.map((item) => (
        <span className={ALERT_CHIP} key={item}>
          {item}
        </span>
      ))}
    </AlertGroup>
  );
}

/** The first few allergies as chips, the rest behind a "+N more" popover listing them all. */
export function Allergies({ allergies }: { allergies: readonly string[] }) {
  const shown = allergies.slice(0, VISIBLE_ALLERGIES);
  const rest = allergies.length - shown.length;
  return (
    <AlertGroup label="Allergies">
      {shown.map((a) => (
        <span className={ALERT_CHIP} key={a}>
          {a}
        </span>
      ))}
      {rest > 0 && (
        <Popover>
          <PopoverTrigger
            className={cn(
              CHIP,
              "flex items-center gap-1 bg-card text-destructive hover:bg-danger-soft focus-visible:ring-2 focus-visible:ring-primary-foreground focus-visible:outline-none",
            )}
          >
            +{rest} more
            <ChevronDown aria-hidden className="size-3" />
          </PopoverTrigger>
          <PopoverContent align="end" className="w-80">
            <p className="mb-3 text-sm font-semibold">All allergies ({allergies.length})</p>
            <ul className="flex flex-wrap gap-2">
              {allergies.map((a) => (
                <li className={ALERT_CHIP} key={a}>
                  {a}
                </li>
              ))}
            </ul>
          </PopoverContent>
        </Popover>
      )}
    </AlertGroup>
  );
}

/** A select over the service records, styled for the banner's blue. */
export function ServiceRecordSelect({
  labelledBy,
  onChange,
  records,
  value,
}: {
  labelledBy: string;
  onChange: (id: string) => void;
  records: readonly ServiceRecord[];
  value: string | undefined;
}) {
  return (
    <Select onValueChange={onChange} value={value}>
      <SelectTrigger
        aria-labelledby={labelledBy}
        className="h-10 w-full rounded-xs border-primary-foreground/30 bg-primary-foreground/15 font-semibold text-primary-foreground [&_svg:not([class*='text-'])]:text-primary-foreground"
      >
        <SelectValue />
      </SelectTrigger>
      <SelectContent position="popper">
        {records.map((r) => (
          <SelectItem key={r.id} value={r.id}>
            {r.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

/**
 * When a box's data was last synced, as its date and time in Singapore.
 * Display only: a sync is never triggered from here.
 */
export function SyncedAt({ className, syncedAt }: { className?: string; syncedAt: string }) {
  const [date, minute] = formatInstant(syncedAt).split(" ");
  return (
    <p className={cn("text-xs text-primary-foreground/60", className)}>
      Synced at{" "}
      <time dateTime={syncedAt}>
        {formatCalendarDate(date)} {minute}
      </time>
    </p>
  );
}
