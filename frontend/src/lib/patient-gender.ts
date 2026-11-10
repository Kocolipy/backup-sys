/**
 * A Patient's gender, as every page that shows a Patient reads and names it:
 * Home's patient in context, and Patient Search's matches.
 */

/**
 * The FHIR R5 administrative gender codes. Spelled out rather than imported:
 * `@types/fhir` is a devDependency, which production source may not import
 * (`cq-no-devdep-in-prod`), so `home-api.test.ts` holds this to `fhir5`'s own.
 */
export const PATIENT_GENDERS = ["male", "female", "other", "unknown"] as const;

export type PatientGender = (typeof PATIENT_GENDERS)[number];

/** What the SPA calls each gender code, wherever a Patient's gender is shown. */
export const PATIENT_GENDER_LABELS: Readonly<Record<PatientGender, string>> = {
  female: "Female",
  male: "Male",
  other: "Other",
  unknown: "Unknown",
};
