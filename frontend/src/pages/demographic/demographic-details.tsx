import type { LucideIcon } from "lucide-react";
import {
  ChevronDownIcon,
  MailIcon,
  MapPinIcon,
  PhoneIcon,
  TriangleAlertIcon,
  UserIcon,
} from "lucide-react";
import { useState } from "react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { formatCalendarDateTwoDigitDay } from "@/lib/time";

import type {
  MilitaryHistory,
  NextOfKin,
  PatientSummary,
  ServiceRecord,
} from "@/pages/patient-api";
import { usePatientInContext } from "@/pages/patient-in-context";

/** What a field the patient has no value for shows. */
const NONE = "-";

/** The service record slots the overview always lays out, filled or not. */
const SERVICE_RECORD_SLOTS = 3;

/** A patient with no Military history: every slot and date unknown. */
const NO_MILITARY_HISTORY: MilitaryHistory = { serviceRecords: [] };

/** `parts` that are present, joined by " / ", or {@link NONE} when none is. */
const joined = (...parts: (string | undefined)[]): string => {
  const present = parts.filter((part) => part !== undefined);
  return present.length === 0 ? NONE : present.join(" / ");
};

/** A `YYYY-MM-DD` date as `dd MMM yyyy` (`08 Sep 2037`), or {@link NONE} for none. */
const dateOrNone = (date: string | undefined): string =>
  date === undefined ? NONE : formatCalendarDateTwoDigitDay(date);

const isEmpty = (record: ServiceRecord) => Object.keys(record).length === 0;

