# Internship completion and academic sessions ? implementation report

Implemented directly in the existing application on 30 October 2026. IIIC uses the existing ADMIN role. Authentication, maps, GPS evidence, resume parsing, matching and report review permissions remain in place.

## Session management and account creation

Settings ? Academic Sessions supports listing, creation, dates, description and activation. Session names are normalized (2027?28 and 2027-2028 are duplicates); dated sessions cannot overlap. A single database settings row stores the active session. Its lock serializes activation with student creation, including each bulk-import row. Existing memberships never change during activation. Dashboard visits default to the database active session; explicit historical selections remain available.

Both supported creation paths, Excel import and configured bootstrap accounts, obtain the session inside the backend transaction. No frontend session ID or current-date calculation determines membership. Imports without an active session return 409. Bootstrap skips new student accounts and logs an administrative setup message so the IIIC account and Settings remain accessible; activate a session and restart to create configured bootstrap students.

V40 imports historical membership from existing profile academic-year values without changing those values or timestamps. Dates for those historical sessions remain unknown until IIIC uses **Set Dates**; existing dated sessions cannot be rewritten. Legacy accounts without an existing profile remain unassigned rather than being moved into the new session. Such legacy membership requires administrative review before completion approval.

## Completion rules, approval and statistics

The server derives every expected inclusive weekly period and monthly period from the actual internship dates. Every matching active weekly report and non-deleted monthly report must exist and be explicitly APPROVED with mentor reviewer/time. Missing periods, mismatched boundaries, pending reviews and rejections block approval. Registration/diary issuance must be complete, session membership must be recorded, and the internship end date must have been reached. No separate final-project report workflow exists, so the UI explicitly shows it as not applicable. Attendance/GPS remains a separate evidence/review workflow.

IIIC reviews student/company/dates/duration/report counts and confirms completion with optional remarks. Approval locks the student and internship, recalculates eligibility, validates the revision and writes COMPLETED, reviewer ID, timestamp, remarks and an append-only audit event in one transaction. Duplicate/stale approvals return 409. Student notifications use internship/revision keys to prevent duplicates and roll back with a failed transaction. Completed details and mandatory-report deletion are locked until IIIC reopens the internship with a 5?1000 character reason. Reopening preserves the prior event, adds another audit event and notifies the student. The student dashboard shows the official status/date and reopening feedback.

Completion statistics and searchable/paged tables filter by backend academic-session membership. Total internships means registered internship-details records. COMPLETED counts require official IIIC approval; an elapsed end date alone gives COMPLETION_PENDING, never COMPLETED. Active includes in-progress and not-yet-started internships. Percentage is completed / total ? 100, or zero for an empty session. Completion totals preserve archived student accounts; the existing lifecycle dashboard continues its active-account policy. Historical counts are unaffected by activation. Reopening changes that internship's current count while retaining its earlier approval in the audit.

The table includes name, UID, company, mentor, dates and IIIC reviewer, with search/status/sort filters and student links. Existing XLSX/PDF export is reused with the same session/status/search filters. The report catalog and report center also expose completion columns, a COMPLETIONS report type and summary statistics.

## Migration and APIs

Only **V40__academic_sessions_and_internship_completion.sql** was added for this enhancement. It creates academic sessions, a singleton active-session pointer and completion audit events; it adds student membership to users and completion state/audit summary fields to internship_details. No historical completion date is fabricated and no existing record is deleted.

| Method | API | Behavior |
| --- | --- | --- |
| GET, POST | `/api/v1/admin/academic-sessions` | List/create sessions |
| POST | `/api/v1/admin/academic-sessions/{id}/activate` | Transactional activation |
| PUT | `/api/v1/admin/academic-sessions/{id}/dates` | Record missing historical calendar dates; optional activation |
| GET | `/api/v1/admin/students/{id}/internship-completion` | Eligibility, status and audit |
| POST | `/api/v1/admin/students/{id}/internship-completion/complete` | IIIC approval with `{revision, remarks}` |
| POST | `/api/v1/admin/students/{id}/internship-completion/reopen` | Authorized reopening with required reason in `remarks` |
| GET | `/api/v1/student/internship-completion` | Own status/counts/date/history |
| GET | `/api/v1/admin/internship-completions` | Session statistics and table; `sessionId`, `search`, `status`, `sort`, `page` |
| GET | `/api/v1/admin/dashboard/summary` | Added `sessionId`; absent defaults to active session |
| GET | `/api/v1/admin/students` | Added `academicSessionId` directory filter |
| Existing reports APIs | `/api/v1/{admin,mentor}/dashboard/reports/*`, `/reports/*` | Added session/completion columns and filters; COMPLETIONS report type |

