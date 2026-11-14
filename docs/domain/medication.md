---
type: Domain Rules
title: Medication
description: A patient's Medication History — the in-house patient source's own Medication Records, read through it at GET {base}/Patient/{id}/medication on the FFI History's terms, the six required and six optional elements, the free text shown as the source gives it, the Start Date and End Date as date-times with an offset shown in Singapore time, the Dispense Status and the Dispensary's Medical Centre carried as codes, the source's order and paging, the failures and their Medication log reasons, the logging rules, no caching, no auditing, the Medication page and its states, and the mock's Medication fixtures and fault stubs.
tags: [patient, medication]
---

# Medication

**Medication Record** — one prescription of a patient's, with how it was
dispensed and who prescribed it, as the in-house patient source holds it. A
patient's **Medication History** is every Medication Record the source holds
for them, in the source's own order. The backend's `patient` module reads it
from the source on every read and serves it, read-only, at
`GET /api/patient-medication/{patientId}` to a session holding `patient:read`
([authorization](authorization.md)), as plain JSON a page at a time. Nothing
stores it.

It is read on the same terms as an FFI History (ADR 0019, D7): what the
[FFI rules](ffi.md) say of the source's paged read, its client and timeouts,
its failures, its logging, caching and auditing holds here too, and this file
says only where a Medication History differs — the endpoint, the twelve
elements, the date-times and the closed sets — and what is its own. The wire
contract is `backend/docs/openapi.yaml`. The SPA never reaches the source:
SPA → `GET /api/patient-medication/{patientId}` → backend →
`GET {base}/Patient/{id}/medication`.

## The elements

Six elements are required — the Medication, Dispense Status, Start Date, End
Date, Dispensary and Prescribing Doctor — and six optional: the Dosage, Route,
Frequency, Step, Quantity and Duration.

| Element            | On the wire (both sides)                       | Shown as                                                                                                      |
| ------------------ | ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| Medication         | `medication`, free text                        | as the source gives it, in bold                                                                               |
| Dosage             | `dosage`, free text, or absent                 | as the source gives it, or `—`                                                                                |
| Route              | `route`, free text, or absent                  | as the source gives it, or `—`                                                                                |
| Frequency          | `frequency`, free text, or absent              | as the source gives it, or `—`                                                                                |
| Step               | `step`, free text, or absent                   | as the source gives it, or `—`                                                                                |
| Quantity           | `quantity`, free text, or absent               | as the source gives it, or `—`                                                                                |
| Dispense Status    | `dispenseStatus`, a code                       | `pending` PENDING (muted badge) · `dispensed` DISPENSED (green badge) · `uncollected` UNCOLLECTED (red badge) |
| Duration           | `duration`, free text, or absent               | as the source gives it, or `—`                                                                                |
| Start Date         | `startDate`, ISO-8601 date-time with an offset | `18 Oct 2026 17:30`, in Singapore time                                                                        |
| End Date           | `endDate`, ISO-8601 date-time with an offset   | `22 Oct 2026 17:30`, in Singapore time                                                                        |
| Dispensary         | `dispensary`, a code                           | a Medical Centre's label ([ffi](ffi.md), "The closed sets")                                                   |
| Prescribing Doctor | `prescribingDoctor`, a name                    | the name, as the source gives it                                                                              |

Each Medication Record also carries its own `id` at the source, in FHIR's id
grammar, which the page keys its row by and never shows.

- **Free text is the source's.** The Medication, Dosage, Route, Frequency,
  Step, Quantity, Duration and Prescribing Doctor are shown exactly as the
  source gives them, and nothing reads them. The Duration is its own element
  ("5 days"): nothing works it out from the dates.
- **The Prescribing Doctor is a name.** It is not linked to our Users or
  accounts, and nothing looks it up. A missing or blank one is a failure of
  the source, as a missing or blank Medication is.
- **An optional element the source lacks** is left out by the source, and by
  our API, as an ARMC Board's End Date is ([armc](armc.md), "The elements"):
  never sent as `null`, and shown as `—`.
- **The Start Date and End Date are date-times**, to the minute. The source
  gives each as ISO-8601 with an offset (`Z` among them), and our API passes
  each on with the offset the source gave. The page shows each in Singapore
  time, `dd MMM yyyy HH:mm`, its seconds dropped. Both are required.
