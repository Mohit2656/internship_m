# IMS Project Technical Handover

> Prepared from the repository working tree on 2026-11-02 for continuation by another coding agent. Current code, migrations, and tests are authoritative where older documents disagree. No secret values are included.

## 1. Project overview

The Internship Management System (IMS) manages institutional internship registration, workplace verification, attendance, progress reporting, opportunities, and completion.

Primary roles:

- **Student**: maintains profile and professional data, registers up to two authorized internships, submits workplace coordinates, attendance evidence, and progress reports.
- **Mentor**: reviews assigned students, verifies workplace coordinates, handles exceptional attendance, acknowledges finalized attendance, and reviews progress.
- **III Cell Incharge** (`ADMIN` in code): manages accounts, assignments, opportunities, academic sessions, reporting, second-internship authorization, and audited administrative corrections.

`HOD` appears in `AuthService` role labeling but has no dedicated frontend or API area in the current repository.

### Technology stack

| Layer | Current repository version / technology |
|---|---|
| Frontend | Next.js 16.3.5, React 19.3.0, TypeScript 5.8.3, Tailwind/PostCSS 4.1.5 |
| Mapping | Leaflet 1.9.4 and React Leaflet 5.0.0 remain dependencies; current workplace registration primarily uses coordinate fields/external Google Maps links, while some shared/admin components still reference OSM/Leaflet code |
| HTTP client | Axios 1.8.4; Next.js rewrites `/api/v1/*` to the backend |
| Edge vision | MediaPipe Tasks Vision 1.0.1; WASM and face-landmarker assets are in `frontend/public/mediapipe` and `frontend/public/models` |
| Backend | Java 17, Spring Boot 3.4.5, Spring MVC, Security, Data JPA, Validation, Actuator |
| Authentication | JJWT 0.12.6, BCrypt, stateless Spring Security |
| Database | PostgreSQL in normal use; Flyway; H2 in many automated tests |
| Documents | Apache POI 5.3.0, PDFBox 2.0.32, metadata-extractor 2.19.0 |
| AI service | Node 24 container; Fastify 5.6.1, `@huggingface/transformers` 4.3.0, local models; no paid AI API required |

### Repository layout

```text
ims/
├── backend/                 Spring Boot service, Flyway migrations, tests, Dockerfile
│   └── src/main/java/edu/institution/ims/
│       ├── academicsession, attendance, auth, dashboard, deletion, editing
│       ├── internship, mentor, monthlyprogress, notification, opportunity
│       ├── professionalprofile, security, studentaccounts, studentprofile
│       ├── weeklyprogress, workplace, user
│       └── ...DTOs, repositories, services, controllers
├── frontend/                Next.js App Router application
│   ├── app/{student,mentor,admin}/
│   ├── components/          shared forms, tables, sidebars, attendance/workplace UI
│   ├── lib/                 API/types/auth/domain helpers
│   ├── public/              branding, DOCX templates, MediaPipe assets
│   └── tests/               Node test runner source/harness tests
├── ai-service/              local resume/JD parsing and semantic-support service
├── docs/                    historical feature/audit notes (verify against code)
└── IMS_PROJECT_HANDOVER.md  this file
```

### Current Git state

- Branch: `tanu`.
- HEAD: `71d644f map,list changed`.
- Remote: `origin` points to the GitHub repository `Mohit2656/internship_m`.
- Related branches: `feature-workplace-location-rebuild` at `71d644f`; recoverable snapshot `backup-location-phases-1-4-20261101` at `f16b310`.
- The working tree is intentionally dirty. Current uncommitted work includes attendance acknowledgement/status enforcement, role sidebars, mentor/admin UI cleanup, tests, and Flyway `V49`.
- Do not reset, checkout over, clean, stash, or broadly stage this tree without first reviewing `git status --short` and creating a recoverable branch/commit.

### Deployment architecture observed

- `backend/Dockerfile`: multi-stage Maven/Temurin 17 build; runtime Java 17; binds `${PORT:-10000}`.
- `ai-service/Dockerfile`: Node 24 Trixie, installs local model assets, executes AI typechecks/tests at image build, binds port 10000.
- `frontend/next.config.ts`: local/default backend is `http://localhost:8080`; deployment must supply `BACKEND_API_URL` during build/runtime as appropriate.
- No frontend Dockerfile, compose file, Vercel config, or checked-in Render blueprint was found. The actual production host topology and reverse-proxy limits are therefore **UNKNOWN** and must be confirmed outside the repository.

## 2. Architecture and codebase

### Request flow

