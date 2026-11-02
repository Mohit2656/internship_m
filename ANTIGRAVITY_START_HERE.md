# Antigravity: Start Here

## Project in one minute

IMS is a Next.js 16 / React 19 frontend with a Java 17 / Spring Boot 3.4.5 backend, PostgreSQL/Flyway persistence, and an optional local Node AI service for resume/JD analysis. Roles are Student, Mentor, and III Cell Incharge (`ADMIN`). Read [IMS_PROJECT_HANDOVER.md](./IMS_PROJECT_HANDOVER.md) before changing code.

## Current state

- Branch `tanu`, HEAD `71d644f`; working tree has important uncommitted attendance and role-sidebar work.
- Do **not** reset, clean, stash, mass-stage, commit, push, migrate production, or deploy without explicit review/authorization.
- Flyway V49 (mentor attendance acknowledgement) is uncommitted. H2 migration/startup and focused integration tests pass; disposable PostgreSQL verification remains required.
- Frontend focused tests (17/17), TypeScript, and production build passed on 2026-11-02.
- Backend compile passed. In one combined focused run, 37/38 tests passed; the lone log-capture assertion passed when rerun independently.

## Implemented attendance behavior

- Missing attendance after cutoff → automatic `ABSENT` / `AUTO_ABSENT`.
- Valid submitted evidence → automatic `PRESENT`.
- Submitted but failed/reviewable GPS/liveness/security evidence → `PENDING_MENTOR_REVIEW`.
- Only the active assigned mentor can decide pending records.
- Finalized records have a separate idempotent **Mark as Viewed** action; it stores mentor/time and never changes status.
- `/mentor/attendance` has a five-column UID-sorted assigned-student summary and separate detailed records/review section.

## First files to inspect

```text
backend/src/main/java/edu/institution/ims/attendance/AttendanceRecord.java
backend/src/main/java/edu/institution/ims/attendance/AttendanceReviewService.java
backend/src/main/java/edu/institution/ims/attendance/PhysicalAttendanceService.java
backend/src/main/java/edu/institution/ims/attendance/AttendanceAbsenceService.java
backend/src/main/resources/db/migration/V43__mentor_only_workplace_approval.sql
backend/src/main/resources/db/migration/V44__pending_attendance_mentor_review.sql
backend/src/main/resources/db/migration/V46__dual_internship_approval.sql
backend/src/main/resources/db/migration/V49__mentor_attendance_acknowledgement.sql
frontend/components/AttendanceAbsenceOverview.tsx
frontend/components/AttendanceReviewPanel.tsx
frontend/components/TodayAttendanceCard.tsx
frontend/components/AppFrame.tsx
frontend/components/{StudentSidebar,MentorSidebar,AdminSidebar}.tsx
```

## Local setup

```bash
cd frontend && npm ci
cd ../ai-service && npm ci
cd ../backend && mvn dependency:go-offline

# Supply private backend/application-local-secrets.properties and a local PostgreSQL DB.
cd ai-service && npm run model:setup && npm run dev
cd ../backend && mvn spring-boot:run
cd ../frontend && npm run dev
```

Never copy secret values into source, documentation, logs, or chat. Required configuration names are listed in the full handover.

## Verify before editing

```bash
git status --short
git diff --stat
git diff --check

cd backend
mvn -DskipTests compile
mvn -Dtest=AttendanceReviewIntegrationTest,AttendanceAbsenceIntegrationTest test

cd ../frontend
node --test tests/*.test.cjs
npm run typecheck
npm run build
```

## Priority tasks

1. Preserve the current dirty tree on a recoverable branch/commit after checking for secrets/generated files.
2. Validate V43–V49, especially V46/V49, against a disposable PostgreSQL restore; never production.
3. Run full suites and document any order-sensitive/stale tests.
4. Prove Internship 1/2 isolation across workplace, attendance, reports, reviews, completion, and exports.
5. Audit/remove only genuinely dead Photon/Leaflet-era workplace code; retain manual coordinates and mentor verification.
6. Confirm real deployment topology, persistent document storage, proxy limits, backups, CORS, and secure cookies.

## Non-negotiable invariants

- Backend authorization and active mentor assignment are authoritative.
- UID is never replaced with email.
- 200 m workplace radius and GPS accuracy are distinct configured policies.
- Submitted failed evidence remains pending; only no-submission becomes automatic absence.
- Mentor Viewed is not approval and cannot affect attendance totals.
- Mentor-approved workplace coordinates stay locked; admin corrections are audited and require reapproval.
- Internship 1/2 data must never mix.
- Never edit applied migrations or destroy audit/history/evidence.

For feature-by-feature paths, endpoints, schema, migrations, test evidence, known inconsistencies, and release acceptance criteria, continue with [IMS_PROJECT_HANDOVER.md](./IMS_PROJECT_HANDOVER.md).
