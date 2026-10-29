# Admin-assisted password reset

## Behavior and security

Login links to `/forgot-password`, an informational page directing users to their system administrator. No email provider, SMTP dependency, automated email flow or environment variable was introduced.

Administrators reset active STUDENT accounts from `/admin/students` and active MENTOR accounts from `/admin/mentor-accounts`. ADMIN, HOD and inactive accounts are deliberately ineligible: the existing forced-change routes support STUDENT and MENTOR. The backend enforces eligibility and administrator authorization independently of the UI.

`POST /api/v1/admin/users/{userId}/reset-password` requires ADMIN. Successful response: `{userId, email, temporaryPassword, mustChangePassword}`. The 24-character URL-safe temporary password uses 18 SecureRandom bytes (144 bits). Only its BCrypt hash is stored. The response has Cache-Control: no-store and Pragma: no-cache. DTO string representations redact secrets to avoid default Spring debug logging. There is no credential retrieval endpoint. Unauthorized requests receive 401/403, missing accounts 404, ineligible accounts 409.

The confirmation dialog names the user, prevents duplicate submissions, provides copy functionality and clears the password on close/navigation/unmount. Browser storage and URLs never contain the credential. A network error can occur after the server commits; the error explicitly warns the admin to check before retrying. The OS clipboard can retain a copied password; share securely and clear the clipboard yourself afterward.

`POST /api/v1/auth/change-password` remains authenticated STUDENT/MENTOR only, with `{newPassword, confirmPassword}`. Identity comes exclusively from the verified principal; any supplied userId cannot select a different account. Policy: at least 12 characters, at most 128 characters and 72 UTF-8 bytes (BCrypt limit), matching confirmation, different from the current password. Success remains 204, with a replacement HttpOnly authentication cookie; no password data is returned. Password changes and resets use transactions and a pessimistic user-row lock to serialize concurrent credential updates. Login checks the latest locked hash before issuing a token.

Reset increments token_version and requires a password change. Authentication compares signed JWT claim `ver` against the current database version, rejecting prior tokens. Password change increments the version again, invalidating temporary sessions and other existing sessions while replacing the current browser cookie. Existing tokens without `ver` are treated as version 0 and remain compatible until that user's credentials change. Existing signing and encryption keys are unchanged. Forced users can use login, me, change-password and logout; normal API access is blocked server-side and the existing frontend redirects to the role's change-password page.

Logout retains the existing cookie-clearing behavior; this feature adds account-wide revocation on credential changes, not a new session registry. Already executing requests cannot be recalled after reset. Tests use isolated H2 databases, not the deployed PostgreSQL database; actual browser clipboard/focus behavior and PostgreSQL deployment are manual verification steps.

## Migration

`backend/src/main/resources/db/migration/V23__password_token_version.sql` adds users.token_version BIGINT NOT NULL DEFAULT 0 and a nonnegative check. Existing password hashes and data remain intact. Existing must_change_password from V6 is reused. No historical migration was edited. Do not disable validation or repair a historical checksum without first establishing that the schema and migration history are correct.

## Exact files for this feature

Created:
- backend/src/main/java/edu/institution/ims/auth/AdminPasswordResetController.java
- backend/src/main/java/edu/institution/ims/auth/AdminPasswordResetService.java
- backend/src/main/java/edu/institution/ims/auth/PasswordResetException.java
- backend/src/main/resources/db/migration/V23__password_token_version.sql
- backend/src/test/java/edu/institution/ims/auth/PasswordResetIntegrationTest.java
- frontend/app/forgot-password/page.tsx
- frontend/components/ResetPasswordButton.tsx
- frontend/lib/password-reset.ts
- frontend/tests/password-reset.test.cjs
- ADMIN_PASSWORD_RESET.md

