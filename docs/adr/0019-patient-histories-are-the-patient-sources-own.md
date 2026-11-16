---
type: Decision Record
title: "ADR 0019: Patient histories are the patient source's own, read through a FHIR-shaped page, and not audited"
description: A patient's histories — FFI, NUMC, ARMC and Medication — are the patient source's own, read through it on every request and held by no table or cache, as a FHIR-R4-shaped searchset page documented as not FHIR-conformant, paged by ADR 0017's startIndex and count, sorted and filtered by the source, and not audited, as a read of one Patient by id is not.
tags: [patient, ffi, numc, armc, medication, paging, audit, integration]
---

# 19. Patient histories are the patient source's own, read through a FHIR-shaped page, and not audited

Date: 2026-11-12

## Status

Accepted. A patient's FFI History, NUMC History, ARMC History and Medication
History are each read at `GET /api/patient-<history>/{patientId}`, and the
SPA's page for each reads it for the patient in context. `/docs/domain/ffi.md`,
`/docs/domain/numc.md`, `/docs/domain/armc.md` and `/docs/domain/medication.md`
are the behaviour in full; this file holds the decisions and their reasons.

D1 to D4 are written for the FFI History, the first decided, and bind every
history; D5 to D7 say what each other history changes.

## Context

A clinician with a patient in context needs that patient's histories, which
the in-house patient source holds beside its Patients:

- **FFIs** (Fit for Instruction): what each was for, what it found, when it
  was issued and runs to, the authority it answers to, the medical centre that
  submitted it, and whether it is still in force.
- **NUMC Records**: whether the Navy Underwater Medicine Centre found the
  patient fit for an underwater duty — the same shape of problem as FFIs, with
  fewer fields.
- **ARMC Boards**: the aeromedical fitness boards the Aeromedical Centre sat
  the patient on. A board still open has no end yet, so its End Date is an
  element the source may leave out.
- **Medication Records**: each prescription as the prescriber wrote it, the
  period it runs over to the minute, and the camp medical centre that dispenses
  it — the very centres that submit FFIs. Half of its elements a prescription
  may lack.

The service holds no Patient (ADR 0014), and every Patient search is the
source's own (ADR 0018).

FHIR has no resource for an FFI. Its nearest, `Observation` or a profiled
`DocumentReference`, would need a profile, value sets for five closed sets and an
extension for the Higher Authority — a modelling exercise for data the source
already holds in flat fields of its own.

A read of one Patient by the id the EHR context gave is the ordinary unit of
care and is not audited (`/docs/domain/patient.md`); a search across Patients is,
because it is the shape enumeration takes (ADR 0018, D6).

## Decision

**D1. A history is the patient source's own, read through it.** `PatientStore`'s
`findFfiHistory`, on the module's one outbound client (`PatientSourceAdapter`),
sends `GET {base}/Patient/{id}/ffi` with the page, on every read, with no
credential and the same timeouts as a read by id. The service holds and caches
nothing: no table holds an FFI and no database login holds a grant on one. Only
a `404` is no patient — our `404`; any other status, no answer within the
timeouts, or a `200` the service cannot hold is a failure of the source, the
`502` a read by id answers with. Status is the source's own field, never derived
from the Expiry Date: an FFI past its Expiry Date may still be Active, and
nothing in the service or the SPA works out "expired".

**D2. The source's page is a FHIR-shaped but non-conformant Bundle.** The
contract is ours to define. The source answers a FHIR-R4-shaped `searchset`
`Bundle` — `total`, `entry[].resource` — whose resources are a custom
`"resourceType": "FFI"` object of flat fields. In a history shown as
received — the FFI, the NUMC and the Medication History — each field is
present, missing or in the wrong format and none refused (ADR 0020); in one
read strictly, the ARMC History, every one is required. It borrows
FHIR's paging (`_offset`, `_count`, `_total=accurate`) so that one piece reads
every page the source answers, a Patient search's and each history's
(`SourceSearchset`: the page's parameters, the `total`, the `entry`, an entry's
`id` rules), and it is documented as **not FHIR-conformant**: no FHIR validator
is run over it and no FHIR client should read it as FHIR. The closed sets
travel as codes, each a Java enum, an enum of `openapi.yaml` and an SPA set
held set-equal; the SPA names each code with its label. An FFI's, a NUMC
Record's or a Medication Record's code outside its set is shown as the raw code
(ADR 0020, D2). Paging is ADR 0017's
`startIndex` and `count`, read by `PageParameter` and `PageRequest`, with ADR
0018's D3 departure: the page is the source's to read, `count=0` and a page
past the end included, and `totalResults` is the source's `total`. Our answer
is plain JSON outside `/api/patient`, so its errors are the application API's
`ApiError`.

