---
type: Decision Record
title: "ADR 0019: FFIs are the patient source's own, read through a FHIR-shaped page, and not audited"
description: A patient's FFI History is the in-house patient source's own, read through it on every request at GET {base}/Patient/{id}/ffi and held by no table or cache; the source answers a FHIR-R4-shaped searchset Bundle of custom FFI objects that is documented as not FHIR-conformant, paged by ADR 0017's startIndex and count, sorted by Issue Date or Expiry Date and filtered on every column by the source, in its own order when no sort is chosen; and an FFI read, like a read of one Patient by id, appends no audit row. A patient's NUMC History is read on the same terms at GET {base}/Patient/{id}/numc, its NUMC Records of four elements and two closed sets, sorted by either date and filtered on every column by the source on D4's terms; and a patient's ARMC History likewise at GET {base}/Patient/{id}/armc, its ARMC Boards of six elements, the End Date the one optional, and two closed sets, paged in the source's order; and a patient's Medication History likewise at GET {base}/Patient/{id}/medication, its Medication Records of six required and six optional elements, its Start Date and End Date date-times with an offset, its Dispense Status a closed set and its Dispensary a Medical Centre, the set an FFI's Submitting MC draws on, paged in the source's order.
tags: [patient, ffi, numc, armc, medication, paging, audit, integration]
---

# 19. FFIs are the patient source's own, read through a FHIR-shaped page, and not audited

Date: 2026-11-12

## Status

Accepted. `GET /api/patient-ffi/{patientId}` reads a patient's FFI History, and
the SPA's FFI page reads it five at a time for the patient in context;
`GET /api/patient-numc/{patientId}` reads a patient's NUMC History, and the
SPA's NUMC page reads it five at a time in the same way, sorted and filtered as
the FFI page is (D5); `GET /api/patient-armc/{patientId}` reads a patient's
ARMC History, and the SPA's ARMC page reads it five at a time in the same way,
in the source's order (D6); `GET /api/patient-medication/{patientId}` reads a
patient's Medication History, and the SPA's Medication page reads it five at a
time in the same way, in the source's order (D7).
`/docs/domain/ffi.md`, `/docs/domain/numc.md`, `/docs/domain/armc.md` and
`/docs/domain/medication.md` are the behaviour in full; this file holds the
decisions and their reasons.

## Context

A clinician with a patient in context needs that patient's FFIs (Fit for
Instruction): what each was for, what it found, when it was issued and runs to,
the authority it answers to, the medical centre that submitted it, and whether it
is still in force. The in-house patient source holds them beside its Patients.
The service holds no Patient (ADR 0014), and every Patient search is the
source's own (ADR 0018).

FHIR has no resource for an FFI. Its nearest, `Observation` or a profiled
`DocumentReference`, would need a profile, value sets for five closed sets and an
extension for the Higher Authority — a modelling exercise for data the source
already holds in flat fields of its own.

The source also holds each patient's NUMC Records: whether the Navy Underwater
Medicine Centre found the patient fit for an underwater duty — what duty, what
it found, when it was issued and when it runs to. They have the same shape of
problem as FFIs, with fewer fields.

It holds each patient's ARMC Boards too: the aeromedical fitness boards the
Aeromedical Centre (ARMC) sat the patient on — what kind of board, what it
found, when it started and, once it has, when it ended, and the two Medical
Officers who sat on it. A board still open has no end yet, so its End Date is
the first element of any of these histories that the source may leave out.

And it holds each patient's Medication Records: each prescription — the
medication, its dosage, route, frequency, step, quantity and duration as the
prescriber wrote them, whether it was dispensed and collected, the period it
runs over to the minute, the camp medical centre that dispenses it, and the
doctor who prescribed it. Half of those elements a prescription may lack, and
the dispensing centres are the very camp medical centres that submit FFIs.

