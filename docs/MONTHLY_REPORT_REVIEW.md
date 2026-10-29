# Monthly progress: faculty mentor review

Monthly progress reports now use explicit faculty mentor review. An uploaded report is not an approved report. Daily attendance and liveness behavior are unchanged by this change.

## Lifecycle

- **Draft:** the existing student form, before a successful upload. Drafts are not persisted.
- **PENDING_MENTOR_REVIEW:** a valid description and genuine JPG/JPEG/PNG photo have been uploaded for a canonical internship month. The student sees that the report is not yet approved.
- **APPROVED:** the currently assigned, authenticated faculty mentor explicitly approved this revision. The report is read-only.
- **REJECTED:** the assigned mentor supplied a reason (1–1000 nonblank characters). The student can correct the description, select a new photograph, and resubmit the same report through the dedicated endpoint. It returns to pending review with an incremented revision.

The existing internship eligibility, reporting period, future-period restriction and unique active internship/month rule remain in force. Rejection does not permit a duplicate report through the initial upload endpoint. Students and IIIC/admin users cannot approve or reject. Unassigned mentors cannot access or review the report. Existing mentor soft deletion remains available and retains evidence.

## API

The existing `/api/v1/student/monthly-progress/{monthNumber}` multipart endpoint creates a pending report. The overview, detail and mentor/admin list responses expose `status`, `revision`, `reviewerId`, `reviewerEmail`, `reviewedAt` and `rejectionReason`.

- `POST /api/v1/mentor/monthly-progress/{reportId}/approve`: JSON `{ "revision": 1 }`.
- `POST /api/v1/mentor/monthly-progress/{reportId}/reject`: JSON `{ "revision": 1, "reason": "Explain your contribution" }`.
- `POST /api/v1/student/monthly-progress/{reportId}/resubmit`: multipart `revision`, `workDescription`, `photo`; restricted to the owner of a rejected report.

Review and resubmission run in transactions, lock the report, and reject stale revisions. Reviews lock the active mentor assignment before the report, matching the existing deletion lock order. A competing review cannot silently overwrite a decision. Private photo/PDF endpoints retain owner, assigned-mentor and existing admin access checks.

## Photographs and PDFs

Monthly acceptance no longer extracts or evaluates EXIF/GPS or compares workplace coordinates. Valid photos with missing or invalid GPS metadata are accepted as pending review. Monthly DTOs, report tables, details and PDFs no longer expose GPS/workplace verification warnings. PDFs show the current review status, revision, decision time, reviewer and rejection reason where applicable.

The existing 20 MiB maximum, JPEG marker/signature checks, PNG signature/CRC checks, decoded-image validation, 40-million-pixel guard, SHA-256 calculation, original bytes and private storage remain intact. Corrupt, truncated, disguised and oversized files remain rejected. No upload limit, dependency, authentication configuration or attendance validation was changed.

## Migration and retained history

Additive migration **V29** adds review fields, revision/version fields, database state constraints and `monthly_progress_review_events`. Existing applied migrations are untouched. All historical reports, including soft-deleted reports, receive a pending status because there is no recorded mentor approval to infer. Their original columns and files are retained, and their original submission becomes a legacy audit event.

Every new submission/resubmission records a description, photograph reference and hash snapshot. Decisions record the reviewer, revision, timestamp and reason. Old photographs remain referenced by these immutable events. Transaction failure rolls back the report and event together; a newly uploaded file is removed on rollback. Legacy EXIF/workplace columns remain retained historical data and are not used for acceptance or returned as current verification evidence, including after resubmission.

V29 needs the normal Flyway startup migration when this code is eventually run against an existing database. No application database migration, production SQL, commit, push, deployment or running-server restart was performed during development. Backend and frontend must be updated together because the monthly response fields changed. No new environment variables or deployment configuration are required.

## Validation and changed files

Final Surefire reports contain **353 executed backend tests (263 non-PostgreSQL and 90 PostgreSQL), zero failures, errors or skips**. These results combine the broad run, corrected regression rerun and complete PostgreSQL run. Frontend tests use component rendering and mocked API/browser calls; a live browser, camera and hosted deployment were not exercised.

| Command/check | Actual result |
| --- | --- |
| `mvn.cmd -q -Dtest=MonthlyProgressIntegrationTest,ProgressPhotoValidatorTest -DreuseForks=false test` | Initial run: 14 tests, 13 passed, one assertion expected HTTP 500 instead of the existing HTTP 409 conflict response. Corrected the expectation; rollback assertions passed in the subsequent runs. |
| `mvn.cmd -q -DreuseForks=false test` | Broad run: 263 executed, 261 passed, two obsolete workplace-verdict assertions failed; 90 PostgreSQL opt-in cases skipped without database variables. Updated those tests to seed actual legacy snapshots rather than expect new uploads to perform GPS verification. |
| `mvn.cmd -q -Dtest=DualWorkplaceApprovalIntegrationTest -DreuseForks=false test` | Final regression rerun: 21 passed, zero failures/errors/skips, exit 0. Includes inherited monthly submission/review/resubmission, authorization, audit rollback/file cleanup, liveness independence and legacy retention cases. |
| `python backend/scripts/run-postgres-tests.py` | Complete PostgreSQL suite: 90 passed, zero failures/errors/skips, exit 0. Includes V29 preservation/backfill/constraint tests. Generated-schema cleanup passed. |
| `node --test tests/*.test.cjs` in `frontend` | 64 passed, zero failures/skips, exit 0. |
| `npx.cmd tsc --noEmit` in `frontend` | Passed, exit 0. |
| `npm.cmd run build` in `frontend` | Production build passed, exit 0; all 28 pages generated. |
| `mvn.cmd -q clean package -DskipTests` | Backend production package passed, exit 0, in an isolated source copy; `backend/target/ims-backend-monthly-review.jar` contains the matching V29 and review classes. The running application's JAR was not overwritten. |
| `git diff --check` | Passed. |