/** Label–value rows, each value a plain text node. */
function Rows({ className, rows }: { className: string; rows: [label: string, value: string][] }) {
  return (
    <dl className={className}>
      {rows.map(([label, value]) => (
        <div className="flex items-baseline justify-between gap-4 border-b py-2.5" key={label}>
          <dt className="text-muted-foreground">{label}</dt>
          <dd className="text-right font-medium">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

/**
 * One service record slot: open at first when it holds a record, folded when
 * it is empty, and showing every field it lacks as unknown.
 */
function ServiceRecordSlot({ number, record }: { number: number; record: ServiceRecord }) {
  const [open, setOpen] = useState(!isEmpty(record));
  // Stryker disable all: styling alone — the first record's label is tinted and the chevron turns with the slot; jsdom renders no CSS, and the slot's state is the trigger's aria-expanded, which the suite asserts.
  const labelClass =
    number === 1
      ? "text-xs font-semibold tracking-widest text-primary uppercase"
      : "text-xs font-semibold tracking-widest text-muted-foreground uppercase";
  const chevronClass = open
    ? "size-4 rotate-180 transition-transform"
    : "size-4 transition-transform";
  // Stryker restore all
  return (
    <Collapsible className="border-b" onOpenChange={setOpen} open={open}>
      <CollapsibleTrigger className="flex w-full items-center justify-between py-4 text-left">
        <span className={labelClass}>Service record {number}</span>
        <ChevronDownIcon aria-hidden className={chevronClass} />
      </CollapsibleTrigger>
      <CollapsibleContent>
        <Rows
          className="flex flex-col px-2 pb-2 [&>div:last-child]:border-b-0"
          rows={[
            ["Service Type", record.serviceType ?? NONE],
            ["Rank", record.rank ?? NONE],
            ["Unit / Service", joined(record.unit, record.service)],
            ["Vocation", record.vocation ?? NONE],
          ]}
        />
      </CollapsibleContent>
    </Collapsible>
  );
}

/** The patient's Military history: at least three service record slots, then its dates. */
function MilitaryOverview({ history }: { history: MilitaryHistory }) {
  const records = history.serviceRecords;
  const slots = Array.from(
    { length: Math.max(SERVICE_RECORD_SLOTS, records.length) },
    (_, index): ServiceRecord => records[index] ?? {},
  );
  return (
    <Card aria-labelledby="military-overview" className="w-full" role="region">
      <CardHeader>
        <CardTitle className="uppercase" id="military-overview">
          Military Overview
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col">
        {slots.map((record, index) => (
          <ServiceRecordSlot key={index} number={index + 1} record={record} />
        ))}
        <Rows
          className="flex flex-col pt-6"
          rows={[
            ["Enlistment Date", dateOrNone(history.enlistmentDate)],
            ["ORD Date", dateOrNone(history.ordDate)],
            ["MR Date", dateOrNone(history.mrDate)],
            ["ROD Date", dateOrNone(history.rodDate)],
          ]}
        />
      </CardContent>
    </Card>
  );
}

/** A card's title, upper-case, with its icon before it; `className` styles the title whole. */
function IconTitle({
  className,
  icon: Icon,
  id,
  title,
}: {
  className: string;
  icon: LucideIcon;
  id: string;
  title: string;
}) {
  return (
    <CardTitle className={className} id={id}>
      <Icon aria-hidden className="size-5" />
      {title}
    </CardTitle>
  );
}

/** How the patient is reached: their phone, email and residential address. */
function ContactDetails({ patient }: { patient: PatientSummary }) {
  const rows: [label: string, value: string | undefined, icon: LucideIcon][] = [
    ["Phone", patient.phone, PhoneIcon],
    ["Email address", patient.email, MailIcon],
    ["Residential address", patient.residentialAddress, MapPinIcon],
  ];
  return (
    <Card aria-labelledby="contact-details" className="w-full" role="region">
      <CardHeader>
        <IconTitle
          className="flex items-center gap-3 uppercase [&>svg]:text-primary"
          icon={UserIcon}
          id="contact-details"
          title="Contact Details"
        />
      </CardHeader>
      <CardContent>
        <dl className="flex flex-col">
          {rows.map(([label, value, Icon]) => (
            <div className="flex items-center gap-3 border-b py-3 last:border-b-0" key={label}>
              <span className="flex size-9 shrink-0 items-center justify-center rounded-md bg-muted text-muted-foreground">
                <Icon aria-hidden className="size-4" />
              </span>
              <div className="flex flex-col gap-0.5">
                <dt className="text-xs font-semibold tracking-widest text-muted-foreground uppercase">
                  {label}
                </dt>
                <dd className="font-medium">{value ?? NONE}</dd>
              </div>
            </div>
          ))}
        </dl>
      </CardContent>
    </Card>
  );
}

/** Who to call for the patient: their Next of kin, in a danger-tinted panel. */
function NextOfKinCard({ nextOfKin }: { nextOfKin?: NextOfKin }) {
  return (
    <Card aria-labelledby="next-of-kin" className="w-full" role="region">
      <CardHeader>
        <IconTitle
          className="flex items-center gap-3 text-destructive uppercase"
          icon={TriangleAlertIcon}
          id="next-of-kin"
          title="Next of Kin / Emergency"
        />
      </CardHeader>
      <CardContent>
        {nextOfKin === undefined ? (
          <p className="text-sm text-muted-foreground">No next of kin recorded</p>
        ) : (
          <dl className="grid grid-cols-2 gap-x-6 gap-y-1 rounded-lg border border-destructive/30 bg-danger-soft p-4">
            <div className="col-span-2 flex flex-col gap-1">
              <dt className="text-xs font-semibold tracking-widest text-destructive uppercase">
                Name
              </dt>
              <dd className="text-lg font-bold">{nextOfKin.name}</dd>
            </div>
            <div className="flex flex-col gap-1 pt-2">
              <dt className="text-xs font-semibold tracking-widest text-muted-foreground uppercase">
                Relationship
              </dt>
              <dd className="font-medium">{nextOfKin.relationship ?? NONE}</dd>
            </div>
            <div className="flex flex-col gap-1 pt-2">
              <dt className="text-xs font-semibold tracking-widest text-muted-foreground uppercase">
                Contact number
              </dt>
              <dd className="font-medium">{nextOfKin.phone ?? NONE}</dd>
            </div>
          </dl>
        )}
      </CardContent>
    </Card>
  );
}

/**
 * The Demographic page's details of the patient in context, beneath the
 * Patient card: their Military Overview beside their Contact Details and Next
 * of Kin. Rendered from the signed-in layout's one read of the patient in
 * context (`usePatientInContext`), reading nothing itself; with no patient
 * loaded — none in context, a `404`, no `patient:read`, still loading or
 * failed — it renders nothing, the Patient card saying why. A field the
 * patient has no value for shows "-". Every value is a plain text node.
 */
export function DemographicDetails() {
  const view = usePatientInContext();
  // Stryker disable next-line ConditionalExpression,StringLiteral: equivalent — the patient in context is never `stale` (`patient-in-context.ts`), so `ready` alone reaches past this.
  if (view.kind !== "ready" && view.kind !== "stale") return null;
  const patient = view.data;
  return (
    <div className="grid w-full items-start gap-4 lg:grid-cols-3">
      <div className="min-w-0">
        <MilitaryOverview history={patient.militaryHistory ?? NO_MILITARY_HISTORY} />
      </div>
      <div className="flex min-w-0 flex-col gap-4 lg:col-span-2">
        <ContactDetails patient={patient} />
        <NextOfKinCard nextOfKin={patient.nextOfKin} />
      </div>
    </div>
  );
}
