---
type: Configuration Reference
title: Patient source
description: APP_PATIENT_SOURCE_BASE_URL and APP_PATIENT_SOURCE_READ_TIMEOUT — the in-house patient source the backend reads every Patient, FFI History and NUMC History from and passes every Patient search to, its required base URL, its timeouts, and the local WireMock mock compose runs on 127.0.0.1:8104, with its fixtures, its search set, its FFI Histories and its NUMC Histories.
tags: [patient, configuration]
---

# Patient source

| Variable                          | Default | Meaning                                                          |
| --------------------------------- | ------- | ---------------------------------------------------------------- |
| `APP_PATIENT_SOURCE_BASE_URL`     | none    | The in-house patient source's FHIR base, an absolute http(s) URL |
| `APP_PATIENT_SOURCE_READ_TIMEOUT` | `5s`    | How long the source may take to answer once connected            |

The backend reads every Patient from the in-house patient source at
`GET {base}/Patient/{id}`, passes every Patient search to it at
`GET {base}/Patient?…`, as FHIR R4, and reads every patient's FFI History at
`GET {base}/Patient/{id}/ffi?…` and NUMC History at
`GET {base}/Patient/{id}/numc?…`, on every read, with no cache and **no
credential** (ADR 0014, ADR 0018, ADR 0019; `/docs/domain/patient.md`, "The
in-house patient source" and "Patient search"; `/docs/domain/ffi.md`;
`/docs/domain/numc.md`). The SPA never reaches it.

- **Required, with no default.** `application.yaml` names
  `app.patient-source.base-url` as `${APP_PATIENT_SOURCE_BASE_URL}` with no
  fallback, so a start without it fails, and so does one with anything but an
  absolute `http` or `https` URL. A trailing slash is ignored, and a path is
  kept: `https://patients.internal/fhir/r4/` reads
  `https://patients.internal/fhir/r4/Patient/{id}`. The test JVMs drop it
  (`pom.xml`); the tests point every context at their own mock.
- **Timeouts:** 2 seconds to connect, fixed, and 5 to answer by default
  (ADR 0014). `APP_PATIENT_SOURCE_READ_TIMEOUT` sets
  the second in either of Boot's duration styles (`1500ms`, `PT1.5S`); blank is
  the default, and anything but a positive duration fails startup. A source
  slower than that is the Patient card's failed state, a `502`. No redirect is
  followed and no call is retried. The tests set it to 1 second
  (`ContainerTestConfiguration`), so a case reading a slow fault stub does not
  wait out the five.
- **The deployed stack supplies no source.** The stack (`/infra/`) does not set
  `APP_PATIENT_SOURCE_BASE_URL`; the mock below is for local runs and E2E only.
  A deployment must supply the variable for the service to start.
- **The adapter sends no credential.** This integration is compatible only with
  a source whose access control does not require request credentials. Supporting
  a credential requires an adapter and external configuration change; credentials
  have no fallback in this repository.

## Local mock

The behaviour any source or mock must have — how a search matches, filters,
orders, pages and counts, and which answers the backend holds to be failures — is
`../data-source.openapi.yaml`. The WireMock mock below answers a canned
subset of it.

`compose.yaml`'s `patient-source` service is WireMock (`wiremock/wiremock`,
pinned by tag and digest, the same release as the backend's test dependency)
serving `patient-source-mock/mappings` on **`127.0.0.1:8104`**: FHIR R4
`Patient/{id}` for the twelve development fixtures, a `404` for any other
well-formed id, and the search set below. It has no profile, so `make infra-up` and
`make integration-test` start it beside SQL Server and Redis, healthy when
WireMock's `/__admin/health` answers. `.env.example` points the backend at it:

```bash
APP_PATIENT_SOURCE_BASE_URL=http://127.0.0.1:8104
```

```bash
curl -s http://127.0.0.1:8104/Patient/eq081-VQEgP8drUUqCWzHfw3   # Tan Wei Ming
```

### The search set

