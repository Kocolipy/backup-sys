import { PatientCard } from "@/pages/patient-card";

import { DemographicDetails } from "./demographic-details";

/**
 * The Demographic page, at `/demographic`: the patient in context's Patient
 * card, then their Demographic details — the Military Overview, Contact Details
 * and Next of Kin. Guarded by `patient:read`, so only a session that can read
 * patients opens it; selecting a Patient on the Search page lands here.
 *
 * It reads nothing itself: the card and the details render from the
 * signed-in layout's one read of the patient in context
 * (`patient-in-context.ts`), which the top bar renders from too. With no
 * patient in context — a `404` included — the card is the plain one a history
 * page shows, saying "No patient in context", with no details, and the
 * signed-in layout shows the page's title above it; with a patient, their
 * banner heads the page.
 */
export function Demographic() {
  return (
    <div className="flex w-full flex-col gap-4">
      <PatientCard />
      <DemographicDetails />
    </div>
  );
}
