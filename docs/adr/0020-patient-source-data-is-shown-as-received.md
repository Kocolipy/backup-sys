---
type: Decision Record
title: "ADR 0020: Patient-source data is shown as received"
description: The patient source is the truth, so no record or response is refused for what a field holds — a missing field is shown as "—", one in the wrong format as "Error", a code outside its closed set as the raw code — while transport and structural failures still fail the read; the shared pieces each read adopts it through, enacted for the NUMC History, and the parts of ADR 0014, 0018 and 0019 it supersedes.
tags: [patient, numc, integration, observability, spa]
---

# 20. Patient-source data is shown as received

Date: 2026-11-16

## Status

Accepted. Enacted for the NUMC History (`/docs/domain/numc.md`). The other
patient-source reads — the FFI, ARMC and Medication Histories, a Patient by id
and a Patient search — keep the strict reading their ADRs describe until each
adopts the shared pieces below; the rule is decided for all of them.

## Context

The service reads every patient's data from the in-house patient source on
every request and holds none of it (ADR 0014, 0018, 0019). A strict read
fails the whole response, our `502`, when one record misses an element, holds a
code outside its closed set or a date not to the day.

A clinician then sees "Couldn't load NUMC records" for a whole history because
one field of one record is malformed, and sees nothing of the records that are
well-formed. The service is not the system of record: it cannot correct the
data, and refusing to show it hides what the source holds without making it
right. A blank cell for a code the service does not know hides the code as
surely.

## Decision

**D1. The patient source is the truth.** Nothing rejects a record or a response
because of what a field holds. Each field of a record is one of three things,
and the SPA shows each as its value, as **"—"** (the em dash every empty cell
shows) or as **"Error"**:

- **Missing**: a key that is not there, `null`, an empty string, or a string
  that is only whitespace.
- **Wrong format**: the wrong JSON type — a number, a boolean, an object or an
  array where a string, a date or a date-time is expected, free text included —
  or a string that cannot be parsed as the date or the date-time the field
  holds.
- **Present**: anything else, as it came.

**No semantic or range check is made of the data.** A date in the future, an
end before its start, or an id outside FHIR's id grammar is shown as received.
History record ids are not checked; a row with no id is keyed by its place.

**D2. A code outside a closed set is shown as the raw code**, with a neutral
label or badge. The closed sets stay, for what the service checks and offers:
an unknown code in a filter parameter is our `400`, a Filter panel offers only
the known codes, and each set is one Java enum, one enum of
`openapi.yaml` and one SPA set, held `equal` by `VocabularyContractTests` and
the SPA–API contract check.

**D3. What fails the whole response**, with the read's own error states:

- **Transport failures**: no answer within the timeouts, the source
  unreachable, or a status the read does not take.
- **Structural failures**: a body that is not JSON, not the expected resource
  or not a `searchset` `Bundle`; a missing or negative `total`; an entry with no
  resource, one that is no object, or one of another resource.

**D4. The sort is the source's, passed through unchanged**, as ADR 0018, D7
and ADR 0019, D4 have it: a malformed field sorts wherever the source puts it.

**D5. The shared pieces.** Every read adopts the rule through the same pieces:

- **Lenient mapping** (backend). A patient-source field is bound as the raw
  JSON it arrived as (`JsonNode`), so that Jackson's coercion of a number into
  a string cannot hide a wrong type, and classified — present, missing or
  invalid — rather than thrown on (`ReceivedFields`). A domain record holds
  each such field as a `SourceField` and refuses none in its constructor; it
  names its invalid ones (`ReceivedRecord.invalid()`).
- **The API's shape.** A patient-source field is nullable; a closed-set field is
  "a known code or any string" (`anyOf` the set's enum, any string, and
  `null`); and each row carries `invalid`, the names of its fields whose format
  was wrong. A `null` field `invalid` does not name is missing.
- **Observability.** One `WARN` per response that held invalid fields,
  `patient_source.invalid_fields`, listing each field's path in the source's
  answer (`entry[3].issueDate`) and never its value (ADR 0003), and the counter
  `patient_source.invalid_fields`, tagged by `read` and `field`. Both are
  written by `observability.InvalidSourceFields`: the patient module writes no
  log record (ADR 0014).
- **The SPA–API contract check.** The response-shape sampler samples nullable
  fields, an `anyOf` and the `invalid` array, and the vocabulary check keeps its
  `equal` relation over the known codes.
- **The SPA.** The decoder of the backend's response stays as strict as any
  other — an unknown field and the page envelope are refused — and only
  the values of patient-source fields are lenient, through one lenient field
  decoder (`readObject(…).sourceFields()`). One cell-rendering helper
  (`shown`) turns present, missing and invalid into the value, "—" and
  "Error", and label and badge lookups fall back to the raw code (`byCode`).

**D6. What this supersedes.** For the NUMC History:

- ADR 0019, D2: "every one required", and a code outside a closed set being a
  failure of the source.
- ADR 0019, D4: each rule a NUMC Record breaks being logged under a reason
  naming NUMC — no NUMC Record breaks one.
- ADR 0019, Consequences: a new code being "a `502`, not a row shown with a
  blank cell", and the source having to "send every required element".

For each other read, as it adopts the rule:

- ADR 0014, "Only a `404` is no patient": a Patient with no NRIC, no official
  name or one with no text, no gender or an unknown code, or a birth date
  missing or not to the day being a failure of the source. Another patient's
  id stays one: it is not the expected resource.
- ADR 0018, D1: a search's entry with no `id` being a failure of the source.
- ADR 0019, D6 and D7: an ARMC Board or a Medication Record ending before it
  starts, a date-time with no offset, a missing required element or an unknown
  code being a failure of the source.

## Consequences

- **A clinician sees every record the source holds.** A malformed field costs
  one cell, never the table; the cell says "Error", so the data's fault is
  visible rather than hidden.
- **Data quality is an operational signal, not an outage.** The `WARN` and the
  counter say which read and which field the source got wrong, and how often;
  neither names a value or a patient.
- **A new code is no contract change for a read shown as received.** The
  source may send it, and it is shown raw until the enum, the document and the
  SPA set gain it together — which the filters and their popovers need.
- **The SPA trusts the backend's classification.** A field the backend sends in
  a shape it does not document is "Error" in its cell, never a failed read; the
  row's shape is held strictly.
- **A read shown as received has no data-fault stubs.** Its data's faults are
  a `200` fixture of the mock carrying missing, malformed and unknown-code
  fields, and no failure reason names them; its fault stubs serve transport and
  structural failures only.

## Alternatives considered

- **Keep failing the response on a malformed field.** Rejected: one field of
  one record hides the whole history, and the service cannot correct the data
  it refuses.
- **Drop a malformed record and show the rest.** Rejected: the clinician cannot
  tell a record that is not there from one that was dropped, and the count would
  not match the source's `total`.
- **Show a malformed field blank.** Rejected: a blank reads as "the source holds
  nothing" when it holds something wrong; "Error" and "—" keep the two apart.
- **Send the raw invalid value to the SPA.** Rejected: the SPA would have to
  parse and judge the source's data itself, and a value of the wrong JSON type
  would loosen the API's own shape. `null` with its name in `invalid` keeps the
  shape typed and the judgement in one place.
- **Loosen the SPA's decoder of the whole response.** Rejected: the backend's
  shape is the service's own contract and stays strict; only the patient
  source's values are lenient.