1. Next.js pages/components call the Axios wrapper in `frontend/lib/api.ts` using `/api/v1` paths.
2. `frontend/next.config.ts` rewrites those paths to `BACKEND_API_URL`.
3. Spring Security authenticates JWT cookies (or Bearer tokens) through `JwtAuthenticationFilter`.
4. Controllers delegate to transactional services; services enforce role, assignment, student ownership, state/revision, and concurrency rules.
5. Spring Data repositories and `JdbcTemplate` persist PostgreSQL records; Flyway is enabled and JPA uses `ddl-auto: validate`.

### Authentication and authorization

- `SecurityConfig` permits login, health, and OpenAPI resources; `/api/v1/admin/**`, `/mentor/**`, and `/student/**` require their matching roles.
- JWT is normally stored in an HTTP-only cookie configured by `JWT_COOKIE_NAME` and `JWT_COOKIE_SECURE`. `JwtAuthenticationFilter` also accepts Bearer tokens.
- Tokens include a token version. Password reset/change invalidates prior tokens using `users.token_version` (V23).
- Active state is reloaded from the database. Frontend `ProtectedRoute` is convenience only; backend enforcement is authoritative.
- Mentor services additionally require an active `mentor_student_assignments` row for the target student.
- Student-self APIs derive identity from `UserPrincipal`; they do not accept another student's ID.

### Important backend layers

- Controllers: `backend/src/main/java/edu/institution/ims/**/**Controller.java`.
- Services: domain packages contain transactional business rules (examples: `InternshipDetailsService`, `InternshipLocationService`, `AttendanceReviewService`, `OpportunityMatchService`).
- Repositories/entities: JPA for stable aggregates; `JdbcTemplate` is also used for locks, append-only audits, and aggregate reports.
- Error handling: `GlobalExceptionHandler` returns structured `ApiError`; domain exceptions carry stable error codes.
- Concurrency: student workflow locks (`WorkflowUserLocks`), pessimistic repository queries, unique constraints, revisions, and conditional updates protect high-risk transitions.

### Core relationships

- `users` is the role/account root; optional `student_accounts`, `student_profiles`, and `mentor_profiles` extend it.
- `mentor_student_assignments` links one active mentor assignment to a student and is referenced by review/audit records.
- `internship_onboarding` belongs to a student and is unique by `(student_user_id, internship_number)` where number is 1 or 2.
- `internship_details` belongs to onboarding. Workplace, attendance, weekly periods, monthly reports, and completion are internship-scoped.
- `internship_locations` belongs to internship details and stores coordinates, radius, revision, mentor review state, and retained historical III Cell fields.
- `attendance_records` is unique per student/internship/date active slot and has optional `attendance_evidence`, `attendance_reviews`, and mentor acknowledgement columns.
- Resume, skill, opportunity, notification, report, and audit tables retain their own version/status constraints.

### File storage and static files

- Private uploaded internship documents/resumes use server storage under `DOCUMENT_STORAGE_ROOT`; PostgreSQL stores metadata/storage keys. See `LocalFileStorageService` and professional-profile services.
- Downloadable official DOCX templates are static under `frontend/public/documents/` and remain editable Office documents.
- Attendance/monthly images are validated and evidence metadata is persisted. Inspect the relevant entity/service before changing binary retention behavior.

### External/local integrations

- Resume/JD analysis uses the local `ai-service` through `RESUME_AI_BASE_URL` / `OPPORTUNITY_AI_BASE_URL`.
- Photon workplace search still exists in `backend/.../workplace` and is configured by `WORKPLACE_SEARCH_BASE_URL`, but it is not a reliable business directory and is no longer the primary student workflow.
- Browser geolocation supplies attendance GPS; image EXIF is not authoritative for daily attendance. Server geofence/liveness checks are authoritative.
- No Google Places, Geoapify, Mappls, or paid provider integration is active.

### Configuration names (never commit values)

