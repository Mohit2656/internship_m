# Map registration and dual workplace approval

Current status: workplace registration and review interfaces are temporarily paused, and new daily attendance uses liveness alone. See [LIVENESS_ONLY_ATTENDANCE.md](LIVENESS_ONLY_ATTENDANCE.md). The implementation described below is retained for history and possible restoration; monthly photo EXIF/GPS remains independent.

This document supersedes the single-IIIC-confirmation behavior described in the earlier workplace audit. Existing styling, internship registration, attendance capture, remote/hybrid policy, AI challenges, monthly photo uploads and authentication are retained.

## Registration and configuration

The student uses the existing internship details form to search Google Places for the actual branch. The selected branch opens an interactive map. Clicking the map or dragging its marker changes the coordinates and resolves the new formatted address. Submission cannot use a pending/failed reverse-geocoding result. Coordinates are populated by the map, not typed. The optional Place ID, name, address and validated coordinates are stored in `internship_locations`, uniquely linked to that student's internship. The reference was already per internship; it was not an institution-wide coordinate setting.

Configure `frontend/.env.local` with a restricted browser key and, for production, your Google map ID:

```properties
NEXT_PUBLIC_GOOGLE_MAPS_API_KEY=your_restricted_browser_key
NEXT_PUBLIC_GOOGLE_MAP_ID=your_map_id
```

Enable Maps JavaScript API, Places API (New), and Geocoding API. Restrict the key by application referrers and APIs. Restart local Next.js after configuration changes; production public variables require a rebuild. No key or invented Google result is included. Missing configuration, failed Places lookup, incomplete provider results and failed pin address lookup produce explicit messages.

The implementation uses Google's [current Places widget](https://developers.google.com/maps/documentation/javascript/place-autocomplete-new) and [draggable advanced marker](https://developers.google.com/maps/documentation/javascript/advanced-markers/draggable-markers). SDK behavior is covered with controlled test doubles; live Google Cloud credentials are still needed for an end-to-end provider test.

## Decisions and permissions

V27 adds independent mentor and IIIC decisions, reviewer/timestamp/remarks, optimistic locking, revision numbers, and `workplace_review_audit`. Each decision audit retains the reviewed coordinates, radius, address, optional Place ID, actor, role, time, revision, decision and remarks. Application endpoints do not edit audit history.

The assigned active Faculty Mentor and an active IIIC user independently submit `APPROVE` or `REJECT` through:

* `POST /api/v1/mentor/students/{studentId}/internship/location/review`
* `POST /api/v1/admin/students/{studentId}/internship/location/review`

The application uses `MENTOR`, `ADMIN` (IIIC), and `STUDENT` as its actual role identifiers. Review requests carry the revision displayed to the reviewer. The backend verifies scope, active role, current revision and a registered address. Rejection requires at least five characters of explanation. A stale revision returns 409. Both decisions must be approvals for the effective status to be `APPROVED`; rejection makes it `REJECTED`, otherwise it is `PENDING`.

Changes to coordinates, branch identity/name/address, or the radius increment the revision and clear both decisions. Resubmitting a rejected location, even unchanged, starts a fresh revision. The backend also requires the approving mentor to remain the student's current active assigned mentor. Reassignment or reviewer deactivation removes eligibility without rewriting historical results. The current assigned mentor can review the current reference again.

The existing IIIC and mentor panels show both decisions and their actor IDs, timestamps and remarks. Students can read their own status and rejection reasons in their internship page, then edit and resubmit. Students have no review action. Latitude/longitude are read-only. IIIC retains the map-based audited correction and radius controls; mentors have review permission within their assignment, not correction permission.

Legacy student/admin manual-coordinate PUT endpoints return 409 `MAP_REGISTRATION_REQUIRED`. The legacy single-confirmation endpoint returns 409 `WORKPLACE_REVIEW_REQUIRED`: it cannot bypass explicit revision review. Legacy `CONFIRMED` rows remain readable but never count as dual approval. No old migration checksum or historical verification is changed.

