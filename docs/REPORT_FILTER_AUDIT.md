# Local report testing and retained dummy data

The existing local run at **http://localhost:3010** is connected to the retained
report demo schema on local PostgreSQL. The backend still uses port 8080.
Sign out of any previous session and choose **IIIC Incharge Login**:

| Account | Email | Password |
| --- | --- | --- |
| IIIC/Admin | `test_report_admin@local.test` | `LocalReportTest!2026` |
| Mentor A | `test_report_mentor_a@local.test` | `LocalReportTest!2026` |
| Mentor B | `test_report_mentor_b@local.test` | `LocalReportTest!2026` |

Open **Reports**. There are 60 students named `TEST_REPORT_STUDENT_01` through
`TEST_REPORT_STUDENT_60`, with UIDs `TEST_REPORT_UID_01` through `60`.
Active student logins use `test_report_student_01@local.test`, etc., with the
same dummy password. Students 03 and 60 are deliberately deactivated.

The fixture reference date is **2026-10-30**. Attendance dates are October 29
and 30. Completion statuses and daily attendance filters use the application's
clock: future/ongoing dates will naturally change classification over time.

Your original `public` schema was not changed. A before/after read-only check
confirmed the same 3 students, 1 profile, 1 internship and 1 assigned session.
Dummy rows live in the separate local schema
`ims_report_demo_60d7ed948b3b4c62b0da1298b5330a4d`.
No Render, production database, or remote database was used.

## Manual checks

Use the student roster unless another report is specified. Counts below include
inactive accounts unless you explicitly select Account active = YES.

| Filter or report | Expected result on the fixture date |
| --- | --- |
| No filters / completion ALL / Clear filters | 60 students, pages of 50 and 10 |
| Assigned academic session `TEST_REPORT_SESSION_A` | 30 students; historical closed session |
| Assigned academic session `TEST_REPORT_SESSION_B` | 29 students; active session |
| Student 60 | No assigned academic session |
| Completion = COMPLETED | Students 01–03; 3 records |
| Completion = COMPLETION_PENDING | Students 04–06; 3 records |
| Completion = IN_PROGRESS | Students 07–09; 3 records |
| Completion = NOT_STARTED | Students 10–12; 3 records |
| Workflow = INTERNSHIP_DETAILS_COMPLETE | Students 01–12; 12 records |
| Company contains `ALPHA%_` | Students 01–06; 6 records; percent/underscore are literal characters |
| Company contains `BETA` | Students 07–12; 6 records |
| Stipend = PAID / UNPAID | 6 / 6 students; unknown stipend remains separate |
| Full-time employment offered = YES | Students 03, 06, 09, 12; 4 records |
| Account active = NO | Students 03 and 60; 2 records |
| Mentor A login, unfiltered roster | 11 assigned students: 01 and 03–12 |
| Mentor B login, unfiltered roster | 6 assigned students: 13–18 |
| Monthly progress, no filters | 12 reports: 8 approved, 2 pending, 2 rejected |
| Attendance, no filters | 8 records: 7 present, 1 automatic absence |
| Attendance liveness | 2 verified, 2 failed, 2 historical, 1 not performed, 1 not applicable |
| Session A + company ALPHA + completed + paid | Students 01 and 03; 2 records |
| Nonmatching search or contradictory session/profile year | Zero matching rows where appropriate |

Student 02 is officially completed but deliberately has no optional student
profile, current mentor assignment, or attendance. Admin completion reports must
still include that student. Student 03 is completed but inactive, and remains in
historical reports. Profile academic year deliberately differs from assigned
session: these are distinct filters, not interchangeable labels.

Dummy internship documents contain a clearly marked local-demo PDF. Monthly
photo attachments are generated local-demo PNGs. Their files are retained in
`.local-run/report-demo-storage`.

## Findings and query changes

- `DashboardReportRepository.scope()` previously translated completion status
  `ALL` into `i.id is not null`. In the 60-student fixture that silently removed
  48 students without an internship-details row from the roster and generic
  student exports. ALL now removes the completion predicate. Individual
  completion statuses still require an internship record.
