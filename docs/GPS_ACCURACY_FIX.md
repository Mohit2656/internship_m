# Attendance GPS accuracy diagnosis and fix

## Confirmed cause

The local record corresponding to the screenshot has reported GPS accuracy **115.00 m**, distance **4.87 m**, and workplace radius **200.00 m**. A read-only query retrieved only those measurements and the outcome, without identities or coordinates. At diagnosis, the configured maximum GPS uncertainty was **100 m**, so `INSUFFICIENT_ACCURACY` is the correct server result. A nearby point and a reliable point are separate conditions.

The current limit (increased to **200 m** with user approval after diagnosis) comes from `backend/src/main/resources/application.yml`:

```yaml
app:
  attendance:
    max-gps-accuracy-meters: ${ATTENDANCE_MAX_GPS_ACCURACY_METERS:200}
```

No matching environment, local-secret properties, local profile, or local launch-argument override was present when inspected. The backend now logs the effective accuracy policy at startup and returns the same value in authenticated today's-policy responses as `maxGpsAccuracyMeters`. This avoids a separate guessed frontend threshold. The default and existing workplace radii are unchanged.

Configure `ATTENDANCE_MAX_GPS_ACCURACY_METERS` through the backend environment, or set `app.attendance.max-gps-accuracy-meters` in the existing local properties configuration, then restart the backend. The browser reads this value from the API; it does not need a separate public environment variable. Changing this policy should be an institutional decision, not a workaround that fabricates better measurements. The user approved increasing the GPS accuracy limit from 100 m to 200 m. Both Java fallback defaults match the YAML default; environment/property overrides remain supported. Workplace radii are unchanged. New 115 m readings can pass accuracy validation; readings above 200 m still require review, and server geofence/approval/sample-age checks still apply.

## Traced flow

`TodayAttendanceCard.includeSubmissionLocation()` originally called `navigator.geolocation.getCurrentPosition()` once with `enableHighAccuracy: true`, `maximumAge: 0`, and a 10-second timeout. It accepted the first coordinate pair without checking its accuracy. `position.coords.accuracy` was sent as `accuracy` through BASIC query parameters or physical/remote multipart fields; capture time was also sent when available.

`StudentAttendanceController` binds accuracy to `BigDecimal`. `AttendanceService`, `PhysicalAttendanceService`, and `RemoteAttendanceService` derive ownership from the authenticated principal and call `SubmissionLocation.require()`. Invalid or missing accuracy normalizes to null; it cannot pass location verification. Existing two-decimal database precision is retained. `AttendanceLocationVerificationService.evaluate()` reads only an approved workplace, checks sample age and the configured maximum accuracy, then separately compares Haversine distance against that workplace's radius. `INSUFFICIENT_ACCURACY` and `OUTSIDE_RADIUS` remain different outcomes. Remote attendance remains `NOT_APPLICABLE` for geofencing.

`AttendanceEvidenceResponse` returns the saved GPS outcome and accuracy. `LocationVerificationResult` renders the warning in today's attendance and daily history. Previously the history warning displayed distance/radius but omitted accuracy, making the screenshot misleading.

## Changes

- `frontend/lib/attendance-location.ts` performs an explicit, bounded capture: fresh high-accuracy requests, up to 8 seconds per request, 2-second intervals after a poor fix/error, and an independent 20-second overall deadline. It finishes immediately when a usable reading arrives and retains the best complete actual sample if none meets the limit. Permission denial stops immediately. Cancellation/unmount/closing the submission flow clears timers and ignores late callbacks. No `watchPosition`, indefinite loading, or background tracking is added.
- The original latitude, longitude, accuracy, and capture timestamp remain together. The frontend does not clamp, replace, or improve the payload's accuracy. Invalid values are never described as usable.
- `TodayAttendanceCard.tsx` shows reported accuracy and the backend's configured limit while capturing and after capture. Poor readings offer **Retry GPS capture** and guidance to enable precise location, move outdoors/near a window, or try a GPS-capable phone. Existing submission behavior is preserved: a retained poor reading may still be submitted for review, and the backend still assigns its independent GPS outcome. Submission is disabled while acquisition is pending. Remote mode keeps its existing no-geofence rule.
- `LocationVerificationResult.tsx` now displays stored accuracy on insufficient-accuracy records, explains why a small distance is not enough, and gives guidance for future captures. Today's view can additionally display the current server limit. Historical records are not re-evaluated using a newer threshold.
- Backend startup/result diagnostics contain only raw/normalized accuracy, the configured limit, sample-age policy, and outcome. No student identity, email, coordinates, address, session, or token is added to these logs. Oversized numeric representations are represented as invalid rather than expanded into logs. Verification outcomes and audit inserts still occur in the same authenticated transaction.

Example diagnostic format:

