# Project audit and validation — 11 October 2026

## Scope and protected work

Repository: `Mohit2656/internship_m`, branch `main`, initial HEAD `550207e`.
The existing intentional uncommitted role-login, workplace, monthly-photo,
mentor-deletion and attendance-display work is included in this review.
No existing application records were removed, no applied migration was edited,
and no reset, clean of the Git working tree, force push or deployment was used.

The complete code-backed feature, endpoint, entity, migration, route, service
and test inventory is in [PROJECT_AUDIT_INVENTORY.md](PROJECT_AUDIT_INVENTORY.md).
Project/seminar evaluation tables in V20 do not constitute an implemented
feature: no corresponding controller/service/frontend workflow was found.

## Confirmed defects and fixes

| Defect | Evidence | Fix |
|---|---|---|
| Attendance rejection reason was hidden; mentor ABSENT/UNVERIFIED could display Present | Existing local records and rendered frontend regressions | Show stored GPS accuracy/reason and separate GPS distance/accuracy checks from overall attendance verification; check attendance result first |
| Saving an unchanged radius revoked both approvals | New regression returned PENDING instead of APPROVED for unchanged 200.00 m | Compare numeric radius first; preserve approvals/revision on a no-op. A real change still clears both decisions; historical snapshots stay unchanged |
| Browser PATCH preflight rejected a trusted frontend | New integration test: expected 200, received 403 | Include PATCH in the existing origin-restricted CORS method list; authentication and untrusted-origin rejection remain enforced |
| Malformed record IDs and browser GPS values produced server errors | New integration tests: expected 400, received 500 | Map parameter conversion failures to a generic 400 INVALID_REQUEST, without echoing submitted values |
| Configured public health route did not exist | New integration test: expected 200, received 404 | Add Spring Boot Actuator, expose only health, and hide components/details. Health reports 503 on an unhealthy database |
| First opportunity publish timestamp differed from later reads | Baseline lifecycle test failed: nanoseconds in first response, microseconds after persistence | Generate opportunity lifecycle timestamps at database microsecond precision; existing publish/close integration regression now passes |
| Original monthly PostgreSQL tests had no isolated-schema guard | Code inspection against the newer guarded PostgreSQL tests | Require a generated local test schema and matching JDBC search path before running destructive fixture cleanup |

The local attendance diagnosis was not a wrong enum or internship association.
Five existing records were PRESENT/UNVERIFIED with GPS_ACCURACY_TOO_LOW.
Reported accuracy was 108, 106, 212, 119 and 149 metres. Three estimated locations
were within four metres of the workplace, but every accuracy exceeded the
configured 100-metre maximum. A clickable Maps link proves coordinates exist;
it does not prove they satisfy verification rules.

The workplace is per internship, not global. The legacy CONFIRMED local
reference has no independent mentor/IIIC approvals and therefore cannot satisfy
the current dual-approval requirement for new submissions. Existing historical
attendance/report outcomes are not reclassified by current approval state.

Daily attendance still validates browser coordinates, resolves the authenticated
student's internship, requires its current dual-approved reference, checks
distance plus accuracy against the radius, and preserves camera/AI rules.
The existing physical-camera workflow persists validated image metadata and
verification-session evidence, rather than retaining the original attendance
image bytes. Monthly report photographs are stored separately; this distinction
was preserved rather than claiming attendance photo-file retention exists.
The default radius remains 200 m; maximum accuracy remains 100 m. Missing or
invalid numeric coordinates remain PRESENT/UNVERIFIED when otherwise eligible;
malformed nonnumeric request parameters return 400. Coordinates are never
fabricated. Deterministic tests cover identical coordinates, known inside/outside
distances, accuracy boundaries, uncertainty margins, missing GPS and approval.

Monthly reports independently extract original JPEG/PNG EXIF on the server and
store their own workplace snapshot and comparison. Browser GPS is not substituted
for photo GPS. Photo extraction/status rules were preserved during this audit.
Earlier reviewed upload work supports JPG/JPEG/PNG at 20 MiB, with bounded
multipart limits of 20 MiB per file and 21 MiB per request. Signature, decoding,
dimensions, truncation, PNG CRC, EXIF and hash checks remain in place.

Mentor deletion remains transactional soft deletion: active views exclude the
record, an actor/time/reason audit is retained, and evidence, consumed sessions
and referenced photographs remain available for retention/recovery. Student,
IIIC and unassigned-mentor deletion requests remain denied. Retained files are
not orphans and must not be purged as part of this audit.

## Commands and actual results