Each canned search answers a searchset Bundle of the fixtures themselves, in
the mock's one stable order — newest Last visit first, ties by id, the Patient
with none last — matched on the query the backend sends — the search,
`_count=5`, `_offset`, `_total=accurate`, and the `gender` filter pinned to its
exact value or, where the search uses none, to absent — so it answers the
SPA's five-a-page reads. Every `tan` search is `name:contains=tan`, any case,
and `_offset=0` unless named.

| Stub file                | Search                                                            | Answers                                                       |
| ------------------------ | ----------------------------------------------------------------- | ------------------------------------------------------------- |
| `search-name-tan-page-1` | `tan`                                                             | 5 of `total: 7`                                               |
| `search-name-tan-page-2` | `tan`, `_offset=5`                                                | the last 2 of `total: 7`, the Patient with no Last visit last |
| `search-name-tan-male`   | `tan`, `gender=male`                                              | `total: 3`                                                    |
| `search-name-tan-female` | `tan`, `gender=female`                                            | `total: 4`                                                    |
| `search-nric`            | `identifier=https://fhir.synapxe.sg/NamingSystem/nric\|S9212345A` | Siti Nurhaliza binte Ahmad, `total: 1`                        |
| `search-no-match`        | `name:contains=nobody`                                            | no entry, `total: 0`                                          |
| `search-fallback`        | any other `/Patient` search, at a lower priority                  | no entry, `total: 0`                                          |

A gender filter on any other search or page falls to `search-fallback`.

Each Patient carries its Last visit as the `last-visit` extension, a
`valueDate`, as the source does (ADR 0018).

```bash
curl -s 'http://127.0.0.1:8104/Patient?name:contains=tan&_count=5&_offset=0&_total=accurate'
```

### The FFI Histories

The backend reads a patient's FFI History at
`GET {base}/Patient/{id}/ffi?_offset=…&_count=…&_total=accurate`, on the same
base URL, client and timeouts, with each filter and the sort the FFI page asks
for, and no `_sort` when it asks for none (ADR 0019; `/docs/domain/ffi.md`,
"The source contract"). The mock answers a FHIR-shaped searchset of `FFI`
objects, matched on `_count=5`, `_offset`, `_total=accurate`, the `_sort` and
every filter — `type`, `outcome`, `issue-date`, `expiry-date`,
`higher-authority`, `submitting-mc`, `status` — each pinned to its value or to
absent:

| Stub file                                    | Read                                                           | Answers                                         |
| -------------------------------------------- | -------------------------------------------------------------- | ----------------------------------------------- |
| `ffi-tan-wei-ming`                           | `eq081-VQEgP8drUUqCWzHfw3`                                     | Tan Wei Ming's 4 FFIs, `total: 4`               |
| `ffi-tan-wei-ming-by-issue-date`             | Tan Wei Ming, `_sort=issue-date,_id`                           | his 4, earliest Issue Date first                |
| `ffi-tan-wei-ming-by-issue-date-descending`  | Tan Wei Ming, `_sort=-issue-date,_id`                          | his 4, latest Issue Date first                  |
| `ffi-tan-wei-ming-by-expiry-date`            | Tan Wei Ming, `_sort=expiry-date,_id`                          | his 4, earliest Expiry Date first               |
| `ffi-tan-wei-ming-by-expiry-date-descending` | Tan Wei Ming, `_sort=-expiry-date,_id`                         | his 4, latest Expiry Date first                 |
| `ffi-tan-hui-min-page-1`                     | `dev-patient-05`                                               | 5 of `total: 7`                                 |
| `ffi-tan-hui-min-page-2`                     | `dev-patient-05`, `_offset=5`                                  | the last 2 of `total: 7`                        |
| `ffi-tan-hui-min-ippt-or-dental`             | Tan Hui Min, `type=ippt,dental`                                | `total: 3`                                      |
| `ffi-tan-hui-min-fit`                        | Tan Hui Min, `outcome=fit`                                     | `total: 3`                                      |
| `ffi-tan-hui-min-issued-2024`                | Tan Hui Min, `issue-date=ge2024-01-01&issue-date=le2024-12-31` | `total: 3`                                      |
| `ffi-tan-hui-min-issued-from-2023-page-1`    | Tan Hui Min, `issue-date=ge2023-01-01`                         | 5 of `total: 6`                                 |
| `ffi-tan-hui-min-issued-from-2023-page-2`    | the same, `_offset=5`                                          | the last 1 of `total: 6`                        |
| `ffi-tan-hui-min-expiring-to-2024`           | Tan Hui Min, `expiry-date=le2024-12-31`                        | `total: 3`                                      |
| `ffi-tan-hui-min-expiring-to-2022`           | Tan Hui Min, `expiry-date=le2022-12-31`                        | no entry, `total: 0`: a filter matching nothing |
| `ffi-tan-hui-min-camo-or-cnmo`               | Tan Hui Min, `higher-authority=camo,cnmo`                      | `total: 4`                                      |
| `ffi-tan-hui-min-kranji-camp`                | Tan Hui Min, `submitting-mc=kranji-camp`                       | `total: 2`                                      |
| `ffi-tan-hui-min-active`                     | Tan Hui Min, `status=active`                                   | `total: 3`                                      |
| `ffi-fallback`                               | any other read of a development fixture's, lower priority      | no entry, `total: 0`                            |
| `ffi-not-found`                              | any other well-formed id, lowest priority                      | `404`, as a read by id of an unknown id         |

