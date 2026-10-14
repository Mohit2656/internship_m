# Report PDFs, profile prefill and student updates

Report exports reuse the existing PDFBox dependency and the same authenticated
report queries as Excel and CSV. Monthly PDF routes retain their existing
student ownership, active mentor assignment and admin checks. Dashboard and
institutional PDF exports use the selected fields, filters and authorized
students, and include all matching records rather than only the preview page.

## Times New Roman configuration

PDFs embed the real Times New Roman regular and bold TrueType fonts. There is
no silent substitution with Helvetica or PDF's built-in Times-Roman.
Windows installations discover `times.ttf` and `timesbd.ttf` in the Windows
Fonts directory. Linux/macOS servers and containers must provide licensed
Times New Roman font files:

```text
REPORT_PDF_FONT_DIRECTORY=/opt/ims/report-fonts
```

Place `times.ttf` and `timesbd.ttf` in that directory and ensure the backend
process can read them. A Java system property, `report.pdf.font-directory`,
can also set the directory. The loader also recognizes the standard
`/usr/share/fonts/truetype/msttcorefonts`, `msttcore` and `/Library/Fonts`
locations. Font files are not redistributed in this repository or image.
Mount the directory read-only into a Docker deployment and set the environment
variable to its container path. Other export formats remain available when
fonts are missing; PDF exports return an explicit configuration error.

Text wraps using font measurements, including long words and long metadata
values. PDF pages have page numbers. Unsupported glyphs produce a clear
error instead of silently changing student names or report content. Actual
report previews use a report-specific Times New Roman class; the application
interface retains its existing font. CSV is plain data and carries no font.

## Authoritative fields and timestamps

Institutional profile values are supplied from the authenticated account and
existing stored profile. The prefill response identifies fields that the
student cannot edit. The backend enforces the same values on create/update,
independently of browser fields. Legitimate personal/contact fields remain
editable and legacy stored identity values are preserved.

Monthly reports use `submittedAt` as their last student update: the backend
sets it on submission/resubmission and mentor decisions do not overwrite it.
Other student records display their own persisted save/upload timestamps.
The shared display uses the application's Asia/Kolkata date/time convention
and explicitly handles records without a recorded timestamp.

## Verification artifacts

Targeted PDF tests check embedded font names, text preservation, page bounds
and multi-page output. Test PDFs and rendered PNGs are written to
`backend/target/pdf-report-review/` for visual review and are ignored by Git.
They contain synthetic test records only.

The frontend's lightweight Node harnesses can resolve real shared TypeScript
components without modifying existing fixtures by loading the test-only resolver:

```powershell
node --require ./tests/register-tsx.cjs --test tests/student-update.test.cjs tests/profile-prefill.test.cjs tests/monthly-review.test.cjs tests/liveness-reports.test.cjs tests/auth-routing.test.cjs tests/workplace-paused.test.cjs
```

Relevant backend coverage lives in `DashboardReportIntegrationTest`,
`DashboardReportPdfWriterTest`, `MonthlyProgressPdfServiceTest`,
`MonthlyProgressIntegrationTest`, `MonthlyStudentUpdateTest`,
`StudentProfileIntegrationTest`, `InternshipOnboardingIntegrationTest`,
`InternshipDetailsIntegrationTest`, `MentorAssignmentIntegrationTest`,
`ProfessionalProfileIntegrationTest`, and `StudentSkillIntegrationTest`.

Validation on 15 October 2026:

- 44 targeted frontend tests passed, including the unchanged workplace-paused fixture.
- 69 tests passed in the final clean backend rerun; the other targeted monthly
  review, mentor assignment, professional profile, skills and onboarding tests
  also passed in the preceding run.
- Frontend type checking and production build passed; backend packaging passed.
- Short, long, narrow and wide PDF samples were rendered and visually reviewed.
- Role links retain their responsive layout; white text contrast is 6.70:1 on
  the primary blue, 8.72:1 on hover and 10.36:1 when active. Live browser
  viewport checks were unavailable because no browser connection was enabled.

Primary changed areas are the shared PDF font/text helpers and report writers,
dashboard/Reports Center export controls, profile prefill/form/service,
student timestamps in backend responses and mentor/admin views, and their
targeted test coverage. No database migrations or new dependencies were added.
