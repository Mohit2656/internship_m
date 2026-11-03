# Dashboard and attendance update verification

## Implemented behavior

- Student, Mentor and IIIC share the existing Student visual language through `WorkspaceSidebar`, `DashboardLayout`, shared status styling and global design tokens. Mobile grids, table containers, dialogs, focus restoration and academic-session date fields were corrected.
- Each role has one Reports disclosure containing the existing Weekly Report and Monthly Report links. Active child links, keyboard navigation and mobile drawer behavior are preserved. The institutional report generator remains available as Report Center.
- Mentor and IIIC Attendance pages show student-specific individual records, GPS/location evidence, review state and permitted actions. Separate `/mentor/attendance-report` and `/admin/attendance-report` pages contain student/date summaries, independent filters and existing PDF/Excel exports.
- Students explicitly select A or B while completing/editing their profile. Missing legacy sections remain unassigned and require review. Computer Engineering and 8th Semester are fixed and validated by the API. Section grouping/filtering is available in directories and attendance/weekly/monthly reporting.
- Students submit a dated late-attendance request with a reason and optional supporting details. The pending queue contains actual requests only; completed decisions appear in history. Assigned mentors are authorized on the server; IIIC handles students without an active mentor. Notifications reuse the existing persistent inbox and unique event keys.
- Approved late requests reuse an automatic absence or create the applicable attendance record once. Manual approval does not fabricate GPS/liveness evidence. Existing student captures, completed internships and non-applicable dates are protected. Rejected requests permit resubmission only when explicitly allowed.

## Root causes and corrections

The old late-attendance screen listed assigned students and toggled a same-day permission. It did not represent student-submitted requests, pending queues or decision history. V51 introduces persisted attempts and decisions with a unique active request for each student/internship/date. Request submission, reviewer authorization, attendance updates and notifications run transactionally.

Normal attendance notifications were emitted before the final attendance/evidence state was ready. They now run after finalization and retain transaction rollback and duplicate protection.

Attendance records and summary reporting previously shared navigation/content. Their pages now use distinct components and requests while reusing existing history and institutional reporting infrastructure.

Academic forms and database constraints previously allowed multiple branches/semesters and lacked a required section. V52 audits old values before normalization, preserves IDs/UIDs/rolls/session history and valid A/B choices, and leaves unknown sections unset. It also preserves conflicting legacy roll identities for explicit review instead of deleting or randomly reassigning students.

## Database changes and safety

- `V51__late_attendance_requests.sql`: request/decision history, duplicate constraints/indexes, distinct manual attendance submission/verification types.
- `V52__standardize_student_academics.sql`: section fields, academic normalization audit, CE/8 checks/defaults, A/B checks and section-based roll uniqueness.
- Historical migrations and `docs/app_updates_V36_V41.sql` are unchanged.
- Database integration tests use H2 or disposable PostgreSQL schemas on loopback. Browser fixtures intercept API requests and do not seed the development database. No production database is used.
- A PostgreSQL custom-format backup and a read-only preservation snapshot precede local migration. Verification compares attendance, evidence, internships, mentor assignments, reports, sessions and student identity hashes.

## Main implementation files

| Area | Files |
| --- | --- |
| Shared dashboard/navigation | `frontend/components/WorkspaceSidebar.tsx`, `DashboardLayout.tsx`, `AppFrame.tsx`, `StudentSidebar.tsx`, `MentorSidebar.tsx`, `AdminSidebar.tsx`, `frontend/app/globals.css` |
| Individual attendance/report separation | `frontend/components/AttendanceRecords.tsx`, `AttendanceAbsenceOverview.tsx`, `ReportsCenter.tsx`, `frontend/app/mentor/attendance/page.tsx`, `frontend/app/admin/attendance/page.tsx`, both roles' `attendance-report/page.tsx` |
| Late requests | `frontend/components/StudentLateAttendanceRequests.tsx`, `LateAttendancePermissions.tsx`, `frontend/lib/late-attendance.ts`, `frontend/app/student/attendance/page.tsx`, `backend/src/main/java/edu/institution/ims/attendance/LateAttendanceRequestController.java`, `LateAttendanceRequestService.java`, attendance submission/review classes, mentor/student notification services |
| Academic profiles/directories | `frontend/components/ProfileForm.tsx`, `ProfileDetails.tsx`, `AcademicSectionFilter.tsx`, `frontend/lib/student-profile.ts`, `profile-completion.ts`, Mentor/IIIC directories, backend student-profile/account/import DTOs and services |
| Academic/report fields | `frontend/components/WeeklyProgress.tsx`, `MonthlyProgressReportList.tsx`, backend dashboard report catalog/repository/service and monthly PDF/response services |
| Dialogs/interactions | `frontend/components/ModalDialog.tsx`, `DocumentPreviewModal.tsx`, reset/deletion buttons, workplace `InternshipDetailsForm.tsx`, opportunity and academic-session settings pages |
| Migrations | `backend/src/main/resources/db/migration/V51__late_attendance_requests.sql`, `V52__standardize_student_academics.sql` |
| Regression tests | Frontend profile, navigation, attendance, reporting, notification and workplace suites; backend academic migration/profile, late-request, attendance, notification, report and compatibility fixtures |

## Verification evidence

The final frontend suite has 306 passing tests, including required A/B profile selection, POST/PUT persistence, report navigation, attendance separation, manual evidence labels, workplace locking and request interactions. The optimized production build passes.

Focused PostgreSQL runs passed 37 late-request/notification/review tests and 48 academic/report tests. The broad UI audit covered 24 pages at 1920, 1366, 1024, 768 and 375 pixels: 120 layouts with no page overflow, plus 148 successful navigation/dialog/notification interaction checks and no runtime exceptions. These browser checks use controlled API fixtures; backend integration tests verify real database behavior.

Integrated browser verification passed another 150 checks (92 attendance/report checks and 58 profile/late-request checks), with no runtime exceptions or console errors. It covers independent filters/exports, role redirects, required A/B selection, student request submission/duplicate blocking, pending/history separation and review dialogs at all five widths. Screenshots were inspected for alignment and readability. On-screen report typography now follows the dashboard font; PDF generation remains unchanged.

The full backend run covered 801 cases: 520 passed, 280 opt-in PostgreSQL cases were skipped, and one rollback test exposed a shared temporary-upload-directory race between parallel test forks. Isolating monthly test storage fixed that issue; all 36 affected monthly/deletion/shared-workplace cases passed on rerun. All 521 non-opt-in backend cases now have passing results. Test fixture updates retain existing permission, rollback, evidence and session assertions while using the current import format, attendance states and allowed academics. Opportunity matching and other backend business logic were not changed for these fixture corrections.

V51/V52 were applied successfully to the backed-up local `internship_management` database. Before/after counts and hashes match for all 26 attendance records, 9 evidence records, the internship, mentor assignment, 3 monthly reports, academic sessions and student identity fields. There are still 3 student accounts and 1 profile; the profile is now CE/8. Unknown sections are intentionally left for student selection. No dummy requests were inserted into the application schema.

Ignored diagnostic artifacts are stored under `.local-run/`: `resume-frontend-tests.log`, `resume-frontend-build.log`, `resume-backend-full-tests.log`, `resume-backend-tests.log`, `resume-attendance-separation/`, `resume-profile-late/`, `resume-local-before-final.json`, `resume-local-after.json` and the custom-format database backup. The local frontend, backend and AI service run at ports 3010, 8080 and 8091 respectively.