Results are finalized after the last validation run. Logs are intentionally
ignored by Git because test diagnostics can contain fixture tokens and data.
Numbers below describe JUnit/Node test cases, not numbers of report files.
Inherited integration scenarios run again under PostgreSQL; do not add those
executions and call the result a count of unique feature scenarios.

| Check | Command | Exit / result |
|---|---|---|
| Backend baseline | `mvn.cmd -q -DreuseForks=false test` | 1: 234 passed, 1 failed, 61 PostgreSQL cases skipped; timestamp defect reproduced |
| Defect reproductions | `mvn.cmd -q -Dtest=ApiBoundaryIntegrationTest,DualWorkplaceApprovalIntegrationTest#savingUnchangedRadiusPreservesApprovalsButARealChangeRevokesThem -DreuseForks=false test` | 1: five expected regression failures before fixes |
| Final full backend suite | `mvn.cmd -q -DreuseForks=false test` | 0: 250 executed cases passed; 62 opt-in PostgreSQL cases skipped in this run |
| Backend production packaging | `mvn.cmd -q -DskipTests package` | 0 |
| Packaged backend startup | Production-profile jar on isolated local PostgreSQL schema / port 18081 | Startup completed; public /actuator/health returned HTTP 200 |
| All opt-in PostgreSQL suites | `python backend/scripts/run-postgres-tests.py` | 0: 62 passed, 0 failed/errors/skipped; disposable schema cleanup passed |
| PostgreSQL runner safeguards | `python -m unittest discover -s backend/scripts -p 'test_*.py'` | 0: 4 passed |
| Frontend tests | `node --test tests/*.test.cjs` from frontend | 0: 44 passed, 0 failed, 0 skipped |
| Frontend TypeScript | `npm.cmd run typecheck` from frontend | 0 |
| Frontend production build | `npm.cmd run build` from frontend | 0 |
| Frontend lint | No lint script/configuration or installed ESLint | Not run: unavailable; type checking is not lint |
| AI service tests | `npm.cmd test` from ai-service | 0: 20 passed, 6 opt-in model cases skipped |
| Actual cached AI models | Set AI_REAL_MODEL_TEST=true, then `node --import tsx --test test/real-model.test.ts` | 0: all 6 model tests passed locally, with no remote model download |
| AI service TypeScript | `npm.cmd run typecheck` from ai-service | 0 |
| Whitespace review | `git diff --check` and staged equivalent | 0 |
| Docker image build/runtime | Docker executable unavailable | Not run |

An intermediate targeted run passed the API, radius, opportunity lifecycle and
attendance policy regressions, but a new static-mock experiment failed because
this repository intentionally uses Mockito's subclass mock maker. That experiment
was removed; the existing API lifecycle regression continues to cover the
timestamp bug. One full-run attempt aborted before tests when Maven clean tried
to delete its open log on Windows; the runner was corrected without changing
application code or removing existing work.

## PostgreSQL and migrations

The supplied runner rejects non-local database hosts and base URLs with a schema
or search-path override, creates a new `ims_deletion_test_<uuid>` schema, supplies
the opt-in test environment privately, and removes only the schema it created.
It runs monthly metadata, physical attendance, shared workplaces, dual approval,
mentor deletion and V26-to-V27 preservation suites. Migration preservation tests
use an additional independently generated test schema.

The actual V1-V27 PostgreSQL chain is exercised from an empty schema. The upgrade
fixture tests legacy CONFIRMED references, previously VERIFIED attendance,
retained photo/workplace snapshots, and the new dual-approval database constraint.
The 62 PostgreSQL executions comprise physical attendance (6), monthly metadata
(8), shared workplace (13), dual approval (20), mentor deletion (14), and migration
preservation (1). All passed with zero skipped cases.
V24 adds per-internship reference/evidence/photo snapshots; V25 adds deletion and
audit retention; V26 adds workplace name/correction audit; V27 adds independent
reviewers/decisions, revision locking and approval audit. These already-applied
local migrations remain unchanged. No production schema or migration checksums
were accessed or repaired. Local success cannot prove production compatibility.

## Secret and security review

758 reachable Git blobs were checked for private keys and common cloud/GitHub
token patterns. Historical configuration scalars were also inspected for literal
passwords/secrets outside test fixtures. No unexpected secret was found. This is
a pattern/configuration review, not a guarantee that every possible secret format
can be recognized. Test-only fixture keys are not deployment credentials.

Local properties, environment files, logs, .next, target, node_modules, stored
documents and cached AI models are excluded. The root ignore rules now protect
environment files across project directories while permitting blank examples.
Generated next-env changes caused by the build were restored to their original
content. The final manifest/staged diff is reviewed before committing.

