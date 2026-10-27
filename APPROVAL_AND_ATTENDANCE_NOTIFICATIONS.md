# Approval and attendance notifications

Previously, workplace reviews saved their decisions without creating student approval events, and attendance submission had no mentor inbox event. The review button also used the same appearance for pending and fully approved workplaces. The changes connect notifications to those existing successful transactions and style the saved approved state.

## Behavior

- The workplace review button reads **Approved** and stays white in light and dark themes once the saved workplace status is `APPROVED`. The existing requirement for both the assigned mentor and IIIC coordinator to approve the current revision is unchanged.
- Students receive separate **Faculty mentor approval** and **IIIC coordinator approval** notifications. Each opens `/student/internship/details` and can be marked read independently. The first approval explains that the other approval is still required. Completion messages use the same current-mentor eligibility check as the workplace API.
- Approval events use the existing `student_notifications` table. A unique event key includes the workplace, revision, reviewer role and reviewer ID. Repeated saves do not create duplicate notifications; a changed workplace revision or a replacement reviewer can generate a fresh notice. Existing notifications retain the revision that was reviewed.
- A successful student attendance submission creates one notification for the currently assigned active mentor, naming the student and attendance date. BASIC, physical, remote and authorized late submissions use the same persistence path. Automatic absences and rejected/failed submissions do not create alerts.
- Mentors have a notification bell and `/mentor/notifications` inbox with unread counts, individual read actions, mark-all-read and a link to attendance history. Notifications refresh on focus and every 30 seconds. The student inbox keeps its existing opportunity, report feedback and reminder features.

## Persistence and access

`V35__mentor_attendance_notifications.sql` adds a mentor inbox with foreign keys and a unique attendance ID. It does not alter saved attendance evidence, workplace coordinates, approval rules or historical verification results. No historical notifications are fabricated or backfilled. Student approval alerts need no additional migration.

Both notification paths participate in the transaction that saves the approved workplace or submitted attendance. A subsequent evidence, audit or notification failure rolls back the event and its source change together.

Mentor APIs are scoped to the authenticated active mentor and their current assignments. Reassignment and soft deletion of an attendance record remove the corresponding mentor notification from listing, counts and read actions. Endpoints:

- `GET /api/v1/mentor/notifications?page=0`
- `GET /api/v1/mentor/notifications/unread-count`
- `PATCH /api/v1/mentor/notifications/{id}/read`
- `PATCH /api/v1/mentor/notifications/read-all`

Deploy the backend with its normal Flyway migration before the frontend. Notifications are in-app; this change adds no email, SMS or external push service.

## Files

Backend: `AttendanceSubmissionService`, `InternshipLocationService`, `NotificationService`, the new `MentorNotificationService`/`MentorNotificationController`, V35 and integration tests.

Frontend: `InternshipLocationManager`, the approved-button CSS in `globals.css`, `AppFrame`, shared notification components/types, the mentor notification route and regression tests. The related student workplace refresh fix is documented in `WORKPLACE_APPROVAL_SYNC_FIX.md`.

## Verification results

- Frontend: `node --require ./tests/register-tsx.cjs --test tests/*.test.cjs` ? **203 passed**, 0 failed or skipped.
- Frontend: `npm.cmd run typecheck` and `npm.cmd run build` ? passed. The production build includes `/mentor/notifications`.
- Backend H2 integration tests: **72 passed**, 0 failed or skipped: workplace approval (27), mentor notifications (11), existing student notifications (7), physical attendance (15), online/hybrid attendance (6) and late attendance permission (6). The workplace suite was re-run after the reassignment-message correction and passed all 27 tests again.
- PostgreSQL: **38 passed**, 0 failed or skipped: workplace approval (27) and mentor notifications (11). Tests ran in a new localhost schema using the guarded PostgreSQL runner; schema cleanup passed.
- Backend: offline Maven `-DskipTests package` — exit code **0**.
- Coverage includes separate approval notices and read states, retries, new revisions, assignment changes, failed approval/audit/notification writes, attendance evidence rollback, each submission mode, automatic absence replacement, cross-user access, deactivated mentors, pagination, polling and notification navigation.

Tests use test accounts and isolated databases/schemas. No attendance or approval events were submitted against existing local student accounts for testing.

The updated local backend is running with health `UP`. Normal Flyway startup applied V35; a read-only PostgreSQL check confirmed the successful migration, mentor inbox table and unique attendance constraint. Both notification pages return HTTP 200. Unauthenticated requests to the mentor notification endpoint, including through the frontend proxy, and the student unread-count endpoint return HTTP 401 as expected. Authenticated submission, approval and inbox behavior was verified in the integration tests rather than by modifying existing local student records.
