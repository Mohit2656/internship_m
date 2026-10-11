# Internship Management System

The system currently includes the Phase 1 authentication foundation, Phase 2 student/internship workflows, Phase 3 mentor and attendance workflows, Phase 4.1 private versioned resumes, Phase 4.2 local DistilBERT/ONNX resume analysis, Phase 4.3 student-reviewed skill profiles, Phase 4.4 III Cell opportunity management, Phase 4.5 the active Student opportunity feed, and Phase 4.6 local MiniLM-assisted job-description understanding with ADMIN review. The active roles are III Cell Incharge (`ADMIN`), Student (`STUDENT`), and Mentor (`MENTOR`). Student-opportunity matching, identity recognition, weekly reporting, and analytics remain outside the current scope.

## Structure

- `backend/` — Java 17, Spring Boot, Spring Security, JPA, Flyway, PostgreSQL, JWT
- `frontend/` — Next.js App Router, TypeScript, Tailwind CSS, Axios
- `ai-service/` — loopback-only Node/TypeScript service using `resume-extract`, DistilBERT NER, quantized MiniLM sentence embeddings, and ONNX Runtime

## Local configuration

For one-time local setup, copy [`backend/application-local-secrets.example.properties`](backend/application-local-secrets.example.properties) to `backend/application-local-secrets.properties`, then replace its placeholders. The real file is ignored by Git and is required by the default `local` Spring profile, so `mvn spring-boot:run` works in a fresh terminal after that one-time setup. If it is missing, Spring Boot stops immediately and identifies the missing configuration file instead of failing later while creating JWT security beans.

Generate a strong JWT secret (at least 32 bytes) and set institution-controlled bootstrap credentials only when an initial account is needed. Bootstrap accounts are created only when their email does not already exist, and passwords must contain at least 12 characters. Do not commit real credentials.

`PROFILE_ENCRYPTION_KEY` must be a stable Base64-encoded 32-byte AES key. If PostgreSQL already contains encrypted Aadhaar values, use the same existing key; changing it makes those values unreadable.

Production must set `SPRING_PROFILES_ACTIVE=prod` and supply all secrets through its deployment environment or secret store. It never reads the local secrets file.

Create an empty PostgreSQL database named `internship_management` owned by the configured application user. Flyway applies the schema automatically.

Set `DOCUMENT_STORAGE_ROOT` to a private, writable backend directory. Required internship documents and resumes are stored there under server-generated UUID names, never in frontend public content. Internship documents accept PDF, JPEG, and PNG up to 10 MB each. Resumes accept only PDF and DOCX up to the separately configurable `RESUME_MAX_FILE_SIZE_BYTES` limit (5 MB by default).

## Run

One-time local AI setup from `ai-service/` downloads the pinned local model into the ignored `models/` directory:

```bash
npm install
npm run model:setup
```

Start the internal AI service before requesting resume or opportunity analysis:

```bash
npm start
```

It binds to `127.0.0.1:8091` by default. Spring Boot reaches it through `RESUME_AI_BASE_URL` and `OPPORTUNITY_AI_BASE_URL`; the browser never calls it directly. Resume and job-description content is processed locally and is not sent to an external AI API.

From `backend/`, after the one-time local secrets setup:

```bash
mvn spring-boot:run
```

From `frontend/`:

```bash
npm install
npm run dev
```

Open `http://localhost:3000`. There is one login form and no public registration. Authenticated users are redirected by the role returned by the backend:

- `ADMIN` → `/admin/dashboard`
- `MENTOR` → `/mentor/dashboard` after the required first-login password change
- `STUDENT` → `/student/dashboard`

## Authentication API

- `POST /api/v1/auth/login` — validates credentials and returns safe user data while setting the JWT in an HttpOnly cookie
- `GET /api/v1/auth/me` — returns the authenticated user
- `POST /api/v1/auth/logout` — expires the authentication cookie
- `POST /api/v1/auth/change-password` — replaces the temporary password for a Student or Mentor account on first login
- `GET /api/v1/admin/verification` — minimal ADMIN-only endpoint used to verify 403 behavior

### Mentor account API

- `POST /api/v1/admin/mentor-accounts` — creates a Mentor account using its employee ID as the one-time initial credential
- `GET /api/v1/admin/mentor-accounts` — lists Mentor accounts for the III Cell
- `GET /api/v1/mentor/profile` — retrieves only the authenticated Mentor's profile after the required password change
- `GET /api/v1/admin/mentors/{mentorUserId}/students` — returns safe assignment-management data and current totals
- `POST /api/v1/admin/mentors/{mentorUserId}/students` — assigns or transactionally reassigns one or more Students
- `DELETE /api/v1/admin/mentors/{mentorUserId}/students/{studentUserId}` — ends an active assignment without deleting its history
- `GET /api/v1/mentor/students` — returns only the authenticated Mentor's currently assigned Students