Backend: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SERVER_PORT`, `JWT_SECRET`, `JWT_EXPIRATION_SECONDS`, `JWT_COOKIE_NAME`, `JWT_COOKIE_SECURE`, `CORS_ALLOWED_ORIGINS`, `PROFILE_ENCRYPTION_KEY`, `DOCUMENT_STORAGE_ROOT`, `DOCUMENT_MAX_FILE_SIZE`, `DOCUMENT_MAX_REQUEST_SIZE`, `DOCUMENT_MAX_FILE_SIZE_BYTES`, `RESUME_MAX_FILE_SIZE_BYTES`, `MULTIPART_MAX_FILE_SIZE`, `MULTIPART_MAX_REQUEST_SIZE`, `WORKPLACE_SEARCH_BASE_URL`, `WORKPLACE_SEARCH_CONNECT_TIMEOUT_SECONDS`, `WORKPLACE_SEARCH_READ_TIMEOUT_SECONDS`, `ATTENDANCE_ZONE`, `ATTENDANCE_ABSENCE_ENABLED`, `ATTENDANCE_DEFAULT_GEOFENCE_RADIUS_METERS`, `ATTENDANCE_MAX_GPS_ACCURACY_METERS`, `ATTENDANCE_MAX_LOCATION_AGE_SECONDS`, `ATTENDANCE_MAX_IMAGE_SIZE_BYTES`, `ATTENDANCE_MAX_METADATA_LOCATION_DIFFERENCE_METERS`, `ATTENDANCE_MAX_METADATA_AGE_SECONDS`, `ATTENDANCE_AI_SESSION_DURATION_SECONDS`, `MONTHLY_PROGRESS_MAX_PHOTO_SIZE_BYTES`, `RESUME_AI_BASE_URL`, `RESUME_AI_CONNECT_TIMEOUT_SECONDS`, `RESUME_AI_READ_TIMEOUT_SECONDS`, `OPPORTUNITY_AI_BASE_URL`, `OPPORTUNITY_AI_CONNECT_TIMEOUT_SECONDS`, `OPPORTUNITY_AI_READ_TIMEOUT_SECONDS`, `OPPORTUNITY_STRONG_MATCH_THRESHOLD`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`, `BOOTSTRAP_STUDENT_EMAILS`, `BOOTSTRAP_STUDENT_PASSWORDS`.

Frontend: `BACKEND_API_URL`, plus the public/client API base setting used by `frontend/lib/api.ts` if configured in `.env`. Review `frontend/.env.example`; never expose server secrets with `NEXT_PUBLIC_*`.

AI service: see `ai-service/.env.example`, notably service host/port, model paths/revisions, file limits, and optional real-model-test flags. Do not copy credentials into documentation.

Local backend profile intentionally imports `backend/application-local-secrets.properties`; that file must remain untracked/private.

## 3. Feature and workflow inventory

Status meanings: **VERIFIED** = supported by current code and focused tests; **PARTIAL** = implemented with a known limitation or incomplete verification; **UNKNOWN** = runtime/deployment evidence is unavailable; **NOT IMPLEMENTED** = no active implementation found.

### Student accounts, profile, CGPA, skills, and resume — VERIFIED

- Flow: III Cell imports/manages student accounts; student completes their profile; optional CGPA is accepted from 0.00–10.00; professional profile and resume can be updated; resume analysis produces reviewable suggestions; students confirm, reject, add, rename, or delete skills.
- Frontend: `app/student/profile`, `app/student/professional-profile`, `components/ProfileForm.tsx`, resume/skill components; admin directory/account pages.
- Backend: `StudentProfileController/Service`, `StudentProfessionalProfileController`, `StudentResumeAnalysisController`, `StudentSkillController`, `StudentAccountImportController`.
- Tables: `users`, `student_accounts`, `student_profiles`, professional profile/resume/analysis/extracted-data/skill tables.
- Permissions: owners edit their profiles; administrator/assigned-mentor views use scoped endpoints. Student profiles are intentionally always owner-editable; mentor one-time permission no longer applies to profile updates.
- Evidence: `StudentProfileIntegrationTest`, `ProfessionalProfileIntegrationTest`, `ResumeAnalysisIntegrationTest`, `StudentSkillIntegrationTest`; V48 adds optional constrained CGPA.

### Mentor accounts, assignments, and dashboard — VERIFIED (working tree)

- III Cell creates/deactivates mentor accounts and assigns students.
- Mentor dashboard uses `/mentor/profile` plus attendance/student summary APIs. The working tree adds `MentorSidebar.tsx`, a profile block, and a UID-sorted assigned-student table.
- Assigned mentor access is checked server-side by assignment repositories/services.
- Key files: `AdminMentorAccountController`, `AdminMentorAssignmentController`, `MentorProfileController`, `MentorStudentController`, `app/mentor/dashboard`, `components/MentorSidebar.tsx`.

### Internship 1 lifecycle — VERIFIED

- `InternshipOnboarding` status progresses through secured/source/documents/diary/details completion. See `WorkflowStatus` and `InternshipOnboardingService` for exact transition guards.
- Student endpoints: `/api/v1/student/internship-onboarding/**` and `/api/v1/student/internship`.
- `InternshipDetailsForm` validates company, supervisor, mode, dates, duration, stipend, and workplace requirements for office/hybrid modes.
- Tables: `internship_onboarding`, `internship_documents`, `internship_details`.
- Tests: `InternshipOnboardingIntegrationTest`, `InternshipDetailsIntegrationTest`, `InternshipDurationTest`.

