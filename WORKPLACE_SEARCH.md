# Workplace place and address search

The OpenStreetMap workplace picker supports an explicit place/address search for student registration and III Cell corrections. Selecting a result fills the workplace address and moves the draft pin. Adjust the map pin if needed, then confirm the workplace location before saving. Searching alone does not automatically save a workplace or bypass the existing approval process. The existing map click, pin drag, current-location button, Google components, and Google Maps attendance links remain available.

## Provider and deployment

Search uses a backend proxy to [Photon](https://github.com/komoot/photon), an OpenStreetMap geocoder. The default public demo endpoint is `https://photon.komoot.io`. Its [terms](https://photon.komoot.io/) allow reasonable project use, can throttle extensive use, and do not guarantee availability. The provider's [API documentation](https://github.com/komoot/photon/blob/master/docs/api-v1.md) describes the forward search and GeoJSON response. Search attribution links to Photon and OpenStreetMap in the picker. Ordinary Google Maps links require no paid Google API.

`WORKPLACE_SEARCH_BASE_URL` changes the server-side Photon provider without rebuilding the frontend. For larger traffic or multiple backend instances, configure a privately hosted Photon service or an appropriate hosted provider. The public demo default is intended for moderate use with one backend instance. The proxy keeps an in-memory cache of up to 256 normalized queries for 24 hours and permits at most one outbound search per second per backend instance. Repeated cached searches do not consume that rate limit. The cache is lost on restart. No autocomplete, background geocoding, automatic retries, or browser-to-geocoder calls are made.

`WORKPLACE_SEARCH_CONNECT_TIMEOUT_SECONDS` defaults to 3; `WORKPLACE_SEARCH_READ_TIMEOUT_SECONDS` defaults to 5. No API key is required for the default provider. Only the search text is sent to the provider; authentication cookies, user identifiers, saved workplace data, and device coordinates are not forwarded. The user explicitly supplies the search text.

## API and saved data

Authenticated students and administrators can call `GET /api/v1/workplace-locations/search?q=...`. Mentors and unauthenticated callers cannot use the endpoint. Existing mandatory password-change checks also apply. Queries are trimmed and whitespace-normalized, and must contain 3 to 200 characters. The backend asks Photon for up to five English results and returns only:

```json
[{"name":"Example branch","address":"Example branch, Example Road, Nagpur, India","latitude":21.125,"longitude":79.0875}]
```

The backend validates GeoJSON Point geometry, reads longitude before latitude, and rejects missing, nonnumeric, nonfinite, or out-of-range coordinates. Results without any usable address are discarded. Names are limited to 200 characters and addresses to 1000 characters. Empty results remain an empty array. Provider OSM identifiers are not exposed or saved as Google Place IDs. New OSM selections continue to use `placeId: null`.

Invalid queries return HTTP 400 (`INVALID_WORKPLACE_SEARCH`). Rate limits return HTTP 429 (`WORKPLACE_SEARCH_RATE_LIMITED`) and `Retry-After: 1`. Provider failures, malformed responses, and timeouts return HTTP 503 (`WORKPLACE_SEARCH_UNAVAILABLE`). These use the existing `ApiError` response shape. A failed search leaves manual map selection available.

Selected coordinates use the existing workplace registration/correction APIs and database fields. No database migration, attendance submission field, GPS accuracy threshold, geofence calculation, or approval rule changes are required.

## Verification

Backend verification runs from an isolated source copy in `.local-run/workplace-search-backend-build`, preserving the running backend output and local secret configuration:

```powershell
mvn.cmd -o -B -ntp '-Dmaven.repo.local=C:\Users\Mohit Telang\.m2\repository' -f '.local-run/workplace-search-backend-build/pom.xml' '-Dtest=WorkplaceLocationSearchServiceTest,WorkplaceLocationSearchIntegrationTest,AttendanceLocationVerificationServiceTest,SubmissionLocationTest' -DreuseForks=false package
```

The provider is stubbed for automated tests. Coverage includes query encoding/validation, latitude/longitude order, malformed and missing points, zero coordinates, distinct query results, response limits, cache expiry and eviction, empty results, aggregate rate limits, unavailable providers, timeouts, authentication, role restrictions, mandatory password changes, and the error/Retry-After response contract.

Backend results: **32 tests passed**, zero failures/errors/skips: 13 search service tests, 8 API/security tests, 7 existing GPS policy tests, and 4 existing coordinate validation tests. Maven packaging passed. The first sandboxed attempt could not read an existing Maven dependency; the successful command used the same local cache with elevated access. Log: `.local-run/workplace-search-backend-package.log`.

Frontend results: **177 tests passed**, zero failures, with the repository's Node test harness (`node --require ./tests/register-tsx.cjs --test tests/*.test.cjs`). `npm.cmd run typecheck` and `npm.cmd run build` passed. Search coverage includes explicit button/Enter search, composition input, loading/empty/error states, result selection and address fill, draft confirmation, stale response cancellation, disabled state, map/current-location preservation, and keeping Google Place IDs separate from OSM selections.

A single generic public `Nagpur India` query through the actual Java service/client returned five valid results; the first was `Nagpur, Maharashtra, India` at latitude `21.1498134`, longitude `79.0820556`. An immediate repeated query used the service cache. Log: `.local-run/workplace-search-provider-smoke.log`. The local backend was restarted with the existing launch arguments and secrets unchanged; its health endpoint returned `UP`, and unauthenticated search returned HTTP 401. No application records were changed by the smoke check.
