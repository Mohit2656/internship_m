# Internship workflow enhancement

## Architecture and compatibility

The implementation extends the existing Next.js frontend, Spring Boot API, PostgreSQL/Flyway schema and Node AI service. It retains JWT authentication, the existing ADMIN (IIIC), MENTOR and STUDENT route boundaries, assigned-mentor authorization, resume upload history, monthly reports, Google Maps links, OpenStreetMap picker and GPS verification evidence.

IIIC is the existing institution-wide ADMIN role. The repository has no separate coordinator-assignment or institution/department access partition for ADMIN accounts. Resume activity is routed to active ADMIN accounts; mentor events are routed only to active assigned mentors. No new role or login behavior is introduced.

## Implementation by phase

| Phase | Implemented result |
| --- | --- |
| 1: Architecture | Reused existing role boundaries, persistent inboxes, resume storage/parser, internship registration, attendance evidence and dashboard components. |
| 2: Resume | Activity timestamps, IIIC upload alerts, student/IIIC correction editor, original extraction preservation and revision history. |
| 3: Opportunities | Deterministic scores, eligibility, ranked reports, historical snapshots, CSV export and deduplicated strong-match alerts. |
| 4: Duration and weeks | Inclusive automatic week count, transactional generation/backfill and student submission/mentor review/IIIC monitoring. |
| 5: Attendance | Assigned-mentor decisions, reason validation, immutable audit and student-wise lazy history with original GPS evidence. |
| 6: Integration | Role-specific inboxes, workflow links, dashboard activity and mentor resume access. |
| 7: Verification | Focused backend/frontend regressions, PostgreSQL rollback/concurrency and migration preservation checks; results below. |

## Resume review and timestamps

- The IIIC student directory reads saved profile, resume upload, internship and workplace activity times. The latter three are loaded in one batch query for the current directory page. Missing values are shown explicitly. Shared dashboard formatting uses `en-IN`, `Asia/Kolkata`, with date, time and IST.
- Each successful resume upload/replacement creates one durable IIIC notification keyed by the new resume ID. A failed upload produces no notification. Upload, notification and active-resume changes commit together; file storage is cleaned up on rollback.
- Parsing completion notifies the student. The existing parser supports skills, certifications, experience (title, company, start/end text), and education (degree, field, institution). These are the editable fields. Name, email, phone, graduation year, projects and numeric experience are not returned by this parser and are not fabricated.
- Corrections are append-only overlays in `resume_corrections`, with resume ID, revision, values, editor identity, source and edit time. Original AI tables and original resume files remain intact. Old resume versions and their correction history remain stored after replacement.
- Saves serialize on the existing professional-profile lock and require the currently active resume ID and revision. Concurrent or stale saves return 409. Student and IIIC sources are distinguished; repeating an identical save with the same source does not create another revision. Existing parsing guards prevent parsed resumes being automatically analyzed again.
- Matching consumes the latest correction overlay. Separately student-added skills remain available through the existing skill profile.

### Resume and notification endpoints

Existing endpoints remain compatible. Added endpoints:

| Method | Route | Access |
| --- | --- | --- |
| GET, PUT | `/api/v1/student/resume/analysis/corrections` | Own student |
| GET | `/api/v1/admin/students/{studentId}/resume/analysis` | IIIC |
| GET, PUT | `/api/v1/admin/students/{studentId}/resume/analysis/corrections` | IIIC |
| GET | `/api/v1/admin/notifications` | Own IIIC inbox |
| GET | `/api/v1/admin/notifications/unread-count` | Own IIIC inbox |
| PATCH | `/api/v1/admin/notifications/{id}/read` | Own IIIC notification |
| PATCH | `/api/v1/admin/notifications/read-all` | Own IIIC inbox |

The correction PUT body is `{resumeId, revision, values: {skills, experience, education, certifications}}`. GET returns original extraction, effective values, provenance and revision history. The existing student directory response adds `resumeUploadedAt`, `internshipDetailsUpdatedAt`, and `workplaceUpdatedAt`.

The existing notification table and DTO are reused for the IIIC inbox. Its existing `student_user_id` column references `users`, so active ADMIN recipients can be stored without copying a second notification schema. Existing student routes remain STUDENT-only. Mentor notifications retain the existing attendance foreign key and unique attendance event rule, with optional attendance ID plus unique mentor/event key for additional workflows. Visibility still requires the current mentor assignment.

