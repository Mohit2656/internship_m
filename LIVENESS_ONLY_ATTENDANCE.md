# Temporary liveness-only attendance

Monthly reports subsequently changed to explicit faculty mentor review and no longer evaluate photo GPS. See [MONTHLY_REPORT_REVIEW.md](MONTHLY_REPORT_REVIEW.md) for that workflow; the attendance policy and validation results documented here are unchanged.

This change pauses the workplace-registration interface and makes new daily attendance verification depend only on the existing liveness decision. Company details, attendance eligibility, authentication, camera/image validation, hybrid schedules, monthly uploads, photograph access, and mentor deletion safeguards remain in place.

## Attendance decisions

Physical and remote attendance still use the existing student/internship/date-bound verification session, random challenge, expiry, single-use enforcement, face count, quality check, and liveness result. Successful liveness produces `livenessStatus: VERIFIED`; a completed failed or expired attempt produces `FAILED`; absent session/processing evidence produces `NOT_PERFORMED`. Attendance presence remains an independent field (`PRESENT`/`ABSENT`). Basic attendance retains its existing eligibility rules and records `NOT_PERFORMED`, never verified.

Browser GPS permission, GPS accuracy, workplace registration/approval, and distance calculations are no longer attendance dependencies. Optional numeric GPS parameters on the legacy physical endpoint are ignored and not stored for new attendance. The active frontend sends only the camera photograph and existing liveness evidence, plus the existing work summary for remote attendance.

Responses explicitly include `verificationBasis` and `livenessStatus`. Student, mentor, admin/IIIC views, report filters, counts, and exports use the liveness projection. The original `verificationStatus` field is retained for compatibility and audit; consumers must use `livenessStatus` for current labels. A new record cannot appear liveness-verified merely because its stored verification flag is `VERIFIED`: session/processing evidence, all four AI checks, and absence of an AI failure are also required.

## Historical data and migration

V28 adds only `attendance_records.verification_basis`, with a non-null default of `LEGACY` and a check accepting `LEGACY` or `LIVENESS`. It does not rewrite any old status, evidence, session, workplace, approval, correction, deletion audit, photograph, or report. Newly submitted records explicitly use `LIVENESS`. Existing records display `HISTORICAL` / “Historical attendance — liveness not reassessed,” including records whose old AI evidence happened to pass. Historical presence is unchanged.

This migration is tested in a disposable PostgreSQL schema; it has not been applied to the existing local application's schema by this task. Use the matching backend/frontend together after review. Retained workplace data and services allow a deliberate future restoration without deleting the basis column or reinterpreting old rows.

## Paused interfaces and retained functionality

Student workplace selection, Google configuration warnings, mentor/IIIC approval panels, radius controls, and correction forms are not mounted by active pages. Company name/type/postal address/phone and supervisor/internship fields remain. Editing those fields does not require registering a workplace or overwrite an existing workplace.

The common internship-details response omits the workplace payload. New attendance responses omit location coordinates, radius, distance, and workplace snapshots. Original historical evidence remains available through the existing authorized attendance APIs, without rendering its location-based reasons in active attendance panels.

Monthly report image integrity and server-side EXIF extraction are unchanged. JPEG/PNG uploads with missing GPS remain valid images with an explicit missing-GPS metadata outcome. Photo GPS and photo Maps links remain available independently. Monthly workplace comparison remains stored/calculated by its existing backend path, but its paused workplace panel is not displayed and never contributes to attendance status.

## Intentional location references

- `InternshipLocationManager`, `WorkplacePlaceSelector`, and `AttendanceLocations` remain dormant components, with no active page imports. Legacy location types and authorized registration/review/correction endpoints remain for retained records and reversibility.
- `PhysicalVerificationPolicy` and old geofence reasons remain for legacy behavior/tests; neither physical nor remote attendance invokes that policy. Its Haversine helper is still used by the independent monthly photo comparison.
- Monthly progress entities, validators, services, responses, and report fields retain photo EXIF/GPS and historical workplace snapshots. `LocationMapLink` and photo links use actual validated coordinates and do not require a Google API key.
- Old migrations and `DUAL_WORKPLACE_APPROVAL.md` document the previous workflow and remain intact except for an explicit current-status note.

## Deployment configuration

No backend application properties, dependencies, Java/Spring versions, Docker build steps, upload limits, or credentials are changed. The frontend `.env.example` no longer lists a Google API key/map ID as active requirements. Its retained dormant selector would need those values if deliberately restored. `BACKEND_API_URL`, authentication/storage/database settings, and existing monthly comparison configuration continue to apply.

V28 must run with the matching backend before the new policy is served. No deployment, local application schema upgrade, commit, or push is performed by this task. Hosted Render/Vercel behavior and an actual Docker build remain unverified; Docker is unavailable locally.