Beside them, `fault-ffi-<failure>` answers each failure of an FFI read under a
patient id of its own — `unreachable` (the connection reset), `slow` (after 7
seconds), `server-error` (`503`), `unexpected-status` (`400`),
`not-a-searchset`, `no-total`, `no-id`, `invalid-id`, `no-outcome`,
`unknown-higher-authority` and `expiry-date-not-to-the-day`.

```bash
curl -s 'http://127.0.0.1:8104/Patient/eq081-VQEgP8drUUqCWzHfw3/ffi?_offset=0&_count=5&_total=accurate'
```

### The NUMC Histories

The backend reads a patient's NUMC History at
`GET {base}/Patient/{id}/numc?_offset=…&_count=…&_total=accurate`, on the same
base URL, client and timeouts, with no sort and no filter (ADR 0019, D5;
`/docs/domain/numc.md`, "The source contract"). The mock answers a FHIR-shaped
searchset of `NUMC` objects, matched on `_count=5`, `_offset` and
`_total=accurate`:

| Stub file                 | Read                          | Answers                                 |
| ------------------------- | ----------------------------- | --------------------------------------- |
| `numc-tan-wei-ming`       | `eq081-VQEgP8drUUqCWzHfw3`    | Tan Wei Ming's 4 NUMC Records, `total: 4` |
| `numc-tan-hui-min-page-1` | `dev-patient-05`              | 5 of `total: 7`                         |
| `numc-tan-hui-min-page-2` | `dev-patient-05`, `_offset=5` | the last 2 of `total: 7`                |
| `numc-fallback`           | any other read of a development fixture's, lower priority | no entry, `total: 0` |
| `numc-not-found`          | any other well-formed id, lowest priority | `404`, as a read by id of an unknown id |

Beside them, `fault-numc-<failure>` answers each failure of a NUMC read under a
patient id of its own — `unreachable` (the connection reset), `slow` (after 7
seconds), `server-error` (`503`), `unexpected-status` (`400`),
`not-a-searchset`, `no-total`, `no-id`, `invalid-id`, `no-outcome`,
`unknown-type`, `unknown-outcome` and `expiry-date-not-to-the-day`.

```bash
curl -s 'http://127.0.0.1:8104/Patient/eq081-VQEgP8drUUqCWzHfw3/numc?_offset=0&_count=5&_total=accurate'
```

A change to a stub file changes the backend's patient tests' fixtures too: they
serve the same files with WireMock in process (`PatientSourceStubs`).
