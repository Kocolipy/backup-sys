---
type: Decision Record
title: "ADR 0019: FFIs are the patient source's own, read through a FHIR-shaped page, and not audited"
description: A patient's FFI History is the in-house patient source's own, read through it on every request at GET {base}/Patient/{id}/ffi and held by no table or cache; the source answers a FHIR-R4-shaped searchset Bundle of custom FFI objects that is documented as not FHIR-conformant, paged by ADR 0017's startIndex and count in the source's own order; and an FFI read, like a read of one Patient by id, appends no audit row.
tags: [patient, ffi, paging, audit, integration]
---

# 19. FFIs are the patient source's own, read through a FHIR-shaped page, and not audited

Date: 2026-11-12

## Status

Accepted. `GET /api/patient-ffi/{patientId}` reads a patient's FFI History, and
the SPA's FFI page reads it five at a time for the patient in context.
`/docs/domain/ffi.md` is the behaviour in full; this file holds the decisions and
their reasons.

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
source's `total`. No `_sort` is sent, so the source's default order is the order
shown. Our answer is plain JSON outside `/api/patient`, so its errors are the
application API's `ApiError`.

**D3. FFI reads are not audited.** A read of a patient's FFI History appends no
audit row, as a read of one Patient by id appends none: it reads one patient's
records by the id the EHR context gave, the ordinary unit of care, not a sweep
across patients. A refused read is audited as `ACCESS_DENIED`, as every
authorization refusal is. `patient:read` gates it, in the handler's
`@PreAuthorize`, the chain's backstop and the document's `security` (ADR 0015).

## Consequences

- **The FFI page depends on the source's availability.** A slow or failing
  source is "Couldn't load FFIs" with Try again, never an empty history.
- **A real source must answer this contract**, or the adapter changes with it.
  It must page, count accurately, keep its own stable order and send every field
  of every FFI; a source that cannot is no FFI History source until its adapter
  changes.
- **A new code in any closed set is a contract change.** The source sending a
  code the service does not know is a `502`, not a row shown with a blank cell,
  until the enum, the document and the SPA set gain it together.
- **No trail records who read whose FFIs.** A deployment whose policy requires
  every read of patient data to be recorded must revisit D3 together with the
  unaudited read by id (`/docs/domain/patient.md`).
- **The local mock answers canned pages only**: Tan Wei Ming's four FFIs, Tan
  Hui Min's seven over two pages, none for every other fixture, and a `404` for
  any other id (`backend/docs/configuration/patient-source.md`).

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
