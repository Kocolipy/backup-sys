import type { ReactNode } from "react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { type Clock, completedYears, formatCalendarDate, today, systemClock } from "@/lib/time";

import { PATIENT_GENDER_LABELS } from "@/lib/patient-gender";
import { shown } from "@/lib/source-field";

import { NoPatientInContext } from "@/pages/for-patient-in-context";
import type { PatientSummary } from "@/pages/patient-api";
import { isWithoutPatient, usePatientInContext } from "@/pages/patient-in-context";
import { labelled } from "@/pages/source-code";

const NOTE_CLASS = "text-sm text-muted-foreground";

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
 * the text it is, never interpreted as markup. Each is shown as received (ADR
 * 0020): its value, "—" when missing, "Error" when its format was wrong, a
 * gender outside the four codes as its code; Age follows the date of birth.
 */
function PatientBanner({ clock, patient }: { clock: Clock; patient: PatientSummary }) {
  const rows: [label: string, value: ReactNode][] = [
    ["NRIC", shown(patient.nric, String)],
    ["Gender", shown(patient.gender, labelled(PATIENT_GENDER_LABELS))],
    [
      "Age",
      shown(patient.birthDate, (birthDate) => String(completedYears(birthDate, today(clock)))),
    ],
    ["Date of birth", shown(patient.birthDate, formatCalendarDate)],
  ];
  return (
    <section
      aria-label="Patient"
      className="flex w-full flex-col gap-6 rounded-xl bg-linear-to-r from-ring to-primary p-5 text-primary-foreground shadow-md"
    >
      <h2 className="text-4xl font-black tracking-tight">{shown(patient.name, String)}</h2>
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
 * The Patient card, which heads the Demographic page: the patient in context,
 * as a banner headed by their name. With no patient in context — a `404`
 * included, which is not an error — it is the plain card every page showing a
 * patient's history shows then, saying "No patient in context"
 * (`NoPatientInContext`), the page's title shown above it. It renders from the
 * signed-in layout's one read of the patient in context
 * (`usePatientInContext`), which the top bar renders from too, and reads
 * nothing itself; so it re-renders as that read moves — as the tab's EHR
 * context is set, and as a session ending clears it.
 *
 * A new patient in context is a new read: the card, headed Patient, shows
 * "Loading the patient…" at once, never the patient before, and an answer for
 * an earlier patient that arrives late never lands. Any failure but a `404`
 * leaves that card empty, with no retry — its copy, "Couldn't load patient",
 * is on the page's one error line, reported once, by the read. Age is derived
 * from the birth date as completed years on today's date in Singapore, read
 * from `clock`.
 */
export function PatientCard({ clock = systemClock }: { clock?: Clock }) {
  const view = usePatientInContext();
  if (isWithoutPatient(view)) {
    return (
      <Card>
        <CardContent className="flex flex-col gap-4">
          <NoPatientInContext />
        </CardContent>
      </Card>
    );
  }
  switch (view.kind) {
    case "loading":
      return (
        <PlainCard>
          <p className={NOTE_CLASS}>Loading the patient…</p>
        </PlainCard>
      );
    case "failed":
      return <PlainCard>{null}</PlainCard>;
    default:
      // `ready`, or `stale` — which this read never produces: a new patient is
      // a new path, so a new first read, and the same patient is never re-read.
      return <PatientBanner clock={clock} patient={view.data} />;
  }
}