## Validation

Final Surefire reports contain **354 executed backend tests: 265 non-PostgreSQL and 89 PostgreSQL, with zero failures, errors, or skips**. The full suite was run first; after correcting the reporting findings, all affected H2 tests and the complete PostgreSQL suite were rerun. Camera/MediaPipe/API behavior is mocked in frontend tests; physical camera hardware and hosted services were not exercised.

| Command/check | Actual result |
| --- | --- |
| `mvn.cmd -q -DreuseForks=false test` | Initial full run: 260 executed, 259 passed, 1 reporting-summary failure; 88 PostgreSQL opt-in cases skipped without database variables. |
| `mvn.cmd -q -Dtest=AttendanceLivenessStatusTest,DashboardReportIntegrationTest -DreuseForks=false test` | Final rerun: 26 passed, zero failures/errors/skips, exit 0. |
| `python backend/scripts/run-postgres-tests.py` | Final full PostgreSQL run: 89 passed, zero failures/errors/skips, exit 0; generated-schema cleanup passed. |
| `node --test tests/*.test.cjs` in `frontend` | 53 passed, zero failures/skips, exit 0. |
| `npm.cmd run typecheck` in `frontend` | Passed, exit 0. |
| `npm.cmd run build` in `frontend` | Production build passed, exit 0. |
| `mvn.cmd -B --no-transfer-progress clean package -DskipTests` | Final backend package passed, exit 0, using an isolated source copy. |
| `git diff --check` | Passed. |

Initial checks exposed lowercased JSON summary aliases and a PostgreSQL date-export failure. SQL now quotes the two summary aliases and explicitly casts the attendance reference date to `date`. A new regression test also initially assumed the existing student preview returned arbitrary selected columns; its assertion was corrected to check the unchanged identity preview plus the actual XLSX export. The first PostgreSQL run failed the date-export case; the final complete rerun passed. No export API or date writer redesign was needed.

PostgreSQL checks include physical attendance, independent monthly EXIF/GPS, mentor/admin reporting and authorization, deletion/audit retention, dual-approval legacy compatibility, V26-to-V27 migration compatibility, and V27-to-V28 preservation. The runner accepts only localhost and creates/removes its own disposable schemas. No existing application or production schema was migrated or cleared.

Local logs: `backend/audit-liveness-backend.log` (initial full run), `backend/audit-liveness-targeted-final.log`, `backend/audit-postgres.log` (final full PostgreSQL run), `backend/audit-liveness-package.log`, and `backend/audit-liveness-results.log` (structured counts). The final review JAR is `backend/target/ims-backend-liveness-review.jar`; the running local backend was not stopped or replaced.

## Changed files

This list includes every modified or new source/documentation path for this task (43 files); generated artifacts and ignored logs are excluded.

