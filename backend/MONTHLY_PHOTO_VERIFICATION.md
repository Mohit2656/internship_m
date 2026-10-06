# Monthly photograph EXIF/GPS verification

The original upload is read once with a 5 MB bound. JPEG/PNG content is decoded (at most 40 million pixels), matched to the accepted file type, and retained unchanged. Metadata extraction, SHA-256 and storage consume these same bytes. No client coordinate or status fields participate in verification.

`geoVerificationStatus` verifies only parseable, finite, in-range EXIF coordinates with valid DMS values and hemisphere references. (0, 0) is valid. Missing coordinates are MISSING_GPS; malformed/partial coordinates are INVALID_GPS. Invalid coordinates remain null because existing database constraints disallow them. A metadata reader failure yields METADATA_READ_FAILED. Unsupported/corrupt uploads are rejected and therefore have no persisted report.

Capture time is independent: PRESENT, MISSING_CAPTURE_TIME, or INVALID_METADATA. Valid GPS remains VERIFIED when capture time is absent or malformed. EXIF original time uses its offset when present; offsetless EXIF preserves the application's existing UTC convention. Submission/created timestamps never substitute for capture time. No reporting-month inference is made because offsetless camera times cannot reliably establish a local reporting date.

V22 reuses exif_captured_at/exif_latitude/exif_longitude and existing filename/MIME/size columns. Old photographs remain unassessed with null new fields; metadata and hashes are not fabricated. New photographs receive all verification and hash fields. Apply migration through normal Flyway deployment, never by manually editing the database.

Student, mentor and admin retrieval retain existing ownership/active-assignment/role controls. The MONTHLY_PROGRESS institutional report uses the existing authorized student scope, POI/CSV writer, selected fields and filters (geoVerificationStatus, gpsPresent, exifPresent). Optional metadata columns are excluded from default selection. The existing dashboard monthlyPhotos aggregate also contains the new metadata.

EXIF can be edited and does not authenticate physical location or presence. SHA-256 identifies exact uploaded bytes; it does not authenticate EXIF. No geocoding or geofencing is performed.

Deployment checks: verify V22 against a staging PostgreSQL copy, use representative phone JPEG/PNG uploads (with and without EXIF), check student read-only summaries and both authorized reporting roles, and verify private download bytes. Automated integration tests use actual generated JPEG/PNG and embedded EXIF rather than mocking extraction.

## Validation results

- Backend compilation passed. Full suite: 166 tests, 158 passed, 1 failure and 7 errors.
- MonthlyProgressIntegrationTest: 6 passed. DashboardReportIntegrationTest: 18 passed, including existing exports and new report catalog support.
- Seven mentor tests passed when rerun with reuseForks=false; full-suite failures involved shared-database cleanup and transient User references.
- OpportunityManagementIntegrationTest still has one failure in isolation: publishedAt nanoseconds differ from the database's microsecond-rounded timestamp. Its implementation and tests were not modified.
- Frontend TypeScript check and webpack production build passed.
- git diff --check passed. No commit or push performed.
- Manual phone-image/browser interaction checks and staging PostgreSQL migration validation remain pending. Tests ran on H2 in PostgreSQL compatibility mode, using the installed JDK 22 with Java 17 compilation target.

## Files changed

Backend monthlyprogress: ProgressPhotoValidator.java, ValidatedProgressPhoto.java, GeoVerificationStatus.java, MonthlyProgressReport.java, MonthlyProgressReportResponse.java, MonthlyProgressService.java.

Backend reporting: DashboardReportRepository.java, DashboardReportWriter.java, InstitutionalReportService.java.

Migration: src/main/resources/db/migration/V22__complete_monthly_photo_verification.sql.

Tests: src/test/java/edu/institution/ims/monthlyprogress/MonthlyProgressIntegrationTest.java.

Frontend: lib/monthly-progress.ts, app/student/monthly-progress/page.tsx, components/ProgressPhotoVerification.tsx, components/MonthlyProgressReportList.tsx, components/ReportsCenter.tsx.

Documentation: this file.

## Existing local database migration compatibility

The existing database had already applied V20__add_project_seminar_evaluations.sql and V21__monthly_photo_verification.sql from Major_project. Their original migration files were restored byte-for-byte; no Flyway repair or history edits were used. V22 adds capture_time_status and widens existing coordinate columns. V21 photo metadata columns, including the original status/message lengths, are reused. Seminar application code was not added or changed. Existing capture status remains null rather than inferred.

Local PostgreSQL validation: all 22 migrations validated, V22 applied successfully, and ImsApplication started on port 8080. Existing database data and Flyway history were preserved; no repair, reset, or direct schema edits were performed.