### III Cell-approved dual internship — PARTIAL/VERIFIED core authorization

- Student with Internship 1 sends `POST /api/v1/student/second-internship-requests`.
- V46 unique constraints prevent duplicate pending/approved grants and introduce internship number 1/2.
- III Cell reads/reviews requests through `/api/v1/admin/second-internship-requests`; rejection requires a reason. Approval creates onboarding for Internship 2 and notifies the student.
- Frontend: `app/student/internship/second`, admin dashboard/request detail route.
- Core persistence and direct-API authorization are implemented in `SecondInternshipRequestService`.
- Dual-internship isolation is implemented by `internshipId` on major attendance/workplace flows and by internship-scoped report tables, but a complete full-suite regression was not executed in this handover turn. Treat cross-internship selection in every older report/dashboard path as a continuing audit priority.

### Mentor-controlled single-use internship editing — VERIFIED, schema has legacy profile scope

- After internship start, `INTERNSHIP_DETAILS` requires current assigned-mentor permission. Grant lasts 24 hours and is atomically consumed only after a successful save; validation failures do not consume it.
- Mentor grant/revoke and student status APIs are in `StudentEditPermissionController/Service`.
- V45 schema permits both `INTERNSHIP_DETAILS` and `STUDENT_PROFILE`, but current service rejects profile permission changes because profile editing is always allowed. This schema/code mismatch is intentional legacy compatibility, not a reason to restore profile locking.
- Audit: `student_edit_permission_audit` records granted/consumed/revoked events and before/after JSON for consumption.

### Workplace registration and mentor-only verification — VERIFIED with UI debt

- Student company/address/coordinate entry is part of internship details (`InternshipDetailsForm`); coordinates are persisted through the existing workplace service.
- `LocationMapLink` opens exact coordinates in Google Maps. Do not scrape or persist undocumented Google Maps data.
- Assigned mentor reviews company/address/coordinates through `InternshipLocationManager` and `/api/v1/mentor/students/{studentId}/internship/location/review`.
- Mentor approval sets effective `APPROVED` and locks student coordinate changes. Rejection allows correction/resubmission. Admin correction/radius change is audited, invalidates mentor approval, and requires reapproval.
- V43 makes mentor approval sufficient while retaining historical III Cell fields/audit.
- Known inconsistency: `InternshipLocationManager` initializes a local radius string to `100` during loading/reset even though configured/persisted radius is 200; loaded records replace it. This should be cleaned to avoid transient/stale UI assumptions.
- Photon/OSM search components and tests remain in the repository. Do not remove them until all active references are proven absent.

### Attendance — VERIFIED in current working tree, V49 uncommitted

Authoritative state machine:

1. The absence scheduler processes applicable workdays after the configured cutoff. If no record exists, it inserts `ABSENT` with `AUTO_ABSENT`.
2. A submitted photo/location is evaluated against the mentor-approved workplace revision. Default radius is 200 m, boundary inclusive; GPS accuracy (default 200 m maximum) and freshness (default 300 seconds) are independent checks.
3. Required liveness/security and location all pass: `AttendanceRecord.finalizeSubmission(true)` persists `PRESENT` immediately; no mentor approval is required.
4. Submitted evidence with GPS/liveness/reviewable verification problems persists `PENDING_MENTOR_REVIEW`, never automatic absence. Evidence and failure details remain stored.
5. Only the active assigned mentor may approve pending evidence to `PRESENT`, reject to `ABSENT`, or keep manual review. Revision, assignment, reason, audit, notification, and locks are enforced in `AttendanceReviewService`.
6. Finalized `PRESENT`/`ABSENT` records can be acknowledged separately using `POST /api/v1/mentor/attendance/{id}/acknowledgement`. V49 stores mentor ID/time. Acknowledgement is idempotent and never changes status.

Key files:

- Frontend: `TodayAttendanceCard`, `DailyAttendanceHistory`, `AttendanceAbsenceOverview`, `AttendanceReviewPanel`, `AttendanceLocations`, `LocationVerificationResult`, `app/mentor/attendance`.
- Backend: `PhysicalAttendanceService`, `RemoteAttendanceService`, `AttendanceSubmissionService`, `AttendanceLocationVerificationService`, `AttendanceAbsenceService/Writer/Scheduler`, `AttendanceReviewService/Controller`, `StudentAttendanceDirectoryService`.
- Tables: `attendance_records`, `attendance_evidence`, verification sessions, location-verification audit, submission audit, `attendance_reviews`, review audit, late permissions/audit.
- Current mentor Attendance page has a five-column UID-sorted summary and a separate student-selectable records section. Detailed records include a **Mentor Viewed** column and separate pending-decision controls.
- Student UI immediately displays the stored GPS/liveness failure category and shows `Viewed by mentor` after acknowledgement.

