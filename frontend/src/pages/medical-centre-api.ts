/**
 * Medical Centre: the closed set of camp medical centres an FFI's Submitting
 * MC and a Medication Record's Dispensary each draw on — vocabulary the FFI
 * and Medication pages' API modules share, so it sits beside them in
 * `src/pages/` as an API module of its own, data only.
 *
 * Exported for the SPA–API contract check (`/scripts/spa-contract/`), which
 * holds {@link MEDICAL_CENTRES} to its enum in `/backend/docs/openapi.yaml`.
 * Each code travels as itself, matched exactly; only this module names it.
 */

/** Every Medical Centre, by its code, in the order a picker lists them. */
export const MEDICAL_CENTRES = [
  "nee-soon-camp",
  "kranji-camp",
  "hendon-camp",
  "mandai-hill-camp",
  "khatib-camp",
] as const;

export type MedicalCentre = (typeof MEDICAL_CENTRES)[number];

/** What the pages call each Medical Centre. */
export const MEDICAL_CENTRE_LABELS: Readonly<Record<MedicalCentre, string>> = {
  "hendon-camp": "Hendon Camp MC",
  "khatib-camp": "Khatib Camp MC",
  "kranji-camp": "Kranji Camp MC",
  "mandai-hill-camp": "Mandai Hill Camp MC",
  "nee-soon-camp": "Nee Soon Camp MC",
};
