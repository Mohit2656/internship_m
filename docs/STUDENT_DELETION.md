# Student deletion in the IIIC directory

Delete Student performs account deactivation, with no permanent deletion endpoint. It preserves the user's identity, academic session, account/profile, every resume version and extraction/correction, files, internships, workplaces, GPS/attendance evidence, reports, assignments, matching snapshots, notifications, approvals and audits. Email/UID identities remain reserved by existing constraints.

The dialog shows name, UID and email, inspects related records, requires a reason of 5–500 characters and an explicit confirmation checkbox, and prevents repeated submissions. The backend verifies the authenticated administrator against the database, rechecks confirmation identity and dependencies, locks the student, and atomically inserts the audit and changes the existing `active`/`token_version` fields. No child rows or assignment flags are changed. All non-student targets are rejected, protecting the current and last active administrator. Duplicate requests return 409.

The dependency inventory covers 39 related tables plus JSON opportunity-matching snapshots, including archived/soft-deleted rows. Applications use external links in this application; there is no local application table to delete. Counts represent the records observed during the dependency review; related records are retained even if an already-running background job later finishes.

The active directory, dashboard lifecycle counts, mentor assignment lists/counts, attendance directory and weekly operational monitor exclude inactive students. New assignments, late-attendance permissions, report/workplace reviews and completion actions reject deactivated students. Existing matching, reminders and absence generation already filter active students. Historical reports retain their existing membership and records, including official completion counts. Active assignments remain stored unchanged for historical report scope, but an inactive student cannot receive new operational actions. Existing login checks and JWT token-version checks reject the deactivated student and old tokens.

After success the directory reloads its current page (or previous page if the last row was removed), updates its attendance selection and shows confirmation. Dashboard statistics refetch after the change event, on focus, and on a cross-tab storage marker. No student identity is stored in that marker.

## Migration and API

- `V41__student_account_deactivation_audit.sql`: one additive audit table with retained student/administrator foreign keys, timestamp, reason and dependency-review snapshot. No cascade deletion rules or existing data are changed.
- `GET /api/v1/admin/students/{id}/deletion-preview`: authoritative identity, active status and dependency counts.
- `DELETE /api/v1/admin/students/{id}`: `{confirmed: true, confirmationEmail, reason}`; returns student ID, inactive status, administrator ID and timestamp.
- `GET /api/v1/admin/students/{id}/deletion-audit`: authorized audit lookup for retained accounts.

All three endpoints require an active ADMIN/IIIC account. No environment-variable changes are required. Deploy the backend/Flyway V41 and frontend together. No production student accounts were modified or deleted, and no production deployment was performed.

## Focused verification

- Backend/H2: **18 passed** — student deletion (7), mentor assignments (4), monthly progress (7).
- PostgreSQL: **5 unique cases passed** across focused runs — original file/history/session-report preservation, inactive-workflow exclusion, login/JWT rejection, concurrent deletion with restrictive foreign keys, and atomic rollback on database failure. The inactive-workflow case was repeated after the final completion-eligibility display fix. Disposable localhost schemas were cleaned up.
- Frontend: **17 passed** — deletion controls/events (9) and completion regression checks (8).
- Frontend type checking and production build passed. Patch whitespace checks passed. Lint remains unavailable because the repository has no lint setup. No full legacy-suite rerun or browser acceptance run was performed.

## Files changed

New files:

- `backend/src/main/java/edu/institution/ims/deletion/StudentDeletionController.java`
- `backend/src/main/java/edu/institution/ims/deletion/StudentDeletionService.java`
- `backend/src/main/resources/db/migration/V41__student_account_deactivation_audit.sql`
- `backend/src/test/java/edu/institution/ims/deletion/StudentDeletionIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/deletion/StudentDeletionPostgresIntegrationTest.java`
- `frontend/components/DeleteStudentButton.tsx`
- `frontend/lib/student-deletion.ts`
- `frontend/tests/student-deletion.test.cjs`
- `docs/STUDENT_DELETION.md`

Updated files:

- `backend/src/main/java/edu/institution/ims/studentprofile/AdminStudentService.java`
- `backend/src/main/java/edu/institution/ims/attendance/StudentAttendanceDirectoryService.java`
- `backend/src/main/java/edu/institution/ims/attendance/LateAttendancePermissionService.java`
- `backend/src/main/java/edu/institution/ims/attendance/InternshipLocationService.java`
- `backend/src/main/java/edu/institution/ims/mentor/MentorAssignmentService.java`
- `backend/src/main/java/edu/institution/ims/mentor/MentorStudentAssignmentRepository.java`
- `backend/src/main/java/edu/institution/ims/internship/InternshipCompletionService.java`
- `backend/src/main/java/edu/institution/ims/monthlyprogress/MonthlyProgressService.java`
- `backend/src/main/java/edu/institution/ims/weeklyprogress/WeeklyProgressService.java`
- `frontend/app/admin/students/page.tsx`
- `frontend/app/admin/dashboard/page.tsx`
