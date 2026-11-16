---
type: Decision Record
title: "ADR 0018: Patient search is the patient source's own, passed through"
description: A Patient search by name or full NRIC, filtered by gender, is passed through to the in-house patient source, which matches, filters, pages and counts, in its own stable order with no sort sent; the service reads startIndex and count by ADR 0017's rules but filters, orders and pages nothing itself, answers plain JSON with each Patient's Last visit from the source's last-visit extension, and audits every search it answers as PATIENT_SEARCH with the query's kind, whether the gender filter was used and the count, never the query or the filter's value.
tags: [patient, search, paging, audit, integration]
---

# 18. Patient search is the patient source's own, passed through

Date: 2026-11-10

## Status

Accepted. `GET /api/patient-search` searches the Patients by name or by full
NRIC, filtered by gender, and the SPA's Search page reads it five at a time.
`/docs/domain/patient.md` ("Patient search") is the behaviour in full; this
file holds the decisions and their reasons.

## Context

A clinician who does not have a Patient in context needs to find one by the
name they were given, or by the NRIC on a document. The Patients are not this
service's: every one is read from the in-house patient source on every request,
and none is held or cached (ADR 0014). Searching them is searching the source.

Every other paged read in the service pages a store it owns, by ADR 0017's
rules, ordering and counting in its own query. A search over Patients the
service does not hold cannot do that without holding them.

A search for a name or an NRIC is a read of Restricted data about many people
at once — the shape a credential enumerating patients takes — while a read of
one Patient by the id the EHR context gave is the ordinary unit of care, which
is not audited (`/docs/domain/patient.md`).

## Decision

