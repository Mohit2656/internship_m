# IIIC mentor account deletion

The existing Mentor Accounts page offers **Delete** for active faculty mentors.
The confirmation identifies the mentor by name/email and explains that deletion
deactivates login while retaining the account, current student assignments, and
historical records. Completed operations keep the row visible as **Deactivated**.

## API and authorization

`DELETE /api/v1/admin/mentor-accounts/{userId}` returns the existing mentor account
response with `active: false`. The ID is the user ID, not the mentor profile ID.
The route and service require an active authenticated ADMIN/IIIC Coordinator.
The service also verifies the administrator's current account/token version.
Self-deletion, administrative/student targets, and missing mentor profiles are
rejected. Repeating a successful deletion is safe.

Deactivation uses existing `users.active` and `token_version` fields. It blocks
new login and invalidates existing authentication cookies through the existing
JWT validation. There is no new migration or physical account deletion path.

## Retention and student assignments

Existing retention guidance requires an institutional policy before permanent
erasure. This feature retains user/profile IDs, email/employee ID uniqueness,
active and historical assignments, attendance, monthly reports, evaluations,
approval/review events, workplace decisions, and audit attribution. No cascading
delete, record reassignment, or file removal occurs.

Administrators can use **Manage Students** on a deactivated mentor to inspect or
remove current assignments. To transfer students, explicitly assign them from
an active mentor's existing assignment manager. New assignments to deactivated
mentors are rejected in the UI and backend. Assignment creation and deactivation
lock the same mentor row so a competing request cannot add students after the
account has been deactivated. Existing assignment-history behavior is preserved.

No reactivation endpoint or retention/purge policy is introduced.

## Focused checks performed

- `MentorAccountDeletionIntegrationTest`: 5 passed against isolated H2 data.
  Covers anonymous/student/mentor/HOD rejection, active administrator checks,
  protected targets, idempotence, revoked login/sessions, unchanged historical
  rows, and explicit assignment removal/reassignment.
- `node --test tests/mentor-account-delete.test.cjs`: 7 passed.
  Covers confirmation/cancel, pending guards, safe errors, retained account state,
  and inactive mentor assignment controls.
- Frontend typecheck, local Mentor Accounts route compilation, and diff check
  passed. No full test suite or live-account deletion was performed.

The backend check initially encountered sandbox access denial on an existing
cached Maven JAR. Running the same offline, targeted check with the required file
access succeeded; no dependencies or cache files were repaired.

Changed areas: mentor account controller/service, user deactivation, assignment
service, Mentor Accounts page, deletion confirmation component, assignment manager,
the two focused test files, and this guide. Nullable semester handling was fixed
in the assignment service so incomplete student accounts remain manageable.