Coverage includes missing/invalid photo GPS without acceptance penalties, genuine JPG/JPEG/PNG, corrupt/truncated/disguised/oversized images, multipart limits, duplicate periods, explicit pending status, assigned-mentor-only decisions, required rejection reasons, stale revisions, approved immutability, reassignment authorization, original-photo preservation, transactional audit failure, rollback file cleanup and PDFs without GPS/EXIF warnings.

All 60 attendance production Java source files match their hashes at the start of this task. No attendance/liveness policy, application configuration, dependencies, Docker configuration or already-applied migration was changed. V29 was exercised only in disposable local schemas, not the application database. No hosted deployment was performed or verified.

The following **34 files** cover only this monthly-review task; earlier uncommitted attendance changes are preserved.

The attendance migration test is pinned to V28 so its preservation assertions remain scoped to that migration. Related workplace and deletion tests seed legacy photo snapshots or clear the new audit table; production attendance/workplace/deletion services are unchanged.

- LIVENESS_ONLY_ATTENDANCE.md
- MONTHLY_REPORT_REVIEW.md
- backend/src/main/java/edu/institution/ims/dashboard/report/DashboardReportRepository.java
- backend/src/main/java/edu/institution/ims/dashboard/report/InstitutionalReportService.java
- backend/src/main/java/edu/institution/ims/monthlyprogress/MentorMonthlyProgressController.java
- backend/src/main/java/edu/institution/ims/monthlyprogress/MonthlyProgressPdfService.java
- backend/src/main/java/edu/institution/ims/monthlyprogress/MonthlyProgressReport.java
- backend/src/main/java/edu/institution/ims/monthlyprogress/MonthlyProgressReportRepository.java
- backend/src/main/java/edu/institution/ims/monthlyprogress/MonthlyProgressReportResponse.java
- backend/src/main/java/edu/institution/ims/monthlyprogress/MonthlyProgressReviewEvent.java
- backend/src/main/java/edu/institution/ims/monthlyprogress/MonthlyProgressReviewEventRepository.java
- backend/src/main/java/edu/institution/ims/monthlyprogress/MonthlyProgressService.java
- backend/src/main/java/edu/institution/ims/monthlyprogress/MonthlyProgressStatus.java
- backend/src/main/java/edu/institution/ims/monthlyprogress/ProgressPhotoValidator.java
- backend/src/main/java/edu/institution/ims/monthlyprogress/StudentMonthlyProgressController.java
- backend/src/main/resources/db/migration/V29__monthly_progress_mentor_review.sql
- backend/src/test/java/edu/institution/ims/attendance/LivenessMigrationPostgresIntegrationTest.java
- backend/src/test/java/edu/institution/ims/dashboard/report/DashboardReportIntegrationTest.java
- backend/src/test/java/edu/institution/ims/monthlyprogress/DualWorkplaceApprovalIntegrationTest.java
- backend/src/test/java/edu/institution/ims/monthlyprogress/MentorRecordDeletionIntegrationTest.java
- backend/src/test/java/edu/institution/ims/monthlyprogress/MonthlyProgressIntegrationTest.java
- backend/src/test/java/edu/institution/ims/monthlyprogress/MonthlyProgressPostgresIntegrationTest.java
- backend/src/test/java/edu/institution/ims/monthlyprogress/MonthlyReviewMigrationPostgresIntegrationTest.java
- backend/src/test/java/edu/institution/ims/monthlyprogress/ProgressPhotoValidatorTest.java
- backend/src/test/java/edu/institution/ims/monthlyprogress/SharedWorkplaceIntegrationTest.java
- frontend/app/student/monthly-progress/page.tsx
- frontend/components/MonthlyProgressReportList.tsx
- frontend/components/ProgressPhotoLocation.tsx (removed; obsolete monthly GPS UI/test)
- frontend/components/ProgressPhotoVerification.tsx (removed; obsolete monthly GPS UI/test)
- frontend/components/ReportsCenter.tsx
- frontend/lib/monthly-progress.ts
- frontend/tests/liveness-reports.test.cjs
- frontend/tests/monthly-review.test.cjs
- frontend/tests/progress-photo-location.test.cjs (removed; obsolete monthly GPS UI/test)
