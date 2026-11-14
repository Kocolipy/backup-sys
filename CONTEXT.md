# backup-sys

The language of a session-authenticated web application whose identities are
provisioned by external directories over SCIM 2.0. This file says what each term
means; the rules behind them are in `/docs/domain/`, under the same names, one file
per area — each section below names its own.

## Language

### Request paths

Rules: [request-paths](docs/domain/request-paths.md).

**Reserved server path**:
A request path the backend answers itself (`/api`, `/actuator`, `/scim` and
everything beneath them), never with the SPA index.

**SPA index**:
The single `index.html` the single-page application boots from, served for any
client-side route.

**File request**:
A request whose last path segment contains a dot; when missing, it stays a 404
rather than becoming the SPA index.
_Avoid_: frontend route

**Error namespace**:
A reserved server path whose every refusal has one error body, whichever layer
refuses: `/api` answers with `ApiError`, `/api/patient` with a FHIR
`OperationOutcome`, `/scim/v2` with the SCIM error document. The most specific
namespace covering a request's path decides.
_Avoid_: error shape by package, error path

### Sessions

Rules: [sessions](docs/domain/sessions.md).

**Session status**:
What the SPA knows about the current session: `checking`, `unchecked`,
`authenticated` or `guest`. `unchecked` is a start-up check that did not
answer: the session is unknown, not ended, and the visitor is no Guest.

**Guest**:
A person with no authenticated session, and the session status that names them.
_Avoid_: Visitor, anonymous user

**Expired session**:
A session that was authenticated and that the backend has since ended, for
whatever reason; the SPA returns its holder to login saying so.

**Return destination**:
The protected path a Guest asked for before being sent to sign in, replayed once
they have.

**Landing page**:
Where the SPA sends a signed-in User with no Return destination — after signing
in, off the login page, from a path with no page, off a page its guard refuses,
and back from the password change: Patient Search (`/search`) for a session
holding `patient:read`, Home for every other. A Return destination still wins,
and a confined session is still held to the password change.
_Avoid_: default destination, start page

**Home**:
The signed-in page at `/`, open to every signed-in session: the Landing page of
a session without `patient:read`. The login page is `/login`.

**Shell**:
The layout every signed-in page renders inside — who is signed in, the page
title, Sign out and the page's one error line, around the content area the page
fills — and the table that declares each signed-in page once. It is not the SPA
index.
_Avoid_: frame, app layout

**Sign-in reason**:
Why a Guest is at the login page — an Expired session, an Idle sign-out, a
password change, or a Lockout that a password change ran into — and so what
the page says. A cold arrival has none.
_Avoid_: provenance, login notice flag

