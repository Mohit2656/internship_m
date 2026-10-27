# Attendance submission location

**Current policy:** Submission coordinates are now required for all new attendance submissions. The optional-capture implementation and its test results below describe the previous version. See [REQUIRED_ATTENDANCE_LOCATION.md](REQUIRED_ATTENDANCE_LOCATION.md) for the correction.

## Dashboard history regression fixed on 27 October 2026

Commit `688cdc3` replaced the Mentor dashboard's attendance table (which rendered `AttendanceLocations`) with `AttendanceAbsenceOverview`. Its shared `StudentAttendanceHistory` table omitted that component entirely. The III Cell dashboard uses the same history table. The subsequent OpenStreetMap (`e4ef0a4`) and GPS accuracy (`6823136`) changes preserved coordinate capture and persistence; they did not cause the missing dashboard link.

The existing data path remains intact: `TodayAttendanceCard` sends `latitude`, `longitude`, `accuracy`, and the capture timestamp; the attendance services store submitted coordinates in `attendance_evidence.latitude` / `longitude`; `AttendanceEvidenceResponse` returns them as `evidence.latitude` / `longitude` for submission, Student history, assigned-Mentor views, and authorized III Cell history. Report rows expose them as `attendanceLatitude` / `attendanceLongitude`. These are separate from `expected_latitude` / `expected_longitude`, which describe the registered workplace.

The shared dashboard history now renders each record's submitted coordinates beneath its existing submission time, preserving the table columns, expansion controls, and styling. `AttendanceLocations` uses `https://www.google.com/maps?q=LATITUDE,LONGITUDE` (with the coordinate pair URL-encoded), labeled **View Submitted Location on Google Maps**, in a new tab with `noopener noreferrer`. Student-today and report views reuse this component. Missing, partial, nonnumeric, nonfinite, or out-of-range coordinates retain the unavailable fallback; valid zero and negative coordinates remain viewable even when GPS or liveness verification failed.

No backend production code, migration, verification policy, workplace Google Maps helper, or OpenStreetMap/Leaflet picker was changed. Existing uncommitted dashboard-expansion and workplace-picker work was preserved.

Changed implementation files: `frontend/components/AttendanceAbsenceOverview.tsx` and `frontend/components/AttendanceLocations.tsx`. Regression coverage lives in the new `frontend/tests/submitted-location-history.test.cjs`, the updated URL assertions in `frontend/tests/attendance-status.test.cjs` / `frontend/tests/liveness-reports.test.cjs`, and `backend/src/test/java/edu/institution/ims/attendance/PhysicalAttendanceIntegrationTest.java`.

### Actual validation for the dashboard fix

- From `frontend/`: `node --require ./tests/register-tsx.cjs --test tests/*.test.cjs`: **163 passed**, zero failures or skips. This includes six new Mentor/III Cell dashboard-history and report tests, per-student/per-record URL checks, missing/invalid/zero/negative coordinates, browser capture payloads, GPS accuracy, and existing OSM/workplace behavior.
- `npm.cmd run typecheck`: passed. `npm.cmd run build`: passed using the same source and dependencies in `.local-run/submitted-location-frontend-build`, leaving the running development server's output intact.
- From `backend/`: `mvn.cmd -o -q '-Dtest=SubmissionLocationTest,PhysicalAttendanceIntegrationTest,OnlineHybridAttendanceIntegrationTest,AttendanceLocationVerificationServiceTest,StudentAttendanceDirectoryServiceTest' '-DreuseForks=false' test`: **36 passed**. `mvn.cmd -o -q '-Dtest=DashboardReportIntegrationTest' '-DreuseForks=false' test`: **25 passed**. Zero failures, errors, or skips.
- `python .local-run/run-attendance-postgres.py`: **15 passed**, zero failures, errors, or skips. This targeted wrapper reuses the existing `backend/scripts/run-postgres-tests.py` safety checks and runs `PhysicalAttendancePostgresIntegrationTest` in a newly created localhost PostgreSQL schema. The inherited new history/isolation test executed successfully. Disposable schema cleanup passed; application records were not modified.
- Backend `mvn.cmd -o -q package '-Dmaven.test.skip=true'`: passed in `.local-run/attendance-backend-build`; tests were executed separately as listed above. The running server's JAR was untouched.
- `git diff --check`: passed. Logs and the backend count summary are under `.local-run/` (`submitted-location-frontend-all.log`, `submitted-location-typecheck.log`, `submitted-location-frontend-build.log`, `attendance-location-backend-h2.log`, `attendance-location-backend-reports.log`, `attendance-location-postgres.log`, `attendance-location-backend-package.log`, `attendance-location-backend-results.json`).

Automated rendering and full Spring Security/API/PostgreSQL tests used synthetic test accounts and evidence. Real-device GPS capture and interactive navigation on the Google Maps website were not manually exercised; tests verified that each link opens a new tab and its decoded `q` parameter exactly matches the saved submission coordinates.

