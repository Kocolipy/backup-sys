/**
 * The layout of a history page headed by the Patient card — the FFI, NUMC,
 * ARMC and HSP pages: the patient in context's Patient card, then a card
 * holding the page's history, headed by the page's title. Shared by those
 * pages, so it sits beside them in `src/pages/`.
 *
 * The heading is the page's one `<h1>` while a patient is in context: the
 * shell drops its own title then (`titleHidden:
 * "dropped-while-patient-in-context"`), so the title is announced once.
 */

import type { ComponentType, ReactNode } from "react";

import { Card, CardContent } from "@/components/ui/card";
import { ForPatientInContext } from "@/pages/for-patient-in-context";
import { PatientCard } from "@/pages/patient-card";
import { isWithoutPatient, usePatientInContext } from "@/pages/patient-in-context";

/** A history's heading, the page's title, beside its Filter button (`FilteredHistory`'s `heading`). */
export function HistoryHeading({ children }: { children: ReactNode }): ReactNode {
  return <h1 className="text-lg font-semibold">{children}</h1>;
}

/**
 * The Patient card, then `history` of the patient in context in a card of its
 * own, and "Loading records…" there while the patient is read
 * ({@link ForPatientInContext}). With no patient in context — a `404`
 * included — the Patient card alone says so, and there is no history card. A
 * new patient in context is a new history, read from its first page.
 */
export function PatientHistoryPage({
  history,
}: {
  history: ComponentType<{ patientId: string }>;
}): ReactNode {
  const withoutPatient = isWithoutPatient(usePatientInContext());
  return (
    <div className="flex w-full flex-col gap-4">
      <PatientCard />
      {withoutPatient ? null : (
        <Card>
          <CardContent className="flex flex-col gap-4">
            <ForPatientInContext history={history} />
          </CardContent>
        </Card>
      )}
    </div>
  );
}
