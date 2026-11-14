---
okf_version: "0.2"
---

# Domain rules

The behavior behind the terms in `/CONTEXT.md`: what each concept does, the
invariants it keeps, and why, under the glossary's own term names. Where a rule is
also a wire contract, `backend/docs/openapi.yaml` is the authority on statuses and
bodies, and the ADRs in `/docs/adr/` record the decisions.

# Sign-in and sessions

- [Sessions](sessions.md) - Session status in the SPA, how sessions end, sign-in reasons, and every trigger of session revocation.
- [Authentication](authentication.md) - Password Login and its refusals, the failure run, Lockout and Unlock, deactivation, the Bootstrap Admin exemption, and the recovery guard.
- [Passwords](passwords.md) - The change-required flag and confined session, the self-service change, the password policy and history, and hashing.

# Directory

- [SCIM directory](directory.md) - Users, Groups, the Bootstrap Admin and Admin group, edge whitespace, deletion and tombstones, and when a resource version advances.
- [SCIM protocol profile](scim-protocol.md) - Discovery, filtering and sorting with SQL Server's deviations, conditional writes, request bounds, and the release gate.
- [Connectors and their tokens](connectors.md) - Token format and Permissions, Superuser-only administration, lifetime and rotation, credential refusals, and connector deletion.

# Authority and operations

- [Authorization](authorization.md) - Permissions, the Superuser and Group Permissions, baseline access, deny by default, refusals, the Admin group, and the development fixtures.
- [Dormancy and scheduled jobs](dormancy.md) - The dormancy basis and windows, the dormancy job, the audit retention job, and scheduled job locks.
- [Administration and audit](administration.md) - The Accounts page and its projections, and the SCIM audit trail and how it is read.
- [Request paths](request-paths.md) - Reserved server paths, the SPA index, and file requests.

# Clinical data

- [Patients](patient.md) - The FHIR R5 Patient's elements, NRIC and name rules, derived age, Patient search and the Last visit, its Restricted classification, the error contract, logging, the absence of auditing for reads by id and the audit of every search, the Patient card's patient in context, the Search page, the dev fixtures and search stubs, and R5 conformance.
- [FFIs](ffi.md) - A patient's FFI History, the patient source's own, read through its FHIR-shaped but non-conformant page in the source's order: the elements and the five closed sets carried as codes, Status never derived from the Expiry Date, the failures and their log reasons, no caching, no auditing, the FFI page and its empty states, and the mock's FFI fixtures and fault stubs.
- [NUMC](numc.md) - A patient's NUMC History, the patient source's own, read on the FFI History's terms in the source's order: the four elements and the two closed sets carried as codes, the source contract and paging, the failures and their NUMC log reasons, no caching, no auditing, the NUMC page and its states, and the mock's NUMC fixtures and fault stubs.
- [ARMC](armc.md) - A patient's ARMC History, the patient source's own, read on the FFI History's terms: the six elements, the End Date the one optional and on or after the Start Date, the two closed sets carried as codes, the source contract and paging, the sort by either date with the source's own ties and the Outcome and date filters with the open-board rule, the failures and their ARMC log reasons, no caching, no auditing, the ARMC page with its sort headings, Filter panel and states, and the mock's ARMC fixtures, sort and filter stubs and fault stubs.