A read of one Patient by the id the EHR context gave is the ordinary unit of
care and is not audited (`/docs/domain/patient.md`); a search across Patients is,
because it is the shape enumeration takes (ADR 0018, D6).

## Decision

**D1. FFIs are the patient source's own, read through it.** `PatientStore`'s
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
`"resourceType": "FFI"` object of flat fields (`id`, `type`, `outcome`,
`issueDate`, `expiryDate`, `higherAuthority`, `submittingMc`, `status`), every
one required. It borrows FHIR's paging (`_offset`, `_count`, `_total=accurate`)
so that one piece reads both of the source's pages, a Patient search's and an
FFI History's (`SourceSearchset`: the page's parameters, the `total`, the
`entry`, an entry's `id` rules), and it is documented as **not
FHIR-conformant**: no FHIR validator is run over it and no FHIR client should
read it as FHIR. The five closed sets travel as codes, each a Java enum, an enum
of `openapi.yaml` and an SPA set held set-equal; the SPA names each code with
its label. Paging is ADR 0017's `startIndex` and `count`, read by `PageParameter`
and `PageRequest`, with ADR 0018's D3 departure: the page is the source's to
read, `count=0` and a page past the end included, and `totalResults` is the
source's `total`. With no sort chosen, no `_sort` is sent, so the source's
default order is the order shown (D4). Our answer is plain JSON outside
`/api/patient`, so its errors are the application API's `ApiError`.

**D3. FFI reads are not audited.** A read of a patient's FFI History appends no
audit row, as a read of one Patient by id appends none: it reads one patient's
records by the id the EHR context gave, the ordinary unit of care, not a sweep
across patients. A refused read is audited as `ACCESS_DENIED`, as every
authorization refusal is. `patient:read` gates it, in the handler's
`@PreAuthorize`, the chain's backstop and the document's `security` (ADR 0015).
A sorted or filtered read is no different: it is still one patient's records.

