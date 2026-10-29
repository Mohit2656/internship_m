# Required attendance submission location

## Correction

Submission location is required for new physical, remote and basic attendance. The student explicitly captures browser coordinates before submitting; the UI disables submission until location capture succeeds. Denied permission, unavailable geolocation and invalid coordinates show a retry instruction and prevent submission. No workplace registration, approvals or geofence were restored.

All three backend submission services enforce a complete valid latitude/longitude pair independently of the frontend. Missing, partial and out-of-range coordinates return HTTP 400 with SUBMISSION_LOCATION_REQUIRED before persistence or consumption of a liveness session. Students can retry the same liveness evidence after capturing valid location. GPS accuracy is supporting evidence, not a threshold: unavailable/invalid accuracy does not discard valid coordinates, and zero accuracy is normalized to unavailable to respect the existing positive-accuracy database constraint.

Attendance presence and liveness decisions remain separate. Coordinates never produce a verified liveness result. Physical and remote liveness checks remain unchanged; BASIC attendance remains NOT_PERFORMED. Stored coordinates continue to appear on student and authorized mentor dashboards and report history with Google Maps links. Historical missing location remains a neutral unavailable display without rewriting records.

## Migration and delivery

V30 allows location-only BASIC attendance evidence to have no photograph (null image media type, zero image size and cameraCaptureUsed=false). Camera evidence still requires positive image size. No earlier migration was edited, and no historical evidence or attendance data was changed. The migration was exercised only in newly created disposable local test schemas, never the application or production schema.

No dependencies or API keys were added. The running local backend was not restarted, and no commit, push or deployment was performed. Backend packaging used an isolated source copy to avoid overwriting the running server's locked JAR.

## Validation

- Focused H2 backend classes: SubmissionLocationTest, AttendanceIntegrationTest, PhysicalAttendanceIntegrationTest, OnlineHybridAttendanceIntegrationTest and EdgeAiAttendanceIntegrationTest. Initial run: 26 passed, one old optional-GPS expectation failed. After correcting that expectation, PhysicalAttendanceIntegrationTest and SubmissionLocationTest were rerun and all 13 passed. Final results across the five classes: 27 passed.
- PostgreSQL: BasicAttendancePostgresIntegrationTest and PhysicalAttendancePostgresIntegrationTest, through a variant of the existing isolated-schema runner. Final rerun: 13 passed, no failures, errors or skips; disposable schema cleanup passed. The first run had the same old optional-GPS expectation failure, corrected before the rerun. All 30 migrations, including V30, applied successfully in the test schema.
- `node --test frontend/tests/*.test.cjs`: 71 passed.
- `npm.cmd run typecheck`: passed.
- `npm.cmd run build`: passed.
- `mvn.cmd -q package -Dmaven.test.skip=true` in an isolated source copy: passed. Backend tests were executed separately above.
- Unrelated broad suites were skipped as requested.

## Changed files

- `ATTENDANCE_SUBMISSION_LOCATION.md`
- `LIVENESS_ONLY_ATTENDANCE.md`
- `REQUIRED_ATTENDANCE_LOCATION.md`
- `backend/src/main/java/edu/institution/ims/attendance/AttendanceEvidence.java`
- `backend/src/main/java/edu/institution/ims/attendance/AttendanceService.java`
- `backend/src/main/java/edu/institution/ims/attendance/PhysicalAttendanceService.java`
- `backend/src/main/java/edu/institution/ims/attendance/RemoteAttendanceService.java`
- `backend/src/main/java/edu/institution/ims/attendance/StudentAttendanceController.java`
- `backend/src/main/java/edu/institution/ims/attendance/SubmissionLocation.java`
- `backend/src/main/resources/db/migration/V30__allow_location_only_basic_attendance_evidence.sql`
- `backend/src/test/java/edu/institution/ims/attendance/AttendanceIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/attendance/BasicAttendancePostgresIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/attendance/EdgeAiAttendanceIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/attendance/OnlineHybridAttendanceIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/attendance/PhysicalAttendanceIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/attendance/SubmissionLocationTest.java`
- `frontend/components/TodayAttendanceCard.tsx`
- `frontend/tests/attendance-status.test.cjs`