## Opportunity recommendations

Publication now creates an immutable opportunity-specific report within the publication transaction. An IIIC user can recalculate after requirements or resume corrections change, view earlier snapshots, search/filter candidates, sort scores, open current profiles/resumes, and export the filtered table as CSV. CSV cells neutralize formula prefixes. Existing semantic evidence and talent-discovery APIs remain available and consume corrected resume values.

The deterministic score is `100 × (80 × requiredMatched/requiredCount + 20 × preferredMatched/preferredCount) / activeWeight`. A group with no specified requirements contributes neither points nor weight. Matching uses exact normalized skill names, without invented semantic percentages. If neither group is specified, the score is zero. Education, experience and certifications are shown as supporting evidence rather than being converted into unsupported numeric experience or eligibility claims.

Branch and semester restrictions are checked separately from the score. A minimum CGPA requirement is UNKNOWN because the current authoritative profile does not store CGPA; such students receive no strong-match alert. The report covers active students with completed institutional profiles and explicitly states this coverage. Unstructured requirements in descriptions require reviewer assessment. No sensitive personal characteristic contributes to scoring.

Strong-match notifications require ELIGIBLE status, all required skills, at least one matched criterion, and the configured threshold (default 75/100). The event key includes opportunity, eligibility, five-point score band and matching skill evidence. Repeated identical runs keep history but do not send another alert. Changed matching evidence or a changed score band can produce a new alert. Messages include organization, opportunity, score, skills, next steps and an explicit statement that the recommendation is not company selection.

| Method | Route | Access |
| --- | --- | --- |
| GET | `/api/v1/admin/opportunities/{id}/matching-runs` | IIIC history |
| GET | `/api/v1/admin/opportunities/{id}/matching-runs/{runId}` | IIIC snapshot |
| POST | `/api/v1/admin/opportunities/{id}/matching-runs` | IIIC recalculation |
| GET | `/api/v1/student/opportunities/{id}/matching-score` | Own student, visible published opportunity |

Closed opportunities retain read-only matching history. Draft recalculations do not alert students. Matching does not call the AI service during publication, so AI-service downtime does not prevent publication.

## Weekly periods and duration

Both dates are included: `ceil((daysBetween(start, end) + 1) / 7)`. End must be after start. The form recalculates immediately; the server ignores a supplied legacy `totalWeeks` value. A 1 November–30 December 2026 internship has nine periods, with the last period covering 27–30 December.

Generation runs transactionally at the existing `INTERNSHIP_DETAILS_COMPLETE` stage, alongside the existing monthly-report eligibility. Legacy registrations are backfilled when a weekly view opens. A parent-row lock and unique week/bounds key prevent duplicates. Unchanged schedules avoid writes. Valid legacy backfill normalizes only the derived stored week count, preserving dates and activity timestamps. Generation is bounded to 520 weeks per internship. Larger legacy records remain readable; monitoring reports a per-student warning and continues showing the other students.

Changed empty periods are archived. Matching periods retain their IDs. A date change affecting any submitted period, including a rejected report, returns 409 and rolls back the registration edit. Submitted work, reviews and original period dates are never deleted by reconciliation.

Students submit text reports for started, active periods, and may correct a rejected report using its current revision. Assigned mentors approve/reject with reviewer identity, time and immutable history. IIIC monitors student groups and status filters. Attendance counts read the existing records; GPS totals use the independent location-verification signal rather than treating overall/liveness verification as GPS.

| Method | Route | Access |
| --- | --- | --- |
| GET | `/api/v1/student/weekly-progress` | Own overview and periods |
| POST | `/api/v1/student/weekly-progress/{periodId}` | Own submission/resubmission |
| GET | `/api/v1/mentor/weekly-progress` | Assigned students, `{weeks,warnings}` |
| POST | `/api/v1/mentor/weekly-progress/{periodId}/approve` | Assigned mentor, current revision |
| POST | `/api/v1/mentor/weekly-progress/{periodId}/reject` | Assigned mentor, revision and reason |
| GET | `/api/v1/admin/weekly-progress` | IIIC monitoring, `{weeks,warnings}` |

See [weekly-progress.md](weekly-progress.md) for request shapes and workflow details.

## Attendance approval and mentor resume access

Mentor and IIIC attendance views reuse the student-wise expandable history. Mentors initially see student names; histories and review audits load on demand. Existing reports/export remain available in the mentor attendance page. Student history displays mentor status and feedback alongside the original submission/GPS information.

