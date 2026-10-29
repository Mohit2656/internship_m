> Current workflow: see [DUAL_WORKPLACE_APPROVAL.md](DUAL_WORKPLACE_APPROVAL.md). V27 replaces the earlier single-confirmation behavior with separate assigned-mentor and IIIC approval. The content below records the earlier implementation and audit.

# Registered workplace location audit

## Root cause and existing data

The panel is frontend/components/InternshipLocationManager.tsx, used on the IIIC student details page. It previously displayed manual coordinate inputs and omitted the Places fields already returned by the backend. Its API is /api/v1/admin/students/{studentId}/internship/location, implemented by AdminInternshipLocationController and InternshipLocationService. The InternshipLocation entity maps internship_locations with a unique internship_id foreign key. Latitude, longitude, radius, status, confirmer and confirmation time belong to that internship, not the institution globally. Google Places place_id and workplace_address were added by V24.

A read-only inspection of the real local database found internship 1/student 2 with confirmed coordinates 21.068802,79.059511, no place ID and no saved workplace address. There are two existing monthly reports, both without EXIF GPS. The newer report contains the original workplace snapshot and PHOTO_MISSING_GPS; the older report has no comparison snapshot. These existing rows were not converted or recomputed. We cannot infer a Google branch from these manually confirmed coordinates, from the student's profile, or from a company name.

## Final data flow

The existing student internship form uses WorkplacePlaceSelector with Google's PlaceAutocompleteElement. It requests id, displayName, formattedAddress and location for the selected branch. Saving the internship sends these fields to InternshipDetailsService; server-side coordinate validation and the authenticated student determine the internship association. InternshipLocationService persists the selected place as PENDING, and GET returns the saved selection. Changes to a confirmed workplace require IIIC correction. The legacy student coordinate endpoint cannot overwrite a Places-backed location. Changing company information without resupplying its registered branch cannot silently retain a different Places reference.

Daily attendance independently validates browser latitude, longitude and accuracy on the backend, resolves the authenticated student's eligible internship, then queries its internship_locations row. It retains the existing accuracy, camera metadata, geofence, schedule and AI rules. Distance plus browser accuracy must fit the confirmed radius. GPS submission does not automatically imply verification. Evidence records contain the workplace coordinates/radius/address and calculated distance at submission. Missing browser GPS/workplace results remain unverified; no live GPS is used to manufacture a workplace.

Monthly reports independently extract GPS from original image bytes on the server. MonthlyProgressService looks up the same internship's location and persists the original photo metadata plus a workplace/distance/radius/outcome snapshot. Missing photo GPS, missing workplace, pending workplace and outside-radius outcomes remain distinct. Photo GPS validity does not prove physical presence. Historical snapshots are never recalculated when the workplace changes.

Legacy manually confirmed locations remain explicitly labeled as legacy references until an authorized correction registers the actual branch. They are not represented as Google Places selections or reassigned to other internships. Existing legacy verification rules are preserved. Old reports without snapshots remain unassessed.

## Panel and authorization

The panel retains its card, grid, colors and input styling, displays workplace name/address and an actual-coordinate Maps link, and makes latitude/longitude read-only. IIIC can configure radius (10?5000 meters) through /radius; saving resets the reference to PENDING for confirmation. Correct registered workplace opens Places selection plus a required reason, and PUT /correction saves an audit and resets confirmation in the same transaction. The old admin PUT cannot move an existing reference silently. No unrelated global settings are consulted.

V26 adds the nullable workplace_name and workplace_correction_audit containing internship, actor, timestamp, reason, previous/new reference coordinates/place IDs and new radius. Audit FK constraints prevent unrelated cascades. This is an append-only application workflow; there is no audit-edit API. Existing attendance/photo snapshots provide the original verification evidence. No automatic re-verification occurs.

Assigned mentors have a read-only GET /api/v1/mentor/students/{id}/internship/location and the same styled panel on their student page. Backend assignment checks reject other mentors with 404. ADMIN-only correction, radius and confirmation endpoints retain existing JWT/role security. Mentor attendance displays submitted browser GPS and reference workplace maps independently; IIIC/mentor report previews provide workplace, attendance and photo map links when actual coordinate pairs exist. Monthly cards show the photo location, reference snapshot, distance, radius and separate comparison reason.

