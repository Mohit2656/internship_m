# Student attendance summaries

Dashboard attendance tables now start from unique student accounts or current mentor assignments, rather than individual absent-day report rows. The existing paginated admin directory retains its distinct account query and filters; its search also includes account names and UIDs before profile completion.

Backend endpoints: GET /api/v1/{admin|mentor}/attendance/students and GET /api/v1/{admin|mentor}/attendance/students/{studentId}. Existing role URL authorization remains in force, with an additional active-assignment check before mentors can read a student's history.

Percentages use actual active attendance records, grouped by student and date. PRESENT / (PRESENT + ABSENT) is rounded to one decimal; zero recorded applicable days returns null and displays an em dash. Future dates, dates outside the record's internship period, weekends, configured holidays, approved leave and unconfigured hybrid weekdays are excluded. Multiple related records cannot count a date twice. Missing unrecorded days are not invented by this read-only feature; the existing automatic absence process remains responsible for recording overdue absences. Historical records remain available in chronological order even when excluded from totals.

Names in the dashboard summary open an attendance panel with totals, dates, status, actual submission times, liveness and late submission flags. The admin directory has a View Attendance button. Profile links and existing approval/workflow functionality remain available. Mentor deletion controls moved from the former summary table to individual history rows.

Changed files:
- backend/src/main/java/edu/institution/ims/attendance/StudentAttendanceDirectoryService.java
- backend/src/main/java/edu/institution/ims/attendance/StudentAttendanceDirectoryController.java
- backend/src/main/java/edu/institution/ims/attendance/AttendanceRepository.java
- backend/src/main/java/edu/institution/ims/studentprofile/AdminStudentService.java
- backend/src/test/java/edu/institution/ims/attendance/StudentAttendanceDirectoryServiceTest.java
- frontend/components/AttendanceAbsenceOverview.tsx
- frontend/app/mentor/dashboard/page.tsx
- frontend/app/admin/students/page.tsx
- frontend/tests/daily-absence-dashboard.test.cjs

Checks performed:
- Offline Maven, only StudentAttendanceDirectoryServiceTest: 4 passed. Backend source and tests compiled.
- node --test tests/daily-absence-dashboard.test.cjs: 4 passed, including unique summaries, selected-student panel and history display.
- npm.cmd run typecheck: passed.
- git diff --check: passed.

No full test suite, database suite, migration, commit, push or deployment was performed. Browser visual interaction was not exercised.
