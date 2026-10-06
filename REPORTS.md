# Reports Center and attendance exports

Admin and Faculty Mentor navigation now includes **Reports** and **Attendance**. The existing dashboard export dialog remains available for category and individual field selection. The Reports Center adds report types, authorized student selection, filters, paginated previews and direct Excel/CSV downloads. All data queries use the current application database. The historical workbook is never read by production code and supplies no report records.

## Historical reference inspection and mapping

Reference: `III cell Intership Project.xlsx`. All five sheets were inspected through their workbook XML, including all populated cells, style references, merged regions, dimensions, hidden columns/rows, grouping, formulas, filters, row heights and column widths. Rendered previews of all five sheets were also reviewed. The source contains no conditional formatting and no frozen panes. Attendance has 137 formula cells, largely shared weekday/date formulas. The other sheets have no formulas. Totals in the source are stored values, not a trustworthy source of current counts.

| Historical sheet (original spelling) | Current report / database mapping |
| --- | --- |
| `Main report ` | Student / Faculty Mentor Allotment. Student accounts/profiles, active mentor assignments, mentor profiles, onboarding and internship details. Existing custom dashboard exports continue to support additional profile columns. |
| `DATA reguired  from students` | Stipend/placement and diary issuance reports use internship details and onboarding. Its historical evaluation-completed and diary-collected columns cannot be populated from the current database. |
| `Marks report format ` | Inspected as the marks/seminar reference. The report is marked unavailable because there are no evaluation, project, seminar or marks entities. |
| `CDC self  Dept wise student rep` | Source summaries, stipend analysis and distribution, company distribution, company/stipend and placement analysis, source comparison and actual onboarding workflow analysis. Company is the stored internship company name, not an invented company entity. |
| `Attedance report ` | Attendance Records and Student Attendance Summary. Stored attendance records and existing evidence, joined to authorized students, internship details and active mentor assignments. The historical wide daily calendar is adapted to a sortable record table, with one attendance record per row. |

## Excel design

Exports reuse Apache POI `SXSSFWorkbook` and the existing CSV writer. No additional Excel library was added. The historical Times New Roman 12-point body, bold headings, thin grid borders, wrapped headers, merged title bands, white body, `#FBDAD7` pale pink headers, `#B3CEFB` blue title band, `#FFFF00` attendance date headers and `#BFBFBF` summary totals are reused. The company/source summaries retain the source sheet's plain white table headers. The legacy custom export gains the same font and grid treatment.

Useful frozen headers/identity columns and auto filters are added. Data cells are not merged, so sorting and filtering remain reliable. Dates/timestamps are typed and formatted consistently. Stipends are numeric with an INR format. Percentage values are calculated by SQL as values from 0 to 100 and displayed with a percent suffix. Generated time, principal email, authorized scope, selected IDs, applied filters, report definition and record count are on Report Details. No historical session or institution name is silently copied into current metadata.

CSV has UTF-8 BOM, escaped quotes/commas/newlines, empty null values and formula-injection protection. Both formats use the same ordered field allowlist and filtered data query. Excel title bands, metadata and summary footer rows are presentation content; CSV contains the corresponding selected data columns and records.

## Supported report types

`ROSTER`, `OVERALL`, `STIPEND`, `STIPEND_DISTRIBUTION`, `COMPANY`, `COMPANY_STIPEND`, `COMPARISON`, `PLACEMENT`, `PLACEMENT_COMPANY`, `WORKFLOW`, `ATTENDANCE`, `ATTENDANCE_SUMMARY`, `DIARY_ISSUANCE`.

Source values are `DEPARTMENT`, `CDC`, `SELF`, with unrecorded sources kept separate. Missing stipend/offer data is not classified as unpaid/no offer. Placement means the existing `full_time_employment_offered` flag. Workflow uses the existing onboarding states, including `NOT_SECURED` and `INTERNSHIP_DETAILS_COMPLETE`. It does not assert that internship work has started or completed.

Attendance supports date range, result and verification status, student name/UID/email search, one or multiple student selections, company, source, branch, academic year, semester and existing workflow filters. The student summary shows stored-record count, present, absent, verified, unverified and present/stored-record percentage. Missing submissions are not converted into absent records. Working days and working-day attendance percentage are unavailable without a calendar/scheduling model. `Submitted at` is the stored submission timestamp, not check-in/check-out.

## API additions

The existing `DashboardReportController` now also maps to `/api/v1/admin/reports` and `/api/v1/mentor/reports`. The original dashboard paths remain supported.

- `GET /types`: report types, availability explanations, column allowlists, supported filters and scope.
- `POST /table`: paginated preview (50 rows) with selected columns, total matching records and attendance statistics.
- `POST /generate`: all matching authorized records as Excel or CSV, regardless of the current preview page.
- Existing `/options`, `/preview` and `/export` behavior remains compatible.

