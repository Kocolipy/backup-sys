import type { ReactNode } from "react";

import { type ReadView, useGatedRead } from "@/auth/use-gated-read";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { type Clock, completedYears, formatCalendarDate, today, systemClock } from "@/lib/time";

import { PATIENT, type PatientGender, type PatientSummary } from "./home-api";

const NOTE_CLASS = "text-sm text-muted-foreground";

const NO_PATIENT = <p className={NOTE_CLASS}>No patient in context</p>;

const GENDER_LABELS: Readonly<Record<PatientGender, string>> = {
  female: "Female",
  male: "Male",
  other: "Other",
  unknown: "Unknown",
};

/** The card headed Patient, holding `children` — a note, or nothing. */
function PlainCard({ children }: { children: ReactNode }) {
  return (
    <Card className="w-full">
      <CardHeader>
        <CardTitle>Patient</CardTitle>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}

/**
 * The loaded patient as a banner: their name as its heading, and beneath it
 * their fields, each a plain text node — whatever the backend sent is shown as
 * the text it is, never interpreted as markup.
 */
function PatientBanner({ clock, patient }: { clock: Clock; patient: PatientSummary }) {
  const rows: [label: string, value: string][] = [
    ["NRIC", patient.nric],
    ["Gender", GENDER_LABELS[patient.gender]],
    ["Age", String(completedYears(patient.birthDate, today(clock)))],
    ["Date of birth", formatCalendarDate(patient.birthDate)],
  ];
  return (
    <section
      aria-label="Patient"
      className="flex w-full flex-col gap-6 rounded-xl bg-linear-to-r from-ring to-primary p-5 text-primary-foreground shadow-md"
    >
      <h2 className="text-4xl font-black tracking-tight">{patient.name}</h2>
      <dl className="flex flex-wrap gap-x-10 gap-y-4 text-sm">
        {rows.map(([label, value]) => (
          <div className="flex flex-col gap-1" key={label}>
            <dt className="text-[10px] font-bold tracking-widest text-primary-foreground/60 uppercase">
              {label}
            </dt>
            <dd className="font-semibold">{value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

/**
 * What Home shows for each state of the read: the banner once the patient has
 * loaded, and the plain card otherwise. A failure leaves the card empty: its
 * copy is on the page's error line, and the card offers no retry.
 */
function PatientReading({ clock, view }: { clock: Clock; view: ReadView<PatientSummary, true> }) {
  switch (view.kind) {
    case "not-permitted":
      return (
        <PlainCard>
          <p className={NOTE_CLASS}>Your account has no access to patients.</p>
        </PlainCard>
      );
    case "loading":
      return (
        <PlainCard>
          <p className={NOTE_CLASS}>Loading the patient…</p>
        </PlainCard>
      );
    case "absent":
      return <PlainCard>{NO_PATIENT}</PlainCard>;
    case "failed":
      return <PlainCard>{null}</PlainCard>;
    default:
      // `ready`, or `stale` — which this card never produces: a new patient is
      // a new path, so a new first read, and the same patient is never re-read.
      return <PatientBanner clock={clock} patient={view.data} />;
  }
}

/**
 * The read of the patient in context, with `patient:read`: once on mount, and
 * again each time the patient in context changes. A component of its own so
 * that only a card with a patient in context calls the gated read at all.
 */
function PatientInContext({ clock, patientId }: { clock: Clock; patientId: string }) {
  const { view } = useGatedRead(PATIENT, {
    args: [patientId],
    failureMessage: "Couldn't load patient",
    forbiddenAsFailure: true,
  });
  return <PatientReading clock={clock} view={view} />;
}

/**
 * Home's Patient card: the patient in context, as a banner headed by their
 * name, or a plain card headed Patient saying "No patient in context", asking
 * nothing, when there is none. The patient in context is the tab's EHR
 * context's (`@/auth/ehr-context`), which Home reads through its subscribable
 * store and hands down, so the card re-renders when it changes, not only on
 * load: it follows the chart the clinician has open in the EHR, as the
 * FHIRcast hub's `Patient-open` and `Patient-close` move the context, and a
 * session ending clears it.
 *
 * A new patient in context is a new read: the card shows "Loading the
 * patient…" at once, never the patient before, and an answer for an earlier
 * patient that arrives late never lands (`useGatedRead`, the newest read
 * wins). The same patient opened again is no new read.
 *
 * With a patient in context it reads `GET /api/patient/{patientId}` with
 * `patient:read`. A `404` is "No patient in context" too, which is not an
 * error; any other failure — a `403` too, since the card reads only once
 * `patient:read` is held — goes to the page's one error line as "Couldn't load
 * patient". Age is derived from the birth date as completed years on today's
 * date in Singapore, read from `clock`.
 */
export function PatientCard({
  clock = systemClock,
  patientId,
}: {
  clock?: Clock;
  /** The FHIR id of the patient in context; omitted when there is none. */
  patientId?: string;
}) {
  return patientId === undefined ? (
    <PlainCard>{NO_PATIENT}</PlainCard>
  ) : (
    <PatientInContext clock={clock} patientId={patientId} />
  );
}
