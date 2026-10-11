# Monthly progress photograph uploads

## Limits and deployment settings

Photos are limited to **20 MiB (20,971,520 bytes)**. The existing overview response now includes `maxPhotoSizeBytes`, so the frontend validates and displays the actual backend setting rather than a separately hardcoded 5 MB limit. JPG, JPEG and PNG extensions are explicitly accepted. File MIME from a browser is only a hint; the backend checks image signatures, structural completeness, actual decoder format and full image decoding.

Defaults:

| Layer | Limit / behavior |
| --- | --- |
| Monthly photo validator | 20 MiB; configurable downward with MONTHLY_PROGRESS_MAX_PHOTO_SIZE_BYTES; hard safety ceiling 20 MiB |
| Spring multipart file | 20 MiB, MULTIPART_MAX_FILE_SIZE=20MB |
| Spring multipart entire request | 21 MiB, MULTIPART_MAX_REQUEST_SIZE=21MB |
| Multipart spooling | 0-byte threshold; files spool to disk before bounded image validation |
| Tomcat rejected-request draining | 23 MiB, bounded; lets moderately oversized clients receive a clean 413 instead of a connection reset |
| Next.js proxy body buffering | 21 MiB in next.config.ts; bounded, not unlimited |
| Monthly upload client timeout | 120 seconds; other API timeouts unchanged |
| Image dimensions | Existing 40 million pixel cap retained before decoding |

Startup checks reject multipart settings that cannot accommodate the configured photo limit plus 64 KiB of form overhead. The old DOCUMENT_MAX_FILE_SIZE and DOCUMENT_MAX_REQUEST_SIZE environment names remain supported as fallbacks, but stale 10MB/11MB overrides must be removed or superseded with the MULTIPART variables. Resume, internship-document and attendance validators retain their existing limits; the shared servlet envelope is raised to admit monthly photographs.

No production platform environment was changed. When deploying, use the values above on Render and rebuild the Vercel frontend with the existing BACKEND_API_URL. Keep the existing `/api/v1/:path*` external rewrite to Render. Do not route uploads through a Vercel Function/server action: [Vercel Function payloads have a 4.5 MB limit](https://vercel.com/docs/errors/function_payload_too_large). The current [external-origin rewrite](https://vercel.com/docs/routing/rewrites) uses the reverse-proxy route instead. No new storage service or public upload endpoint was introduced. A deployed 20 MiB upload must still be verified against the actual Vercel/Render account and any managed gateway limits; local tests do not certify the hosted ingress. Render's managed proxy is not configured in this repository, so there is no invented editable Render body-limit setting here.

If using your own Nginx reverse proxy, configure `client_max_body_size 21m;` on the monthly-progress POST location and retain your existing authenticated proxy forwarding settings. Set any other configurable gateway request cap to 21 MiB. Do not set unlimited body sizes. If a provider enforces a smaller immutable cap, use an authenticated same-origin reverse proxy capable of the documented envelope before advertising the full limit; do not silently bypass cookie/security checks or strip verification metadata.

## Malformed-image diagnosis

The flow is the existing file input -> Axios multipart POST `/api/v1/student/monthly-progress/{monthNumber}` -> Spring multipart parser -> authenticated controller -> MonthlyProgressService -> ProgressPhotoValidator -> private storage/report persistence. The old validator required JPEG EOI (`FF D9`) to be exactly the last two file bytes, and PNG IEND to be exactly the last 12 bytes. A decodable JPEG with 128 bytes of zero camera padding after EOI reproducibly failed that check before EXIF processing. Missing GPS never caused this error; GPS parsing happens after image validation.

The fix walks JPEG marker and segment boundaries, including entropy scans, and validates PNG chunk boundaries and CRCs. Both require a real end marker and a successful matching-format decode. Up to 4 KiB of zero/FF/whitespace padding after the true image end is accepted; arbitrary appended payloads and longer tails are rejected. Decoder warnings, corrupt CRCs, truncated segments and incomplete images still fail. Raw original bytes are preserved for storage, hash and EXIF extraction; there is no transcoding or metadata removal. EXIF GPS classification and coordinate validation remain server-side. Empty/octet-stream browser MIME and the common image/jpg alias are tolerated only when the extension and actual decoded image agree.

The user's specific failing photograph was not supplied, so the exact byte defect in that individual file cannot be certified. The faulty terminal-byte assumption is established by source trace and a regression fixture. A genuinely truncated camera file still must be rejected rather than relabeled as missing GPS.

## Files for this change

- backend/src/main/java/edu/institution/ims/monthlyprogress/ProgressPhotoValidator.java
- backend/src/main/java/edu/institution/ims/monthlyprogress/MonthlyProgressOverviewResponse.java
- backend/src/main/java/edu/institution/ims/monthlyprogress/MonthlyProgressService.java
- backend/src/main/java/edu/institution/ims/exception/GlobalExceptionHandler.java
- backend/src/main/resources/application.yml
- backend/src/test/java/edu/institution/ims/monthlyprogress/ProgressPhotoValidatorTest.java
- backend/src/test/java/edu/institution/ims/monthlyprogress/MonthlyProgressIntegrationTest.java
- backend/src/test/java/edu/institution/ims/monthlyprogress/MonthlyProgressMultipartLimitTest.java (new)
- frontend/app/student/monthly-progress/page.tsx
- frontend/lib/monthly-progress.ts
- frontend/next.config.ts
- MONTHLY_PROGRESS_PHOTO_UPLOAD.md (new)

Existing role-login changes were preserved and are separate from this feature. No database migration is needed.

## Checks

From backend:

```powershell
mvn.cmd '-Dtest=ProgressPhotoValidatorTest,MonthlyProgressIntegrationTest,MonthlyProgressMultipartLimitTest' -DreuseForks=false test
```

From frontend:

```powershell
npm.cmd run typecheck
node --test tests/*.test.cjs
npm.cmd run build
```

Test fixtures under backend/target/exif-fixtures include valid GPS, invalid GPS, missing capture time, no-GPS JPEG, PNG, corrupt and oversized files. The unit suite also checks `.jpeg`, a valid JPEG above the old 5 MiB limit, harmless trailing padding, disguised PNG content, PNG CRC corruption and truncation. The multipart test uses a real embedded servlet server and authenticated HTTP requests, not MockMvc's multipart builder, to check both the actual 20 MiB per-file and 21 MiB aggregate request limits. Tests use H2 databases; no deployed database is reset.

No commits, pushes or deployments were performed.

## Actual results

- ProgressPhotoValidatorTest: 9 passed.
- MonthlyProgressIntegrationTest: 7 passed, including report submission, private-file access, mentor authorization and GPS metadata mapping.
- MonthlyProgressMultipartLimitTest: 1 passed, exercising both file and aggregate request limits against a real HTTP server.
- Frontend existing Node tests: 7 passed.
- Frontend TypeScript check and production webpack build passed.
- git diff --check passed. No frontend lint script exists.
- An initial real multipart run exposed a Tomcat connection reset on oversized requests; after the bounded 23 MiB drain allowance, both cases return clean 413 responses.
- Tests used isolated H2 databases. Deployed Vercel/Render ingress and the user's particular photograph were not available for verification. No commit, push or deployment was performed.