- `InstitutionalReportService.query()` previously counted the repository scope
  before applying selected student IDs, paid/unpaid and full-time-offer filters.
  It now counts the final filtered student SQL, so completion summaries agree
  with the selected population.
- The session filter previously accepted a raw numeric ID. The catalog now
  returns named choices from `academic_sessions`, using each student's actual
  `users.academic_session_id`. Mentor choices are limited by active mentor
  assignments; closed historical sessions remain selectable.
- Both report APIs now trim filters, normalize recognized select values without
  case sensitivity, clear select ALL consistently, and share date validation.
  Unknown values and unauthorized filters still fail on the server.
- Empty comparison aggregates now use `coalesce(sum(...),0)` instead of returning
  null counts. Monthly report pagination orders by
  `studentId,monthNumber,recordId`, giving related rows a stable order.
- `ReportsCenter.tsx` now ignores fulfilled responses from aborted requests for
  report options, student selection, and previews. An older response cannot
  overwrite the current filtered table.

The existing base student query already uses LEFT JOINs for optional profile,
mentor, internship and history data. Report-specific joins requiring an actual
attendance/monthly/completion record remain intentional. No report migration or
database structure change was needed.

The original local data contains **zero officially completed internships**. It
therefore cannot establish the cause of a particular allegedly completed real
student being missing. Official COMPLETED status requires an IIIC completion
decision; submitting internship details alone does not grant that status. This
rule was preserved. The dummy fixture creates three genuine completion decisions
through the completion service, including the missing-optional-data case.

## Coverage

All 15 available report types were checked against semantic expectations derived
from the seeded students, including API tables, all table pages and CSV records.
Excel data rows and its separate totals footer are checked; PDF identity exports
check the same selected records. Existing PDF writer tests cover wide fields,
wrapping and page continuation. CSV is checked for every applicable filter case;
Excel/PDF parity is additionally checked for every report and all three viewers.

| Report | Unfiltered Admin table rows |
| --- | ---: |
| COMPLETIONS | 12 |
| ROSTER | 60 |
| OVERALL | 4 |
| STIPEND | 4 |
| STIPEND_DISTRIBUTION | 2 |
| COMPANY | 2 |
| COMPANY_STIPEND | 2 |
| COMPARISON | 6 |
| PLACEMENT | 4 |
| PLACEMENT_COMPANY | 2 |
| WORKFLOW | 6 |
| MONTHLY_PROGRESS | 12 |
| ATTENDANCE | 8 |
| ATTENDANCE_SUMMARY | 60 |
| DIARY_ISSUANCE | 4 |

The matrix takes filter definitions from the actual APIs and checks every
offered select choice, lower-case/whitespace variants, clearing, select ALL,
valid/no-match text, literal wildcard characters and applicable date windows.
The full filter inventory is:

| Filter ID | Coverage |
| --- | --- |
| `search` | Student name, UID, email, case, no match |
| `studentId` | Exact student, no match, clearing |
| `branch` | Every API-provided branch choice |
| `completionSearch` | Name, UID, company, case, wildcard literals |
| `academicSessionId` | Both real assigned session IDs; historical session |
| `completionStatus` | ALL and every actual completion state |
| `academicYear` | Profile year independent of assigned session |
| `semester` | All eight semesters |
| `profileStatus` | COMPLETE and INCOMPLETE |
| `workflowStatus` | Every offered workflow state |
| `company` | Both companies, case, percent/underscore, no match |
| `internshipMode` | All four modes |
| `internshipSource` | DEPARTMENT, CDC, SELF |
| `mentorId` | Both mentors and no match; admin authorization |
| `mentorAssignment` | Assigned and unassigned |
| `companyType` | All four types |
| `paid` | Paid, unpaid, unknown handling |
| `fullTimeOffered` | Yes, no, unknown handling |
| `accountActive` | Active and inactive; historical completed student |
| `attendanceDate` | Generic dashboard reference-date filtering |
| `historyFrom` / `historyTo` | Inclusive history boundaries, empty windows, reversed/invalid dates |
| `attendanceStatus` | Every offered value on the applicable report |
| `verificationStatus` | Verified, failed, historical, not performed, not applicable and not submitted where offered |
| `monthlyStatus` | Approved, pending mentor review, rejected |

