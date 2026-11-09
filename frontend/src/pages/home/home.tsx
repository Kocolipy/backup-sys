import { PatientCard } from "./patient-card";

/**
 * Home, the signed-in landing page at `/`.
 *
 * Every signed-in User lands here. The Patient card comes first: the patient of
 * the tab's EHR context (`ehr-context.ts`), which an EHR launch's Launch
 * context seeded, read with `patient:read`, a baseline Permission every active
 * User holds — read once, by the signed-in layout (`patient-in-context.ts`),
 * for the card and the top bar both. With no patient in context — a password
 * Login, or a launch that named none — the card shows "No patient in context"
 * and nothing is asked for. The other signed-in pages are linked from the
 * navigation sidebar (`signed-in-shell.tsx`), not from here.
 */
export function Home() {
  return (
    <div className="flex w-full flex-col gap-4">
      <PatientCard />
    </div>
  );
}