**D3. History reads are not audited.** A read of a patient's history appends no
audit row, as a read of one Patient by id appends none: it reads one patient's
records by the id the EHR context gave, the ordinary unit of care, not a sweep
across patients. A sorted or filtered read is no different: it is still one
patient's records. A refused read is audited as `ACCESS_DENIED`, as every
authorization refusal is. `patient:read` gates it, in the handler's
`@PreAuthorize`, the chain's backstop and the document's `security` (ADR 0015).

**D4. The sort and the filters are the source's, and the source's order is the
default.** As for a Patient search (ADR 0018, D7), the source sorts, filters,
pages and counts, and the service passes the sort and the filters through and
does none of it itself: one date column at a time, as `_sort` on its search
parameter, `-` first for descending, ties broken by id (`,_id`); and the filters its history declares — a
code set sent once with its codes joined by commas (FHIR's OR), and a range for
each date, as `ge` and `le` on its search parameter — combined with AND. Every
value is checked as the request is read in, so a code outside its set, a
malformed date or an inverted range is our `400` before the source is called.
**With no sort chosen, no `_sort` is sent**, and the source's own order is the
order shown until a heading is pressed. The sort (`HistorySort`) and the date
ranges are shared by every history, and each history declares its own date
columns — name, the source's search parameter, and precision, a calendar date
or a date-time to the minute — so the shared code names none, and how a
date-time bound is sent to the source is decided once there. Each history's
own filter type (`FfiFilter`, `NumcFilter`, `ArmcFilter`, `MedicationFilter`)
holds its filters, and its domain file's "Sort and filters" lists them.

**Each rule a record breaks is logged under a reason naming its history**
(`armc-no-id`, `armc-end-before-start`, …), so a fault record says which
history broke; a failure of the call or of the searchset itself is the one
every paged read shares. An FFI, a NUMC Record and a Medication Record break no
rule: their histories fail only by their call or their searchset (ADR 0020,
D3). Each is shown as received — a field present, missing or in the wrong
format, "—" and "Error" in its cell, and none refused, its id never checked,
a code outside its closed set shown raw, and a response holding fields in the
wrong format one `WARN` naming their paths.

**D5. NUMC Records are read on the FFI History's terms** (`findNumcHistory`,
`GET {base}/Patient/{id}/numc`, `"resourceType": "NUMC"`), shown as received
as an FFI is. What differs:

- **Its sets are its own.** The NUMC Outcome is not the FFI Outcome: the two
  share codes by chance, not by meaning, so an FFI's `request-waiver` is no
  NUMC Outcome as a filter's code — a `400` — and is shown raw as a record's.
- **It has no Status**, and nothing works out "expired" from its Expiry Date.

**D6. ARMC Boards are read on the FFI History's terms** (`findArmcHistory`,
`GET {base}/Patient/{id}/armc`, `"resourceType": "ARMC"`). What differs:

- **The End Date is optional.** A board still open has none, and it is left
  out of our answer then, never sent as `null`. When it is there it is on or
  after the Start Date, a rule of the domain's `ArmcBoard`: a board ending
  before it starts is a failure of the source, never a row shown. Every other
  date of a history read strictly stays required.
- **The source breaks the sort's ties.** The sort is sent as the column alone,
  with no `_id` after it as an FFI History's and a NUMC History's have: the
  source breaks ties, and places a board with no End Date in an End Date sort,
  by its own rule.
- **An open board matches no End Date range**, so any End Date bound leaves it
  out — the source's rule, which its mock's stubs hold.
- **The two Medical Officers are names** as the source gives them, linked to
  no User or account.

**D7. Medication Records are read on the FFI History's terms**
(`findMedicationHistory`, `GET {base}/Patient/{id}/medication`,
`"resourceType": "Medication"`), shown as received as an FFI is. What differs:

