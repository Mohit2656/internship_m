# Report PDFs, profile prefill and student updates

Report exports reuse the existing PDFBox dependency and the same authenticated
report queries as Excel and CSV. Monthly PDF routes retain their existing
student ownership, active mentor assignment and admin checks. Dashboard and
institutional PDF exports use the selected fields, filters and authorized
students, and include all matching records rather than only the preview page.

## PDF formatting and deployment

PDF exports use PDFBox's standard PDF fonts: `Times-Roman` for regular text
and `Times-Bold` for headings. These are PDF standard fonts, not Microsoft
Times New Roman files, and are not embedded. PDFBox supplies their metrics,
so creating these PDFs requires no external font files. See the
[PDFBox font FAQ](https://pdfbox.apache.org/2.0/faq.html#what-fonts-do-i-need-on-my-system).

PDF exports work after a normal application deployment without an
operating-system font installation or an external runtime font directory.
`REPORT_PDF_FONT_DIRECTORY` and the Java `report.pdf.font-directory` property
are no longer read. Existing values are harmless and may be removed manually
from Render's environment configuration.

There is no manual Render font configuration: no font disk, Secret Files,
SSH upload or font installation step is required. No Microsoft font files
are copied from Windows, committed or redistributed. The PDF routes retain
the existing authenticated report permissions and download behavior.

Text wraps using font measurements, including long words and long metadata
values, and PDF pages have page numbers. Unsupported glyphs produce a clear
error instead of silently changing student names or report content. Browser
report previews retain their existing styles. CSV is plain data and carries
no font.

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

Targeted PDF tests check regular/bold report fonts, text preservation, page
bounds and multi-page output. Any synthetic PDF review artifacts under
`backend/target/pdf-report-review/` are ignored by Git.

The frontend's lightweight Node harnesses can resolve real shared TypeScript
components without modifying existing fixtures by loading the test-only resolver:

```powershell
node --require ./tests/register-tsx.cjs --test tests/student-update.test.cjs tests/profile-prefill.test.cjs tests/monthly-review.test.cjs tests/liveness-reports.test.cjs tests/auth-routing.test.cjs tests/workplace-paused.test.cjs
```

Relevant backend coverage lives in `DashboardReportIntegrationTest`,
`DashboardReportPdfWriterTest`, `MonthlyProgressPdfServiceTest`,
`ReportPdfFontDeploymentTest`,
`MonthlyProgressIntegrationTest`, `MonthlyStudentUpdateTest`,
`StudentProfileIntegrationTest`, `InternshipOnboardingIntegrationTest`,
`InternshipDetailsIntegrationTest`, `MentorAssignmentIntegrationTest`,
`ProfessionalProfileIntegrationTest`, and `StudentSkillIntegrationTest`.

Primary changed areas are the shared PDF font/text helpers and report writers,
dashboard/Reports Center export controls, profile prefill/form/service,
student timestamps in backend responses and mentor/admin views, and their
targeted test coverage. No database migrations or new dependencies were added.

## Font-independent export fix

The focused run on 15 October 2026 passed 11 tests: two report integration
tests covering all export formats and PDF authorization, five report writer
tests, two monthly PDF tests, and two isolated-JVM deployment font tests.
The deployment tests remove or invalidate the real font-directory environment
variable and legacy Java property, save a PDF, and verify both regular and
bold font resources. Short, long and wide rendered samples were visually
reviewed with no clipping or overlapping content.

```powershell
mvn.cmd -o "-Dtest=ReportPdfFontDeploymentTest,DashboardReportPdfWriterTest,MonthlyProgressPdfServiceTest,DashboardReportIntegrationTest#pdfExportsReuseAuthorizedSelectionAndRejectOtherStudentsBeforeGeneration+everyAvailableInstitutionalReportPreviewsAndExportsAllFormats" -DreuseForks=false test
```

Exact changed files for this fix:

- `backend/src/main/java/edu/institution/ims/reports/ReportPdfFonts.java`
- `backend/src/main/java/edu/institution/ims/reports/ReportPdfText.java`
- `backend/src/main/java/edu/institution/ims/monthlyprogress/MonthlyProgressPdfService.java`
- `backend/src/main/java/edu/institution/ims/exception/GlobalExceptionHandler.java`
- `backend/src/test/java/edu/institution/ims/reports/ReportPdfFontDeploymentTest.java`
- `backend/src/test/java/edu/institution/ims/dashboard/report/DashboardReportPdfWriterTest.java`
- `backend/src/test/java/edu/institution/ims/monthlyprogress/MonthlyProgressPdfServiceTest.java`
- `REPORT_PDF_AND_STUDENT_UPDATES.md`

The updated backend must be deployed before Render uses this fix. No font
environment variables, disks or licensed font files are required for that
deployment. No commit, push or deployment was performed during implementation.
