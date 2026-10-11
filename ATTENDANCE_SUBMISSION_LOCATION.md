# Attendance submission location

## Root cause and fix

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