- **Its dates are date-times**, to the minute, not calendar dates: the source
  gives each as ISO-8601 with an offset, our answer passes each on with that
  offset, and the SPA shows each in Singapore time. One with no offset names no
  instant, and is in the wrong format, "Error" in its cell (ADR 0020, D1); an
  End Date before its Start Date is shown as it came. Each end of a range is a
  Singapore minute, inclusive, sent as the instant it names in UTC — `ge` the
  from-minute, `lt` the minute after the to-minute. The sort is sent as the
  column alone, the source breaking ties as for an ARMC History (D6).
- **Its free text is read by nothing**: shown as the source gives it, the
  Duration never worked out from the dates, the Prescribing Doctor a name
  linked to no User or account.
- **The Dispensary shares Medical Centre with the FFI's Submitting MC** — one
  Java enum (`MedicalCentre`), one enum of `openapi.yaml` and one SPA set. The
  fields keep their own names, and a centre the set gains is a centre of both.

## Consequences

- **Every history page depends on the source's availability.** A slow or
  failing source is the page's "Couldn't load …" with Try again, never an
  empty history.
- **A real source must answer this contract**, or the adapter changes with it.
  It must filter, sort, page, count accurately, keep its own stable order,
  break a sort's ties the same way on every page and send every required
  element of a history read strictly; a source that cannot is no history
  source until its adapter changes. A history shown as received, the FFI, the
  NUMC or the Medication History, needs only the searchset (ADR 0020).
- **A new code in a closed set of a history read strictly is a contract
  change.** The source sending an ARMC code the service does not know is a
  `502`, until the enum, the document and the SPA set gain it together. An FFI,
  NUMC or Medication code outside its set is no `502` and no blank cell: it is
  shown as the raw code (ADR 0020, D2), and only its filter needs the set to
  gain it.
- **No trail records who read whose histories.** A deployment whose policy
  requires every read of patient data to be recorded must revisit D3 together
  with the unaudited read by id (`/docs/domain/patient.md`).
- **The local mock answers canned pages only**
  (`backend/docs/configuration/patient-source.md`).

## Alternatives considered

- **Model an FFI as a FHIR resource** — an `Observation` profile, with value
  sets and an extension. Rejected: the source holds flat fields, and the profile,
  value sets and extension would be ours to maintain for no client that reads
  FHIR. The FHIR-shaped page keeps FHIR's paging without claiming conformance.
- **Hold a copy of the FFIs in the service.** Rejected for the reasons ADR 0014
  rejects holding Patients: Restricted data at rest, kept fresh by a sync.
- **Work Status out from the Expiry Date.** Rejected: the source records Status
  for reasons the date does not show — a waiver, a supersession — so a derived
  Status would contradict the record.
- **Audit every FFI read, as a Patient search is.** Rejected: a read by the id
  in context is not the bulk read that makes a search audited, and auditing it
  alone would leave the read of the Patient itself unaudited beside it.
- **A default sort of the service's choosing** — newest Issue Date first.
  Rejected: the source keeps an FFI History in an order of its own, which a
  default sort would hide behind one the service chose.
- **Fold NUMC Records into the FFI History**, as an FFI Type of their own.
  Rejected: a NUMC Record has no Higher Authority, Submitting MC or Status, and
  its Outcome is another set, so every FFI column would be blank or wrong for
  it. An ARMC Board is further still: it has a period and two Medical Officers,
  and no Issue or Expiry Date.
- **Give the Dispensary a set of its own**, a Dispensary set beside the
  FFI's Submitting MC set. Rejected: both name the same camp medical centres,
  so two sets would have to gain each new centre together, and a centre known
  to one would be unknown to the other.
- **Read a Medication Record's dates as calendar dates**, as an ARMC Board's
  are. Rejected: two prescriptions on one day differ by the minute, and the
  source gives the instant with its offset; a calendar date would drop the
  time, and the day itself would depend on the zone it was read in.
- **Send a still-open board's End Date as `null`**, as the application API
  sends its other absent values. Rejected: the source leaves it out, and an
  absent field keeps "still open" one shape from the source to the SPA.
- **Sort and filter the page in the service.** Rejected: the service holds only
  the page the source answered, so its order and its count would be wrong across
  pages, as a Patient search's would (ADR 0018, D7).