- **The End Date is never before the Start Date**, compared as instants,
  whatever offset each was given in; a record may end the minute it starts. The
  rule is the domain's own (`MedicationRecord`), so a record ending before it
  starts is a failure of the source, never a row.

### The closed sets

The Dispense Status and the Dispensary are closed sets, carried and matched as
an FFI's are ([ffi](ffi.md), "The closed sets"): each value travels as its
code, from the source and to the SPA, matched exactly, and the SPA alone names
it with its label. A code the service does not know is a failure of the source,
never a row with a blank cell.

- **Dispense Status** is one Java enum (`DispenseStatus`, codes through
  `SourceCode`), one enum in `backend/docs/openapi.yaml` (`DispenseStatus`) and
  one SPA set (`src/pages/medication/medication-api.ts`).
- **The Dispensary draws on Medical Centre**, the set an FFI's Submitting MC
  draws on: one Java enum (`MedicalCentre`), one enum in
  `backend/docs/openapi.yaml` (`MedicalCentre`) and one SPA set
  (`src/pages/medical-centre-api.ts`), shared by both histories.

Each is held set-equal by `VocabularyContractTests` and by
`make verify-contract` (`/scripts/spa-contract/README.md`).

## The source contract

As an FFI History's ([ffi](ffi.md), "The source contract"), FHIR-shaped paging
only — not FHIR-conformant:

- **The call.**
  `GET {base}/Patient/{id}/medication?_offset=…&_count=…&_total=accurate`, on
  the same client, base URL, timeouts and absent credential, the id sent
  unchanged. Nothing else is sent: no `_sort` and no filter, so the source's
  own order is the order shown.
- **The answer.** A FHIR-R4-shaped `searchset` `Bundle` — its `total` and each
  `entry[].resource` — whose every resource is a custom
  `"resourceType": "Medication"` object of flat fields: `id`, `medication`,
  `dosage`, `route`, `frequency`, `step`, `quantity`, `dispenseStatus`,
  `duration`, `startDate`, `endDate`, `dispensary` and `prescribingDoctor`, the
  six optional ones absent when the source has none. Nothing else in the Bundle
  is read, and any other field of a record is never bound.
- **The page.** Read by ADR 0017's rules and sent as `_offset`
  (`startIndex - 1`) and `_count`; `totalResults` is the source's `total`, and
  the source is asked even for `count=0` or a page past the end (ADR 0018, D3).
  The SPA asks for 5. `SourceSearchset` reads it, as it reads an FFI History's,
  and `SourceFields` each field, its date-time with an offset for each date.