## Earlier liveness-only regression

The liveness-only transition removed the location component from student and mentor dashboards, ignored supplied coordinates in PhysicalAttendanceService, and hid stored coordinates in AttendanceEvidenceResponse for LIVENESS records. Historical evidence remained in the database.

Submission latitude, longitude and GPS accuracy now flow from optional student capture through the existing attendance evidence record into student, assigned-mentor and authorized report-history responses. Both dashboards and report history display validated stored coordinates and an ?Open submission location in Google Maps? link in a new tab. Missing, partial or invalid coordinates display a neutral unavailable message. Workplace coordinates are never substituted for submission coordinates.

New physical and remote submissions offer an explicit optional capture button. Browser permission is requested only when the student chooses that button; denial or unavailable geolocation does not block submission. Displaying previously captured coordinates never requests permission. Optional coordinates do not affect liveness decisions, attendance presence, eligibility or authentication. Mentor access remains limited by the existing assignment checks and report query scope.

Coordinates are normalized and range-checked before persistence and response mapping. Invalid accuracy does not discard valid coordinates. No reverse geocoder, new dependency, Google Maps API key or deployment setting is required. Existing location evidence and audit history remain intact. No database migration, workplace-registration restoration or monthly-report change was made.

## Actual validation results

- Targeted backend command: `mvn.cmd -q -Dtest=SubmissionLocationTest,PhysicalAttendanceIntegrationTest,OnlineHybridAttendanceIntegrationTest,EdgeAiAttendanceIntegrationTest,AttendanceLivenessStatusTest,DashboardReportIntegrationTest -DreuseForks=false test`. Initially 45 passed and 2 old assertions failed because they expected valid GPS to be discarded. Those assertions were corrected to enforce the restored evidence flow.
- Corrected classes: `mvn.cmd -q -Dtest=AttendanceLivenessStatusTest,EdgeAiAttendanceIntegrationTest -DreuseForks=false test` passed all 8 cases. Final results across the six targeted classes: 47 passed, no failures, errors or skips.
- PostgreSQL: `mvn.cmd -q -Dtest=PhysicalAttendancePostgresIntegrationTest -DreuseForks=false test`, run through an isolated-schema variant of the existing PostgreSQL runner: 9 passed, no failures, errors or skips. Disposable schema cleanup passed. Application/production records were not changed.
- Frontend: `node --test frontend/tests/*.test.cjs`: 70 passed, no failures or skips.
- Frontend: `npm.cmd run typecheck` passed; `npm.cmd run build` passed.
- Backend packaging: `mvn.cmd -q package -Dmaven.test.skip=true` passed in a temporary source copy because the running local backend locks the original JAR. Review artifact: `backend/target/ims-backend-submission-location-review.jar`. Packaging reused separately executed test results; it did not rerun tests.
- `git diff --check` passed.

Coverage includes stored-coordinate display and Maps links, optional capture and permission denial, invalid/missing coordinates, zero coordinates, physical and remote evidence persistence, authorized student/mentor/history retrieval, cross-mentor denial, legacy evidence preservation, and unchanged liveness statuses. Broader unrelated backend and PostgreSQL suites were skipped at the user's request. No commit, push, deployment or running-server restart was performed.

## Changed files

- `ATTENDANCE_SUBMISSION_LOCATION.md`
- `LIVENESS_ONLY_ATTENDANCE.md`
- `backend/src/main/java/edu/institution/ims/attendance/AttendanceEvidence.java`
- `backend/src/main/java/edu/institution/ims/attendance/AttendanceEvidenceResponse.java`
- `backend/src/main/java/edu/institution/ims/attendance/PhysicalAttendanceService.java`
- `backend/src/main/java/edu/institution/ims/attendance/RemoteAttendanceService.java`
- `backend/src/main/java/edu/institution/ims/attendance/StudentAttendanceController.java`
- `backend/src/main/java/edu/institution/ims/attendance/SubmissionLocation.java`
- `backend/src/main/java/edu/institution/ims/dashboard/report/InstitutionalReportService.java`
- `backend/src/test/java/edu/institution/ims/attendance/AttendanceLivenessStatusTest.java`
- `backend/src/test/java/edu/institution/ims/attendance/EdgeAiAttendanceIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/attendance/OnlineHybridAttendanceIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/attendance/PhysicalAttendanceIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/attendance/SubmissionLocationTest.java`
- `backend/src/test/java/edu/institution/ims/dashboard/report/DashboardReportIntegrationTest.java`
- `frontend/app/mentor/dashboard/page.tsx`
- `frontend/components/AttendanceLocations.tsx`
- `frontend/components/ReportsCenter.tsx`
- `frontend/components/TodayAttendanceCard.tsx`
- `frontend/tests/attendance-status.test.cjs`
- `frontend/tests/liveness-reports.test.cjs`
- `frontend/tests/workplace-panel.test.cjs`