Latest verification evidence:

- Backend compiled and Flyway validated 49 migrations under H2.
- Combined focused run executed 38 attendance tests: 37 passed; one log-capture assertion failed only in the shared run and passed when rerun alone. Functional review/absence suites passed (`AttendanceReviewIntegrationTest` 11, `AttendanceAbsenceIntegrationTest` 6, `AttendanceAbsenceWriterTest` 6).
- Frontend focused attendance/navigation tests: 17/17 passed; TypeScript and production build passed.
- PostgreSQL-specific acknowledgement migration execution was not run in this documentation turn; syntax uses PostgreSQL-compatible `BIGINT REFERENCES` and `TIMESTAMP WITH TIME ZONE`. Run it only in a disposable schema before deployment.

### Weekly progress — VERIFIED

- V37 adds generated bounded weekly periods, active/archive reconciliation, revisioned text submissions, mentor approval/rejection, history, attendance summaries, and notifications.
- APIs: `/student/weekly-progress`, `/mentor/weekly-progress`, `/admin/weekly-progress`.
- Date changes cannot rewrite submitted periods. See `docs/weekly-progress.md` and `WeeklyProgressIntegrationTest`; verify code if the doc diverges.

### Monthly progress — VERIFIED

- Student uploads monthly report/photo; photo metadata and integrity are retained. Mentor reviews pending reports; rejection requires a reason and permits revision.
- Frontend: student/mentor/admin monthly routes and `MonthlyProgressReportList`.
- Backend: monthly-progress controllers/services/repositories, photo validator/PDF service.
- Tables/migrations: V19, V21, V22, V24, V25, V29.

### Opportunities and resume-based matching — VERIFIED with documented limits

- III Cell creates/publishes/closes opportunities. Current form no longer requires branch/semester selection; existing records remain supported.
- Publication/current recalculation uses explainable matching and stored/effective student skills. Required skills receive the dominant weight; missing/unparseable resume/profile conditions are represented without inventing skills.
- Admin recommendation route: `app/admin/opportunities/[id]/talent`; backend `OpportunityMatchingController`, `OpportunityMatchService`, `AdminTalentDiscoveryService`.
- V38 stores immutable matching-run reports and notification deduplication data.
- `docs/opportunity-matching.md` contains algorithm details, but its statement that authoritative CGPA is unavailable predates V48 and must be revalidated before changing eligibility logic.

### III Cell dashboard/reporting/administration — VERIFIED, UI work uncommitted

- Dashboard, student directory/detail, accounts, mentor assignments, sessions, opportunities, completion, attendance, weekly/monthly progress, notifications, and reports have App Router pages.
- `AdminSidebar.tsx` and student-directory cleanup are untracked/modified working-tree work and must be preserved.
- Reports support authorized table/preview/PDF/XLSX operations through `DashboardReportController/Service/Writer` and catalog/repository SQL.

### Notifications — VERIFIED

- Separate student, mentor, and admin inbox controllers expose list, unread count, individual read, and read-all operations.
- Workflow notifications are persisted and deduplicated by event keys. V47 repairs second-internship admin task URLs.
- Frontend shared `NotificationMenu` and role inbox pages poll/refresh counts and validate action routes.

### Documents/templates and completion — VERIFIED

- Static official templates: `frontend/public/documents/*.docx`; student page: `/student/documents`.
- Uploaded internship documents use private backend storage and authorized view/download endpoints.
- Internship completion and academic-session lifecycle use V40/V42 plus completion/session services and tests. Preserve historical data and deletion/audit behavior.

## 4. Current UI and navigation

### Shared frame/design

- `AppFrame` retains IMS branding, theme toggle, notifications, account/menu controls.
- `StudentSidebar`, `MentorSidebar`, and `AdminSidebar` reuse `.student-sidebar*` styles in `app/globals.css`.
- Desktop uses a sticky independently scrollable sidebar; mobile uses a focus-managed drawer, Escape/outside-click closing, and backdrop.
- The design system is class-based in `globals.css` plus existing Tailwind utilities. Preserve existing colors, typography, button variants, dark theme, branding assets, and report-document exceptions.