Student-account import automatically assigns membership. Internship detail updates and mentor mandatory-report deletion reject changes to completed internships. New persistent events are `INTERNSHIP_COMPLETED` and `INTERNSHIP_REOPENED`.

## Focused validation

- **42 backend/H2 cases passed across focused runs:** completion/session workflow (12), dashboard (3), account import (2), existing dashboard reporting (25). The additional completed-report deletion case was verified on PostgreSQL.
- **10 PostgreSQL cases passed across focused runs:** 9 workflow/concurrency/rollback/export/session checks plus 1 V39?V40 migration preservation/idempotency check. Tests used guarded disposable localhost schemas, which were removed afterward. The migration fixture was corrected to include the legacy diary-issued fields before its successful rerun.
- **41 frontend behavioral cases passed:** completion/session (8), notification (18), attendance dashboard/history (10), report liveness (5). The attendance test harness now loads its existing location-display dependencies.
- Frontend `npm run typecheck`: **passed**. `npm run build`: **passed**, including the new routes. `git diff --check`: **passed**.
- Frontend lint remains unavailable: there is no lint script/configuration or ESLint dependency. No browser acceptance run or complete legacy backend rerun was performed. A final report-option description correction was text-only and did not require another build.

## Deployment

No new Vercel/Render environment variables, dependencies or services are required. Deploy/restart the backend with Flyway V40 and deploy the frontend together. Before importing new students, use IIIC Settings to create/activate a session, or record dates and activate an imported historical session. Restart again if configured bootstrap student accounts need creation after activation. Existing Vercel API proxy, Render database, JWT and storage settings remain in use. No production database changes or deployment were performed.

## Files changed for this enhancement

### Backend sessions and accounts

- `backend/src/main/java/edu/institution/ims/academicsession/AcademicSessionController.java`
- `backend/src/main/java/edu/institution/ims/academicsession/AcademicSessionService.java`
- `backend/src/main/java/edu/institution/ims/user/User.java`
- `backend/src/main/java/edu/institution/ims/user/AccountBootstrap.java`
- `backend/src/main/java/edu/institution/ims/studentaccounts/StudentAccountImportService.java`

### Backend completion and reporting

- `backend/src/main/java/edu/institution/ims/internship/InternshipCompletionController.java`
- `backend/src/main/java/edu/institution/ims/internship/InternshipCompletionService.java`
- `backend/src/main/java/edu/institution/ims/internship/InternshipDetailsService.java`
- `backend/src/main/java/edu/institution/ims/deletion/MentorRecordDeletionService.java`
- `backend/src/main/java/edu/institution/ims/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/edu/institution/ims/dashboard/DashboardAnalyticsRepository.java`
- `backend/src/main/java/edu/institution/ims/dashboard/DashboardController.java`
- `backend/src/main/java/edu/institution/ims/dashboard/DashboardService.java`
- `backend/src/main/java/edu/institution/ims/dashboard/report/DashboardReportCatalog.java`
- `backend/src/main/java/edu/institution/ims/dashboard/report/DashboardReportRepository.java`
- `backend/src/main/java/edu/institution/ims/dashboard/report/InstitutionalReportService.java`
- `backend/src/main/java/edu/institution/ims/studentprofile/AdminStudentController.java`
- `backend/src/main/java/edu/institution/ims/studentprofile/AdminStudentService.java`
- `backend/src/main/resources/db/migration/V40__academic_sessions_and_internship_completion.sql`

### Frontend

- `frontend/app/admin/settings/academic-sessions/page.tsx`
- `frontend/app/admin/internship-completions/page.tsx`
- `frontend/app/admin/dashboard/page.tsx`
- `frontend/app/admin/students/page.tsx`
- `frontend/app/admin/students/[id]/page.tsx`
- `frontend/app/student/dashboard/page.tsx`
- `frontend/components/AcademicSessionPicker.tsx`
- `frontend/components/CompletionStatistics.tsx`
- `frontend/components/InternshipCompletionPanel.tsx`
- `frontend/components/AppFrame.tsx`
- `frontend/components/DashboardReportExport.tsx`
- `frontend/components/NotificationsContent.tsx`
- `frontend/components/ReportsCenter.tsx`
- `frontend/lib/academic-sessions.ts`
- `frontend/lib/internship-completion.ts`

### Tests

- `backend/src/test/java/edu/institution/ims/internship/InternshipCompletionIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/internship/InternshipCompletionPostgresIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/internship/CompletionSessionMigrationPostgresTest.java`
- `backend/src/test/java/edu/institution/ims/dashboard/DashboardIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/studentaccounts/StudentAccountImportIntegrationTest.java`
- `frontend/tests/internship-completion.test.cjs`
- `frontend/tests/daily-absence-dashboard.test.cjs`

- `docs/COMPLETION_SESSIONS.md` (this report). Ignored local test copies/logs and generated Next.js type references are excluded.
