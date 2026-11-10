---
type: Configuration Reference
title: Patient source
description: APP_PATIENT_SOURCE_BASE_URL and APP_PATIENT_SOURCE_READ_TIMEOUT — the in-house patient source the backend reads every Patient from and passes every Patient search to, its required base URL, its timeouts, and the local WireMock mock compose runs on 127.0.0.1:8104, with its fixtures and its search set.
tags: [patient, configuration]
---

# Patient source

| Variable                          | Default | Meaning                                                          |
| --------------------------------- | ------- | ---------------------------------------------------------------- |
| `APP_PATIENT_SOURCE_BASE_URL`     | none    | The in-house patient source's FHIR base, an absolute http(s) URL |
| `APP_PATIENT_SOURCE_READ_TIMEOUT` | `5s`    | How long the source may take to answer once connected            |

The backend reads every Patient from the in-house patient source at
`GET {base}/Patient/{id}`, and passes every Patient search to it at
`GET {base}/Patient?…`, as FHIR R4, on every read, with no cache and **no
credential** (ADR 0014, ADR 0018; `/docs/domain/patient.md`, "The in-house
patient source" and "Patient search"). The SPA never reaches it.

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

Each canned search answers a searchset Bundle of the fixtures themselves,
matched on the whole query the backend sends — the search, its `_sort`,
`_count=5`, `_offset`, `_total=accurate`, and each filter (`gender`,
`birthdate`, `last-visit`) pinned to its exact values or, where the search uses
none, to absent — so it answers the SPA's five-a-page reads. Unless the table
names a `_sort`, it is `-last-visit,_id`, newest Last visit first; every `tan`
search is `name:contains=tan`, any case, and `_offset=0` unless named.

| Stub file                                    | Search                                                            | Answers                                                       |
| -------------------------------------------- | ----------------------------------------------------------------- | ------------------------------------------------------------- |
| `search-name-tan-page-1`                     | `tan`                                                             | 5 of `total: 7`, newest Last visit first                      |
| `search-name-tan-page-2`                     | `tan`, `_offset=5`                                                | the last 2 of `total: 7`, the Patient with no Last visit last |
| `search-name-tan-by-birth-date`              | `tan`, `_sort=birthdate,_id`                                      | 5 of `total: 7`, oldest first                                 |
| `search-name-tan-by-birth-date-descending`   | `tan`, `_sort=-birthdate,_id`                                     | 5 of `total: 7`, youngest first                               |
| `search-name-tan-by-last-visit-page-1`       | `tan`, `_sort=last-visit,_id`                                     | 5 of `total: 7`, oldest Last visit first                      |
| `search-name-tan-by-last-visit-page-2`       | `tan`, `_sort=last-visit,_id`, `_offset=5`                        | the last 2 of `total: 7`, the Patient with no Last visit last |
| `search-name-tan-male`                       | `tan`, `gender=male`                                              | `total: 3`                                                    |
| `search-name-tan-female`                     | `tan`, `gender=female`                                            | `total: 4`                                                    |
| `search-name-tan-born-from-1960-page-1`      | `tan`, `birthdate=ge1960-01-01`                                   | 5 of `total: 6`                                               |
| `search-name-tan-born-from-1960-page-2`      | `tan`, `birthdate=ge1960-01-01`, `_offset=5`                      | the last 1 of `total: 6`                                      |
| `search-name-tan-born-to-1979`               | `tan`, `birthdate=le1979-12-31`                                   | `total: 3`                                                    |
| `search-name-tan-born-1980-to-1999`          | `tan`, `birthdate=ge1980-01-01&birthdate=le1999-12-31`            | `total: 3`                                                    |
| `search-name-tan-visited-from-2026-10`       | `tan`, `last-visit=ge2026-10-01`                                  | `total: 3`                                                    |
| `search-name-tan-visited-to-2026-06`         | `tan`, `last-visit=le2026-06-30`                                  | `total: 1`                                                    |
| `search-name-tan-visited-2026-08-to-2026-10` | `tan`, `last-visit=ge2026-08-01&last-visit=le2026-10-20`          | `total: 3`                                                    |
| `search-nric`                                | `identifier=https://fhir.synapxe.sg/NamingSystem/nric\|S9212345A` | Siti Nurhaliza binte Ahmad, `total: 1`                        |
| `search-no-match`                            | `name:contains=nobody`                                            | no entry, `total: 0`                                          |
| `search-fallback`                            | any other `/Patient` search, at a lower priority                  | no entry, `total: 0`                                          |

A sort and a filter together, a filter on any other search, and any page of a
sorted search but the ones named fall to `search-fallback`.

Each Patient carries its Last visit as the `last-visit` extension, a
`valueDate`, as the source does (ADR 0018).

```bash
curl -s 'http://127.0.0.1:8104/Patient?name:contains=tan&_sort=-last-visit,_id&_count=5&_offset=0&_total=accurate'
```

A change to a stub file changes the backend's patient tests' fixtures too: they
serve the same files with WireMock in process (`PatientSourceStubs`).