## Code and deployment findings

Current branch: main. Local HEAD at inspection: 550207e (Add admin password reset and progress photo location). The Places, verification, deletion and this panel work are uncommitted; no push or deployment was performed. No linked frontend/.vercel metadata, Render deployment configuration, platform CLI or deployment connector was available. A public GitHub fetch failed, and hosted URLs, account environment settings and Render failure logs were not supplied. Consequently, hosted commit IDs, environment values, logs and migration version cannot be certified.

The local frontend has only .env.example, with an empty NEXT_PUBLIC_GOOGLE_MAPS_API_KEY. No configured key was found in frontend environment files. Live Places lookup cannot be certified and the UI reports missing configuration. Enable Maps JavaScript API and Places API (New), restrict the browser key by HTTP referrer/API, set NEXT_PUBLIC_GOOGLE_MAPS_API_KEY, and rebuild/restart the frontend. Do not add the key to Git. On Vercel, BACKEND_API_URL must point at the intended Render backend; the repository fallback is localhost:8080 for local development. Hosted values still need verification. The local backend was on V25 at inspection; V26 must be applied before serving the new fields/endpoints.

## Files changed for this requirement

Backend: InternshipLocation.java; InternshipLocationResponse.java; InternshipLocationService.java; AdminInternshipLocationController.java; new MentorInternshipLocationController.java and WorkplaceCorrection.java; RegisteredWorkplaceRequest.java; InternshipDetailsService.java; V26__workplace_names_and_correction_audit.sql; SharedWorkplaceIntegrationTest.java and SharedWorkplacePostgresIntegrationTest.java.

Frontend: InternshipLocationManager.tsx; WorkplacePlaceSelector.tsx; new LocationMapLink.tsx and AttendanceLocations.tsx; ProgressPhotoLocation.tsx; ReportsCenter.tsx; mentor dashboard and mentor student page; lib/attendance.ts, lib/internship-details.ts and lib/monthly-progress.ts; progress-photo-location.test.cjs and new workplace-panel.test.cjs.

Earlier uncommitted work is preserved. No prior migration was edited. Tests use H2 and fresh isolated PostgreSQL schemas; no production records are deleted by the tests. Test logs are backend/target/workplace-final-tests.log and workplace-postgres.log, and frontend/workplace-panel-build.log. PostgreSQL runner removes only its newly generated schema. Live Google selection and hosted deployment remain blocked by unavailable configuration/access.

## Actual verification results

Passed 81 H2 backend tests: shared workplace/monthly (13), internship registration (19), physical attendance (4), basic attendance (3), edge AI attendance (4), online/hybrid attendance (4), physical verification (2), reporting/export (18), and mentor deletion/monthly regression (14). Passed 13 PostgreSQL workplace/monthly integration tests through V26 in a fresh isolated schema; cleanup passed. Frontend workplace tests and type checking/build passed; additional photograph-preview tests were subsequently added. Local backend restarted successfully and applied V26 without changing existing workplace coordinates or historical verification outcomes.

The later photograph issue exposed an asynchronous popup problem in openPrivateFile: it waited for the authenticated fetch before opening a tab. It now opens the placeholder tab during the click gesture, detaches window.opener, then navigates to the authenticated blob; failure closes the placeholder. PDF downloads remain unchanged. Pop-up blocking, unavailable records and expired sessions have explicit feedback. A subsequent read-only local check found reports 1, 2 and 3 soft-deleted, with all three retained photo files still present. Ordinary viewing intentionally excludes those deleted reports. No deleted record was restored and no file was removed.

Final frontend checks: all 24 tests passed; TypeScript type checking and the production build passed after the photo-preview and error-message changes. Photograph changes also affect frontend/lib/monthly-progress.ts, MonthlyProgressReportList.tsx, app/student/monthly-progress/page.tsx and new tests/private-photo-preview.test.cjs.
