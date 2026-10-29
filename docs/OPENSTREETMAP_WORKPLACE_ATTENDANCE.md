# OpenStreetMap workplace registration and attendance GPS verification

This feature adds an independent Leaflet/React Leaflet picker to student internship registration. It restores the existing workplace review panels and adds an independent GPS verification signal to new daily attendance. Earlier documents describing workplace registration as paused describe the previous state.

## Existing workflows and Google Maps

`WorkplacePlaceSelector.tsx` retains its Google Places search, map interaction, reverse geocoding, and Google SDK configuration. `LocationMapLink.tsx` and `AttendanceLocations.tsx` retain their existing Google Maps links. The III Cell's existing audited correction workflow still uses the original Google selector. No Google component, credential, configuration variable, dependency, or map URL is replaced.

The active registration page is `frontend/app/student/internship/details/page.tsx`, using `InternshipDetailsForm.tsx` and the authenticated `/api/v1/student/internship` API. Backend registration remains in `InternshipDetailsService` and `InternshipLocationService`. Workplace coordinates belong to the internship's single `internship_locations` record. Existing V24/V26/V27 migrations provide the branch metadata, correction audit, independent mentor/IIIC review, revision, and optimistic locking.

The assigned mentor reviews a workplace in the student's profile page; the III Cell reviews it in the institutional student detail page. Students can read their own decisions. Existing student, assigned-mentor, and ADMIN authorization remains enforced by Spring Security and the existing scoped controllers/services.

## Selecting and approving a workplace

The new `OpenStreetMapWorkplacePicker` dynamically loads `OpenStreetMapWorkplaceMap` in the browser. It supports clicking the map, dragging a marker, requesting the device's current position, viewing coordinates, and explicitly confirming the selection. Initial map centre is a viewport only; it is never saved as a selected workplace.

Enter the company address in the existing company form, or use the picker's **Search for a workplace** box to find a company, street, or city. Search runs only on the Search button or Enter; selecting a result moves the draft pin and fills the address. Adjust the pin to the actual entrance and explicitly confirm before saving. The company address remains editable; selecting a pin alone does not reverse geocode it. Search uses the authenticated backend Photon proxy described in [WORKPLACE_SEARCH.md](WORKPLACE_SEARCH.md). `RegisteredWorkplace.placeId` is null for a new OSM selection; existing Google Place IDs remain intact on saved records unless the student explicitly changes the selection.

Office and hybrid registration require a confirmed workplace in the UI. An unfinished pin/address change blocks saving in every mode. Online/college company registration remains usable without a workplace. Missing backend coordinates never result in verified GPS attendance.

Saving uses the existing authenticated internship API; no student or internship ID is supplied by the picker. Coordinates and addresses are validated through the existing nested request validation. New or changed coordinates require both current mentor and III Cell approval. Radius changes, company name/address changes, and branch changes revoke approvals. An address edit without a selected workplace also invalidates the registered branch address, requiring a new map selection before approval. Review requests remain revision-bound. Reassigned or inactive approving mentors cannot authorize location verification.

## Attendance behavior

Existing BASIC, physical camera/liveness, remote work summary, internship eligibility, submission deadline, late permission, duplicate submission, and attendance presence rules remain in place. Browser location is requested on an explicit attendance capture action only. Latitude, longitude, reported accuracy, and the browser capture timestamp when available are sent to the existing attendance API. There is no continuous tracking.

`AttendanceLocationVerificationService` independently checks GPS against the authenticated internship's currently approved workplace. It calculates Haversine distance on the server, checks accuracy and capture freshness, and snapshots the approved revision, reference coordinates, radius, and distance. Client-supplied IDs, distances, or verification claims do not determine the result. Remote attendance receives `NOT_APPLICABLE` for workplace geofencing.

New `attendance_evidence.location_verification_status` outcomes are `VERIFIED`, `OUTSIDE_RADIUS`, `INSUFFICIENT_ACCURACY`, `LOCATION_UNAVAILABLE`, `WORKPLACE_NOT_APPROVED`, `STALE_LOCATION`, and `NOT_APPLICABLE`. Attendance `PRESENT`/`ABSENT` and existing liveness status remain separate. The student dashboard and daily history show the saved backend GPS result. Mentor/IIIC attendance reports offer independent GPS outcome, capture time, workplace revision, distance, and radius columns without changing existing liveness filters or totals.

Existing required-coordinate submission rules are preserved: unavailable, denied, or invalid coordinates stop submission with a meaningful error and retry action. Poor/missing accuracy and stale samples cannot pass GPS verification. Outside-radius and insufficient-evidence results indicate review; no automatic approval override or new override endpoint is introduced. Existing authorized workplace corrections and reviews retain their audit trails.

GPS is a location signal, not proof against spoofing. Browser timestamps and accuracy are reported evidence. Camera/liveness limitations also remain unchanged.