Mentor decisions are separate rows: PENDING initially, then MANUAL_REVIEW, APPROVED or REJECTED. Automatic absences are not reviewable. Rejection and manual review require a 5–1000 character reason. A manual review can be resolved; approved/rejected decisions are final. Stale and duplicate transitions return 409. Every committed transition stores the assignment, reviewer, timestamp, reason and revision in an append-only audit.

Approval requires the original GPS status VERIFIED or NOT_APPLICABLE. Camera submissions also require the existing liveness result VERIFIED. A mentor cannot turn failed GPS, missing workplace approval or missing liveness into verified evidence. Original attendance, coordinates, Maps links, location accuracy, distance and verification audit remain unchanged. Manual-review messages ask the student to contact the mentor; the existing one-submission-per-day policy remains intact.

New review API: `POST /api/v1/mentor/attendance/{id}/review` with `{status,revision,reason}`. Audit reads use `/api/v1/{student|mentor|admin}/attendance/{id}/review/history`, restricted to the student owner, current assigned mentor or IIIC. Existing attendance responses add a nested review summary, and directory summaries add submitted/GPS/pending/approved/rejected/manual-review counts.

Assigned mentors also gain read-only `/api/v1/mentor/students/{studentId}/professional-profile`, `/resume/download`, `/resume/analysis` and `/resume/analysis/corrections`. Assignment changes revoke access. Student and IIIC correction routes remain the only mutation routes.

New workflows lock the mentor account for shared read before the student, assignment and target record. PostgreSQL student locks use NO KEY UPDATE, allowing existing audit/notification foreign-key KEY SHARE checks while serializing attendance and reassignment updates. H2 uses its ordinary update lock. Notifications spanning students are inserted in ascending student-ID order while candidate report ranking remains unchanged.

See [attendance-review.md](attendance-review.md) for the approval rules.

## Notification events and dashboard integration

All events use the existing persistent inboxes, unread counts, mark-read actions and 30-second polling. No WebSocket/SSE infrastructure existed. The IIIC dashboard also shows three recent events with a manual refresh and links to the full inbox; this adds no polling loop. Notification keys carry the relevant entity/revision, and action URLs identify the destination record.

| Event | Recipient | Persistent type |
| --- | --- | --- |
| Resume upload/replacement | Active IIIC accounts | `RESUME_UPLOADED`, `RESUME_UPDATED` |
| Parsing complete | Student | `RESUME_PARSED` |
| Student correction | Active IIIC accounts | `RESUME_CORRECTED` |
| Opportunity publication | Students, existing feed behavior | `OPPORTUNITY_PUBLISHED` |
| Eligible strong match | Matching student | `OPPORTUNITY_MATCH` |
| Attendance decision/review request | Student | `ATTENDANCE_APPROVED`, `ATTENDANCE_REJECTED`, `ATTENDANCE_MANUAL_REVIEW` |
| Workplace approval/rejection | Student | Existing mentor/IIIC approval types; `WORKPLACE_REJECTED` |
| Weekly submission/resubmission | Current assigned mentor | `WEEKLY_REPORT_SUBMITTED` |
| Weekly review | Student | `WEEKLY_REPORT_APPROVED`, `WEEKLY_REPORT_REJECTED` |

Each new mutation and its inbox rows share the caller's database transaction. Upload files are cleaned up after a failed transaction. Unique event keys prevent retry duplication. Existing monthly-report rejection and deadline notifications remain in place.

## Database deployment

New migrations are additive. Do not edit applied migration checksums or run a schema reset. Deploy backend code and Flyway migrations together, followed by the frontend. Existing configuration, file storage and AI-service URLs remain in use. No paid service or new runtime dependency is required.

| Migration | Change |
| --- | --- |
| `V36__resume_corrections_and_workflow_notifications.sql` | Append-only resume corrections; nullable attendance reference and unique workflow event key in the existing mentor inbox |
| `V37__weekly_internship_progress.sql` | Weekly periods, reports and immutable submission/review events |
| `V38__opportunity_matching_history.sql` | Opportunity-specific immutable matching snapshots |
| `V39__mentor_attendance_review.sql` | Latest mentor attendance decision and append-only decision audit |