### Student navigation

Home, Profile, Professional Profile, Skill Gaps; Internship, Internship Details, Second Internship, Opportunities; Weekly Progress, Monthly Progress, dashboard Attendance anchor, Documents & Templates; Notifications, Change Password.

### Mentor navigation

Home and Assigned Students; Attendance and Late Attendance Permissions; Weekly Progress, Monthly Progress, Documents, Reports; Notifications and Change Password. Nested student detail routes highlight Assigned Students.

The mentor dashboard summary table is UID-sorted and uses actual UID only. The attendance page summary has exactly `UID | Student Name | Branch | Company | Attendance %`; dark student names link to the existing profile route. Detailed attendance is separate.

### III Cell navigation

Home, Student Directory, Second Internship Requests; Opportunities, Weekly Progress, Monthly Progress, Attendance, Completions, Reports; Student Accounts, Mentor Accounts; Academic Sessions, Notifications.

### UI inconsistencies/pending corrections

- Sidebar files are currently untracked; staging/committing must include them with corresponding `AppFrame`, CSS, and tests.
- Some older pages still include “Back to dashboard” links designed for top navigation; harmless but visually redundant with sidebars.
- `AttendanceAbsenceOverview` has separate mentor/admin branches; avoid reintroducing mentor accordion fields into the five-column summary.
- Workplace UI still contains OSM/search-era components and transient 100 m local defaults; audit before removal.
- Some older documentation strings use “mentor approval” for attendance generally. Current behavior is automatic finalization plus exceptional review.

## 5. Database and Flyway migrations

Migrations are immutable once applied. Never edit an applied migration, delete history, run `clean`, or point experiments at production.

| Version | Purpose |
|---|---|
| V1 | users/student account base |
| V2 | student profiles and sensitive profile data |
| V3 | internship onboarding/documents |
| V4 | internship details |
| V5 | dashboard/query indexes |
| V6 | bulk student-account management |
| V7 | mentor profiles |
| V8 | mentor/student assignments |
| V9 | base attendance |
| V10 | physical attendance/workplace verification |
| V11 | online/hybrid attendance |
| V12 | edge-AI attendance verification |
| V13–V16 | professional profile, resume history/analysis, skill profile |
| V17–V18 | opportunity management and AI analysis |
| V19–V22 | monthly reports and photo verification |
| V23 | password token version |
| V24 | shared workplace verification |
| V25 | mentor soft deletion/audit |
| V26 | workplace names/correction audit |
| V27 | historical dual workplace approval |
| V28 | liveness verification basis |
| V29 | monthly mentor review |
| V30 | location-only basic attendance compatibility |
| V31 | automatic daily absence |
| V32 | mentor late-attendance permission |
| V33–V36 | student/mentor/workflow notifications and GPS signal |
| V37 | weekly internship progress |
| V38 | matching history |
| V39 | mentor attendance review/audit |
| V40–V42 | academic sessions, completion, account lifecycle |
| V43 | mentor-only workplace approval; recalculates effective status without deleting historical III Cell fields |
| V44 | adds `PENDING_MENTOR_REVIEW` attendance result; PostgreSQL syntax repaired |
| V45 | one-time edit permission/audit tables |
| V46 | internship number 1/2 and second-internship request state machine |
| V47 | repairs request-notification task links |
| V48 | optional constrained current CGPA |
| V49 | **uncommitted** mentor attendance acknowledgement columns/check constraint |

Risks:

- V43 updates effective workplace statuses. Validate representative approved/pending/rejected historical rows in a disposable PostgreSQL restore.
- V45 retains `STUDENT_PROFILE` as an allowed schema scope although current code only grants internship-detail access.
- V46 drops/replaces the old one-internship uniqueness constraint; test all legacy FK/index names on the exact production schema.
- V49 has passed H2 Flyway startup but still requires disposable PostgreSQL verification before release.
- `ddl-auto: validate` means every entity/schema mismatch prevents startup—this is intentional safety.

## 6. Development and testing

### Required tools

- Java/JDK 17 and Maven 3.9.x (Docker build uses Maven 3.9.16).
- Node/npm compatible with Next 16/React 19; Node 20+ is reasonable for frontend. AI Docker explicitly uses Node 24.
- PostgreSQL. The exact local server version is not pinned; production/disposable verification has previously targeted modern PostgreSQL.

### Initial setup

```bash
cd frontend && npm ci
cd ../ai-service && npm ci
cd ../backend && mvn dependency:go-offline
```