## Configuration and migration

Newly registered workplaces default to **100 metres** using `ATTENDANCE_DEFAULT_GEOFENCE_RADIUS_METERS`. Existing workplace radii are preserved; the III Cell's existing radius control changes a particular workplace and requires reapproval. `ATTENDANCE_MAX_GPS_ACCURACY_METERS` controls the allowed accuracy (existing default 100 metres). `ATTENDANCE_MAX_LOCATION_AGE_SECONDS` defaults to 300 seconds. Configure these through the existing Spring environment conventions.

Apply Flyway `V34__attendance_gps_location_signal.sql` with the matching backend before serving the new frontend. It adds nullable GPS status/time/revision columns and `attendance_location_verification_audit`. It does not backfill coordinates, reverify old attendance, change old migration checksums, or update existing workplace radii. Each new saved GPS decision is audited transactionally. Historical records without a GPS status remain unassessed.

The frontend adds `leaflet`, `react-leaflet`, and the development dependency `@types/leaflet`. No API key is required for the default interactive OSM tiles. Optional public `NEXT_PUBLIC_OSM_TILE_URL`, `NEXT_PUBLIC_OSM_TILE_PROVIDER_NAME`, and `NEXT_PUBLIC_OSM_TILE_PROVIDER_URL` settings are separate from Google configuration and require a frontend rebuild. Supply the chosen provider's required attribution. Never put a secret in a `NEXT_PUBLIC_` variable.

