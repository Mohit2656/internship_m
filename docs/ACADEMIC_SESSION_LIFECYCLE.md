# Academic session closure and deletion

The existing IIIC Settings → Academic Sessions page supports creation, activation,
closure, explicit reopening and activation, dependency review, and permanent
deletion of empty inactive or closed sessions. Historical student memberships are
unchanged by all session operations. No student, internship, file, attendance,
report or other academic record is cascade-deleted.

The existing singleton `academic_session_settings.active_session_id` remains the
only source of ACTIVE state. Closing clears that pointer when applicable and
records `closed_at` (date and timestamp) and `closed_by`. Otherwise a session is
CLOSED when closure metadata exists, or INACTIVE when it has not been closed or
activated. Existing historical sessions keep their original dates and state;
the migration does not invent historical closure metadata. Archiving is not a
separate lifecycle operation.

Activation closes the previous active session atomically. An ordinary activation
or historical date update cannot reactivate a closed session. The separate,
confirmed reopening action clears closure metadata, records a REOPENED event,
and activates that session; any current active session is closed. Earlier
closures remain in audit history.

Session mutations and student import/bootstrap assignment share the existing
singleton transaction lock. Deletion also locks the target session row, checks
membership including deactivated students and historical completion snapshots,
and relies on restrictive foreign keys as the final concurrent-dependency guard.
All other current academic records belong through student membership; any such
membership blocks deletion. The dependency review additionally counts internships,
attendance including soft-deleted rows, weekly/monthly reports, project evaluations
and completion events. A dependency discovered after preview is rechecked before
deletion. Failed audit writes roll back lifecycle changes.

## API

All routes require ADMIN authorization under `/api/v1/admin/academic-sessions`:

- `GET /`, `POST /`, `POST /{id}/activate`, and `PUT /{id}/dates` retain their
  existing interfaces, with lifecycle metadata added to session responses.
- `POST /{id}/close` closes an active or inactive session.
- `POST /{id}/reopen` explicitly reopens and activates a closed, dated session.
- `GET /{id}/deletion-preview` returns associated student/core record counts,
  eligibility, and the reason deletion is blocked or permitted.
- `DELETE /{id}` permanently deletes an empty nonactive session; success is 204.
- `GET /{id}/history` returns creation, activation, closure, reopening, date setup
  and deletion events. History remains readable after deletion by its original ID.

Close, reopen and delete require JSON `{ "confirmed": true, "sessionName": "..." }`
with the exact persisted name. The UI requires typing the name for permanent
deletion and disables the final deletion action when dependencies exist.

Flyway migration **V42** adds only closure metadata, audit storage, and indexes.
Audit entries store the original session ID and name without a session foreign
key so that deleting an empty session never deletes its audit history. Known
creation and current activation metadata are backfilled without inventing actors.

## Verification (30 October 2026)

- **H2: 36 unique cases passed** across final focused and regression runs:
  session lifecycle 11, dashboard 3, student deactivation 7, internship completion
  13, and student import 2. Three initial test-fixture authentication-context errors
  were corrected; all 11 lifecycle cases then passed in the final rerun.
- **PostgreSQL: 33 passed**, zero failures, errors or skips: lifecycle and
  concurrency 16, V41 → V42 preservation/idempotency 1, and existing completion/
  assignment/export regressions 16. Tests used fresh guarded localhost schemas;
  disposable schema cleanup passed.
- **Focused frontend: 16 passed**, covering confirmations, exact-name deletion,
  dependency blocking, server conflicts, status display and existing completion UI.
- **Full frontend: 251/252 passed** with the existing TS/TSX test loader. The
  failing `student-update.test.cjs:145` directory date-display assertion also fails
  against the pre-change HEAD source (baseline: 8/9 passed). Its implementation
  and test were left unchanged.
- TypeScript `tsc --noEmit` and an isolated production `next build --webpack`
  passed. Isolation kept verification output separate from the active dev server.
- The local backend was restarted from the tested source build. Flyway applied
  V42 successfully; backend health is UP and the frontend login returns HTTP 200.

Detailed logs are in the ignored `.local-run/session-lifecycle-*.log` files.

## Changed files

- `backend/src/main/java/edu/institution/ims/academicsession/AcademicSessionService.java`
- `backend/src/main/java/edu/institution/ims/academicsession/AcademicSessionController.java`
- `backend/src/main/resources/db/migration/V42__academic_session_lifecycle.sql`
- `backend/src/test/java/edu/institution/ims/academicsession/AcademicSessionIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/academicsession/AcademicSessionPostgresIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/academicsession/AcademicSessionMigrationPostgresTest.java`
- `frontend/app/admin/settings/academic-sessions/page.tsx`
- `frontend/components/AcademicSessionDeletionDialog.tsx`
- `frontend/lib/academic-sessions.ts`
- `frontend/tests/academic-sessions.test.cjs`
- `frontend/tests/internship-completion.test.cjs` (mock for the extracted dialog)
- `docs/ACADEMIC_SESSION_LIFECYCLE.md`