No new required environment variable is introduced. `OPPORTUNITY_STRONG_MATCH_THRESHOLD` optionally changes the default 75/100 notification threshold; it must be between 0 and 100. Existing JWT, storage, database and AI configuration remains in use. The frontend and backend must both be deployed for the new routes. This work does not deploy or modify the production database.

## Verification notes

The frontend currently has no lint script, ESLint installation or lint configuration. `npm run lint` therefore reports a missing script. Type checking, behavioral Node tests and a production build are the available frontend checks; this limitation must not be reported as a lint pass.

PostgreSQL verification uses disposable schemas on the configured localhost database, guarded against production hosts and public-schema changes. The version-upgrade test starts at V35 with existing resume, attendance and read-notification rows and verifies preservation when applying V36 through V39.

### Actual results (30 October 2026)

| Check | Result |
| --- | --- |
| Final focused backend unit/H2 integration run | **46 passed, 0 failed/errors/skipped.** Resume analysis/corrections (8), matching reports (7), scoring (3), duration (2), weekly progress (9), attendance review (9), notifications (8). |
| Final focused PostgreSQL checks | **14 passed, 0 failed/errors/skipped across targeted runs.** Attendance (3), matching publication/corrections/notification rollback (3), resume replacement rollback (1), V35→V39 preservation/idempotency (1), weekly generation/review/rollback and concurrent attendance/reassignment/notification locking (6). Disposable schemas were cleaned up. |
| Earlier PostgreSQL workflow coverage | **24 unique cases passed** across the resume/notification and V35→V36 migration checks, including concurrent correction saves and failed-transaction handling. These overlap some final checks and are not added to the final count. |
| Earlier backend regression coverage | Existing professional profile, student profile, internship registration, semantic matching and opportunity publication suites passed during their implementation phases. The complete legacy backend suite was not rerun at the end. |
| Frontend Node suite | Initial full run: **226/227 passed**. The remaining workplace-search fixture lacked the newly required internship dates; after updating that fixture, its focused file passed **14/14**, including the previously failing case. All 227 cases have passing results across those runs; the full suite was not repeated. |
| Frontend type checking | **Passed.** |
| Frontend production build | **Passed.** New student/mentor/IIIC weekly routes and IIIC inbox compiled successfully. |
| AI-service regression tests | **20 passed, 6 optional real-model tests skipped, 0 failed.** Type checking also passed. |
| Frontend lint | **Unavailable:** no lint script, ESLint dependency or configuration exists in the project. |
| Patch whitespace | `git diff --check` passed. |

The final backend checks ran against an isolated source copy to avoid interference with existing local build output. SHA-256 comparison verified all 465 backend source/configuration/test/build-definition files against both the workspace and that tested copy. No browser acceptance session or production deployment was performed. A PostgreSQL test-cleanup lock was fixed in the test transaction handling; only unfinished focused checks were then rerun.


## Scope and remaining limits

All seven requested modules are represented in the existing application. No authentication redesign, paid service, new runtime dependency, map replacement or production deployment is part of this change.

- The current AI parser does not expose name/email/phone/graduation year/projects/numeric experience; those fields were not invented. Existing personal-profile validation continues to handle the separate profile fields. Resume experience dates retain the parser's bounded free-text format.
- Opportunity scores evaluate configured skills and institutional eligibility. Other degree/experience criteria in free-text descriptions remain a human review task. CGPA is UNKNOWN when restricted because the authoritative profile does not store it.
- Attendance manual review creates feedback and an audit; it does not introduce a second same-day submission or override GPS/workplace requirements.
- Internships outside the generation bound receive a monitoring warning and retain their existing records.
- The project has no frontend lint setup. Optional real-model AI tests remain skipped; no deployed-browser/live-company selection claim is made.
- At the user's request to finish faster, final validation focuses on changed workflows and important regressions. The entire legacy backend suite is not rerun after every phase.

## Created and modified files

The following inventory includes source, migrations, tests and documentation; ignored local build copies and logs are excluded.

### Backend source