- [DUAL_WORKPLACE_APPROVAL.md](DUAL_WORKPLACE_APPROVAL.md)
- [LIVENESS_ONLY_ATTENDANCE.md](LIVENESS_ONLY_ATTENDANCE.md)
- [backend/src/main/java/edu/institution/ims/attendance/AttendanceEvidenceResponse.java](backend/src/main/java/edu/institution/ims/attendance/AttendanceEvidenceResponse.java)
- [backend/src/main/java/edu/institution/ims/attendance/AttendanceRecord.java](backend/src/main/java/edu/institution/ims/attendance/AttendanceRecord.java)
- [backend/src/main/java/edu/institution/ims/attendance/AttendanceResponse.java](backend/src/main/java/edu/institution/ims/attendance/AttendanceResponse.java)
- [backend/src/main/java/edu/institution/ims/attendance/AttendanceService.java](backend/src/main/java/edu/institution/ims/attendance/AttendanceService.java)
- [backend/src/main/java/edu/institution/ims/attendance/AttendanceVerificationBasis.java](backend/src/main/java/edu/institution/ims/attendance/AttendanceVerificationBasis.java)
- [backend/src/main/java/edu/institution/ims/attendance/LivenessStatus.java](backend/src/main/java/edu/institution/ims/attendance/LivenessStatus.java)
- [backend/src/main/java/edu/institution/ims/attendance/PhysicalAttendanceService.java](backend/src/main/java/edu/institution/ims/attendance/PhysicalAttendanceService.java)
- [backend/src/main/java/edu/institution/ims/attendance/RemoteAttendanceService.java](backend/src/main/java/edu/institution/ims/attendance/RemoteAttendanceService.java)
- [backend/src/main/java/edu/institution/ims/dashboard/report/AttendanceLivenessSql.java](backend/src/main/java/edu/institution/ims/dashboard/report/AttendanceLivenessSql.java)
- [backend/src/main/java/edu/institution/ims/dashboard/report/DashboardReportCatalog.java](backend/src/main/java/edu/institution/ims/dashboard/report/DashboardReportCatalog.java)
- [backend/src/main/java/edu/institution/ims/dashboard/report/DashboardReportRepository.java](backend/src/main/java/edu/institution/ims/dashboard/report/DashboardReportRepository.java)
- [backend/src/main/java/edu/institution/ims/dashboard/report/InstitutionalReportService.java](backend/src/main/java/edu/institution/ims/dashboard/report/InstitutionalReportService.java)
- [backend/src/main/java/edu/institution/ims/internship/InternshipDetailsService.java](backend/src/main/java/edu/institution/ims/internship/InternshipDetailsService.java)
- [backend/src/main/resources/db/migration/V28__attendance_liveness_verification_basis.sql](backend/src/main/resources/db/migration/V28__attendance_liveness_verification_basis.sql)
- [backend/src/test/java/edu/institution/ims/attendance/AttendanceIntegrationTest.java](backend/src/test/java/edu/institution/ims/attendance/AttendanceIntegrationTest.java)
- [backend/src/test/java/edu/institution/ims/attendance/AttendanceLivenessStatusTest.java](backend/src/test/java/edu/institution/ims/attendance/AttendanceLivenessStatusTest.java)
- [backend/src/test/java/edu/institution/ims/attendance/EdgeAiAttendanceIntegrationTest.java](backend/src/test/java/edu/institution/ims/attendance/EdgeAiAttendanceIntegrationTest.java)
- [backend/src/test/java/edu/institution/ims/attendance/LivenessMigrationPostgresIntegrationTest.java](backend/src/test/java/edu/institution/ims/attendance/LivenessMigrationPostgresIntegrationTest.java)
- [backend/src/test/java/edu/institution/ims/attendance/OnlineHybridAttendanceIntegrationTest.java](backend/src/test/java/edu/institution/ims/attendance/OnlineHybridAttendanceIntegrationTest.java)
- [backend/src/test/java/edu/institution/ims/attendance/PhysicalAttendanceIntegrationTest.java](backend/src/test/java/edu/institution/ims/attendance/PhysicalAttendanceIntegrationTest.java)
- [backend/src/test/java/edu/institution/ims/dashboard/report/DashboardReportIntegrationTest.java](backend/src/test/java/edu/institution/ims/dashboard/report/DashboardReportIntegrationTest.java)
- [backend/src/test/java/edu/institution/ims/dashboard/report/DashboardReportPostgresIntegrationTest.java](backend/src/test/java/edu/institution/ims/dashboard/report/DashboardReportPostgresIntegrationTest.java)
- [backend/src/test/java/edu/institution/ims/internship/InternshipDetailsIntegrationTest.java](backend/src/test/java/edu/institution/ims/internship/InternshipDetailsIntegrationTest.java)
- [backend/src/test/java/edu/institution/ims/monthlyprogress/SharedWorkplaceIntegrationTest.java](backend/src/test/java/edu/institution/ims/monthlyprogress/SharedWorkplaceIntegrationTest.java)
- [frontend/.env.example](frontend/.env.example)
- [frontend/app/admin/students/[id]/page.tsx](frontend/app/admin/students/[id]/page.tsx)
- [frontend/app/mentor/dashboard/page.tsx](frontend/app/mentor/dashboard/page.tsx)
- [frontend/app/mentor/students/[id]/page.tsx](frontend/app/mentor/students/[id]/page.tsx)
- [frontend/app/student/internship/details/page.tsx](frontend/app/student/internship/details/page.tsx)
- [frontend/components/InternshipDetailsForm.tsx](frontend/components/InternshipDetailsForm.tsx)
- [frontend/components/InternshipLocationManager.tsx](frontend/components/InternshipLocationManager.tsx)
- [frontend/components/ProgressPhotoLocation.tsx](frontend/components/ProgressPhotoLocation.tsx)
- [frontend/components/ReportsCenter.tsx](frontend/components/ReportsCenter.tsx)
- [frontend/components/TodayAttendanceCard.tsx](frontend/components/TodayAttendanceCard.tsx)
- [frontend/components/WorkplacePlaceSelector.tsx](frontend/components/WorkplacePlaceSelector.tsx)
- [frontend/lib/attendance.ts](frontend/lib/attendance.ts)
- [frontend/lib/internship-details.ts](frontend/lib/internship-details.ts)
- [frontend/tests/attendance-status.test.cjs](frontend/tests/attendance-status.test.cjs)
- [frontend/tests/liveness-reports.test.cjs](frontend/tests/liveness-reports.test.cjs)
- [frontend/tests/progress-photo-location.test.cjs](frontend/tests/progress-photo-location.test.cjs)
- [frontend/tests/workplace-paused.test.cjs](frontend/tests/workplace-paused.test.cjs)
