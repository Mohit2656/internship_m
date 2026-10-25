# IIIC mentor account deletion

The existing Mentor Accounts page offers **Delete** for active faculty mentors.
The confirmation identifies the mentor by name/email and explains that deletion
deactivates login while retaining the account, current student assignments, and
historical records. Completed operations immediately remove the row from the
mentor list, including after a page refresh. The list API returns active mentors
only; there is no deactivated-account view or Restore button.

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

Student assignments stay unchanged when the mentor is deleted or created again.
Explicit reassignment through an active mentor's existing assignment manager is
still available when an administrator chooses it. New assignments to deactivated
mentors remain prohibited. Assignment creation and deactivation lock the same
mentor row so competing requests cannot add students to a deactivated account.

## Creating a deleted account again

The administrator must complete the normal **Create Mentor** form again. For a
retained inactive mentor, the original name, institute email, and Employee ID
must match. Active accounts, protected roles, missing profiles, and mismatched
identities are rejected instead of being overwritten.

After a valid form submission, the server reuses the retained user/profile IDs
and preserves the original name/email/Employee ID so historical attribution
stays unchanged. Department, designation, and phone use the newly submitted
values. The temporary password is reset to the uppercase Employee ID, password
change is required, and token version is incremented again. Old sessions remain
invalid. No student account, attendance, report, review, audit, or assignment row
is changed during this operation.

There is no separate reactivation endpoint, physical deletion, schema migration,
or new retention/purge policy.

## Focused checks performed

- `MentorAccountDeletionIntegrationTest`: 8 passed against isolated H2 data.
  Covers anonymous/student/mentor/HOD rejection, active administrator checks,
  protected targets, idempotence, revoked login/sessions, unchanged historical
  rows, explicit assignment removal/reassignment, active-only listing, and full-form
  recreation with reset credentials and unchanged student/history rows.
- `MentorAccountIntegrationTest`: 3 passed for existing creation, login, duplicate,
  and role-boundary behavior.
- `node --test tests/mentor-account-delete.test.cjs`: 8 passed.
  Covers confirmation/cancel, pending guards, safe errors, retained account state,
  immediate list removal, full-form recreation, and inactive mentor assignment controls.
- Frontend typecheck, local Mentor Accounts route compilation, and diff check
  passed. No full test suite or live-account deletion was performed.

The backend check initially encountered sandbox access denial on an existing
cached Maven JAR. Running the same offline, targeted check with the required file
access succeeded; no dependencies or cache files were repaired.

Changed areas: mentor account controller/service, user deactivation, assignment
service, Mentor Accounts page, deletion confirmation component, assignment manager,
the two focused test files, and this guide. Nullable semester handling was fixed
in the assignment service so incomplete student accounts remain manageable.