- **Only a `404` is no patient**, our API's `404` too. Every other answer is a
  failure of the source, our `502` ([Failures](#failures)).
- **No caching.** No Medication Record is kept in Redis or in memory: every
  read, of the same page too, is a call to the source.

## Our API

`GET /api/patient-medication/{patientId}?startIndex=…&count=…`, outside the
`/api/patient` FHIR namespace: plain JSON, `{totalResults, startIndex,
records: [{id, medication, dosage, route, frequency, step, quantity,
dispenseStatus, duration, startDate, endDate, dispensary, prescribingDoctor}]}`,
each code as the source sent it, the free text as the source gave it, each
optional element absent when the source lacks it, and both date-times ISO-8601
with the offset the source gave. It takes no sort and no filter. Errors are the
application API's `ApiError`:

| Case                                                                        | Status |
| --------------------------------------------------------------------------- | ------ |
| `patientId` outside FHIR's id grammar, or a paging value that is no integer | `400`  |
| No session                                                                  | `401`  |
| Session lacks `patient:read`                                                | `403`  |
| The source's `404` for the patient                                          | `404`  |
| The source failing                                                          | `502`  |

The `400` is refused before the source is called. The `403` is refused by the
handler's `@PreAuthorize`, the chain's backstop and the document's `security`,
audited as an `ACCESS_DENIED` and logged as every authorization refusal is.

## Failures

Each of these is a failure of the source, our `502`, and its one fault record's
`event.reason`. A failure of the call or of the searchset is the one every paged
read of the source shares; **every rule a Medication Record breaks has a reason
naming Medication or an element of its own**, so a log line says which history
broke.

| Failure                                                                 | `event.reason`                                                                                                                        | `error.category` |
| ----------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ---------------- |
| No connection, or one refused, reset or dropped                         | `unreachable`                                                                                                                         | `network`        |
| No connection within 2 seconds, or no whole answer within 5             | `timed-out`                                                                                                                           | `network`        |
| A `5xx`                                                                 | `server-error`                                                                                                                        | `server`         |
| Any other status but a `200` or a `404`                                 | `unexpected-status`                                                                                                                   | `data`           |
| A body that arrived whole and is not JSON or not the shape              | `unreadable`                                                                                                                          | `data`           |
| A `200` that is no searchset `Bundle`                                   | `not-a-searchset`                                                                                                                     | `data`           |
| A searchset with no `total`, or a negative one                          | `no-total`                                                                                                                            | `data`           |
| An entry with no resource, or one that is no `Medication`               | `another-resource`                                                                                                                    | `data`           |
| An entry with no `id`, or one outside FHIR's id grammar                 | `medication-no-id`, `medication-invalid-id`                                                                                           | `data`           |
| A record missing a required element, or naming a blank one of its texts | `no-medication`, `no-dispense-status`, `no-medication-start-date`, `no-medication-end-date`, `no-dispensary`, `no-prescribing-doctor` | `data`           |
| A code outside its closed set                                           | `unknown-dispense-status`, `unknown-dispensary`                                                                                       | `data`           |
| A date that is no date-time with an offset                              | `medication-start-date-not-a-date-time`, `medication-end-date-not-a-date-time`                                                        | `data`           |
| An End Date before its Start Date                                       | `medication-end-before-start`                                                                                                         | `data`           |

An absent optional element is none of these. The adapter raises each as a
`PatientSourceException`, which the app-wide handler answers `502` with the
generic `server-error` `ApiError`, as for an FFI History.

## Logging

As an FFI History's ([ffi](ffi.md), "Logging"): each failure of the source is
exactly one `ERROR`, the `502`'s `http.request.fault` record naming its reason;
neither the log nor the exception's message names the patient id or anything of
the source's body — not a record's id, text, code or date, nor an error
answer's body; and a read adds no record of its own, the request record naming
the route template (`/api/patient-medication/{patientId}`), never the id.
`MedicationHistoryIntegrationTests` holds each Medication fault stub's read to
exactly one `ERROR` naming its failure under its category, and the stream, for
those reads and for successful ones, to naming neither the patient id nor any
record's id, Medication, dates or Prescribing Doctor.

## Not audited

A Medication History read writes **no audit row**, as an FFI History read and a
read of one Patient by id write none (ADR 0019, D3 and D7). A refused read is
audited as `ACCESS_DENIED`, as every authorization refusal is.

## The Medication page

`/medication`, titled **Medication**, linked **Medication** in the sidebar
directly above FFI — Search, Profile, Medication, FFI, NUMC, … — with the
`Pill` icon, for a session holding `patient:read`, and guarded by it as FFI is:
a session without it is offered no link and is routed to its landing page.

- **It reads the patient in context** as the FFI page does, from the signed-in
  layout's one reader of it ([patient](patient.md), "The patient in context"),
  through the same shared wrapper: it reads that patient's Medication History
  once the patient is read, says "Loading medication records…" while the
  patient or the first page is read, and a failed read of the patient leaves
  it empty. It is read-only, five Medication Records a page, with no row
  action.
- **The table**: Medication, Dosage, Route, Frequency, Step, Quantity, Dispense
  Status, Duration, Start Date, End Date, Dispensary, Prescribing Doctor — the
  Medication in bold, the free text as the source gave it and `—` for an
  absent optional element, the Dispense Status as its badge
  ([the elements](#the-elements)), both date-times in Singapore time as
  `dd MMM yyyy HH:mm`, and the Dispensary by its Medical Centre's label. No
  column sorts and nothing filters. The table is wider than its card: each
  cell keeps to one line and the table scrolls sideways inside the card rather
  than squeezing its columns.
- **The shared paged listing**: the numbered pager and "Showing x to y of N
  medication records", named on a single page too, the page shown held while
  the next loads.
- **No patient in context** — the tab holds none, or the source holds no
  patient under its id (its `404`, our `404`) — shows "No patient in context",
  as the other history pages do, and reads no Medication Records or reports
  nothing.
- **A patient with no Medication Records** — a `200` with `total: 0` — shows
  "No medication records".
- **The source failing** shows "Couldn't load medication records" on the page's
  error line, and Try again, which reads the same page again.

## The patient source's Medication fixtures

The patient source's mock (`backend/patient-source-mock`, [patient](patient.md),
"The patient source's fixtures") serves the Medication Histories of the
development fixtures beside their FFI, NUMC and ARMC Histories, on the same
patients, and the backend's tests read the same stub files
(`PatientSourceStubs`).

- **Tan Wei Ming** (`eq081-VQEgP8drUUqCWzHfw3`) has six Medication Records over
  two pages (`medication-tan-wei-ming-page-1`, `-page-2`). The first five, none
  with a Step:

  | Medication  | Dosage | Route | Frequency | Quantity   | Dispense Status | Duration | Start Date        | End Date          | Dispensary       | Prescribing Doctor         |
  | ----------- | ------ | ----- | --------- | ---------- | --------------- | -------- | ----------------- | ----------------- | ---------------- | -------------------------- |
  | Paracetamol | 750 mg | PO    | QDS       | 30 tablets | PENDING         | 5 days   | 18 Oct 2026 17:30 | 22 Oct 2026 17:30 | Nee Soon Camp MC | Dr Lim Wei Jie             |
  | Amoxicillin | 300 mg | PO    | TDS       | 20 tablets | PENDING         | 5 days   | 18 Oct 2026 17:31 | 22 Oct 2026 17:31 | Khatib Camp MC   | Dr Lim Wei Jie             |
  | Paracetamol | 750 mg | PO    | QDS       | 30 tablets | PENDING         | 5 days   | 18 Oct 2026 17:30 | 22 Oct 2026 17:30 | Nee Soon Camp MC | Dr Chen Mei Ling           |
  | Paracetamol | 500 mg | PO    | QDS       | 20 tablets | DISPENSED       | 3 days   | 13 Oct 2026 14:30 | 15 Oct 2026 14:30 | Khatib Camp MC   | Dr Rajesh Kumar            |
  | Amoxicillin | 300 mg | PO    | TDS       | 20 tablets | UNCOLLECTED     | 3 days   | 22 Apr 2025 10:00 | 24 Apr 2025 10:00 | Kranji Camp MC   | Dr Nur Aisyah binte Rahman |

  The sixth, on the second page, is Prednisolone 30 mg PO OM, Step 1, 14
  tablets, DISPENSED, 7 days, 02 Dec 2024 09:15–09 Dec 2024 09:15, Hendon Camp
  MC, Dr Chen Mei Ling.

- **Tan Hui Min** (`dev-patient-05`) has four Medication Records on one page
  (`medication-tan-hui-min`), between them every Dispense Status and some
  absent optional elements: the first's dates are given in UTC (`Z`) and shown
  in Singapore time, the second lacks its Dosage, Frequency, Step and Duration,
  and the fourth has none of the six optional elements and ends the minute it
  starts. The two histories hold every Medical Centre between them as their
  Dispensaries.
- **Every other fixture** has none — Siti Nurhaliza binte Ahmad
  (`erXuFYUfucBZaryVksYEcMg3`) is the one the tests read — answered by
  `medication-fallback`, which also answers any page of a fixture's history
  the canned stubs do not name, with an empty searchset, `total: 0`.
- **Any other well-formed id** is the source's `404` (`medication-not-found`).

Each canned page is matched on the page the backend sends — `_count=5`,
`_offset` and `_total=accurate` — so a read of any other page falls to the
fallback. Each fixture is selectable on Patient Search (the name search `tan`
finds both Tans).

**Fault stubs.** Beside them, each failure above has a stub of its own under a
well-formed patient id no fixture uses, `fault-medication-<failure>`, marked
with `metadata.fault`: `unreachable` (the connection reset), `slow` (the answer
after 7 seconds), `server-error` (`503`), `unexpected-status` (`400`),
`not-a-searchset`, `no-total`, `no-id`, `invalid-id`, `no-dispense-status` and
`no-prescribing-doctor` (a record missing a required element),
`unknown-dispense-status` and `unknown-dispensary` (a code outside each set),
`start-date-not-a-date-time` (a Start Date with no offset) and
`end-before-start`. A fault stub is no fixture. `PatientSourceFixturesTests`
holds the fixtures to these rules, `PatientStoreContract` holds what each
history reads, written out, and `PatientSourceMedicationAdapterTests` each
fault stub's failure.