**Idle timeout**:
The length of time without a request after which the backend ends a session.
_Avoid_: inactivity (that is the dormancy job's word)

**Absolute session lifetime**:
The maximum age of a session from the Login that signed it in, however active it
is; a session nobody signed in to counts from its creation.

**Idle sign-out**:
The SPA ending an idle session on its own, after warning the User.
_Avoid_: inactivity sign-out

**One session per User**:
The rule that an accepted Login ends every other session the User holds.

**Session revocation**:
Ending the sessions a User already holds, because something about its identity,
credential or authority changed.

### Directory

Rules: [directory](docs/domain/directory.md),
[scim-protocol](docs/domain/scim-protocol.md),
[connectors](docs/domain/connectors.md).

**SCIM service provider**:
The role this application plays: external identity providers call its SCIM
interface to create and manage Users and Groups.

**SCIM directory**:
The single, untenanted namespace of Users and Groups in one deployment, shared by
every connector.

**Connector**:
One external client of the SCIM interface, registered by the Superuser under a
name no other live connector holds, and authenticated by its own tokens.
_Avoid_: client, integration, IdP (the IdP is what drives a connector)

**SCIM connector token**:
An opaque bearer credential issued to one connector, carrying Token Permissions,
usable only on the SCIM interface. It is active while it still authenticates —
not revoked and not yet expired — and only active tokens are listed.
_Avoid_: API key, scope (no token has one), read-only or read-write token

**Connector external identifier**:
One connector's own `externalId` alias for a User or Group, invisible to every
other connector.

**Deleted SCIM connector**:
A connector the Superuser removed: its tokens are revoked and its aliases deleted, but
its record remains so audit events still name it. It holds no name, so a new
connector may take it.

**User**:
Any identity in the SCIM directory — active or deactivated, with a session or
without, with a password or without.
_Avoid_: Account, SCIM User (in prose), member (except of a Group)

**Group**:
A set of Users in the SCIM directory with direct members only; it confers its
Group Permissions on its direct members.

**Member label**:
How a Group's rendered members name each User: its `displayName`, or its
`userName` when it has none.

**Admin group**:
The server-seeded Group `Admin`, recognised by its `admin-group` reservation
marker alone, never its name; a direct member is an Admin, and is the Superuser.
It carries no Group Permissions.

**Admin**:
A direct member of the Admin group, and so the Superuser. A User holding
administrative Permissions through other Groups is not an Admin.
_Avoid_: admin (for a holder of administrative Permissions)

**Bootstrap Admin**:
The seeded recovery User: an immutable member of the Admin group, never locked,
and not writable over SCIM.

**Reservation marker**:
The mark seeding writes once on the Bootstrap Admin and the Admin group, by which
their protections are recognised instead of by name.

**SCIM tombstone**:
The privacy-minimal record left when a User or Group is deleted: its type, stable
id and deletion time, and nothing else.

**Deleted User** / **Deleted SCIM Group**:
A User or ordinary Group removed with SCIM `DELETE`, leaving only a tombstone.

**Deactivated User**:
A User whose SCIM `active` is false; it cannot sign in.
_Avoid_: disabled, inactive User

**Reactivation**:
A SCIM write that takes a User's `active` from false to true.

**Resource version**:
The opaque value each User and Group carries, advanced exactly once by every
write that changes its rendered document, and exposed as its strong ETag.
_Avoid_: revision, weak ETag

**Conditional write**:
A SCIM `PUT`, `PATCH` or `DELETE` carrying `If-Match`, applied only while the
resource version still matches; optional, so a write without one is
unconditional.

**Edge whitespace**:
Whitespace (any Unicode `White_Space` character) at the start or end of a key —
a unique or looked-up User or Group attribute, or a password Login's username —
which is refused rather than trimmed.
_Avoid_: padding, trailing spaces (the rule covers both edges and every kind of whitespace)

**Practical SCIM protocol profile**:
The part of SCIM 2.0 this application implements and advertises in discovery;
everything outside it is refused rather than ignored.

**SCIM release gate**:
The deployment switch (`APP_SCIM_ENABLED`) that, when closed, makes the whole
`/scim/v2` namespace answer `404` ahead of authentication.

### Authorization

Rules: [authorization](docs/domain/authorization.md).

**Permission**:
One named, fine-grained power a protected action requires, such as
`user:read` or `ops:read`; the set is closed and defined in code, not
configuration. The Superuser is not one.
_Avoid_: scope, right, privilege

**Group Permissions**:
The Delegatable Permissions a Group carries, stored per Group in the database
rather than configured, and held by each of its direct members from its next
sign-in. A Group with none confers nothing. Set only by the Superuser, as a
complete set (`PUT /api/admin/groups/{id}/permissions`), never on the Admin group
and never over SCIM, so `group:write` on a Group that carries Permissions decides
who holds them.
_Avoid_: Role, role mapping, mapped Group, Role assignment; role (for `ROLE_USER`,
baseline access)

**Permission-change propagation**:
How a change of what a User's Groups confer reaches live sessions: losing a
Permission — leaving a Group that carries it, the Group being deleted, or the
Superuser taking it off the Group — ends them after the commit; gaining one waits
for the next Login.
_Avoid_: role-change propagation

**Superuser**:
The session authority, spelled `ROLE_SUPERUSER`, of a User who is a direct member
of the Admin group. It is not a Permission, so no Group can carry it; its session
also holds every delegatable Permission, and `/api/auth/me` reports it as
`superuser`. Only the Superuser administers connectors and their tokens, and sets
a Group's Permissions.
_Avoid_: admin (for a holder of administrative Permissions, who is not a Superuser)

**Conferred power**:
What a User's direct Group memberships confer beyond baseline access, answered as
one: its effective Permissions (its Groups' Group Permissions, plus every
Delegatable Permission for a member of the Admin group) and whether it is the
Superuser. Sign-in, the Accounts list and every Permission change read it from the
same place.
_Avoid_: authority (the Superuser is the session authority, and Spring's
`GrantedAuthority` is any entry a session holds)

**Delegatable Permission**:
A Permission a Group may carry: `user:read`, `account:unlock`, `group:read`,
`audit:read`, `ops:read`, `connector:read` and `patient:read`. `account:unlock`
is the helpdesk Permission: Unlock, which has no effect on a User that is not
locked. `patient:read` reads a Patient and runs a Patient search; a User holds
it only through a Group that carries it, or as the Superuser.

**Token-grantable Permission**:
A Permission a connector token may carry: `user:read`, `user:write`, `group:read`
and `group:write`. The write halves are token-only: no session holds `user:write`
or `group:write`, a Superuser's included.

**Deny by default**:
The rule that every operation of the application chain declares what it needs —
public, self-service, one or more Permissions or the Superuser — and whatever
declares nothing is refused.

**Token Permissions**:
The Permissions a connector token carries: only Token-grantable Permissions, from
the same vocabulary a Group carries. A token with a write Permission is Superuser-equivalent, which is why only
the Superuser mints one.
_Avoid_: scope, `scim.read`, `scim.write`

**Baseline access**:
What every active User holds whatever its Groups carry, without a required
password change: self-service, spelled `ROLE_USER` in the session, and no
Permission. A User in no Group holds baseline access alone, so it reads no
Patient.

**Development fixtures**:
The development Groups and their Users and — under the `local` profile alone —
the development Groups' Permissions, seeded only when `APP_DEV_FIXTURES_ENABLED`
is on. The dummy Patients are not among them: they are the Patient source's
mock's. Turned on outside the `local` profile they
fail startup, because they waive the Bootstrap Admin's first-login change.

**Self-service**:
An operation needing a signed-in session and no Permission, acting only on the
session's own User.

**Authorization refusal**:
A `403` for a signed-in caller the operation does not admit — a Permission it
lacks, a route nothing declares, or a confined session — that never names the
Permission, Group or rule.
_Avoid_: forbidden (for a CSRF refusal, which is not an authorization decision)

### Authentication

Rules: [authentication](docs/domain/authentication.md),
[passwords](docs/domain/passwords.md).

**Login**:
The one operation that turns submitted credentials into a session or a refusal.
Its login method is recorded on the audit trail's login events and on
`session-start`: `password`, the only method there is, or `sso`, which the audit
trail reserves for an SSO Login.
_Avoid_: sign-on, authenticate (as a noun for the operation), database login
(a SQL Server principal, see Database)

**SSO Login**:
A Login whose credential an external identity provider checks rather than this
service. None exists: the audit trail keeps its login method, `sso`, and its MFA
factor ready for one.
_Avoid_: single sign-on (spelled out), federated login

**EHR context**:
The patient this browser tab has in context, a FHIR id, possibly absent. Kept
by the tab alone, never server-side, and ended with the session. Selecting a
Patient on the Search page sets it, replacing the patient there; only another
selection or the session ending takes a patient out of context.
_Avoid_: patient context, session context

**MFA factor**:
The second factor an SSO Login was made with, recorded on its `LOGIN_SUCCESS`:
`idp-attested`, or an RFC 8176 factor the identity provider named. A password
Login carries none.
_Avoid_: MFA method, authentication method, acr

**Failure run**:
The consecutive failures recorded against one User: rejected password Logins,
and wrong current passwords on the self-service password change. A User with no
password has none: there is no password to guess, so neither lengthens it.

**Lockout**:
The permanent state a User enters when its failure run reaches the limit or the
dormancy job finds it dormant; only an Unlock ends it.

**Lock cause**:
Why a User is locked, `FAILURES` or `DORMANCY`, recorded beside the lock.

**Unlock**:
A holder of `account:unlock` ending a User's lockout, whatever its cause, and its
failure run; it
never changes `active`, just as deactivation never ends a lockout. Only while a
lockout is in force: an Unlock of a User that is not locked is refused and
changes nothing.

**Bootstrap Admin exemption**:
The Bootstrap Admin's immunity from lockout and from both dormancy steps, so the
deployment always has a way back in.

**Change-required flag**:
The application's mark that a User's current password was imposed by someone
else and must be replaced before a password Login may do anything else.
_Avoid_: password expiry, temporary password

**Confined session**:
A session issued by a password Login while the User's change-required flag is set;
it may only change the password or log out.

**Self-service password change**:
A User replacing its own password from its own session.

**Password policy**:
The one set of rules every path that sets a password applies: length, no
`userName` inside it, and no reuse from the password history.
_Avoid_: complexity rules (there are none)

**Password history**:
The User's three most recent passwords, the current one included, kept as
hashes so a new password can be refused for repeating one.

**Recovery guard**:
The rules that keep a deployment recoverable: no Admin may Unlock its own User,
and the Bootstrap Admin can never be locked.

### Dormancy

Rules: [dormancy](docs/domain/dormancy.md).

**Dormancy basis**:
The instant a User's dormancy is measured from — its last real Login, completed
password change, reactivation or Unlock, or else its creation.

**Dormant User**:
A User whose dormancy basis is older than one of the dormancy windows: the
lockout window or the longer revocation window.
_Avoid_: inactive User (that is a deactivated one)

**Dormancy verdict**:
What the dormancy job owes one User at one instant, decided from its dormancy
basis and "now" alone: not due, Lockout, or Lockout + Membership revocation. The
only place the job compares a basis with a window.

**Membership revocation**:
The dormancy job's second step: removing every direct Group membership — the
Admin group's included, the Bootstrap Admin's exempt — of a User past the
revocation window, recorded as one `DORMANCY_MEMBERSHIP_REVOCATION`.
_Avoid_: role revocation

**Dormancy job**:
The daily scheduled job that locks dormant Users and revokes every Group
membership of those dormant for longer. It never writes `active` and never deletes.
_Avoid_: inactivity deactivation, dormant-authority revocation (the two jobs it
replaced)

**Audit retention job**:
The daily scheduled job that deletes audit events older than the retention period.

**Scheduled job lock**:
The per-job row a scheduled job run holds, so two runs of one job never overlap
across instances.

### Patients

Rules: [patient](docs/domain/patient.md). Decided in ADR 0014 and ADR 0018.

**Patient**:
The person care is about, as this service reads them from the Patient source: one
NRIC, an official name whose text is authoritative, a gender and a date of birth,
served as a FHIR R5 `Patient` resource at `GET /api/patient/{id}` to a session
holding `patient:read`. Age is derived from the date of birth, never held.
_Avoid_: patient record, demographics, subject (a Patient is no User)

**Patient source**:
The in-house system the backend reads every Patient from, by its EHR's FHIR id
or by a Patient search, on every read: assumed FHIR R4 and unauthorised until
its contract is known, and mocked by WireMock for local runs and E2E. The SPA
never reaches it.
_Avoid_: patient service, patient API, FHIR server (the EHR's FHIR API is
another system)

**Patient card**:
Home's card showing the EHR context's patient — name, NRIC, gender, date of
birth and age — or "No patient in context" when the tab holds no EHR context,
its EHR context names no patient, or the Patient source holds none under its id. With no patient in context it reads nothing, and for a session without `patient:read` it reads nothing and says the account has no access to patients. The Patient source failing is the card's failed state, never "No patient in context".

**Patient search**:
Finding Patients by a name, of at least three characters, or by a full NRIC,
typed into the Search page's one box — a page only a session holding
`patient:read` opens or is linked to: the Patient source's own search, passed
through and read five to a page, in the source's own stable order, and
narrowed by gender. The source orders and filters; the service does neither.
Every search the source answers is audited as `PATIENT_SEARCH`, by its kind,
whether it used the gender filter and its count, and never its query or the
filter's value; a read of one Patient
by id is not. Selecting a result puts that Patient into the EHR context.
_Avoid_: patient lookup, patient list, patient query (no search lists every
Patient)

**Last visit**:
The date of a Patient's latest visit, as the Patient source records it on the
Patient (its `last-visit` extension); a Patient may have none. Patient search
shows it in each row.
_Avoid_: last seen, last encounter, last admission

### FFIs

Rules: [ffi](docs/domain/ffi.md). Decided in ADR 0019.

**FFI (Fit for Instruction)**:
One record of whether a patient was found fit for an instruction — an
exercise, a test, a release — as the Patient source holds it: its FFI Type,
Outcome, Issue Date, Expiry Date, Higher Authority, Submitting MC and Status,
every one required. Read from the Patient source on every read, never held,
and not audited.
_Avoid_: fitness record, medical clearance, certificate

**FFI History**:
Every FFI of one patient, in the Patient source's own order unless sorted by
its Issue Date or Expiry Date, narrowed by any filter on its columns, read five
to a page on the FFI page (`/ffi`) for the patient in context, by a session
holding `patient:read`. The source sorts and filters it, as it does a Patient
search.
_Avoid_: FFI list, FFI log

**FFI Type**:
What an FFI is for, one of a closed set: Overseas Exercises, CAT A - OCS/SCS,
IPPT FFI, Dental FFI, ORD / Release from Service, Vocational FFI.

**Outcome**:
What an FFI found, one of FIT, REQUEST WAIVER and UNFIT.
_Avoid_: result, verdict (the dormancy job's word)

**Issue Date**:
The calendar date an FFI or a NUMC Record was issued.

**Expiry Date**:
The calendar date an FFI or a NUMC Record runs to. Nothing is derived from it:
it does not decide an FFI's Status, and nothing works out "expired".

**Higher Authority**:
The medical authority an FFI answers to, one of CAMO, CARMO, CNMO, Comd MMI
and DIS.

**Submitting MC**:
The camp medical centre that submitted an FFI, one of Nee Soon Camp MC,
Kranji Camp MC, Hendon Camp MC and Mandai Hill Camp MC.

**Status**:
Whether an FFI is Active or Inactive, as the Patient source says. It is the
source's own and is never derived from the Expiry Date: an FFI past its
Expiry Date may still be Active, and nothing works out "expired".
_Avoid_: expired, valid

### NUMC

Rules: [numc](docs/domain/numc.md). Decided in ADR 0019 (D5).

**NUMC Record**:
One record of whether a patient was found fit for one underwater duty, as the
Patient source holds it for the Navy Underwater Medicine Centre (NUMC): its
NUMC Type, NUMC Outcome, Issue Date and Expiry Date, every one required. It has
no Status. Read from the Patient source on every read, never held, and not
audited.
_Avoid_: NUMC Assessment, dive medical, underwater fitness record

**NUMC History**:
Every NUMC Record of one patient, in the Patient source's own order unless
sorted by its Issue Date or Expiry Date, narrowed by any filter on its columns,
read five to a page on the NUMC page (`/numc`) for the patient in context, by a
session holding `patient:read`. The source sorts and filters it, as it does an
FFI History.
_Avoid_: NUMC list, NUMC log

**NUMC Type**:
The underwater duty a NUMC Record is for, one of a closed set: Hyperbaric
Chamber Duties, Submariner Duties, Diving Duties.

**NUMC Outcome**:
What a NUMC Record found, one of FIT, UNFIT, WAIVER GRANTED and WAIVER
REJECTED. A set of its own, apart from an FFI's Outcome.
_Avoid_: NUMC result, a bare "Outcome" (that is an FFI's)

### Database

Decided in ADR 0013. Every term here names a database principal, never the
sign-in operation: write "database login" or one of the qualified names below, and
keep a bare **Login** for the operation under Authentication.

**Database login**:
One of the three database identities the backend connects as — the owner
login, the app login and the retention login — each holding only its own
privileges, with no role switching on a connection.
_Avoid_: database role (the roles are what a login is a member of), `SET ROLE`,
a bare "login" (that is the sign-in operation)

**Owner login**:
The database login that owns the schema and runs migrations; only the migrate
step holds it, never the running service.
_Avoid_: admin user, master user (that is RDS's own)

**App login**:
The database login every request runs as, a member of `backend_app` only; it may
append audit events but never update or delete one.
_Avoid_: runtime role, service account

**Retention login**:
The database login only the audit retention job uses, a member of
`backend_audit_retention` only, holding `SELECT` and `DELETE` on audit events and
nothing else: the one principal that may delete an aged-out event, and no
principal may update one.
_Avoid_: retention role (the role it belongs to), audit admin

**Migrate step**:
The one-shot run of the service that applies the schema migrations as the owner
login and exits, before the service starts, so the service itself never holds
the owner credential.
_Avoid_: startup migration, Flyway at boot

### Administration

Rules: [administration](docs/domain/administration.md).

**Accounts page**:
The administrative screen at `/accounts`: the Users and Groups projections, Unlock
of a locked User, setting a Group's Permissions (the Superuser only), and
connector management, each view shown by its own Permission.

**Users projection**:
What administration may know about each User — never a credential — read a page
at a time, searched by `userName` or display name, and sortable by Directory
attributes (display name, `active`) but never by login state; never listed whole
(ADR 0017).

**Groups projection**:
What administration may know about each Group: its name, member count, whether
it is the Admin group, and its Group Permissions. Read a page at a time,
searched and sortable by display name, as the Users projection is. Who its members are is the
Group member read's, not the projection's.

**Group member read**:
A page of one Group's direct members (`GET /api/admin/groups/{id}/members`),
each as the Users projection identifies it — never a credential — read on
expanding the Group's row. Requires both `group:read` and `user:read`.

**Permissions listing**:
The read of every Delegatable Permission (`GET /api/admin/permissions`), for a
holder of `group:read`: what any Group could carry. API only.
_Avoid_: Roles listing

**SCIM audit trail**:
The append-only history of provisioning, connector, authentication,
administrative and scheduled-job events, holding ids and classifications but no
profile or credential value.
_Avoid_: log (the application log is a different stream)

**Audit listing**:
The read of the audit trail, by a holder of `audit:read`.

**Permission change**:
An audit event of a User gaining or losing direct membership of a Group that
confers Permissions, or of the Admin group: `PERMISSION_GRANT` or
`PERMISSION_REVOKE`, recording the User's effective Permissions before and after
and whether the change granted or removed the Superuser. A Group that confers
nothing records none.
_Avoid_: role change

**Group Permissions change**:
An audit event of the Superuser setting a Group's Permissions:
`GROUP_PERMISSIONS_CHANGE`, naming the Superuser and the Group and recording the
Group's Permissions before and after; a refused one is a failure with a generic
reason and no Permission. Not a Permission change: no membership moved.
_Avoid_: role edit, permission assignment

**Audit page**:
The SPA screen at `/audit` showing the Audit listing, for a holder of
`audit:read`.
