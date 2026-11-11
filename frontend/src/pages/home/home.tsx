import { PatientCard } from "./patient-card";

/**
 * Home, the signed-in page at `/`: open to every signed-in session, and the
 * landing page of one without `patient:read` (`landingPath`).
 *
 * The Patient card comes first: the patient of the tab's EHR context
 * (`ehr-context.ts`), read with `patient:read` — read once, by the signed-in
 * layout (`patient-in-context.ts`), for the card and the top bar both. With no
 * patient in context the card shows "No patient in context", and for a session
 * without `patient:read` it says the account has no access to patients; either
 * way nothing is asked for. The other signed-in pages are linked from the
 * navigation sidebar (`signed-in-shell.tsx`), not from here.
 */
export function Home() {
  return (
    <div className="flex w-full flex-col gap-4">
      <PatientCard />
    </div>
  );
}