Create local environment files from examples where available. Create `backend/application-local-secrets.properties` locally with required names, never values in Git. At minimum configure database credentials, JWT secret, and profile encryption key. Use a dedicated PostgreSQL database/user.

Example database preparation (adapt names; do not run against production):

```sql
CREATE USER ims_user WITH PASSWORD '<local-only-password>';
CREATE DATABASE internship_management OWNER ims_user;
```

### Start services

```bash
# PostgreSQL: use your OS service manager, e.g. brew services start postgresql@18

cd ai-service
npm run model:setup       # first time; downloads local models
npm run dev               # default expected backend target is 127.0.0.1:8091

cd ../backend
mvn spring-boot:run       # default port 8080; Flyway runs automatically

cd ../frontend
npm run dev               # port 3010
```

### Safe Flyway practice

1. Restore/snapshot production into a disposable database/schema.
2. Point `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` only at that disposable target.
3. Start backend or run tests; Flyway migrates and JPA validates.
4. Query `flyway_schema_history` and representative data counts/statuses.
5. Never use Flyway clean/reset/repair to bypass a failed migration without an approved recovery plan.

### Tests/builds

```bash
cd backend
mvn test
mvn -Dtest=AttendanceReviewIntegrationTest,AttendanceAbsenceIntegrationTest test
mvn -DskipTests compile

cd ../frontend
node --test tests/*.test.cjs
npm run typecheck
npm run build

cd ../ai-service
npm run typecheck
npm test
# Opt-in only after models exist:
npm run test:real

cd ..
git diff --check
```

PostgreSQL integration tests are guarded by environment variables and disposable-schema naming checks (for example `EXIF_TEST_DB_URL`, username/password/schema variables). Read the specific `*PostgresIntegrationTest` before setting them; never reuse a production schema.

### Common diagnostics

- Backend fails before security initialization: verify `application-local-secrets.properties` exists in the backend working directory and contains required keys.
- Flyway checksum/error: do not edit history. Compare the applied migration with Git and restore a disposable copy to reproduce.
- JPA validation failure: compare the named column/constraint against migrations; do not enable schema auto-update.
- Frontend API 404/connection: verify `BACKEND_API_URL`, backend port, and `/api/v1` rewrite.
- Authentication loop: inspect cookie secure/domain/CORS settings and token version/account active state.
- Upload rejected: compare proxy limits, Next `proxyClientMaxBodySize`, Spring multipart limits, Tomcat limits, and domain file validators.
- Attendance unexpectedly pending: inspect `locationVerificationStatus`, GPS accuracy/capture time, workplace revision/approval, and liveness reason; do not relabel it in the UI.
- Combined physical-attendance test log capture can be order-sensitive; rerun the named test alone before treating it as a functional failure.

## 7. Git and release safety

Current changes are mixed and uncommitted. Before continuation:

```bash
git status --short
git diff --stat
git diff --name-status
git ls-files --others --exclude-standard
git diff --check
```

Recommended safe sequence:

1. Verify no secrets/generated `.next`, `target`, uploads, database dumps, or model files are staged.
2. Create a named recovery branch from the current working state.
3. Stage only reviewed source/migration/test/docs files.
4. Commit a snapshot before further refactoring.
5. Rebase/merge only after fetching and inspecting divergence; never use destructive reset on this worktree.
6. Push only with explicit authorization. No push/deployment was performed for this handover.

Production checklist must include disposable PostgreSQL migration tests, backup/restore rehearsal, secrets supplied by the platform, persistent document storage, HTTPS/secure cookies, allowed origins, request-body limits, health checks, and frontend/backend/AI version alignment.

## 8. Current problems and pending work