**D1. The search is the source's, passed through.** `PatientStore.search`,
on the existing HTTP adapter (`PatientSourceAdapter`, the module's one outbound
client), sends FHIR R4 `GET {base}/Patient?…` with the query, the filter, the
page and `_total=accurate`, and takes the searchset `Bundle` it answers as the
page and the total. The service filters, orders and pages nothing of its own and
holds and caches nothing. The source's answer is held to the same rules as a
read by id: every entry is copied as a Patient is (ADR 0014, "A straight
copy"), and only a `200` searchset is an answer — any other status, a `404`
included, no answer within the timeouts, a Bundle with no `total`, or an entry
that is no Patient the service can hold, one with no `id` included, is a
failure of the source, the `502` a read by id answers with.

**D2. One box, two searches, refused before the source.** The query, trimmed,
is an exact NRIC search when it is a full NRIC in either case
(`^[STFGM]\d{7}[A-Z]$`, sent upper-cased as the identifier under the NRIC
NamingSystem), and a name search (`name:contains`) otherwise. A name search
needs at least three characters. There is no partial NRIC search and no
search that lists every Patient. The domain's `PatientQuery` decides it, and
the request binds `q` through it, so a query too short is the binder's `400`
before the source is called.

**D3. Where it departs from ADR 0017.** The search reads `startIndex` and
`count` with ADR 0017's `PageParameter` and `PageRequest`, so its parameters
are coerced and refused exactly as every paged read's are, and it answers
`totalResults` and `startIndex` beside its rows. It departs in what follows:
the page is the source's to read, sent as `_offset` (`startIndex - 1`) and
`_count`, and `totalResults` is the source's own count. The service does not
answer `count=0` or a page past the end from a count of its own, as D1 of ADR
0017 has every other reader do; the source is asked, and its answer is the
answer. The order is the source's too, and so is the filter (D7).

**D4. Last visit is the source's extension.** A Patient's Last visit is the
date of their latest visit, which FHIR's base `Patient` has no element for. The
source carries it as an extension on each Patient,
`https://fhir.synapxe.sg/StructureDefinition/last-visit`, a `valueDate` to the
day, absent for a Patient with none. The adapter reads it from a search's
entries; a value not to the day is a failure of the source. A read by id ignores it, and the R5
`Patient` served at `GET /api/patient/{id}` does not carry it.

**D5. Plain JSON, outside the FHIR namespace.** The answer is the application
API's own shape, `{totalResults, startIndex, patients: [{id, name, nric,
gender, birthDate, lastVisit}]}`, at `/api/patient-search` — outside
`/api/patient`, so its errors are `ApiError`, not a FHIR `OperationOutcome`.
Each row carries the Patient's id, the EHR's FHIR id a read by id takes, so a
page can open the Patient from it. `patient:read` gates it, in the handler's
`@PreAuthorize`, the chain's backstop and the document's `security` (ADR 0015).

**D6. Every answered search is audited; reads by id stay unaudited.** Each
search the source answers appends one `PATIENT_SEARCH`: the signed-in User as
actor, resource type `Patient` with no subject, the number of Patients the page
held, and the query's kind — `NRIC` or `NAME`, a closed set — as the row's
filter shape. Never the query, a name or an NRIC: no component of an audit
event can hold a string (ADR 0004's boundary), and the kind is what tells a
lookup from a sweep. Fail-closed, as a SCIM bulk read is: a search the trail
cannot record is not answered (ADR 0004). A search refused with `400`, or that
the source failed, read nothing and records nothing. The `patient` module names
the searcher through `audit`'s own port, `AuditActorContext`, which the session
module implements, so `patient` gains edges to `audit` and `paging` and none to
`auth`.

**D7. Only the gender filter is forwarded, and no sort is sent.** The one
filter is a `gender`, `male` or `female`, sent as the source's `gender`; the
domain's `PatientFilter` holds it, and it is checked as the request is read in:
a gender spelled any other way is a `400` before the source is called. Any
other request parameter is ignored. The adapter sends no `_sort` — not even an
`_id` tie-break — because the in-house patient source answers every search in
a stable order of its own, so the pages of one search are consistent without
one. The results appear in that order. The service orders and filters nothing
itself, so a page is always the source's page, and its total the source's count
of what the filter kept.

**D8. The row records whether the filter was used, never its value.** A
filter's value — a gender — narrows a search toward one person as a query does,
so the `PATIENT_SEARCH` row's filter shape names the query's kind and then each
filter used, as a comparison whose value is `?` (`NAME and gender eq ?`), from a
closed set (`AuditPatientSearchFilter`). A row stored with another shape is
kept as it was stored, and the Audit page shows it verbatim.

## Consequences

- **A search depends on the source's availability and its search support.** A
  slow or failing source is the search's error, with Retry, never an empty
  result. A source that cannot filter by `gender`, page by `_offset` or count
  accurately supports no Patient search, or none so filtered, until its adapter
  changes, and one whose order is not stable between two page reads can repeat
  or skip a Patient across pages.
- **How a name matches is the source's.** `name:contains` may match a usual or
  maiden name as well as the official one; the row always shows the official
  name, so a row may not visibly contain what was typed.
- **A page is a slice of a changing source**, as ADR 0017's are: a Patient
  visited between two page reads can move between pages.
- **No query or filter value reaches a log or the trail.** The request's record
  names the route template, never the query string, and the audit row carries
  only the kind and whether the filter was used.
- **The local mock answers only the documented searches.** Its canned stubs
  answer the name search `tan` (two pages), each gender filter on it, one NRIC
  and one search that matches nothing, each in the mock's one stable order; any
  other search is an empty searchset with `total: 0`
  (`backend/docs/configuration/patient-source.md`).

## Alternatives considered

- **Search a copy of the Patients held by this service**, paged by ADR 0017's
  readers. Rejected for the reasons ADR 0014 rejects holding Patients: a copy of
  Restricted data at rest, kept fresh by a sync.
- **Read every match and page in the service.** Rejected: a name search can
  match many Patients, every one of them Restricted data read for nothing, and
  the source already pages.
- **Record the query, masked or hashed, in the audit row.** Rejected: a masked
  name or NRIC is still a partial identifier, and a hash of an NRIC is reversible
  by enumerating the NRIC space. The kind and count say what kind of search was
  made without saying for whom.
- **Leave searches unaudited, as reads by id are.** Rejected: a search is a
  bulk read, the shape enumeration takes, and an unrecorded one is invisible.