- `backend/src/main/java/edu/institution/ims/attendance/AttendanceRecord.java` (modified)
- `backend/src/main/java/edu/institution/ims/attendance/AttendanceRepository.java` (modified)
- `backend/src/main/java/edu/institution/ims/attendance/AttendanceResponse.java` (modified)
- `backend/src/main/java/edu/institution/ims/attendance/AttendanceReview.java` (created)
- `backend/src/main/java/edu/institution/ims/attendance/AttendanceReviewController.java` (created)
- `backend/src/main/java/edu/institution/ims/attendance/AttendanceReviewRepository.java` (created)
- `backend/src/main/java/edu/institution/ims/attendance/AttendanceReviewResponse.java` (created)
- `backend/src/main/java/edu/institution/ims/attendance/AttendanceReviewService.java` (created)
- `backend/src/main/java/edu/institution/ims/attendance/AttendanceReviewStatus.java` (created)
- `backend/src/main/java/edu/institution/ims/attendance/AttendanceService.java` (modified)
- `backend/src/main/java/edu/institution/ims/attendance/InternshipLocationService.java` (modified)
- `backend/src/main/java/edu/institution/ims/attendance/StudentAttendanceDirectoryService.java` (modified)
- `backend/src/main/java/edu/institution/ims/internship/InternshipDetailsRepository.java` (modified)
- `backend/src/main/java/edu/institution/ims/internship/InternshipDetailsService.java` (modified)
- `backend/src/main/java/edu/institution/ims/internship/InternshipDuration.java` (created)
- `backend/src/main/java/edu/institution/ims/internship/dto/InternshipDetailsRequest.java` (modified)
- `backend/src/main/java/edu/institution/ims/notification/AdminNotificationController.java` (created)
- `backend/src/main/java/edu/institution/ims/notification/MentorNotificationService.java` (modified)
- `backend/src/main/java/edu/institution/ims/notification/NotificationService.java` (modified)
- `backend/src/main/java/edu/institution/ims/opportunity/OpportunityMatchService.java` (modified)
- `backend/src/main/java/edu/institution/ims/opportunity/OpportunityMatchingController.java` (created)
- `backend/src/main/java/edu/institution/ims/opportunity/OpportunityMatchingService.java` (created)
- `backend/src/main/java/edu/institution/ims/opportunity/OpportunityService.java` (modified)
- `backend/src/main/java/edu/institution/ims/professionalprofile/MentorResumeController.java` (created)
- `backend/src/main/java/edu/institution/ims/professionalprofile/ProfessionalProfileService.java` (modified)
- `backend/src/main/java/edu/institution/ims/professionalprofile/ResumeAnalysisPersistenceService.java` (modified)
- `backend/src/main/java/edu/institution/ims/professionalprofile/ResumeCorrectionController.java` (created)
- `backend/src/main/java/edu/institution/ims/professionalprofile/ResumeCorrectionService.java` (created)
- `backend/src/main/java/edu/institution/ims/studentprofile/AdminStudentService.java` (modified)
- `backend/src/main/java/edu/institution/ims/studentprofile/dto/AdminStudentSummaryResponse.java` (modified)
- `backend/src/main/java/edu/institution/ims/user/UserRepository.java` (modified)
- `backend/src/main/java/edu/institution/ims/user/WorkflowUserLocks.java` (created)
- `backend/src/main/java/edu/institution/ims/weeklyprogress/AdminWeeklyProgressController.java` (created)
- `backend/src/main/java/edu/institution/ims/weeklyprogress/MentorWeeklyProgressController.java` (created)
- `backend/src/main/java/edu/institution/ims/weeklyprogress/StudentWeeklyProgressController.java` (created)
- `backend/src/main/java/edu/institution/ims/weeklyprogress/WeeklyProgressService.java` (created)

### Backend configuration and migrations

- `backend/src/main/resources/application.yml` (modified)
- `backend/src/main/resources/db/migration/V36__resume_corrections_and_workflow_notifications.sql` (created)
- `backend/src/main/resources/db/migration/V37__weekly_internship_progress.sql` (created)
- `backend/src/main/resources/db/migration/V38__opportunity_matching_history.sql` (created)
- `backend/src/main/resources/db/migration/V39__mentor_attendance_review.sql` (created)

### Backend tests

