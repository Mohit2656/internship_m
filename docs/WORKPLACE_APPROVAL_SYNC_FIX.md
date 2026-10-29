# Workplace approval status synchronization

## Root cause

Approval persistence and the existing two-stage rules are correct. `InternshipLocationManager` loaded its record only on mount, a student/scope change, or the manual **Refresh status** button. Approval updates change `internship_locations.updated_at`, not `internship_details.updated_at`, so the student details page's existing component key did not cause a reload. A student view left open during review could keep its earlier pending snapshot.

The student dashboard did not request or render current workplace approval at all. Its attendance card could show a saved `WORKPLACE_NOT_APPROVED` outcome from an earlier submission, which is historical attendance evidence and must not be changed by a later workplace approval. The literal text "Workplace Approval Pending" is absent from the current source; the stale workplace view and historical attendance message were the relevant display paths.

## Verified workflow and database

The reviewer panel posts `{revision, decision: "APPROVE" | "REJECT", remarks}` to `/api/v1/mentor/students/{studentId}/internship/location/review` or the corresponding `/admin/` endpoint. The path identifies the student account, and the backend resolves that student's internship and its unique workplace. Reviewer identity comes from the authenticated principal, then the current active user and mentor assignment are checked. Students cannot approve workplaces. A rejection requires a reason; stale workplace revisions are rejected.

`InternshipLocationService.review` runs in a write transaction. `InternshipLocation.review` updates the appropriate independent decision, reviewer and timestamp, and derives `PENDING`, `REJECTED`, or `APPROVED`. `saveAndFlush` writes the entity, and the same transaction inserts `workplace_review_audit`. Student reads use `GET /api/v1/student/internship/location`; reviewer reads use the same student-scoped location routes. `InternshipLocationResponse` also checks the approving mentor's current assignment and both reviewers' eligibility before exposing final approval.

The existing PostgreSQL fields are `status`, `revision`, `lock_version`, `mentor_decision`, `mentor_approved_by`, `mentor_reviewed_at`, `mentor_remarks`, `iiic_decision`, `iiic_remarks`, `confirmed_by`, `confirmed_at`, and `updated_at`. Coordinates and branch metadata remain in the same record. The unique `internship_id` constraint prevents competing old/new workplace rows; the existing dual-approval constraint remains in force.

A read-only audit of the configured **local** PostgreSQL database found one workplace: stored `APPROVED`, both decisions `APPROVE`, active reviewers with correct roles, and the approving mentor still assigned. The effective approval check was also true. Coordinates/address and substantive review audit details were present and consistent; no duplicate workplace, internship, or onboarding rows existed. All 34 migrations had succeeded. This confirms the inspected pending-display problem was not an uncommitted approval or a stale duplicate database row. No application records were modified during inspection.

## Fix

- Reuse the authoritative student workplace panel on the student dashboard once internship details exist.
- Refetch the saved workplace on window focus, when the page becomes visible, and every 30 seconds while visible and idle. Manual refresh remains available. Failed background refreshes retain the last loaded state and show a retry message.
- Pause background refresh during saves/reviews, map corrections, changed radius values, or unsent review remarks. Do not overwrite unsaved reviewer input.
- Guard reads and mutations with request versions and cancellation so an older pending GET or a previous student's late response cannot overwrite a newer review response or another student's workplace.
- Describe `WORKPLACE_NOT_APPROVED` attendance evidence in the past tense: it records the state when attendance was submitted. Later approval does not rewrite historical GPS evidence.

Final approval still requires **both** the current assigned mentor and III Cell for the current workplace revision. One approval correctly remains pending; rejection, reapproval after relevant changes, reviewer identities, timestamps, and audits remain governed by the backend. Google Maps links, OSM/Leaflet search and selection, and attendance GPS/geofence rules are unchanged.

## Files changed

- `frontend/app/student/dashboard/page.tsx`
- `frontend/components/InternshipLocationManager.tsx`
- `frontend/components/LocationVerificationResult.tsx`
- `frontend/tests/workplace-panel.test.cjs`
- `frontend/tests/workplace-paused.test.cjs`
- `frontend/tests/workplace-map-interaction.test.cjs`
- `frontend/tests/attendance-status.test.cjs`
- `backend/src/test/java/edu/institution/ims/monthlyprogress/DualWorkplaceApprovalIntegrationTest.java`
- `WORKPLACE_APPROVAL_SYNC_FIX.md`

## Validation

Frontend `node --require ./tests/register-tsx.cjs --test tests/*.test.cjs`: **185 passed**, zero failures or skips. Targeted workplace/picker tests: **30 passed**. `npm.cmd run typecheck`: passed. `npm.cmd run build`: passed using an isolated source copy in `.local-run/workplace-approval-frontend-build`, preserving the running development server. Student dashboard HTTP and backend health checks both returned 200. There is no configured frontend lint script or lint configuration. An initial raw Node run lacked the required alias preload; the documented command above passed after an older expected-message assertion was updated for the historical wording.

Coverage includes both approval stages, student focus/visibility/timer refresh, rejection, reopening an approved view, failed requests, stale read/mutation responses, student-scoped dashboard rendering, timer/listener cleanup, and preserving unsaved corrections/radius/remarks. Backend integration regressions check committed database state and fresh HTTP reads for both approval orders and different students; failed workplace updates and failed audit inserts must roll back rather than return a false success. Failure injection is restricted to in-memory test databases or a disposable local PostgreSQL schema, and temporary constraints are removed in `finally`.

Backend H2/unit command, run in the isolated source copy `.local-run/approval-backend-989623a4a13f42a5bf7deaff6dd537d9`:

```powershell
mvn.cmd -o -q '-Dmaven.repo.local=C:/Users/Mohit Telang/.m2/repository' '-Dtest=DualWorkplaceApprovalIntegrationTest,SharedWorkplaceIntegrationTest,AttendanceLocationVerificationServiceTest,PhysicalVerificationPolicyTest,PhysicalAttendanceIntegrationTest' test
```

Result: **67 passed**, zero failures, errors, or skips: dual approval 25 (including the four new regressions), shared workplace 14, physical attendance 15, GPS verification 7, and physical policy 6. Log: `approval-h2-tests.log` in that isolated copy. Initial attempts failed before tests due to dependency-cache/network access; the successful offline run used the existing cache with elevated access.

From the same isolated copy, `python scripts/run-postgres-tests.py` passed **41 PostgreSQL tests**, zero failures, errors, or skips: `DualWorkplaceApprovalPostgresIntegrationTest` 25, `PhysicalAttendancePostgresIntegrationTest` 15, and `WorkplaceApprovalMigrationPostgresTest` 1. The copied runner retains the original localhost/disposable-schema safety checks, reads the existing ignored secrets file, and selects only these suites with offline Maven. All four new approval/rollback tests explicitly passed in the PostgreSQL XML report. Runner exit code was 0 and disposable-schema cleanup passed. Log: `audit-postgres.log` in the isolated copy. No application or production records were modified.

Backend packaging passed with `mvn.cmd -o -q '-Dmaven.repo.local=C:/Users/Mohit Telang/.m2/repository' -DskipTests package` in the isolated copy; tests had already run separately as listed above. Log: `approval-backend-package.log`. The running backend's JAR and production classes were unchanged. Final `git diff --check` passed.

## Migration and deployment

No database migration, status backfill, data repair, or backend production-code change is required. Serve the updated frontend to refresh current approval state; backend endpoints and schema remain compatible. The existing local development frontend picks up the source changes. No production database reset, historical record deletion, commit, push, or hosted deployment was performed for this fix.
