# Student notifications

The student navigation now includes a bell, unread badge and Notifications page.
Notifications persist in the database, sort newest first, and support individual
read actions, mark-all-read and pagination. The menu and page refresh on focus
and every 30 seconds; read actions refresh the badge immediately.

Report rejection already requires a reason. After saving the review and audit
event, the existing review transaction writes a notification using the saved
reason, rejection timestamp, reporting month and faculty mentor name (email
fallback). If the transaction fails, neither the rejection nor notification
commits. The event key includes the report revision, so a subsequent rejection
after correction has its own notification. Report links target the relevant
report in the existing monthly progress page. Existing review history is retained.

## New opportunity publication

Publishing a valid draft now adds an `OPPORTUNITY_PUBLISHED` entry to the same
persistent student inbox. It contains the opportunity title, company, a short
description, the publication timestamp, and a link to its student detail page.
The publication and all notification writes share one transaction, so a failed
publication leaves no alerts. The existing database uniqueness constraint and a
stable opportunity event key prevent duplicates on retries, edits, or restarts.

Recipients follow the current feed visibility: active students can see published,
unexpired opportunities. Branch, semester, and CGPA criteria remain matching
information and do not change access permissions. Drafts, closed opportunities,
and expired opportunities do not generate new-publication alerts. No additional
table, migration, email service, or historical backfill is introduced.

The inbox labels publication dates and includes a **View opportunity** action.
The existing owner-scoped read controls, unread badge, and 30-second/focus refresh
remain in use. The shared navbar is sticky across all three roles, with a compact
mobile menu and reduced-motion-aware styling. Restart a running backend to load
the new publication hook.

## Deadlines

The backend scheduler runs 10 seconds after startup and every 15 minutes.
Reminder intervals are 4–7 days before, 1–3 days before, due today and one day
overdue, using the configured attendance clock/time zone. The database uniquely
identifies each student/event/deadline/interval; retries and concurrent jobs cannot
create duplicates. There is no email, SMS or browser push integration.

Published opportunity deadlines come from `opportunities.application_deadline`.
Recipients match the existing student feed's scope: active student accounts.
Expired opportunity reminders link to the opportunity list because the current
feed does not expose expired detail pages. Draft/closed opportunities and entries
without deadlines generate no reminders.

The monthly report model has reporting periods but **no submission deadline**.
Period-end dates are not treated as deadlines. Configure actual institution-set
deadlines through existing external Spring YAML configuration:

```yaml
app:
  notifications:
    monthly-report-deadlines:
      - internship-id: <registered-internship-id>
        month-number: <reporting-month-number>
        due-date: <actual-deadline-in-YYYY-MM-DD-format>
```

Replace the placeholders with actual configured values. The default list is
empty; no monthly reminders are invented. Only the registered active student is
notified, only after the reporting month begins, and only if the report is
missing or rejected. Invalid/out-of-period month references are skipped. Existing
submission eligibility and late-correction rules are unchanged.
Set `app.notifications.reminders.enabled=false` to disable the scheduler.

## APIs and migration

All endpoints use the authenticated student; they accept no recipient ID:

- `GET /api/v1/student/notifications?page=0`
- `GET /api/v1/student/notifications/unread-count`
- `PATCH /api/v1/student/notifications/{id}/read`
- `PATCH /api/v1/student/notifications/read-all`

Reads and updates are scoped by student ID on the backend. Foreign notification
IDs return 404. Existing role security blocks mentor/admin access to student APIs.
Marking a notification read again preserves its original read time.

Flyway V33 adds only `student_notifications` and its indexes. Existing reports,
attendance and review audits are unchanged. Notifications survive restarts and
report corrections/deletions. Deleting a user account cascades its notifications.
Restart the matching backend to apply V33 through existing Flyway startup.
This task applied the migration only to isolated H2 test databases.

Changed areas: new backend notification service/controller, deadline scheduler
and configuration properties, V33, monthly review integration, student navigation,
new notification page/menu/types, monthly report anchors, focused tests and this
document.

## Original notification checks

- `NotificationIntegrationTest`: 4 passed.
- Only `MonthlyProgressIntegrationTest#rejectionRequiresReasonAndResubmissionRetainsAllRevisionsAndEvidence`: 1 passed.
- `node --test tests/notifications.test.cjs`: 3 passed.
- `npm.cmd run typecheck`: passed.
- `git diff --check`: passed.

No full suite, PostgreSQL suite, application-database migration, commit, push or
deployment was performed. Browser interaction was not exercised.

## Publication and shared UI update checks

- Focused notification UI tests: 6 passed.
- Frontend typecheck: passed.
- Role navigation, mobile menu controls, and sign-out render checks: passed.
- Final code review and diff whitespace check: passed.
- Backend Maven reached test compilation, then failed with a compiler error
  referencing the cached `accessors-smart-2.5.2.jar`. The three targeted backend
  test classes were not executed; production compilation completed.
- Browser verification was unavailable after two browser-tool timeouts.

No application-database changes, full test suite, commit, push, or deployment
was performed for this update.