- `backend/src/test/java/edu/institution/ims/attendance/AttendanceReviewIntegrationTest.java` (created)
- `backend/src/test/java/edu/institution/ims/attendance/AttendanceReviewPostgresIntegrationTest.java` (created)
- `backend/src/test/java/edu/institution/ims/internship/InternshipDetailsIntegrationTest.java` (modified)
- `backend/src/test/java/edu/institution/ims/internship/InternshipDurationTest.java` (created)
- `backend/src/test/java/edu/institution/ims/notification/NotificationIntegrationTest.java` (modified)
- `backend/src/test/java/edu/institution/ims/notification/WorkflowNotificationPostgresIntegrationTest.java` (created)
- `backend/src/test/java/edu/institution/ims/opportunity/OpportunityMatchIntegrationTest.java` (modified)
- `backend/src/test/java/edu/institution/ims/opportunity/OpportunityMatchingReportIntegrationTest.java` (created)
- `backend/src/test/java/edu/institution/ims/opportunity/OpportunityMatchingReportPostgresIntegrationTest.java` (created)
- `backend/src/test/java/edu/institution/ims/opportunity/OpportunityMatchingScoringTest.java` (created)
- `backend/src/test/java/edu/institution/ims/opportunity/OpportunityPublicationNotificationTest.java` (modified)
- `backend/src/test/java/edu/institution/ims/professionalprofile/ResumeAnalysisIntegrationTest.java` (modified)
- `backend/src/test/java/edu/institution/ims/professionalprofile/ResumeAnalysisPostgresIntegrationTest.java` (created)
- `backend/src/test/java/edu/institution/ims/professionalprofile/ResumeWorkflowMigrationPostgresIntegrationTest.java` (created)
- `backend/src/test/java/edu/institution/ims/weeklyprogress/WeeklyProgressIntegrationTest.java` (created)
- `backend/src/test/java/edu/institution/ims/weeklyprogress/WeeklyProgressPostgresIntegrationTest.java` (created)

### Frontend source and tests

- `frontend/app/admin/dashboard/page.tsx` (modified)
- `frontend/app/admin/notifications/page.tsx` (created)
- `frontend/app/admin/opportunities/[id]/talent/page.tsx` (modified)
- `frontend/app/admin/students/page.tsx` (modified)
- `frontend/app/admin/weekly-progress/page.tsx` (created)
- `frontend/app/mentor/attendance/page.tsx` (modified)
- `frontend/app/mentor/dashboard/page.tsx` (modified)
- `frontend/app/mentor/students/[id]/page.tsx` (modified)
- `frontend/app/mentor/weekly-progress/page.tsx` (created)
- `frontend/app/student/dashboard/page.tsx` (modified)
- `frontend/app/student/opportunities/[id]/page.tsx` (modified)
- `frontend/app/student/professional-profile/page.tsx` (modified)
- `frontend/app/student/weekly-progress/page.tsx` (created)
- `frontend/components/AdminProfessionalProfile.tsx` (modified)
- `frontend/components/AppFrame.tsx` (modified)
- `frontend/components/AttendanceAbsenceOverview.tsx` (modified)
- `frontend/components/AttendanceReviewPanel.tsx` (created)
- `frontend/components/DailyAttendanceHistory.tsx` (modified)
- `frontend/components/InternshipDetailsForm.tsx` (modified)
- `frontend/components/MentorResumeReview.tsx` (created)
- `frontend/components/NotificationsContent.tsx` (modified)
- `frontend/components/OpportunityMatchingReport.tsx` (created)
- `frontend/components/OpportunityScorePanel.tsx` (created)
- `frontend/components/ResumeCorrectionEditor.tsx` (created)
- `frontend/components/WeeklyProgress.tsx` (created)
- `frontend/lib/attendance.ts` (modified)
- `frontend/lib/internship-details.ts` (modified)
- `frontend/lib/notifications.ts` (modified)
- `frontend/lib/opportunities.ts` (modified)
- `frontend/lib/professional-profile.ts` (modified)
- `frontend/lib/student-profile.ts` (modified)
- `frontend/lib/weekly-progress.ts` (created)
- `frontend/tests/attendance-review.test.cjs` (created)
- `frontend/tests/daily-absence-dashboard.test.cjs` (modified)
- `frontend/tests/notifications.test.cjs` (modified)
- `frontend/tests/opportunity-matching-report.test.cjs` (created)
- `frontend/tests/resume-corrections.test.cjs` (created)
- `frontend/tests/student-update.test.cjs` (modified)
- `frontend/tests/weekly-progress.test.cjs` (created)
- `frontend/tests/workplace-paused.test.cjs` (modified)
- `frontend/tests/workplace-search.test.cjs` (modified)

### Documentation

- `docs/FEATURE_ENHANCEMENT.md` (created)
- `docs/attendance-review.md` (created)
- `docs/opportunity-matching.md` (created)
- `docs/weekly-progress.md` (created)