JWT authentication reloads active accounts and token versions. Password changes
and admin resets revoke old versions; logout clears the browser cookie rather
than maintaining a token blacklist. Frontend role selection is checked against
the stored role before a login cookie is issued. Expired/malformed/bad-signature,
inactive and revoked token cases and forged role-claim authority are tested.
Mentor visibility/deletion/review require assignment checks in backend services.

GPS and browser Edge AI results are client evidence, not proof of identity or
tamper-resistant physical presence. Bound, expiring, single-use server challenges
and server geofence/EXIF checks are preserved; a malicious client can still alter
GPS or assert browser analysis results. The private AI service has no application
authentication middleware and must be network-restricted if hosted. A full
penetration/load test and production network verification were not performed.

## Exact production checks requiring platform access

No Render access is required for the local audit. No deployment was performed.
The repository has no linked Vercel project metadata or Render/Vercel deployment
manifest from which current hosted commit/environment/logs can be established.

1. Confirm both platforms use the intended resulting Git commit and corresponding
   V1-V27 migration resources. Inspect Render startup/Flyway logs; do not run
   repair to conceal a checksum mismatch. The reported earlier V20 mismatch is
   not reproduced by current local migration validation.
2. Set Render SPRING_PROFILES_ACTIVE=prod. Default local requires a private file
   omitted from the Docker image. Configure DB_URL, DB_USERNAME, DB_PASSWORD,
   stable JWT_SECRET and the existing PROFILE_ENCRYPTION_KEY through secrets.
   Never replace an encryption key merely to get startup passing.
3. Confirm Render PORT is honored by the existing Docker entrypoint. Set the
   health path to `/actuator/health` after this backend is deployed. Only health
   is exposed, with details hidden, using [Spring Boot's actuator configuration](https://docs.spring.io/spring-boot/3.4/reference/actuator/endpoints.html).
4. Confirm DOCUMENT_STORAGE_ROOT is on a private persistent writable volume.
   Test retrieval of existing photos/documents after a restart and deployment.
   Existing filesystem paths/production files were not available to this audit.
5. Set MULTIPART_MAX_FILE_SIZE=20MB and MULTIPART_MAX_REQUEST_SIZE=21MB (Spring
   DataSize uses binary megabytes), and reconcile any older document multipart
   overrides. Monthly photo limits must fit multipart/proxy limits; startup
   rejects smaller inconsistent limits. Check production memory/concurrency.
6. Set Vercel BACKEND_API_URL to the intended Render origin before rebuilding;
   localhost is only the local fallback. Verify authenticated upload/download
   and cookie propagation through the external rewrite, including a near-20-MiB
   upload. [Vercel external rewrites proxy requests](https://vercel.com/docs/routing/rewrites),
   whereas [Vercel Functions have a separate 4.5-MB payload limit](https://vercel.com/docs/functions/limitations).
   Do not assume a Next.js proxy setting overrides a Function/platform limit;
   the deployed routing path and limits need an actual hosted test.
7. Set JWT_COOKIE_SECURE=true and CORS_ALLOWED_ORIGINS to the actual frontend
   origins. Verify login, logout, role isolation, forced password changes and
   upload cookies over HTTPS. Keep AI endpoints private and configure
   RESUME_AI_BASE_URL / OPPORTUNITY_AI_BASE_URL and model paths/resources.
8. Configure the existing NEXT_PUBLIC_GOOGLE_MAPS_API_KEY and
   NEXT_PUBLIC_GOOGLE_MAP_ID before the frontend build. Enable Maps JavaScript,
   Places (New), and Geocoding; restrict the browser key by referrer and APIs.
   No key was fabricated and no Leaflet/OpenStreetMap migration was made.

## Features not fully tested

Automated tests cover local service/API behavior, component rendering/actions,
authorization, validation, migration and transaction/retention scenarios. They
do not prove every browser/page interaction, actual camera/GPS permission and
hardware behavior, live Google provider behavior, hosted production uploads,
existing production file recovery, production migration checksums, Docker
runtime, email/human delivery of temporary credentials, or production performance.
Forgot password is the implemented admin-assisted contact workflow; no automated
email reset feature was inferred from documentation.

## Commit review

The exact intended file manifest is
[PROJECT_AUDIT_COMMIT_FILES.txt](PROJECT_AUDIT_COMMIT_FILES.txt). All existing intentional changes
are included explicitly; credentials, test logs, local fixtures/data, model caches
and build output are excluded. Commit/push status is reported after execution;
this document intentionally does not claim a future push has succeeded.