## Daily attendance

The existing browser geolocation, camera capture, challenge/session and attendance endpoints remain in use. The server resolves the authenticated student and eligible internship, reads that internship's workplace under a transaction lock, validates GPS, checks both approvals, and calculates Haversine distance. Frontend student/workplace IDs, distance and verification claims are ignored.

The initial radius is **200 metres**. `ATTENDANCE_DEFAULT_GEOFENCE_RADIUS_METERS` configures the default for newly registered workplaces; it is not a shared reference coordinate. IIIC configures each internship's radius through the existing authorized `/radius` mechanism (10–5000 metres). Radius changes revoke approvals and apply to future submissions only.

Existing conservative accuracy rules remain: distance plus reported GPS accuracy must fit the configured radius, accuracy must satisfy the existing maximum, and existing camera/EXIF/AI conditions must pass. Coordinates alone never verify attendance. Missing, denied, partial or numerically invalid GPS yields `UNVERIFIED`; invalid numeric values are not stored as coordinates. Attendance remains `PRESENT` when otherwise eligible. Malformed request values and invalid camera files retain normal request validation.

Attendance evidence retains the submitted valid coordinates/accuracy, server submission timestamp, workplace coordinate/address/radius snapshot, distance, reason, camera metadata and AI-session evidence. Historical attendance is not silently re-verified when a workplace changes. Remote attendance keeps its existing evidence rules and does not become a geofence workflow.

## Monthly reports

Photo GPS is extracted from the original JPEG/PNG bytes on the server and compared with the same dual-approved internship workplace. Browser attendance GPS is never substituted for missing photo EXIF. The report stores its independent photo metadata and workplace/distance/radius/outcome snapshot. Missing/invalid photo GPS, unavailable workplace, incomplete approval and outside-radius outcomes remain separate. Existing photo capture-time, signature, size and integrity checks remain active. Valid EXIF alone does not prove physical presence.

Mentor and IIIC views retain independent workplace, attendance GPS and photo GPS Maps links whenever the corresponding actual coordinate pair exists. Missing coordinates produce an unavailable state.

## Validation and deployment

Backend regression log: `backend/target/workplace-regression.log`. PostgreSQL integration log: `backend/target/dual-postgres.log`. Frontend production build log: `frontend/build-workplace.log`. PostgreSQL tests use a newly generated isolated local schema and remove only that schema, never existing application data. Exact final counts are recorded below after execution.

No commit, push, or deployment is performed. V27 must run with the matching backend before serving the new review endpoints. Vercel/Render deployment identity and logs remain unverified because linked platform access and failure logs were not available. The frontend environment files contained no configured Google browser key. Hosted configuration must point `BACKEND_API_URL` to the intended backend and supply the Maps variables before rebuilding.

The supplied request ends at “Missing photo GPS must not be confused with browser ge”. Work covers the complete preceding requirements and preserves the earlier testing/security constraints; any omitted trailing instructions still need to be supplied.

## Files changed for this implementation

Backend registration/review: `InternshipLocation.java`, `InternshipLocationStatus.java`, `InternshipLocationResponse.java`, `InternshipLocationRepository.java`, `InternshipLocationService.java`, `WorkplaceReview.java`, `AdminInternshipLocationController.java`, `MentorInternshipLocationController.java`, `RegisteredWorkplaceRequest.java`, `InternshipDetailsService.java`, and `V27__dual_workplace_approval.sql`.

Backend verification: `PhysicalAttendanceService.java`, `PhysicalVerificationPolicy.java`, `VerificationReason.java`, `MonthlyProgressService.java`, `MonthlyProgressReport.java`, and the optimistic-lock error handling in `GlobalExceptionHandler.java`.