The temporary employee-ID credential is BCrypt-hashed before persistence and is never returned as a password or hash. A new Mentor must replace it through the existing password-change flow before accessing Mentor routes. Assignments retain history when removed or reassigned, and mentor-facing data derives ownership from the authenticated principal. Basic attendance is intentionally unverified until later evidence phases are implemented.

### Attendance API

- `POST /api/v1/student/attendance/today` — records the authenticated Student as `PRESENT` and `UNVERIFIED` using the configured server clock
- `POST /api/v1/student/attendance/today/physical` — accepts a fresh camera capture and optional live coordinates/accuracy; the backend calculates OFFICE_REPORTING geofence verification
- `POST /api/v1/student/attendance/today/remote` — accepts a fresh camera capture plus a required 20–1000 character daily work summary; no GPS/geofence is required
- `POST /api/v1/student/attendance/verification-session` — issues a three-minute, student/internship/date-bound one-time liveness challenge
- `GET /api/v1/student/attendance/today/policy` — returns the server-selected verification policy and expected work mode for today
- `GET /api/v1/student/attendance/today` — returns only the authenticated Student's state for the server's current date
- `GET /api/v1/student/attendance` — returns only the authenticated Student's attendance history
- `GET /api/v1/mentor/attendance/today` — returns today's state only for the authenticated Mentor's currently assigned Students
- `GET /api/v1/student/internship/hybrid-schedule` — lets a hybrid Student view the institution-controlled weekly schedule
- `GET /api/v1/mentor/students/{studentId}/hybrid-schedule` — lets a Mentor view the schedule of an assigned hybrid Student
- `GET/PUT /api/v1/admin/students/{studentId}/internship/hybrid-schedule` — lets the III Cell view or configure the normalized weekly Office/Remote schedule
- `GET/PUT /api/v1/student/internship/location` — lets a Student view or propose their own internship coordinates; proposals remain `PENDING`
- `GET/PUT /api/v1/admin/students/{studentId}/internship/location` — lets the III Cell review or update expected coordinates and radius
- `POST /api/v1/admin/students/{studentId}/internship/location/confirm` — confirms the trusted location; only ADMIN can call it

Attendance requires completed internship details and a server date inside the registered internship period. One record per Student, internship, and date is enforced in both the service and database. OFFICE_REPORTING and hybrid Office days combine local Edge-AI evidence with the physical camera/GPS/geofence policy. ONLINE and hybrid Remote days combine local Edge-AI evidence with a fresh camera capture and a 20–1000 character daily work summary without requesting GPS. MediaPipe Face Landmarker runs locally in the browser to check one-face presence, deterministic positioning quality, and a temporal blink or head-turn challenge. The server issues and consumes the short-lived challenge and remains authoritative for final status. This is participation/liveness evidence, not identity recognition; browser evidence is not cryptographically tamper-proof. No face embeddings, raw video, or raw camera images are stored in PostgreSQL. Missing or insufficient evidence remains `PRESENT + UNVERIFIED`, never `ABSENT`, and no Mentor approval is involved. Geofence, image, metadata, and AI-session tolerances are configured through the `ATTENDANCE_*` environment properties documented by their defaults in `application.yml`.

### Student profile API

- `GET /api/v1/student/profile` — retrieves only the authenticated student's profile
- `POST /api/v1/student/profile` — creates the authenticated student's first profile
- `PUT /api/v1/student/profile` — updates the authenticated student's profile
- `GET /api/v1/student/profile/options` — returns centrally defined branches and controlled field options
- `GET /api/v1/admin/students` — paginated ADMIN-only directory with server-side search and filters
- `GET /api/v1/admin/students/{userId}` — ADMIN-only institutional student detail

Student-self endpoints never accept a user ID. Aadhaar is encrypted at rest and returned only as `XXXX XXXX 1234`; it is absent from directory summaries.

### Professional profile and resume API

- `GET/PUT /api/v1/student/professional-profile` — views or updates the authenticated Student's headline and career interests
- `POST /api/v1/student/resume` — uploads or atomically replaces the authenticated Student's active PDF/DOCX resume
- `GET /api/v1/student/resume` — returns safe active-resume metadata
- `GET /api/v1/student/resume/download` — authenticated owner-only resume download
- `GET /api/v1/admin/students/{studentId}/professional-profile` — ADMIN-only professional profile view
- `GET /api/v1/admin/students/{studentId}/resume/download` — ADMIN-only, student-scoped resume download
- `GET/POST /api/v1/student/resume/analysis` — views or explicitly runs analysis for the authenticated Student's active resume
- `GET/POST /api/v1/student/professional-profile/skills` — lists the reviewed skill profile or adds a manual skill
- `PATCH/DELETE /api/v1/student/professional-profile/skills/{skillId}` — edits or deactivates an owned active skill
- `POST /api/v1/student/professional-profile/skill-suggestions/{suggestionId}/confirm` — confirms one current-resume AI suggestion
- `POST /api/v1/student/professional-profile/skill-suggestions/{suggestionId}/reject` — rejects one current-resume AI suggestion
- `POST /api/v1/student/professional-profile/skill-suggestions/confirm-all` — atomically confirms all pending suggestions for the current analysis

