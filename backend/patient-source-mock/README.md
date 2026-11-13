# The patient source's mock

WireMock stub files standing in for the in-house patient source, for local runs
and E2E only. `backend/compose.yaml`'s `patient-source` service mounts
`mappings/` and serves it on `127.0.0.1:8104`; the backend's patient tests serve
the same files in process (`PatientSourceStubs`), so a change to a stub is a
change to the tests' fixtures. Nothing under `/infra/` builds or deploys it.

| Stub files                               | Answers                                                                                                                                                                                                                                                                                                                                                                      |
| ---------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `patient-*.json`                         | `GET /Patient/{id}`: the twelve development fixtures, FHIR R4, and `404` for any other well-formed id (`patient-not-found`)                                                                                                                                                                                                                                                  |
| `search-*.json`                          | `GET /Patient?…`: the canned Patient searches, and an empty searchset for any other (`search-fallback`)                                                                                                                                                                                                                                                                      |
| `ffi-*.json`                             | `GET /Patient/{id}/ffi?…`: Tan Wei Ming's four FFIs, Tan Hui Min's seven over two pages, none for any other fixture's read or any other filter or sort (`ffi-fallback`), and `404` for any other well-formed id (`ffi-not-found`)                                                                                                                                            |
| `ffi-tan-wei-ming-by-*.json`             | each sort of Tan Wei Ming's four FFIs: `-by-issue-date`, `-by-issue-date-descending`, `-by-expiry-date`, `-by-expiry-date-descending`                                                                                                                                                                                                                                        |
| `ffi-tan-hui-min-*.json` (but `-page-*`) | each filter of Tan Hui Min's seven FFIs: `-ippt-or-dental` (FFI Type), `-fit` (Outcome), `-issued-2024` and `-issued-from-2023-page-1`, `-page-2` (Issue Date, the second over two pages), `-expiring-to-2024` and `-expiring-to-2022` (Expiry Date, the second matching nothing), `-camo-or-cnmo` (Higher Authority), `-kranji-camp` (Submitting MC) and `-active` (Status) |
| `fault-*.json` (but `fault-ffi-*`)       | one failure of a read by id each, under an id of its own                                                                                                                                                                                                                                                                                                                     |
| `fault-ffi-*.json`                       | one failure of an FFI History read each — unreachable, slow, `503`, `400`, and each kind of page the service cannot hold — under a patient id of its own                                                                                                                                                                                                                     |

What each stub serves, and the rules the fixtures hold, are
`/docs/domain/patient.md` ("The patient source's fixtures") and
`/docs/domain/ffi.md` ("The patient source's FFI fixtures"); the setting, the
port and each stub file by name are `backend/docs/configuration/patient-source.md`;
the behaviour any source or mock must have is `backend/docs/data-source.openapi.yaml`.