Combined filters, selected student IDs, clear/reset, 50+10 page navigation,
unfiltered exports exceeding the 250-row export batch size, guessed IDs,
unauthenticated/student access, and mentor isolation are also checked.

Participation, evaluation and seminar report types were already unavailable and
remain unavailable. Separate workplace-approval, internship start/end-date, and
department filters do not exist in these APIs; unsupported filter keys are
rejected. Branch, history dates, monthly review status and workflow are tested
where they actually exist.

## Files changed for report work

Production changes are limited to:

- `backend/src/main/java/edu/institution/ims/dashboard/report/DashboardReportCatalog.java`
- `backend/src/main/java/edu/institution/ims/dashboard/report/DashboardReportRepository.java`
- `backend/src/main/java/edu/institution/ims/dashboard/report/InstitutionalReportService.java`
- `frontend/components/ReportsCenter.tsx`

Added regression/seeding files:

- `backend/src/test/java/edu/institution/ims/dashboard/report/ReportFilterRegressionIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/dashboard/report/ReportFilterRegressionPostgresIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/dashboard/report/ReportDemoSeedPostgresTest.java`
- `frontend/tests/report-filters.test.cjs`
- This guide.

Earlier academic-session lifecycle changes in the workspace were preserved.

## Local management and evidence

Run these commands from the repository root. The retained manifest, connections,
logs and helper scripts are local, ignored files under `.local-run`.
The manifest includes dummy account credentials, not the real DB password.

```powershell
# Return the existing localhost:3010 run to your original local data.
python .local-run/manage-report-demo.py restore

# Select the retained demo again after restoring.
python .local-run/manage-report-demo.py start

# Check the selected backend and retained dataset.
python .local-run/manage-report-demo.py status

# Optional: after restore, permanently remove only the owned demo schema.
python .local-run/manage-report-demo.py remove
```

The switch helper verifies backend PID, Java process, startup timestamp and log
before replacing it. Cleanup accepts only its recorded local
`ims_report_demo_<32 hex digits>` schema. Disposable regression schemas are
separate and dropped after each run. The retained manual dataset stays available
at the user's explicit request.

Evidence files include `report-demo-fixture.json`, `report-demo-live-checks.json`,
`report-filter-matrix-completed.json`, the export matrix JSON, Maven
Surefire reports, and frontend test/build logs under `.local-run`.

Backend results:

- New PostgreSQL filter/security matrix: 7 methods passed in the combined run.
  That run initially reported 7/8 because the new export test treated the
  historical Excel totals footer as a student row. After correcting the test
  reader for totals, typed dates and wrapped PDF text, the export method passed
  **1/1** in its focused rerun. All 8 distinct new regression methods passed
  across these runs. No export-format production change was needed.
- Existing PostgreSQL report regression suite: **25/25 passed**, including
  report exports, authorization and export batching beyond 250 students.
- Existing PDF writer suite: **5/5 passed**.
- Retained demo seeder: **1/1 passed**.
- Disposable schemas were removed after every test run. The manual demo schema
  remains available on request.

Automated frontend filter tests passed **19/19**. The full frontend suite passed
**270/271**; the existing `student-update.test.cjs:145` dateTime assertion also
fails on the unchanged baseline (8/9 baseline tests). The production Next build,
including TypeScript checking, passed.

Live checks authenticated through **localhost:3010**, exercised all 15 report
tables, checked expected student counts, session/company/completion filters,
50+10 pagination, and mentor scopes (11 and 6). The actual `ReportsCenter`
component was also rendered with live API responses for all 15 reports and all
pages; displayed row counts and student identities matched those responses.
This component check is recorded in `report-demo-component-checks.json`.
Browser visual verification was
blocked: computer-use tooling stopped because it could not confidently determine
the Windows browser URL. No visual browser verification is claimed.