Resume binaries remain in private file storage; PostgreSQL stores only metadata and server-generated storage keys. Each professional profile has exactly one active resume at most. A successful re-upload creates a new `NOT_PARSED` resume version and marks the previous version inactive, while a failed upload leaves the current version untouched. Analysis is stored against the exact resume version. AI skills remain suggestions until the student confirms them; confirmed and manually added skills are normalized records with provenance and survive resume re-upload. Raw analysis is never rewritten by skill review. Opportunity matching is not implemented.

### Opportunity management API

- `POST /api/v1/admin/opportunities` — creates an ADMIN-owned draft without accepting client-controlled lifecycle state
- `GET /api/v1/admin/opportunities` — lists draft, published, and closed opportunities for management
- `GET /api/v1/admin/opportunities/{id}` — returns normalized opportunity detail
- `PUT /api/v1/admin/opportunities/{id}` — updates a draft or applies safe corrections to a published opportunity
- `POST /api/v1/admin/opportunities/{id}/publish` — validates and publishes a draft using a server timestamp
- `POST /api/v1/admin/opportunities/{id}/close` — preserves and closes a published opportunity using a server timestamp

Eligible branches, semesters, and required/preferred skills are normalized relational data. Lifecycle transitions are backend controlled.

### Opportunity AI analysis API

- `GET/POST /api/v1/admin/opportunities/{id}/ai-analysis` — views or explicitly runs local analysis for the current title and job description
- `POST /api/v1/admin/opportunities/{id}/ai-analysis/suggestions/{suggestionId}/confirm` — adopts one current suggestion into authoritative opportunity skills
- `POST /api/v1/admin/opportunities/{id}/ai-analysis/suggestions/{suggestionId}/reject` — rejects one current suggestion without deleting its provenance
- `POST /api/v1/admin/opportunities/{id}/ai-analysis/suggestions/confirm-all` — atomically confirms all pending current suggestions

Analysis history is stored separately from authoritative opportunity data. A SHA-256 title/JD fingerprint makes previous results stale after an edit, manual skills suppress duplicate suggestions, and CLOSED opportunities cannot be analyzed. Only ADMIN may run or review analysis. No student matching, scoring, ranking, or recommendation logic exists in Phase 4.6.

### Student opportunity feed API

- `GET /api/v1/student/opportunities` — paginated active feed with case-insensitive company/title search, mode filtering, and `NEWEST` or `DEADLINE_SOON` sorting
- `GET /api/v1/student/opportunities/{id}` — structured active opportunity detail without creator or audit identifiers

Both queries select only `PUBLISHED` opportunities whose deadline is absent or has not passed according to the server date. Draft, closed, and expired records are unavailable even when a Student guesses their ID. Phase 4.5 does not perform eligibility decisions, skill comparison, AI ranking, recommendations, or applications.

### Internship onboarding API

- `GET /api/v1/student/internship-onboarding` — authenticated student's onboarding status and four-document checklist
- `POST /api/v1/student/internship-onboarding/secure` — starts onboarding after profile completion
- `PUT /api/v1/student/internship-onboarding/source` — selects `DEPARTMENT`, `CDC`, or `SELF`
- `POST /api/v1/student/internship-onboarding/documents/{documentType}` — uploads or replaces one required document
- `GET /api/v1/student/internship-onboarding/documents/{documentId}/download` — owner-checked document download
- `POST /api/v1/student/internship-onboarding/diary/confirm` — confirms diary receipt after all four documents exist
- `GET /api/v1/admin/students/{studentId}/internship-onboarding/documents/{documentId}/download` — ADMIN-only, student-scoped download

The backend owns all onboarding workflow transitions; uploaded document metadata and internship details remain in separate normalized domains.

### Internship details API

- `GET /api/v1/student/internship` — retrieves the authenticated student's internship information
- `POST /api/v1/student/internship` — creates details only after the diary is issued
- `PUT /api/v1/student/internship` — updates the authenticated student's existing details
- `GET /api/v1/admin/students/{studentId}/internship` — read-only ADMIN view

Valid detail creation advances onboarding from `DIARY_ISSUED` to `INTERNSHIP_DETAILS_COMPLETE`. Company type and internship mode are controlled values; stipend uses an exact decimal representation and may be zero for an unpaid internship. Attendance, weekly reporting, evaluation, and internship activation remain outside the current scope.