```text
Attendance GPS reportedAccuracyMeters=115 normalizedAccuracyMeters=115.00 maxAccuracyMeters=100 result=INSUFFICIENT_ACCURACY
```

Browser high-accuracy mode requests the best available result; it cannot guarantee a particular accuracy. Browser timeout accounting also excludes time spent obtaining permission, which is why the independent overall deadline is necessary. See the [W3C Geolocation specification](https://www.w3.org/TR/geolocation/).

## Preservation and existing records

Google Maps, OpenStreetMap workplace selection, approval revisions, geofence configuration, authentication, role restrictions, one-submission/deadline/late-permission rules, liveness, presence, and audit storage are preserved. No database migration or historical attendance update is required. The 115 m historical record remains an insufficient reading; it is not silently changed to verified or resubmitted. Any existing attendance review must follow authorized institutional procedures.

Pre-existing edits to `AttendanceAbsenceOverview.tsx` and `daily-absence-dashboard.test.cjs` remain untouched. Local runtime files, credentials, and those unrelated edits are excluded from the GPS fix commit. Publishing to GitHub does not by itself verify a hosted production deployment.

## Validation

Tests cover usable and insufficient readings, invalid accuracy, the actual API threshold, bounded retries/timeouts, permission denial, cancellation, unchanged raw accuracy in the submission payload, separate geofence/accuracy outcomes, privacy of diagnostic logs, remote mode, and existing attendance eligibility rules. Commands/results are recorded after final execution below. Device GPS accuracy cannot be reproduced by unit tests; a real-device capture remains necessary to confirm the user's hardware can provide a reading within the configured limit.

- `node --require ./tests/register-tsx.cjs --test tests/*.test.cjs` in `frontend`: **152 passed**, zero failures/skips. Log: `frontend/gps-accuracy-full-tests.log`.
- `npm.cmd run typecheck` in `frontend`: passed, exit 0.
- `npm.cmd run build` in `frontend`: final production build passed, exit 0. Log: `frontend/gps-accuracy-build.log`.
- Focused Maven tests: **62 passed**, zero failures/errors/skips, exit 0. Command below; log: `backend/gps-accuracy-backend.log`.

```powershell
mvn.cmd -o -B -ntp '-Dmaven.repo.local=C:\Users\Mohit Telang\.m2\repository' '-Dtest=AttendanceLocationVerificationServiceTest,SubmissionLocationTest,PhysicalAttendanceIntegrationTest,OnlineHybridAttendanceIntegrationTest,AttendanceIntegrationTest,AttendanceAbsenceServiceTest,AttendanceAbsenceWriterTest,AttendanceSubmissionWindowTest,PhysicalVerificationPolicyTest' -DreuseForks=false test
```

The existing Google selector/map interaction and OpenStreetMap tests are included in the complete frontend run. There is no configured frontend lint script. No full unrelated backend suite or production deployment was repeated for this focused change.

Before the approved threshold increase below, the local backend was restarted with the tested classes. Its startup log confirmed `maxAccuracyMeters=100`, `maxLocationAgeSeconds=300`, and an up-to-date V34 schema with no new migration. Local runtime log: `.local-run/backend-gps-accuracy.out.log`. The local frontend was restarted with the new capture UI. Existing historical verification results were preserved.

## Approved 200 m accuracy policy follow-up

The default GPS accuracy limit is now 200 m. Added boundary coverage for 115 m, exactly 200 m, above 200 m, and an outside-radius point with acceptable accuracy. The physical integration flow verifies the API reports 200 m and stores an actual 115 m reading as verified; poor-accuracy/logging checks now use 206 m. Historical outcomes remain unchanged. Focused Maven validation after the increase: **31 passed**, zero failures/errors/skips; `BUILD SUCCESS` (1 min 52 sec). Command: `mvn.cmd -o -B -ntp "-Dmaven.repo.local=C:\Users\Mohit Telang\.m2\repository" "-Dtest=AttendanceLocationVerificationServiceTest,PhysicalAttendanceIntegrationTest,PhysicalVerificationPolicyTest,SubmissionLocationTest" -DreuseForks=false test`. Log: `backend/gps-200m-tests.log`. This backend-only configuration change required no frontend rebuild; the browser obtains the limit from the policy API.

Local backend restarted successfully with the tested classes: startup confirms `maxAccuracyMeters=200 maxLocationAgeSeconds=300`. `/actuator/health` returned HTTP 200 / UP; frontend `/login` returned HTTP 200. Runtime log: `.local-run/backend-gps-200m.out.log`. Refresh the dashboard to fetch the updated policy.

GitHub publication check: the frontend snapshot containing only this change and the existing committed code passed **146 tests**, zero failures/skips. The full working tree run above includes six additional tests from unrelated local dashboard edits. Snapshot log: `.local-run/gps-publish-check/frontend/gps-publish-tests.log`.
