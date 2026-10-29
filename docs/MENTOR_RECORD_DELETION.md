# Faculty Mentor attendance and monthly report deletion

Delete actions are available on the existing mentor dashboard, attendance record list and monthly report cards. Confirmation identifies the student and attendance date or reporting month/period (including year). A reason of 5?500 characters is required. Requests are disabled while pending, lists refresh after success, and failures show safe feedback. No actions are offered for ADMIN or STUDENT.

Endpoints (authenticated MENTOR only):

- DELETE /api/v1/mentor/attendance/{id}
- DELETE /api/v1/mentor/monthly-progress/{id}

Both accept JSON {"reason":"Incorrect submission"} and return 204. Missing, already deleted and out-of-scope IDs return 404; students and IIIC cannot use the mentor endpoints (403); anonymous requests return 401. The service independently checks role and active student assignment, locks the assignment, and updates only the selected record. Deletion and audit insert use one server-side transaction. Audit failure rolls back deletion.

## Retention and recovery

This is soft deletion, not physical erasure. V25 adds deleted_at and active_slot to attendance and monthly reports, plus mentor_record_deletion_audit containing type, original ID, deleting user, timestamp and reason. Original student/internship, GPS/verification metadata, attendance evidence, consumed AI sessions and photograph storage keys remain intact. Consumed sessions stay consumed, preventing replay. Original photo files therefore remain referenced; deletion never unlinks them, including files shared with another report. No sensitive evidence is copied into the audit table. There is no purge job or new file deletion path, so failed transactions cannot remove files or leave broken references.

ORM reads and both native report/export query paths exclude deleted records. Active records remain unique per student/internship/date or internship/month. A deleted record releases its active slot, allowing a replacement submission through the existing workflow and verification rules. Multiple archived versions remain possible. Normal photo/PDF download APIs return 404 for a deleted report.

Recovery is possible from retained database rows and existing file references, using a controlled database maintenance process and backups. No restore endpoint or automated purge has been added. Restoring a record requires an explicit review, checking that no active replacement exists and retaining its deletion audit. The institution must define a retention/purge policy before permanent erasure; this change does not invent one. A future purge must consider all retained and active photo references before unlinking a file.

## Files changed for this requirement

- backend/src/main/java/edu/institution/ims/deletion/MentorRecordDeletionController.java
- backend/src/main/java/edu/institution/ims/deletion/MentorRecordDeletionService.java
- backend/src/main/java/edu/institution/ims/attendance/AttendanceRecord.java
- backend/src/main/java/edu/institution/ims/monthlyprogress/MonthlyProgressReport.java
- backend/src/main/java/edu/institution/ims/dashboard/report/DashboardReportRepository.java
- backend/src/main/java/edu/institution/ims/dashboard/report/InstitutionalReportService.java
- backend/src/main/resources/db/migration/V25__mentor_record_deletion.sql
- backend/src/test/java/edu/institution/ims/monthlyprogress/MentorRecordDeletionIntegrationTest.java
- backend/src/test/java/edu/institution/ims/monthlyprogress/MentorRecordDeletionPostgresIntegrationTest.java
- frontend/components/MentorRecordDelete.tsx
- frontend/components/MonthlyProgressReportList.tsx
- frontend/components/ReportsCenter.tsx
- frontend/app/mentor/dashboard/page.tsx
- frontend/tests/mentor-record-delete.test.cjs
- MENTOR_RECORD_DELETION.md

Earlier uncommitted Google Places, photo validation and role-login work has been preserved. V25 follows V24; no previously applied migration has been edited for this deletion feature. The migration has been tested on H2 and PostgreSQL. Local running servers have not been restarted or migrated during this task. Apply through normal backend startup before using the new endpoints. No production data was deleted; no commit, push or deployment was performed.

PostgreSQL tests require a fresh localhost schema named ims_deletion_test_<32 hex characters>, with the JDBC currentSchema parameter matching. The test runner creates a new schema, runs migrations and tests only there, then removes that exact generated schema. It does not use or clean public. Logs are under backend/target/deletion-*.log; frontend build output is frontend/deletion-build.log.

## Actual validation results

Passed 63 relevant H2 backend tests across deletion/monthly progress (14), attendance (3), physical attendance (4), physical verification (2), edge AI attendance (4), online/hybrid attendance (4), authentication (11), dashboards (3), and dashboard reporting/export (18). Passed 14 PostgreSQL integration tests, including the seven deletion scenarios and inherited monthly progress tests, in a fresh local schema migrated through V25. Database audit failure was injected for both record types and the API returned 500 while the original records and files remained available. Passed all 14 frontend tests, including five confirmation, cancellation, duplicate prevention, refresh and failure tests for deletion. Frontend TypeScript checks and the production build passed. These checks do not claim a deployed Render test or a live-browser end-to-end session.
