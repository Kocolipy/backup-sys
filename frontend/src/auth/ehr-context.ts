/**
 * The **EHR context**: the patient this tab has in context, a FHIR id,
 * possibly absent — kept for this tab alone, under one `sessionStorage` key.
 * The backend keeps none of it.
 *
 * Its lifecycle (`docs/BACKEND_CONTRACT.md`):
 *
 * - {@link setEhrContext} **replaces** it, holding the patient to FHIR's id
 *   grammar and keeping it only if it passes; nothing in the SPA sets it, so a
 *   tab holds none;
 * - a reload **keeps** it, since `sessionStorage` outlives the page;
 * - every end of the session — sign-out, expiry, an Idle sign-out, a password
 *   change — and a password Login **clear** it (`session-transitions.ts`,
 *   `auth-context.tsx`).
 *
 * It is a subscribable store, shaped for `useSyncExternalStore`
 * ({@link useEhrContext}): the signed-in layout's read of the patient in
 * context re-renders when it changes, not only on load.
 */

import { useSyncExternalStore } from "react";

import { watchers } from "@/lib/watchers";

/** The patient in context, a FHIR id; it may be absent. */
export interface EhrContext {
  patient?: string;
}

/** FHIR's id grammar, as the backend's `PatientId` holds it. */
const FHIR_ID = /^[A-Za-z0-9\-.]{1,64}$/;

const STORAGE_KEY = "ehr-context";

const isFhirId = (value: unknown): value is string =>
  typeof value === "string" && FHIR_ID.test(value);

/** The context naming `patient` when it is a FHIR id, and no patient otherwise. */
function withinGrammar(patient: unknown): EhrContext {
  return isFhirId(patient) ? { patient } : {};
}

/** Whoever is watching the EHR context, told when it changes. */
const watching = watchers();

/** What the stored text decodes to: held to the grammar again rather than trusted. */
function decoded(stored: string): EhrContext {
  let value: unknown;
  try {
    value = JSON.parse(stored);
  } catch {
    // Not JSON: the value stays undefined, which decodes to nothing below.
  }
  if (typeof value !== "object" || value === null) return {};
  return withinGrammar(Reflect.get(value, "patient"));
}

/** The last read, so an unchanged storage reads back as the same value. */
let lastRead: { stored: string | null; context: EhrContext | null } = {
  context: null,
  stored: null,
};

/**
 * The tab's EHR context, or `null` when the tab holds none. The storage is the
 * page's to write, so what is read back is decoded rather than trusted; the
 * same value comes back until the storage changes.
 */
export function ehrContext(): EhrContext | null {
  const stored = window.sessionStorage.getItem(STORAGE_KEY);
  if (stored !== lastRead.stored) {
    lastRead = { context: stored === null ? null : decoded(stored), stored };
  }
  return lastRead.context;
}

/** Calls `listener` whenever the EHR context changes; answers how to stop. */
export const subscribeEhrContext: (listener: () => void) => () => void = watching.watch;

/** The tab's EHR context, or `null`, re-rendering the caller whenever it changes. */
export function useEhrContext(): EhrContext | null {
  return useSyncExternalStore(subscribeEhrContext, ehrContext);
}

/**
 * Replaces the tab's EHR context with `context`'s patient when it is a FHIR
 * id, and tells every watcher. A patient outside the grammar is left out.
 */
export function setEhrContext(context: EhrContext): void {
  const kept = withinGrammar(context.patient);
  window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(kept));
  watching.changed();
}

/** Forgets the tab's EHR context. */
export function clearEhrContext(): void {
  window.sessionStorage.removeItem(STORAGE_KEY);
  watching.changed();
}