Request:

```json
{
  "reportType": "ATTENDANCE",
  "fields": ["uid", "studentName", "date", "result", "verification"],
  "filters": {"historyFrom": "2026-10-01", "historyTo": "2026-10-06"},
  "studentIds": [],
  "page": 0,
  "format": "XLSX"
}
```

An empty student selection means all within the authorized filtered scope. Explicit IDs must be positive, unique and authorized, with a maximum of 200. Fields are validated against the selected report type. Unknown report types, fields, filters, unavailable types, invalid filter values and reversed date ranges are rejected.

## Authorization and performance

Existing authentication, role URL guards, password-change enforcement and method security remain in place. Admin reporting scopes to institutional student accounts. Mentor reporting always requires an active assignment to the authenticated mentor, including attendance history and stipend/offer reporting requested for assigned students. IDs are checked against unfiltered authorized membership before additional filters. Mixed ID lists containing an unauthorized student fail with 403, even when another filter would hide that student. The frontend picker queries the same protected roster API.

Queries reuse the existing report repository scope and one-row-per-student projection. Aggregates run in SQL. Attendance uses set-based joins to records/evidence; there are no per-student queries. Previews are limited to 50 rows and exports read batches of 250 within repeatable-read transactions. The streaming writer retains a limited window of Excel rows. Temporary downloads are deleted after streaming or generation failure. No database migrations or new domain models are required.

## Unavailable requirements

- No department or section student field. Branch is used with its actual label. Academic year and semester filters are available; a separate academic-session entity is absent.
- No selected/joined/not-joined or completed/ongoing lifecycle field. Actual onboarding workflow is reported instead, clearly labeled.
- No evaluation-completed/pending, seminar/project scores, evaluation dates, score ranges or remarks.
- No diary-submitted/collected model. Diary issuance is reported separately.
- No working-day calendar, attendance check-in or check-out. The stored-record denominator is explicit.

The UI lists participation, evaluation/diary submission and seminar reports as unavailable with the reason. Their APIs reject generation rather than fabricate data.

## Validation

The existing report integration suite is extended to generate every available report in both formats, open each Excel file, check ordered columns/merged titles/freeze panes/fonts, exercise unknown data, attendance history/date filters and summaries, and reject unauthorized IDs, arbitrary fields/filters and unavailable types. All fixture records reside in an isolated H2 test database. Optional `-Dreport.previewDir=...` saves these test exports for visual review; they are not production data or delivered historical-data reports.

Run `mvn -Dtest=DashboardReportIntegrationTest test` or `mvn test` in backend. Run `npm run typecheck` and `npm run build` in frontend.

Verified on 6 October 2026:

- Report integration suite: 18 tests passed, including the 13 existing tests and 5 added tests.
- All 13 supported Excel exports were opened and rendered for visual review. CSV exports were generated for the same report types. Leading-zero UIDs, numeric INR stipends, typed dates, ordered columns, fonts, merged titles, frozen panes, scope and date filtering were checked.
- Frontend type check and production build passed; all four new pages are included.
- Full backend suite: 162 tests, 1 failure and 7 errors. The unchanged Git baseline was tested separately: 157 tests, the same 1 failure and 7 errors. The seven errors are transient User references in `MentorAccountIntegrationTest` / `MentorAssignmentIntegrationTest` cleanup. The failure is nanosecond versus database microsecond timestamp precision in `OpportunityManagementIntegrationTest.backendControlsPublishAndCloseLifecycleAndTimestamps`. These unrelated modules were left unchanged.
- No live authenticated browser session or production database was used for verification. Backend security/export integration tests use the existing isolated H2 setup.

## File manifest

Modified:

- `backend/src/main/java/edu/institution/ims/dashboard/report/DashboardReportController.java`
- `backend/src/main/java/edu/institution/ims/dashboard/report/DashboardReportRepository.java`
- `backend/src/main/java/edu/institution/ims/dashboard/report/DashboardReportWriter.java`
- `backend/src/test/java/edu/institution/ims/dashboard/report/DashboardReportIntegrationTest.java`
- `frontend/components/AppFrame.tsx`

Added:

- `backend/src/main/java/edu/institution/ims/dashboard/report/InstitutionalReportService.java`
- `backend/src/main/java/edu/institution/ims/dashboard/report/ReportTableRequest.java`
- `frontend/components/ReportsCenter.tsx`
- `frontend/app/admin/reports/page.tsx`
- `frontend/app/admin/attendance/page.tsx`
- `frontend/app/mentor/reports/page.tsx`
- `frontend/app/mentor/attendance/page.tsx`
- `REPORTS.md`