| Area | Status | Evidence / next check |
|---|---|---|
| No submission after cutoff becomes automatic absence | **VERIFIED** | `AttendanceAbsenceService/Writer`; absence integration/writer tests pass |
| Valid location + mandatory checks becomes automatic present | **VERIFIED** | `finalizeSubmission(submissionVerificationPassed)` in physical/basic/remote services; physical suite passed except an order-sensitive logging assertion that passed alone |
| Failed/reviewable submitted evidence becomes pending, not absent | **VERIFIED** | `AttendanceResult.PENDING_MENTOR_REVIEW`, V44, review/physical tests |
| Mentor decision only for pending cases | **VERIFIED** | backend rejects finalized records with `ATTENDANCE_ALREADY_FINALIZED`; frontend separates controls |
| Mentor Viewed does not change status | **VERIFIED in H2/tests; PostgreSQL migration pending** | `AttendanceReviewService.acknowledge`, V49, integration/frontend tests |
| Student exact pending reason and viewed status | **VERIFIED** | `TodayAttendanceCard`, `AttendanceReviewFeedback`, response evidence/acknowledgement |
| Mentor attendance five-column summary | **VERIFIED** | mentor branch of `AttendanceAbsenceOverview`; focused source/render test |
| UID sorting/no email fallback | **VERIFIED** | numeric `localeCompare`, `—` fallback, student-name profile link |
| Detailed attendance separate from summary | **VERIFIED** | Attendance Records section with student selector and detailed table |
| Dual-internship isolation | **PARTIAL** | core schema/services are internship-scoped; run full cross-internship attendance/progress/report regression before release |
| V49 on PostgreSQL | **UNKNOWN** | not executed against disposable PostgreSQL in this documentation turn |
| Production startup/deployment | **UNKNOWN** | local compile/build passes; actual infrastructure/secrets/storage not accessible |
| Photon company search | **PARTIAL/unreliable** | implementation remains but known business-name coverage is insufficient; manual coordinates are the reliable workflow |
| Old docs consistency | **PARTIAL** | `docs/attendance-review.md`, `docs/README.md`, and matching docs contain statements predating current code |

## 9. Antigravity continuation instructions

### Inspect first

1. `git status --short` and the full working diff.
2. `backend/.../attendance/{AttendanceRecord,AttendanceReviewService,AttendanceService,PhysicalAttendanceService,AttendanceAbsenceService}.java`.
3. `backend/src/main/resources/db/migration/V43...V49`.
4. `frontend/components/{AppFrame,StudentSidebar,MentorSidebar,AdminSidebar,AttendanceAbsenceOverview,AttendanceReviewPanel,TodayAttendanceCard}.tsx`.
5. `frontend/app/mentor/attendance/page.tsx` and focused frontend/backend tests.
6. Dual-internship controllers/services and every API that accepts optional `internshipId`.

### Priority order

1. Create a recoverable snapshot commit/branch after secret/generated-file review.
2. Run V43–V49 against a disposable PostgreSQL restore, especially V46 and V49; document counts/status preservation.
3. Run the full backend/frontend/AI suites and separate real regressions from stale test assumptions.
4. Audit dual-internship isolation end-to-end for attendance, workplace, weekly/monthly progress, completion, exports, and mentor views.
5. Resolve remaining workplace UI debt (transient 100 m defaults and dead Photon/Leaflet references) only after reference tracing.
6. Reconcile stale documentation with current mentor-only workplace and automatic-attendance semantics.
7. Confirm production topology, persistent storage, proxy limits, environment values, backups, and observability before deployment.

### Invariants that must not be broken

- Never trust frontend role/disabled state for authorization.
- Never accept an arbitrary student ID on student-self mutations.
- Never substitute email for UID in student summaries.
- Never silently change mentor-approved coordinates; audited admin corrections invalidate approval and require fresh mentor verification.
- Never change the default 200 m geofence or GPS tolerance without approved policy/evidence; they are separate settings.
- Never mark submitted failed evidence automatically absent; preserve it as pending for assigned-mentor review.
- Never allow acknowledgement to modify attendance status, review, evidence, or totals.
- Never let a finalized attendance decision be overwritten by duplicate/concurrent review.
- Never mix Internship 1 and 2 records; require explicit internship scope.
- Never expose uploaded resume/photo/document storage paths or secrets.
- Never edit applied migrations, erase Flyway history, or rewrite historical audit/evidence records.
- Never remove III Cell powers outside the specifically removed workplace-approval responsibility.

### Acceptance criteria for remaining release work

- Disposable PostgreSQL migrates V1–V49 from empty and V43–V49 over representative legacy data with preserved audits/counts.
- Full automated suites pass, or every excluded/order-sensitive test is documented and independently green.
- Two internships for one student produce isolated workplace, attendance, weekly/monthly report, mentor review, and export results.
- Mentor attendance page shows only five summary columns; pending records alone expose decisions; finalized records alone expose acknowledgement; student sees persisted results.
- Direct unauthorized/stale/concurrent requests return structured errors without partial writes.
- Production configuration uses HTTPS, secure cookies, persistent private storage, bounded uploads, backups, and correct CORS/proxy settings.

## 10. Evidence rules for future work

- Prefer current source, migration constraints, and executable tests over narrative docs.
- Cite paths/symbols in commits and reports.
- Mark runtime behavior **UNKNOWN** unless actually exercised against the intended environment.
- Do not infer provider coverage, production capacity, or data quality from code alone.
- Keep logs free of tokens, private coordinates, personal data, photos, resumes, and secret-bearing URLs.

