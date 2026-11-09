---
type: Decision Record
title: "ADR 0011: Accepted policy deviations and scope limits"
description: The IM8 controls and App-Standards requirements this application knowingly does not meet, the entries still open, and what is out of scope.
tags: [compliance, im8, app-standards, deviations]
---

# 11. Accepted policy deviations and deliberate scope limits

Date: 2026-10-05

## Status

Accepted. Records the controls and standard requirements this application knowingly
does not meet, as reviewed against the IM8 application control catalog and the
Standalone User Access Control standard, so a reviewer sees a decision rather than
an oversight. The open work is dp-3 and dp-8; each entry says what stays
open.

## Context

The application is a SCIM 2.0 service provider with its own password Login and an
administration SPA. Its compliance reviews raised controls that are either met
elsewhere (at the deployment edge, by the directory) or deliberately not met. Left
unrecorded, each looks like a gap and invites someone to re-litigate it in code.

## Decision

### IM8 controls not met

| Control                              | Deviation                                                           | Rationale                                                                                                                                                                                                                                                                                                                                                                                                                 |
| ------------------------------------ | ------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| as-4 Authentication rate-limiting    | No application-layer limiter                                        | Throttling is the deployment edge's job for `/scim/v2/**`, Login and change-password (`/infra/docs/edge-throttling.md`, "Edge throttling"). The application keeps only per-request safety bounds — parser, body and page limits — which are not rate limiting.                                                                                                                                                            |
| dp-3 Data in transit encryption      | TLS unspecified beyond "TLS at the edge"                            | **Open beyond the database hop**, which is met (ADR 0013, "IM8 controls"; `/infra/docs/database-encryption.md`). Otherwise no minimum version, cipher policy or internal-hop TLS is specified. HSTS is Spring Security's default — sent only on a request the application sees as secure, never with `preload` — and no forwarded-headers strategy is configured, so behind a TLS-terminating edge the edge must send it. |
| ac-2 MFA enforcement                 | Password Login stays single-factor, Superuser-only actions included | Password Login is the deliberate authentication model for every User and the Bootstrap Admin. The same goes for the actions only a Superuser may take (connectors and their tokens, ADR 0015): there is no step-up, because password Login has no second factor to step up to. No SSO Login exists; the audit trail keeps its login method, `sso`, and MFA factor for one.                                                |
| ac-4 Access review                   | No periodic privilege re-attestation                                | Membership is directory-derived, and Group Permissions are set by a Superuser and audited (ADR 0015). The `group:read` Groups view supports a manual review. There is still no review cycle.                                                                                                                                                                                                                              |
| ac-12 SSO for internal services      | Every User uses the application's own Login                         | Every User and the Bootstrap Admin sign in by password Login, the application's own authentication interface; SCIM provisions identity. No SSO Login exists yet: one is designed on its own terms when it comes, and the audit trail keeps its login method, `sso`, and its MFA factor ready for it.                                                                                                                      |
| dp-8 Data classification labels      | The SPA shows no classification labels anywhere                     | **Open.** An app-wide deviation: no page of the SPA labels the classification of the data it shows.                                                                                                                                                                                                                                                                                                                       |
| st-3 Public vulnerability disclosure | No `security.txt` or reporting channel                              | Out of scope for this capability.                                                                                                                                                                                                                                                                                                                                                                                         |

ac-6 (default credentials) is met: every imposed credential — the seeded Bootstrap
Admin's, a connector password write's, an Unlock's — sets the change-required flag,
and a password session stays confined until the User replaces it (ADR 0007, "The change-required flag"). The one waiver is the seeded Bootstrap Admin with the
development fixtures on, which startup refuses outside the `local` profile, so
only a developer's machine can enable them. The Bootstrap Admin's first password
has no fallback: a start without one fails.

### Standalone User Access Control standard

- **No admin-initiated password reset tokens.** The standard asks for paired
  token-issuance and redemption endpoints. An Unlock of a locked User plus the
  authenticated change-password flow restores access without an administrator ever
  learning or transporting the credential, and with no reset token to issue,
  display, log or replay. The cost: a User who has forgotten their password cannot
  self-recover and needs a connector to set a new one, and a User whose password is
  suspected compromised but who is not locked cannot be made to change it by anyone
  in this application; the connector sets a new password, or deactivates the User,
  in the directory. There is no email recovery.
- **`userName` is not restricted to alphanumerics.** The standard's §5 Input says:
  "User-supplied input exceeding maximum length or with invalid format (e.g.,
  invalid email, non-alphanumeric username) is rejected with `400 Bad Request`."
  SCIM `userName` instead follows RFC 7643, which leaves its format to the service
  provider, and the identity provider's own format: directories provision emails
  and UPNs (`alice@example.org`). An alphanumeric-only rule would refuse
  the identifiers the directory owns. The length limit and the refusals of control
  characters and edge whitespace still apply (ADR 0013).
- **`userName` is mutable.** SCIM's core schema makes `userName` read-write and
  directory renames are routine, so rejecting them would break interoperability.
  The standard's concern — renames orphaning state — is answered structurally: the
  stable SCIM `id` is the principal and the key for all application-owned data,
  sessions are indexed by id, and a rename revokes the User's sessions.
- **Email addresses are not unique across Users.** The standard's reason is that
  email is a login and reset identifier; here it is neither, since Login is
  `userName`-only and no email recovery exists. RFC 7643 gives `emails` uniqueness
  `none`. Uniqueness holds only within a User: at most one primary and no duplicate
  `(type, value)` pair.
- **A deleted `userName` is reusable,** as RFC 7644 §3.6 allows. Audit resolves every
  actor and resource by stable id, never by `userName`, so a reused name cannot merge
  two identities in the trail.
- **Deletion is not a soft delete, and the tombstone holds no profile.** Traceability
  lives in the append-only audit stream, which keeps actor, resource id, operation
  and changed paths for the full retention period. Keeping a readable profile after
  a SCIM `DELETE` would retain personal data the deletion is understood to remove.
- **A write token is Superuser-equivalent.** The standard reserves role assignment to
  role administrators. Here the directory is authoritative for membership and a
  Group's membership confers its Permissions, so a token holding `group:write`
  necessarily can confer them (ADR 0015). Only a Superuser mints a token, but a
  compromised write token is still an escalation path, bounded by its 365-day
  lifetime and 14-day rotation overlap, immediate revocation, session revocation on
  every lost membership of a permission-bearing Group, and an audit event for each
  change.
- **No account-owner notifications.** Deferred, not settled: no channel exists, and
  dormancy lockout, Membership revocation and Unlock are recorded only in the audit stream
  (App-Standards finding USR-8).

### Out of scope

- SCIM Bulk, `/Me` self-service (answered `501`), and Enterprise User or custom
  schema extensions.
- Nested, transitive or dynamic Groups.
- Multi-tenancy, or more than one directory per deployment.
- Vendor-specific workarounds for Microsoft Entra ID, Okta or other connectors.
- OAuth authorization-server flows, refresh tokens and JWT access tokens: connector
  tokens are locally issued opaque credentials.
- SCIM-driven end-user Login or session creation.
- Administrator editing of SCIM-owned User, Group or membership data through the
  application's own API: the directory owns it.

## Consequences

- A change that would meet one of these controls reverses its entry here, and is
  edited into this ADR in the same change, never made in code alone.
- dp-3 stays open for what its entry names until a deployment specifies it. A
  production deployment must close it before it relies on this application. dp-8 stays
  open until the SPA labels the classification of what it shows.
- A deployment that exposes the service without edge throttling is misconfigured,
  since as-4 is met nowhere else.