Frontend: `components/WorkplacePlaceSelector.tsx`, `components/InternshipDetailsForm.tsx`, `components/InternshipLocationManager.tsx`, `lib/attendance.ts`, `lib/internship-details.ts`, `app/student/internship/details/page.tsx`, `app/admin/students/[id]/page.tsx`, `.env.example`, and the workplace selector/panel/interaction tests.

Regression tests: `DualWorkplaceApprovalIntegrationTest.java`, `DualWorkplaceApprovalPostgresIntegrationTest.java`, `WorkplaceApprovalMigrationPostgresTest.java`, `PhysicalAttendancePostgresIntegrationTest.java`, `SharedWorkplaceIntegrationTest.java`, `PhysicalAttendanceIntegrationTest.java`, `EdgeAiAttendanceIntegrationTest.java`, `OnlineHybridAttendanceIntegrationTest.java`, and `InternshipDetailsIntegrationTest.java`. Earlier unrelated uncommitted work is preserved.

## Re-running checks

From `backend`, run the relevant H2 suite sequentially (do not run another Maven build in the same directory at the same time):

```powershell
mvn.cmd '-Dtest=PhysicalAttendanceIntegrationTest,DualWorkplaceApprovalIntegrationTest,PhysicalVerificationPolicyTest,InternshipDetailsIntegrationTest,InternshipOnboardingIntegrationTest,AttendanceIntegrationTest,OnlineHybridAttendanceIntegrationTest,EdgeAiAttendanceIntegrationTest,MentorAssignmentIntegrationTest,DashboardIntegrationTest,DashboardReportIntegrationTest,MentorRecordDeletionIntegrationTest,ProgressPhotoValidatorTest,MonthlyProgressMultipartLimitTest,AuthIntegrationTest,PasswordResetIntegrationTest' -DreuseForks=false test
```

From `frontend`:

```powershell
node --test tests/*.test.cjs
npm.cmd run typecheck
npm.cmd run build
```

PostgreSQL opt-in tests require `EXIF_TEST_DB_URL`, `EXIF_TEST_DB_USERNAME`, `EXIF_TEST_DB_PASSWORD`, and `EXIF_TEST_DB_SCHEMA`. The URL must point to localhost and contain the matching `currentSchema=ims_deletion_test_<32 hex characters>`. Create a fresh disposable schema for each run. The guards reject other schema names; never point these tests at the application's existing schema. Run `DualWorkplaceApprovalPostgresIntegrationTest`, `PhysicalAttendancePostgresIntegrationTest`, and `WorkplaceApprovalMigrationPostgresTest`. The migration test additionally creates and removes its own fresh `ims_workplace_migration_<32 hex characters>` schema and verifies a V26 fixture upgrade to V27. The local runners used during this work are under ignored `backend/target` and clean only their generated schema.

## Final actual results

- 143 backend tests passed, with zero failures, errors or skips in the final Surefire XML reports. Backend packaging returned Maven exit code 0. The PowerShell redirected test wrapper reported exit code 1 while its log contained a `NativeCommandError` warning record; no Maven test errors appeared, and the individual Surefire results are the source of the test count.
- 19 PostgreSQL dual-approval/monthly/workplace tests, 4 PostgreSQL physical-attendance tests, and 1 PostgreSQL V26-to-V27 migration preservation test passed. All generated test schemas were cleaned up.
- 31 frontend tests passed; TypeScript type checking and the final production build returned exit code 0.
- Local backend startup applied V27 successfully. Before and after the upgrade: 5 attendance rows, 3 report rows (including retained deleted rows), 1 workplace, and 1 legacy confirmed workplace. No application rows were deleted or restored by this work.
- Local frontend home and login returned HTTP 200; protected API access through its proxy returned 401 without authentication. `/actuator/health` is permitted by security configuration but is not exposed by this build and returned 404; it was not counted as a health-test success.
- Live Google Maps and hosted Vercel/Render behavior remain unverified because no configured frontend key or linked deployment/log access was available. No commit, push or deployment occurred.
