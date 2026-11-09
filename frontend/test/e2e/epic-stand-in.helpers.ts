import { randomBytes } from "node:crypto";

import { expect, type Page } from "@playwright/test";

/**
 * The local Epic stand-in, from the specs' side: self-hosted Medplum as the
 * SMART EHR launcher, fronted by the shim (`backend/compose.yaml`, profile
 * `fhircast`). `make integration-test` starts it, points the one JAR's Epic
 * Login at it, and sets every variable below (frontend/README.md lists each).
 * The `epic` project runs against it.
 *
 * Medplum is called as its super admin, who creates the launches and finds
 * what the seed made: the clinicians a spec signs in as, and the backend's
 * fixture patients.
 */

/** Medplum's base URL, which the shim answers on. */
const MEDPLUM_URL = process.env.E2E_FHIRCAST_MEDPLUM_URL ?? "";
/** Medplum's FHIR base: the launch `iss`, which must equal the backend's `APP_EPIC_FHIR_BASE`. */
export const FHIR_BASE = process.env.E2E_FHIRCAST_FHIR_BASE ?? "";
/** The Epic Login clinicians, as compose.yaml's `SEED_EPIC_LOGIN_CLINICIANS`: email=Practitioner id, comma-separated. */
const EPIC_LOGIN_CLINICIANS = process.env.E2E_EPIC_CLINICIANS ?? "";

/** Tan Wei Ming's Epic id, one of the development fixtures. */
const TAN_WEI_MING = "eq081-VQEgP8drUUqCWzHfw3";

const ADMIN = {
  email: process.env.E2E_FHIRCAST_ADMIN_EMAIL ?? "",
  password: process.env.E2E_FHIRCAST_ADMIN_PASSWORD ?? "",
};
/** The password every clinician the seed makes signs in with. */
const CLINICIAN_PASSWORD = process.env.E2E_FHIRCAST_CLINICIAN_PASSWORD ?? "";

/** Fails the test with a named variable rather than a confusing request error. */
export function expectStandInConfigured(): void {
  for (const [name, value] of Object.entries({
    E2E_FHIRCAST_MEDPLUM_URL: MEDPLUM_URL,
    E2E_FHIRCAST_FHIR_BASE: FHIR_BASE,
    E2E_FHIRCAST_ADMIN_EMAIL: ADMIN.email,
    E2E_FHIRCAST_ADMIN_PASSWORD: ADMIN.password,
    E2E_FHIRCAST_CLINICIAN_PASSWORD: CLINICIAN_PASSWORD,
  })) {
    expect(value, `${name} is set (make integration-test sets it)`).not.toBe("");
  }
}

async function medplumJson(path: string, init: RequestInit): Promise<Record<string, unknown>> {
  const answer = await fetch(new URL(path, MEDPLUM_URL), init);
  const text = await answer.text();
  expect(
    answer.ok,
    `Medplum answers ${init.method ?? "GET"} ${path}, not ${answer.status}: ${text}`,
  ).toBe(true);
  return JSON.parse(text) as Record<string, unknown>;
}

/** An access token for Medplum's super admin, signed in as Medplum's own client does. */
async function medplumAdminToken(): Promise<string> {
  const verifier = randomBytes(32).toString("base64url");
  const login = await medplumJson("auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      ...ADMIN,
      scope: "openid",
      codeChallenge: verifier,
      codeChallengeMethod: "plain",
    }),
  });
  const tokens = await medplumJson("oauth2/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "authorization_code",
      code: String(login.code),
      code_verifier: verifier,
    }),
  });
  return String(tokens.access_token);
}

/** A FHIR request to Medplum as its super admin, its JSON answer asserted ok. */
async function asMedplumAdmin(
  path: string,
  method = "GET",
  body?: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  return medplumJson(`fhir/R4/${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${await medplumAdminToken()}`,
      ...(body === undefined ? {} : { "Content-Type": "application/fhir+json" }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

/** The one resource a search answers, which the seed made (`backend/fhircast-shim/src/seed.mjs`). */
async function seeded(query: string): Promise<Record<string, unknown>> {
  const found = await asMedplumAdmin(query);
  const entries = (found.entry ?? []) as Array<{ resource: Record<string, unknown> }>;
  expect(entries, `the seed made one ${query}`).toHaveLength(1);
  return entries[0].resource;
}

/** A Patient the seed made in Medplum: his Medplum id, and his Epic id beside it. */
export interface MedplumPatient {
  id: string;
  epicId: string;
}

/** Tan Wei Ming as the seed made him, one of the backend's fixtures, found by his Epic id. */
export async function tanWeiMing(): Promise<MedplumPatient> {
  const patient = await seeded(`Patient?identifier=${encodeURIComponent(TAN_WEI_MING)}`);
  return { id: String(patient.id), epicId: TAN_WEI_MING };
}

/**
 * A SmartAppLaunch: what the EHR names in a launch's `launch`. Its patient is
 * `patient`, by reference and by Epic id: Medplum's token response puts the
 * identifier's value in `patient` ahead of the reference's id, so the backend
 * is handed the Epic id. With none, the token response names no patient at all.
 */
export async function createLaunch(patient?: MedplumPatient): Promise<string> {
  const launch = await asMedplumAdmin("SmartAppLaunch", "POST", {
    resourceType: "SmartAppLaunch",
    ...(patient === undefined
      ? {}
      : {
          patient: { reference: `Patient/${patient.id}`, identifier: { value: patient.epicId } },
        }),
  });
  return String(launch.id);
}

/** Who signs in on Medplum's page. */
export interface MedplumSignIn {
  email: string;
  password: string;
}

/** A clinician the seed made: a Practitioner with a Medplum sign-in in the local EHR's project. */
export interface MedplumClinician extends MedplumSignIn {
  /**
   * The Practitioner id the seed made it at: the `fhirUser` a launch signs
   * in, and so the `userName` of the User it links to (ADR 0012 D2, D3).
   */
  practitionerId: string;
}

/**
 * One of the clinicians the seed made for Epic Login's cases, by the email it
 * signs in with, at the Practitioner id compose.yaml's
 * `SEED_EPIC_LOGIN_CLINICIANS` gives it, which `make integration-test` passes
 * on as `E2E_EPIC_CLINICIANS`; every seeded clinician signs in with the
 * same password.
 */
export function seededClinician(email: string): MedplumClinician {
  const pair = EPIC_LOGIN_CLINICIANS.split(",").find((entry) => entry.startsWith(`${email}=`));
  expect(pair, `E2E_EPIC_CLINICIANS names ${email} (make integration-test sets it)`).toBeDefined();
  return {
    email,
    password: CLINICIAN_PASSWORD,
    practitionerId: pair!.slice(email.length + 1),
  };
}

/**
 * Signs a clinician in on Medplum's own sign-in page, where Medplum's
 * authorize sends the browser, and grants the scopes the app asked for; the
 * browser then goes on to the app's callback.
 */
export async function signInAtMedplum(page: Page, clinician: MedplumSignIn): Promise<void> {
  await page.getByLabel("Email").fill(clinician.email);
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByLabel(/^Password/).fill(clinician.password);
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.getByRole("button", { name: "Set Scope" }).click();
}