The JWT contains only the user ID, role, issue time, and expiry. CORS origins, cookie security, JWT expiry, secret, database access, and bootstrap accounts are environment-configurable.

## Verification

### Dashboard report exports

The III Cell and Mentor dashboards have an **Export Report** button. Choose student filters, select individual columns or whole categories, review the matching student count, and download Excel (`.xlsx`) or UTF-8 CSV (`.csv`). Select All and Deselect All are available. Excel includes a report-details sheet, typed dates/numbers, readable widths, a frozen header, and column filters. Both formats use exactly the selected columns, in the same order, with one student per row and readable multiline summaries for related records.

Report APIs under both `/api/v1/admin/dashboard/reports` and `/api/v1/mentor/dashboard/reports`:

- `GET /options` returns the role-specific field/filter catalog and attendance reference date.
- `POST /preview` validates the configuration and returns the authorized matching student count plus the first 20 student identities.
- `POST /export` accepts the same configuration and returns a private downloadable file. The JSON body contains `fields` (an ordered list of catalog IDs), `filters` (catalog filter IDs and string values), and `format` (`XLSX` or `CSV`).

Admin scope matches the existing student directory, including active and inactive accounts; use the account-active filter to reproduce active-only dashboard statistics. Mentor scope includes only current active assignments. Mentor fields follow existing mentor APIs: basic/academic/professional profiles, current mentor details, onboarding/document status, company name/mode, today's attendance, hybrid schedules, and monthly reports. Private profile details, resume/AI analysis, detailed internship data, and attendance history/evidence are admin-only. Full Aadhaar is an optional admin-only column, reusing the existing authorized detail/decryption capability; it is not selected by default. Credentials, encrypted values, storage keys, camera images, and document binaries are never exported.

Attendance and verification filters apply to the reference date (today by default; mentors are limited to today). Admin history dates limit attendance history/counts and monthly reports by attendance date or report-period start, without excluding students lacking history. Missing attendance is `NOT_SUBMITTED`, not `ABSENT`. Student branch and academic year exist; student department, CGPA/SGPA, projects, and opportunity applications do not, so no such columns or filters are invented. Matching scores are computed on demand and are not stored student records; report generation does not invoke AI inference.

Exports page through students and batch-fetch selected relationships. Excel is written with the existing Apache POI streaming API; CSV is written incrementally with escaping, a UTF-8 BOM, and spreadsheet-formula protection. Temporary exports are removed after download or generation failure. Excel cells are limited to 32,767 characters: select a narrower history range or CSV if a related-history cell exceeds that limit. CSV contains only headers/data so it remains a regular table; report metadata is included in Excel.

Export integration coverage: `mvn -Dtest=DashboardReportIntegrationTest test`.

Backend integration tests use an isolated H2 database in PostgreSQL compatibility mode while exercising the complete Spring Security filter chain:

```bash
mvn test
```

Frontend validation:

```bash
npm run typecheck
npm run build
```

AI-service validation after local model setup:

```bash
npm run typecheck
npm test
npm run test:real
```

The production build uses Webpack because it is reliable in restricted build environments where Turbopack cannot create its internal worker connection.

### Render Maven download failures (HTTP 429)

An HTTP 429 while resolving the Spring Boot parent is a Maven Central download
rate limit, before compilation or database migrations. It does not indicate a
missing Spring Boot version or require changing Flyway history.

The backend Docker build preserves its dependency layer cache, limits artifact
download concurrency to two, and uses Maven 3.9.16's native Resolver retries:
at most two retries per HTTP request, with 30-second then 60-second waits when
the server supplies no Retry-After. Server Retry-After is respected up to
120 seconds; longer requested waits fail rather than stall the build.
Both dependency resolution and packaging use these settings. There is no
unbounded build retry loop, alternate public mirror, or disabled TLS validation.
[Resolver 1.9.27 configuration](https://maven.apache.org/resolver-archives/resolver-1.9.27/configuration.html).

For this failure, wait before using Render's **Deploy latest commit** with the
normal build cache. Repeated cache-cleared deployments discard reusable
dependencies and can increase download traffic. If 429 persists, ask Render to
investigate its outbound build-network rate limiting, or use an approved caching
repository manager. These build settings cannot remove an upstream block.
[Maven Central rate-limit guidance](https://central.sonatype.org/faq/429-error/),
[Render Docker build caching](https://render.com/docs/docker).

Local validation: four disposable HTTP-repository checks reproduced an
unretried 429, successful recovery, the retry cap, and rejection of excessive
Retry-After. All passed. Backend `mvn package -DskipTests` with the same Maven
options exited 0. Docker is unavailable locally; Render execution remains
unverified. No application code or database migration changed in this fix.
