/**
 * The part of a page the patient in context decides, for every page that
 * shows a history of that patient read from its source: it renders from the
 * signed-in layout's one read of them (`usePatientInContext`), so it reads no
 * patient itself, and it sits in `src/pages/` beside that read, shared by the
 * pages it serves.
 */

import type { ComponentType, ReactNode } from "react";

import { usePatientInContext } from "./patient-in-context";

/** What a page says with no patient whose history it could show. */
export function NoPatientInContext(): ReactNode {
  return <p className="text-sm text-muted-foreground">No patient in context</p>;
}

/** What a page says, as `copy`, while the patient in context is still being read. */
function LoadingPatient({ copy }: { copy: string }): ReactNode {
  return (
    <p className="text-sm text-muted-foreground" role="status">
      {copy}
    </p>
  );
}

/**
 * The page's `history` of the patient in context, once read: with none in
 * context, a `404` for the id there, or a session that may not read patients,
 * "No patient in context", rendering no history; while the patient is read,
 * the page's `loading` copy; a failed read leaves it empty, its copy on the
 * page's one error line; and once read, `history` of that patient, by its id.
 * A new patient in context is a new `history`, mounted afresh.
 */
export function ForPatientInContext({
  history: History,
  loading,
}: {
  history: ComponentType<{ patientId: string }>;
  loading: string;
}): ReactNode {
  const view = usePatientInContext();
  switch (view.kind) {
    case "none":
    case "absent":
    case "not-permitted":
      return <NoPatientInContext />;
    case "loading":
      return <LoadingPatient copy={loading} />;
    case "failed":
      return null;
    default:
      return <History key={view.data.id} patientId={view.data.id} />;
  }
}