**D4. The sort and the filters are the source's, and the source's order is the
default.** As for a Patient search (ADR 0018, D7), the source sorts, filters,
pages and counts, and the service passes the sort and the filters through and
does none of it itself: the Issue Date or the Expiry Date, one at a time, as
`_sort=±issue-date|±expiry-date,_id`, ties broken by id; and a filter on every
column — any of the codes of each closed set but the Status, sent once with its
codes joined by commas (FHIR's OR), one Status, and a range for each date, as
`ge` and `le` on its search parameter — combined with AND. Every value is
checked as the request is read in, so a code outside its set, a date that is no
calendar date or an inverted range is our `400` before the source is called.
**With no sort chosen, no `_sort` is sent**: unlike a Patient search, whose
matches have no order of their own and so take newest Last visit first, an FFI
History has the order the source keeps it in, and that is the order shown until
a heading is pressed. The domain's `HistorySort` and `FfiFilter` hold the rules
(`/docs/domain/ffi.md`, "Sort and filters").

**D5. NUMC Records are read on the same terms as FFIs.** A patient's NUMC
History is the source's own, read through it on every read, held and cached by
nothing, and not audited, sorted and filtered reads included, as D1 to D3
decide for an FFI History, through the same port (`PatientStore`'s
`findNumcHistory`), client, timeouts, failures and `SourceSearchset`. It is
sorted and filtered on D4's terms: by the Issue Date or the Expiry Date, one at
a time, as `_sort=±issue-date|±expiry-date,_id`, and with no sort chosen no
`_sort` is sent and the source's order is the order shown; a filter on every
column — any of the NUMC Types (`type`) and of the NUMC Outcomes (`outcome`),
each sent once with its codes joined by commas, and a range for each date, as
`ge` and `le` on `issue-date` and `expiry-date` — combined with AND; and every
value checked as the request is read in, a bad code, a non-calendar date or an
inverted range our `400` before the source is called. The domain's
`HistorySort` is the FFI History's, and `NumcFilter` holds the filters
(`/docs/domain/numc.md`, "Sort and filters"). It differs in three things alone:

- **The endpoint.** The source's `GET {base}/Patient/{id}/numc`, answering the
  same FHIR-shaped, non-conformant `searchset` of custom `"resourceType": "NUMC"`
  objects; our `GET /api/patient-numc/{patientId}`, plain JSON a page at a time,
  paged as D2 pages an FFI History.
- **The four elements.** A NUMC Record has a NUMC Type, a NUMC Outcome, an Issue
  Date and an Expiry Date, every one required, beside its own `id`. It has no
  Status, and nothing works out "expired" from its Expiry Date.
- **The two sets.** NUMC Type (`hyperbaric-chamber`, `submariner`, `diving`) and
  NUMC Outcome (`fit`, `unfit`, `waiver-granted`, `waiver-rejected`), each a
  Java enum, an enum of `openapi.yaml` and an SPA set held set-equal, as D2's
  are. The NUMC Outcome is a set of its own, not the FFI Outcome: the two share
  codes by chance, not by meaning, so an FFI's `request-waiver` is no NUMC
  Outcome, as a record's code or as a filter's. With no Status, Higher
  Authority or Submitting MC, nothing filters on them.

Each rule a NUMC Record breaks is logged under a reason naming NUMC
(`numc-no-id`, `no-numc-type`, `unknown-numc-outcome`, …), so a fault record
says which history broke; a failure of the call or of the searchset itself is
the one every paged read shares.

**D6. ARMC Boards are read on the same terms as FFIs.** A patient's ARMC
History is the source's own, read through it on every read, held and cached by
nothing, and not audited, as D1 to D3 decide for an FFI History, through the
same port (`PatientStore`'s `findArmcHistory`), client, timeouts, failures and
`SourceSearchset`. It differs in four things alone:

- **The endpoint.** The source's `GET {base}/Patient/{id}/armc`, answering the
  same FHIR-shaped, non-conformant `searchset` of custom `"resourceType": "ARMC"`
  objects; our `GET /api/patient-armc/{patientId}`, plain JSON a page at a time,
  paged as D2 pages an FFI History, the order the source's own.
- **The six elements.** An ARMC Board has an ARMC Board Type, an ARMC Outcome,
  a Start Date, an End Date, a Medical Officer #1 and a Medical Officer #2,
  beside its own `id`. The two Medical Officers are names as the source gives
  them, linked to no User or account; a missing or blank one is a failure of
  the source.
- **The one optional element.** The End Date may be absent — the board is still
  open — and is left out of our answer then, never sent as `null`. When it is
  there it is on or after the Start Date, a rule of the domain's `ArmcBoard`: a
  board ending before it starts is a failure of the source, never a row shown.
  The shared field reading (`SourceFields`) gains an optional calendar date for
  it; every other date of every history stays required.
- **The two sets.** ARMC Board Type (`aces`) and ARMC Outcome (`fit`,
  `unfit`), each a Java enum, an enum of `openapi.yaml` and an SPA set held
  set-equal, as D2's are. The ARMC Outcome is a set of its own, neither the FFI
  Outcome nor the NUMC Outcome.

Each rule an ARMC Board breaks is logged under a reason naming ARMC
(`armc-no-id`, `no-armc-board-type`, `unknown-armc-outcome`,
`armc-end-before-start`, …), so a fault record says which history broke. The
ARMC History is not sorted or filtered: the page and its order are the
source's alone.

**D7. Medication Records are read on the same terms as FFIs.** A patient's
Medication History is the source's own, read through it on every read, held
and cached by nothing, and not audited, as D1 to D3 decide for an FFI History,
through the same port (`PatientStore`'s `findMedicationHistory`), client,
timeouts, failures and `SourceSearchset`. It is not sorted or filtered: the
page and its order are the source's alone. It differs in four things alone:

- **The endpoint.** The source's `GET {base}/Patient/{id}/medication`,
  answering the same FHIR-shaped, non-conformant `searchset` of custom
  `"resourceType": "Medication"` objects; our
  `GET /api/patient-medication/{patientId}`, plain JSON a page at a time, paged
  as D2 pages an FFI History, the order the source's own.
- **The elements.** A Medication Record has six required elements — the
  Medication, Dispense Status, Start Date, End Date, Dispensary and Prescribing
  Doctor — and six optional ones — the Dosage, Route, Frequency, Step, Quantity
  and Duration — beside its own `id`. Every element but the Dispense Status,
  the dates and the Dispensary is free text, shown as the source gives it and
  read by nothing: the Duration is never worked out from the dates, and the
  Prescribing Doctor is a name linked to no User or account; a missing or
  blank Medication or Prescribing Doctor is a failure of the source. An
  optional element the source lacks is left out of our answer, never sent as
  `null`, as D6 leaves out an open board's End Date. The Dispense Status
  (`pending`, `dispensed`, `uncollected`) is a closed set of its own, a Java
  enum, an enum of `openapi.yaml` and an SPA set held set-equal, as D2's are.
- **The date-time precision.** The Start Date and End Date are date-times, to
  the minute, not calendar dates: the source gives each as ISO-8601 with an
  offset, our answer passes each on with that offset, and the SPA shows each
  in Singapore time. One with no offset names no instant, and is a failure of
  the source; so is an End Date before its Start Date, compared as instants, a
  rule of the domain's `MedicationRecord`. The shared field reading
  (`SourceFields`) reads a date-time with an offset for them.
- **The Dispensary shares Medical Centre with the FFI.** The Dispensary draws
  on the closed set an FFI's Submitting MC draws on, Medical Centre — one Java
  enum (`MedicalCentre`), one enum of `openapi.yaml` and one SPA set, held
  set-equal as D2's are. The fields keep their own names: `submittingMc` on an
  FFI, `dispensary` on a Medication Record. A centre the set gains is a centre
  of both.

Each rule a Medication Record breaks is logged under a reason naming
Medication or an element of its own (`medication-no-id`,
`no-dispense-status`, `unknown-dispensary`, `medication-end-before-start`, …),
so a fault record says which history broke.

## Consequences

- **The FFI, NUMC, ARMC and Medication pages depend on the source's
  availability.** A slow or failing source is "Couldn't load FFIs", "Couldn't
  load NUMC records", "Couldn't load ARMC records" or "Couldn't load
  medication records" with Try again, never an empty history.
- **A real source must answer this contract**, or the adapter changes with it.
  It must filter, sort, page, count accurately, keep its own stable order and
  send every field of every FFI, every element of every NUMC Record and every
  required element of every ARMC Board and Medication Record, each date-time
  with its offset; a source that cannot is no history source until its adapter
  changes.
- **A new code in any closed set is a contract change.** The source sending a
  code the service does not know is a `502`, not a row shown with a blank cell,
  until the enum, the document and the SPA set gain it together.
- **No trail records who read whose FFIs, NUMC Records, ARMC Boards or
  Medication Records.** A deployment whose policy requires
  every read of patient data to be recorded must revisit D3 together with the
  unaudited read by id (`/docs/domain/patient.md`).
- **The local mock answers canned pages only**: Tan Wei Ming's four FFIs and
  each sort of them, Tan Hui Min's seven over two pages and one canned read of
  each filter; Tan Wei Ming's four NUMC Records and each sort of them, Tan Hui
  Min's seven over two pages and one canned read of each filter; Tan Wei Ming's
  two ARMC Boards and Tan Hui Min's seven over two pages, the first still open;
  Tan Wei Ming's six Medication Records over two pages and Tan Hui Min's four,
  some lacking optional elements; none for every other fixture or read, and a
  `404` for any other id
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
- **A default sort, as a Patient search has** — newest Issue Date first.
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
  to one would be a failure of the source in the other.
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
