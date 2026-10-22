# Faculty mentor late attendance permission

The faculty mentor dashboard now has **Late Attendance Permission** switches for
currently assigned students, showing name, enrollment ID, effective date and
status. The student attendance card displays the permission and refreshes it on
browser focus and every 30 seconds. The Admin/IIIC dashboard is unchanged.

Permission applies only to the server's current attendance date in
`app.attendance.zone` (`ATTENDANCE_ZONE`, default `Asia/Kolkata`). It expires at
midnight without a cleanup job. A grant after noon takes effect immediately;
revoking it restores the normal noon cutoff. A changed mentor assignment
invalidates the previous assignment's grant. Repeated identical requests do not
create duplicate permissions or audit events.

Backend entry points:

- `GET /api/v1/mentor/attendance/late-permissions`: assigned students and today's status.
- `PUT /api/v1/mentor/attendance/late-permissions/{studentUserId}` with
  `{"enabled":true}` or `{"enabled":false}`: change today's permission only.
- Existing student attendance and verification-session endpoints enforce the
  permission using the authenticated student and server clock.

Granting permission never changes attendance. An actual submission still follows
the existing basic, physical or remote validation flow, including liveness session
ownership, challenge, expiry and single use. Existing submission coordinates
remain evidence; no GPS verification, workplace geofencing or new location
requirements were added. Existing failed-liveness submissions retain their
unverified outcome.

An eligible late submission can update today's `AUTO_ABSENT` row in place. It
retains its ID and original creation time, stores the original absence timestamp,
and records the actual submission time and authorizer snapshot. Append-only
`attendance_submission_audit` entries preserve both the original absence and
the later submission. Other submissions and past dates cannot be replaced by
permission. Revocation does not change existing attendance or audit records.
Student row locks serialize submissions and permission changes; existing unique
constraints also prevent duplicate daily rows.

Flyway migration
`backend/src/main/resources/db/migration/V32__mentor_late_attendance_permission.sql`
adds permission and permission-audit tables, four attendance metadata columns,
and the submission-audit table. It preserves existing attendance and evidence.
The migration has not been applied to the application database by this change;
the matching backend applies it through its existing Flyway startup process.

Changed areas: mentor dashboard and new `LateAttendancePermissions` component;
`TodayAttendanceCard` and attendance types; mentor permission controller/service
and persistence; shared attendance submission/policy/liveness-session services;
focused backend and frontend tests. Two existing absence unit-test constructors
were updated for the policy service's new dependency.

Validation completed:

- Offline Maven run of `LateAttendancePermissionIntegrationTest` and
  `AttendanceSubmissionWindowTest`: **11 passed**, no failures or skips. These
  compiled the backend and exercised V32 in an isolated H2 in-memory database.
- `npm.cmd run typecheck`: passed.
- `node --require ./tests/register-tsx.cjs --test
  tests/late-attendance-permission.test.cjs tests/attendance-status.test.cjs`:
  **24 passed**.
- `git diff --check`: passed.

The first backend attempts hit sandbox restrictions reading the cached Maven
dependencies. The final offline check ran successfully with access to that cache
after correcting a new test helper's builder return type. No full suite,
PostgreSQL database suite, application database migration, commit, push or
deployment was performed. Browser camera hardware was not exercised.