Modified:
- backend/src/main/java/edu/institution/ims/auth/AuthController.java
- backend/src/main/java/edu/institution/ims/auth/AuthService.java
- backend/src/main/java/edu/institution/ims/auth/ChangePasswordRequest.java
- backend/src/main/java/edu/institution/ims/auth/LoginRequest.java
- backend/src/main/java/edu/institution/ims/exception/GlobalExceptionHandler.java
- backend/src/main/java/edu/institution/ims/security/JwtAuthenticationFilter.java
- backend/src/main/java/edu/institution/ims/security/JwtService.java
- backend/src/main/java/edu/institution/ims/security/PasswordChangeRequiredFilter.java
- backend/src/main/java/edu/institution/ims/security/UserPrincipal.java
- backend/src/main/java/edu/institution/ims/user/User.java
- backend/src/main/java/edu/institution/ims/user/UserRepository.java
- frontend/app/admin/mentor-accounts/page.tsx
- frontend/app/admin/students/page.tsx
- frontend/app/login/page.tsx
- frontend/components/ChangePasswordForm.tsx

Pre-existing monthly-progress/EXIF modifications are separate and were preserved.

## Local checks and manual verification (PowerShell)

1. Use your existing local PostgreSQL configuration and credentials. Back up data before applying migrations. Do not recreate the database.
2. Backend:
   ```powershell
   cd 'C:\Users\Mohit Telang\Desktop\Project\internship_m\backend'
   mvn.cmd test
   mvn.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
   ```
3. In another terminal:
   ```powershell
   cd 'C:\Users\Mohit Telang\Desktop\Project\internship_m\frontend'
   node --test tests/*.test.cjs
   npm.cmd run typecheck
   npm.cmd run build
   npm.cmd run dev
   ```
   There is no lint script/configured lint tool in package.json.
4. Visit http://localhost:3000/login, verify Forgot Password and return navigation.
5. Sign in as admin, open Students or Mentor Account Management, cancel a reset first (no request), then explicitly confirm for a disposable local test account. Copy securely and close. Reopen: the old temporary credential must be absent.
6. In a separate browser session, verify that the account's pre-reset session gets 401 and its old password fails. Log in using the generated temporary password; normal API/dashboard access must require a change. Choose a distinct password of at least 12 characters. Verify normal login/access afterward and that the temporary password now fails.
7. Verify a non-admin direct reset request receives 403, an unauthenticated request 401, and a nonexistent ID 404. Verify no plaintext/hash is added to list responses or logs.

## Render/Vercel deployment sequence (not executed)

1. Review the changes and back up the Render PostgreSQL database using your established backup procedure. Resolve any existing Flyway validation mismatch before deployment; never edit deployed migrations.
2. When ready, explicitly commit/push the reviewed version using your normal workflow. These actions were not performed here.
3. Deploy the backend on Render first using its existing build/start commands and unchanged database/JWT/encryption settings. Flyway applies V23 on startup. Confirm healthy startup and migration success. Ensure every serving backend instance runs the version-checking code before enabling resets; an older instance would still accept old tokens.
4. Deploy the frontend on Vercel using the existing frontend project root, npm build command and BACKEND_API_URL proxy configuration. No new environment variables are needed.
5. Use a designated verification account with admin approval, repeat the manual local flow over HTTPS, verify old-session rejection and forced-change enforcement, then set a permanent password. Do not use a production user's account without authorization.
6. Keep database backups and migration history. Do not roll back to backend code that ignores token_version after resets have occurred; that would remove revocation enforcement. No deployment was performed in this task.

## Actual verification results

- New PasswordResetIntegrationTest: 5 passed (including compatibility and revocation of legacy JWTs).
- AuthIntegrationTest: 9 passed.
- MentorAccountIntegrationTest: 3 passed in an isolated JVM.
- MentorAssignmentIntegrationTest: 4 passed in an isolated JVM.
- StudentAccountImportIntegrationTest: 2 passed in an isolated JVM.
- Frontend Node tests: 7 passed (3 password-reset interaction/error tests plus 4 existing GPS-display tests).
- TypeScript typecheck passed; production Next.js webpack build passed.
- git diff --check passed. No lint script is configured.
- Full backend suite executed: 185 tests, 1 failure, 7 errors, 8 skipped. The 7 errors were mentor cleanup failures from shared H2 state; rerunning those suites in separate JVMs passed. OpportunityManagementIntegrationTest.backendControlsPublishAndCloseLifecycleAndTimestamps still fails in isolation because nanosecond expected timestamps differ from microsecond database rounding. Its 5-test suite had 1 failure. These unrelated files were not changed to make the suite green.
- Backend test logs/reports are under backend/target/. No production database was reset or used for tests. No commit, push or deployment was performed.