The default map uses the [OSM tile policy](https://operations.osmfoundation.org/policies/tiles/): visible attribution, HTTPS, browser caching/referrer, no bulk/offline downloads, and no speculative tile buffering. Select a suitable tile provider for deployment traffic and service requirements. The subsequent workplace search feature adds a separately configured geocoding proxy; it does not add a backend tile proxy or change the AI service or frontend routing.

No production deployment or application-data migration is performed by this implementation.

## Verification

Frontend checks use the repository's Node test harness (`node --require ./tests/register-tsx.cjs --test tests/*.test.cjs`), `npm.cmd run typecheck`, and `npm.cmd run build`. No frontend lint command or lint configuration exists in this repository. Backend verification uses Maven and the existing isolated-schema PostgreSQL runner where available.

Coverage includes preserving Google Place IDs/names when reconfirming an unchanged workplace, draft pin confirmation/invalidation, click/drag/current location, GPS denial/retry, saving and editing coordinates, scoped review panels, approval invalidation, inside/outside geofence, accuracy/freshness, malformed input, forged identity parameters, duplicates, audit persistence, remote/liveness compatibility, and retained Google Places/map interactions. Frontend: **133 tests passed**, zero failures/skips (`frontend/osm-frontend-tests.log`); `npm.cmd run typecheck` passed; `npm.cmd run build` passed. No frontend lint script/configuration exists. Provider interactions and device permissions are exercised with controlled browser/Leaflet/Google doubles; physical GPS/camera hardware and hosted deployment were not exercised. Backend results are recorded below.

The initial complete backend run used Maven's default shared JVM: 468 discovered tests, 108 opt-in PostgreSQL cases skipped, and 360 executed. It exposed seven mentor cleanup errors, two inherited workplace tests using an outdated exactly-noon/missing-location fixture, and an opportunity matching assertion failure. The workplace fixture now explicitly uses the existing mentor late-attendance authorization and supplies required coordinates; no submission rule is relaxed. Affected classes are revalidated with `-DreuseForks=false`, matching the project's existing recommended integration-test convention. The full-run log is `backend/gps-backend-full.log`; isolated validation is `backend/gps-backend-isolated.log`.

Latest-source isolated checks passed 64/64 feature and mentor tests: location verification (5), coordinate normalization (4), physical attendance (13), dual workplace approval (21), shared workplace (14), mentor account (3), and mentor assignment (4). This confirms the original seven mentor cleanup errors were caused by the shared test context; no mentor production code was changed. Including the unrelated opportunity class, the isolated run executed 81 tests: 80 passed, one failed, no errors or skips.

The one remaining failure is **pre-existing**: `OpportunityMatchIntegrationTest#talentDiscoveryFiltersSearchesOrdersAndPaginatesWithoutScores`, line 201, expects `$.totalElements` to be 1 but receives 0. The exact single test was independently reproduced against original Git HEAD `ba503f1f0c47e8ef142d1249f48afaa6166f5e51` in a separate temporary source copy: one test, one identical failure, no errors. Log: `.local-run/backend-head-baseline-opportunity.log`. That temporary source copy/archive was removed; the log remains. Opportunity matching product code is unchanged.

The isolated Maven selection is:

```powershell
mvn.cmd '-Dtest=AttendanceLocationVerificationServiceTest,SubmissionLocationTest,PhysicalAttendanceIntegrationTest,SharedWorkplaceIntegrationTest,DualWorkplaceApprovalIntegrationTest,MentorAccountIntegrationTest,MentorAssignmentIntegrationTest,OpportunityMatchIntegrationTest' -DreuseForks=false test
```

The actual validation commands additionally used offline mode (`-o -B -ntp`) and the user's existing local Maven cache. The default full run omitted the selection and isolation flags. The PostgreSQL runner is `python backend/scripts/run-postgres-tests.py`; it accepts only a localhost database, creates a generated disposable schema, and removes that exact schema in `finally`.

Final PostgreSQL run: **108 passed**, zero failures/errors/skips, across 11 suites; runner exit 0 and disposable-schema cleanup passed. This includes the 13 physical/GPS cases, 21 dual-approval cases, 14 shared-workplace cases, 25 reporting cases, and V26-to-V34 historical-upgrade checks. Log: `backend/audit-postgres.log`. The exact runner invocation from `backend` was:

```powershell
$env:MAVEN_ARGS = '-o "-Dmaven.repo.local=C:\Users\Mohit Telang\.m2\repository"'
python scripts/run-postgres-tests.py
```

Final combined latest Surefire XML reports: **57 suites, 468 tests, 467 passed, one baseline-proven opportunity failure, zero errors, zero skipped**. These totals combine the full run, corrected isolated reruns, and PostgreSQL rerun by unique test class; repeated executions are not counted twice. The backend suite is not reported as entirely green because that unrelated existing failure remains.

Final diff inspection confirms no changes to `WorkplacePlaceSelector.tsx`, `LocationMapLink.tsx`, `AttendanceLocations.tsx`, existing Google settings, or existing dependency versions. `git diff --check` passed. At the initial implementation handoff, no commit, push, deployment, or application/production-schema migration had been performed.

For the subsequent user-requested GitHub publication, the feature-only staged snapshot was checked separately: **127 frontend tests passed**, zero failures/errors/skips. The earlier 133-test working-tree run additionally included pre-existing dashboard changes. Those dashboard edits and local runtime/classpath files are excluded from the feature commit and remain in the user's working tree.

## Files

New frontend files: `components/OpenStreetMapWorkplacePicker.tsx`, `components/OpenStreetMapWorkplaceMap.tsx`, `components/LocationVerificationResult.tsx`, `lib/workplace-location.ts`, and `tests/openstreetmap-workplace.test.cjs`.

Frontend integration: `components/InternshipDetailsForm.tsx`, `components/InternshipLocationManager.tsx`, `components/TodayAttendanceCard.tsx`, `components/DailyAttendanceHistory.tsx`, `components/ReportsCenter.tsx`, `app/student/internship/details/page.tsx`, `app/admin/students/[id]/page.tsx`, `app/mentor/students/[id]/page.tsx`, `lib/internship-details.ts`, `lib/attendance.ts`, `package.json`, `package-lock.json`, `.env.example`, and `tests/workplace-paused.test.cjs`, `tests/attendance-status.test.cjs`, `tests/liveness-reports.test.cjs`, and `tests/student-update.test.cjs`. The timestamp fixture updates align tests with the current attendance history/directory UI.

Backend source under `backend/src/main/java/edu/institution/ims/`: `attendance/AttendanceEvidence.java`, `attendance/AttendanceEvidenceResponse.java`, `attendance/AttendanceService.java`, `attendance/InternshipLocationService.java`, `attendance/PhysicalAttendanceService.java`, `attendance/RemoteAttendanceService.java`, `attendance/StudentAttendanceController.java`, `attendance/SubmissionLocation.java`, new `attendance/AttendanceLocationVerificationService.java`, new `attendance/LocationVerificationDecision.java`, new `attendance/LocationVerificationStatus.java`, `internship/InternshipDetailsService.java`, and `dashboard/report/InstitutionalReportService.java`.

Backend resources: `backend/src/main/resources/application.yml` and new `backend/src/main/resources/db/migration/V34__attendance_gps_location_signal.sql`.

Backend tests under `backend/src/test/java/edu/institution/ims/`: new `attendance/AttendanceLocationVerificationServiceTest.java`, `attendance/PhysicalAttendanceIntegrationTest.java`, `attendance/OnlineHybridAttendanceIntegrationTest.java`, `internship/InternshipDetailsIntegrationTest.java`, `monthlyprogress/DualWorkplaceApprovalIntegrationTest.java`, `monthlyprogress/SharedWorkplaceIntegrationTest.java`, and `monthlyprogress/WorkplaceApprovalMigrationPostgresTest.java`.

Pre-existing changes to `AttendanceAbsenceOverview.tsx`, `daily-absence-dashboard.test.cjs`, `.local-run/`, and `backend/classpath.txt` are preserved.
