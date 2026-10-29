# Code-backed project inventory

Generated from the working tree before audit implementation on 2026-10-11. Paths and mappings below are observed code, not claims of runtime coverage. See PROJECT_AUDIT.md for executed results and limitations.

## Authorization

`SecurityConfig` protects `/api/v1/admin/**` with ADMIN, `/mentor/**` with MENTOR, and `/student/**` with STUDENT. JWT cookie authentication reloads active users and token versions. Mentor services further check current student assignments. Frontend role selection cannot grant permissions.

## Feature groups and verification plan

| Feature | Frontend | Backend / persistence | Existing coverage |
|---|---|---|---|
| Authentication, role selection, logout | login, AppFrame, lib/auth.tsx | AuthController/AuthService, JWT filters; users / V1,V23 | AuthIntegrationTest |
| Admin-assisted reset, forced and normal password changes | forgot-password, ChangePasswordForm, ResetPasswordButton | AdminPasswordResetService, PasswordChangeRequiredFilter; users.token_version / V23 | PasswordResetIntegrationTest; password-reset.test.cjs |
| Student personal profile, academic options, admin student search | student/profile, admin/students, ProfileForm | StudentProfileService, AdminStudentService, encrypted sensitive fields / V2 | StudentProfileIntegrationTest |
| Student account XLSX import, account administration | admin/student-accounts | StudentAccountImportService; users, student_accounts / V1,V6 | StudentAccountImportIntegrationTest |
| Mentor accounts and assignments | admin/mentor-accounts, MentorAssignmentManager | MentorAccountService, MentorAssignmentService / V7,V8 | MentorAccountIntegrationTest, MentorAssignmentIntegrationTest |
| Internship onboarding, source, documents, diary | student/internship, InternshipOnboardingDetails, DocumentPreviewModal | InternshipOnboardingService, LocalFileStorageService / V3 | InternshipOnboardingIntegrationTest |
| Company/internship registration and editing | InternshipDetailsForm, InternshipDetailsView | InternshipDetailsService / V4 | InternshipDetailsIntegrationTest |
| Workplace Places search, map pin, corrections and radius | WorkplacePlaceSelector, InternshipLocationManager | InternshipLocationService; internship_locations / V10,V24,V26,V27 | SharedWorkplaceIntegrationTest, DualWorkplaceApprovalIntegrationTest; workplace frontend tests |
| Independent assigned-mentor and IIIC approvals, revision audit | InternshipLocationManager | WorkplaceReview, fullyApproved, workplace_review_audit / V27 | DualWorkplaceApprovalIntegrationTest; isolated PostgreSQL migration tests |
| Daily attendance, browser GPS, photo and AI evidence | TodayAttendanceCard, AttendanceLocations, LocationMapLink | AttendanceService, PhysicalAttendanceService, PhysicalVerificationPolicy, AiVerificationService / V9,V10,V12,V24 | AttendanceIntegrationTest, PhysicalAttendanceIntegrationTest, EdgeAiAttendanceIntegrationTest, attendance-status.test.cjs |
| Online/hybrid attendance and weekly schedule | HybridScheduleManager, TodayAttendanceCard | RemoteAttendanceService, AttendancePolicyService, HybridScheduleService / V11,V12 | OnlineHybridAttendanceIntegrationTest |
| Mentor today view, student history, IIIC/mentor attendance reports | mentor/dashboard, mentor/attendance, admin/attendance, ReportsCenter | MentorAttendanceService, InstitutionalReportService, DashboardReportRepository | PhysicalAttendanceIntegrationTest, DashboardReportIntegrationTest |
| Monthly report periods, photo validation, EXIF GPS, capture dates, PDF/history | student/monthly-progress, MonthlyProgressReportList, ProgressPhotoLocation, ProgressPhotoVerification | MonthlyProgressService, ProgressPhotoValidator, MonthlyProgressPdfService / V19,V21,V22,V24 | MonthlyProgressIntegrationTest, ProgressPhotoValidatorTest, MonthlyProgressMultipartLimitTest, monthly PostgreSQL variants; photo frontend tests |
| Mentor-only soft deletion, confirmation, reason, retention and rollback | MentorRecordDelete | MentorRecordDeletionService; deleted_at, active_slot, mentor_record_deletion_audit / V25 | MentorRecordDeletionIntegrationTest and PostgreSQL variant; mentor-record-delete.test.cjs |
| Professional profile and PDF/DOCX resume upload/history | student/professional-profile, AdminProfessionalProfile | ProfessionalProfileService, ResumeFileValidator / V13,V14 | ProfessionalProfileIntegrationTest |
| Resume AI extraction and reviewable suggestions | ResumeAnalysisCard, SkillProfileCard | ResumeAnalysisService, ResumeAnalysisPersistenceService, HttpResumeAiClient; ai-service / V15 | ResumeAnalysisIntegrationTest, AI service tests, opt-in real-model tests |
| Confirmed/manual skills, suggestion acceptance/rejection/rename/delete | SkillProfileCard | StudentSkillService / V16 | StudentSkillIntegrationTest |
| Opportunity CRUD, publication, filters, eligibility and student feed | OpportunityForm, student/opportunities, admin/opportunities | OpportunityService, StudentOpportunityService / V17 | OpportunityManagementIntegrationTest, StudentOpportunityFeedIntegrationTest |
| Job description AI analysis and suggestion review | OpportunityAiPanel | OpportunityAiAnalysisService, HttpOpportunityAiClient / V18 | OpportunityAiAnalysisIntegrationTest; AI service tests |
| Opportunity matching, student skill gaps, admin talent discovery | OpportunityMatchPanel, student/skill-gaps, admin/opportunities/[id]/talent | OpportunityMatchService, SkillGapService, AdminTalentDiscoveryService | OpportunityMatchIntegrationTest; AI matching tests |
| Dashboard analytics and authorized PDF/XLSX report exports | admin/dashboard, DashboardReportExport, ReportsCenter | DashboardService, InstitutionalReportService, DashboardReportWriter / V5 | DashboardIntegrationTest, DashboardReportIntegrationTest |

## API controller inventory

Mappings retain annotation spelling; class request prefix and method mappings are listed together.

### AdminHybridScheduleController.java

`backend/src/main/java/edu/institution/ims/attendance/AdminHybridScheduleController.java`

- `@RequestMapping("/api/v1/admin/students/{studentId}/internship/hybrid-schedule")`
- `@GetMapping`
- `@PutMapping`

### AdminInternshipLocationController.java

`backend/src/main/java/edu/institution/ims/attendance/AdminInternshipLocationController.java`

- `@RequestMapping("/api/v1/admin/students/{studentId}/internship/location")`
- `@PostMapping("/review")`
- `@GetMapping`
- `@PutMapping`
- `@PutMapping("/correction")`
- `@PutMapping("/radius")`
- `@PostMapping("/confirm")`

### MentorAttendanceController.java

`backend/src/main/java/edu/institution/ims/attendance/MentorAttendanceController.java`

- `@RequestMapping("/api/v1/mentor/attendance")`
- `@GetMapping("/today")`

### MentorHybridScheduleController.java

`backend/src/main/java/edu/institution/ims/attendance/MentorHybridScheduleController.java`

- `@RequestMapping("/api/v1/mentor/students/{studentId}/hybrid-schedule")`
- `@GetMapping`

### MentorInternshipLocationController.java

`backend/src/main/java/edu/institution/ims/attendance/MentorInternshipLocationController.java`

- `@RequestMapping("/api/v1/mentor/students/{studentId}/internship/location")`
- `@PostMapping("/review")`
- `@GetMapping`

### StudentAttendanceController.java

`backend/src/main/java/edu/institution/ims/attendance/StudentAttendanceController.java`

- `@RequestMapping("/api/v1/student/attendance")`
- `@PostMapping("/today")`
- `@PostMapping(value = "/today/physical", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`
- `@PostMapping(value = "/today/remote", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`
- `@PostMapping("/verification-session")`
- `@GetMapping("/today/policy")`
- `@GetMapping("/today")`
- `@GetMapping`

### StudentHybridScheduleController.java

`backend/src/main/java/edu/institution/ims/attendance/StudentHybridScheduleController.java`

- `@RequestMapping("/api/v1/student/internship/hybrid-schedule")`
- `@GetMapping`

### StudentInternshipLocationController.java

`backend/src/main/java/edu/institution/ims/attendance/StudentInternshipLocationController.java`

- `@RequestMapping("/api/v1/student/internship/location")`
- `@GetMapping`
- `@PutMapping`

### AdminPasswordResetController.java

`backend/src/main/java/edu/institution/ims/auth/AdminPasswordResetController.java`

- `@RequestMapping("/api/v1/admin/users")`
- `@PostMapping("/{userId}/reset-password")`

### AdminVerificationController.java

`backend/src/main/java/edu/institution/ims/auth/AdminVerificationController.java`

- `@RequestMapping("/api/v1/admin")`
- `@GetMapping("/verification")`

### AuthController.java

`backend/src/main/java/edu/institution/ims/auth/AuthController.java`

- `@RequestMapping("/api/v1/auth")`
- `@PostMapping("/login")`
- `@GetMapping("/me")`
- `@PostMapping("/change-password")`
- `@PostMapping("/logout")`

### DashboardController.java

`backend/src/main/java/edu/institution/ims/dashboard/DashboardController.java`

- `@RequestMapping("/api/v1/admin/dashboard")`
- `@GetMapping("/summary")`

### DashboardReportController.java

`backend/src/main/java/edu/institution/ims/dashboard/report/DashboardReportController.java`

- `@RequestMapping({"/api/v1/admin/dashboard/reports", "/api/v1/mentor/dashboard/reports", "/api/v1/admin/reports", "/api/v1/mentor/reports"})`
- `@GetMapping("/types")`
- `@PostMapping("/table")`
- `@PostMapping("/generate")`
- `@GetMapping("/options")`
- `@PostMapping("/preview")`
- `@PostMapping("/export")`

### MentorRecordDeletionController.java

`backend/src/main/java/edu/institution/ims/deletion/MentorRecordDeletionController.java`

- `@RequestMapping("/api/v1/mentor")`
- `@DeleteMapping("/attendance/{id}")`
- `@DeleteMapping("/monthly-progress/{id}")`

### AdminInternshipDetailsController.java

`backend/src/main/java/edu/institution/ims/internship/AdminInternshipDetailsController.java`

- `@RequestMapping("/api/v1/admin/students/{studentId}/internship")`
- `@GetMapping`

### AdminInternshipDocumentController.java

`backend/src/main/java/edu/institution/ims/internship/AdminInternshipDocumentController.java`

- `@RequestMapping("/api/v1/admin/students/{studentId}/internship-onboarding/documents")`
- `@GetMapping("/{documentId}/download")`
- `@GetMapping("/{documentId}/view")`

### InternshipDetailsController.java

`backend/src/main/java/edu/institution/ims/internship/InternshipDetailsController.java`

- `@RequestMapping("/api/v1/student/internship")`
- `@GetMapping`
- `@PostMapping`
- `@PutMapping`

### InternshipOnboardingController.java

`backend/src/main/java/edu/institution/ims/internship/InternshipOnboardingController.java`

- `@RequestMapping("/api/v1/student/internship-onboarding")`
- `@GetMapping`
- `@PostMapping("/secure")`
- `@PutMapping("/source")`
- `@PostMapping(value = "/documents/{documentType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`
- `@GetMapping("/documents")`
- `@GetMapping("/documents/{documentId}/download")`
- `@GetMapping("/documents/{documentId}/view")`
- `@PostMapping("/diary/confirm")`

### MentorInternshipDocumentController.java

`backend/src/main/java/edu/institution/ims/internship/MentorInternshipDocumentController.java`

- `@RequestMapping("/api/v1/mentor/students/{studentId}/internship-onboarding")`
- `@GetMapping`
- `@GetMapping("/documents/{documentId}/view")`
- `@GetMapping("/documents/{documentId}/download")`

### AdminMentorAccountController.java

`backend/src/main/java/edu/institution/ims/mentor/AdminMentorAccountController.java`

- `@RequestMapping("/api/v1/admin/mentor-accounts")`
- `@GetMapping`
- `@PostMapping`

### AdminMentorAssignmentController.java

`backend/src/main/java/edu/institution/ims/mentor/AdminMentorAssignmentController.java`

- `@RequestMapping("/api/v1/admin/mentors")`
- `@GetMapping("/{mentorUserId}/students")`
- `@PostMapping("/{mentorUserId}/students")`
- `@DeleteMapping("/{mentorUserId}/students/{studentUserId}")`

### MentorProfileController.java

`backend/src/main/java/edu/institution/ims/mentor/MentorProfileController.java`

- `@RequestMapping("/api/v1/mentor/profile")`
- `@GetMapping`

### MentorStudentController.java

`backend/src/main/java/edu/institution/ims/mentor/MentorStudentController.java`

- `@RequestMapping("/api/v1/mentor/students")`
- `@GetMapping`
- `@GetMapping("/{studentId}/profile")`

### AdminMonthlyProgressController.java

`backend/src/main/java/edu/institution/ims/monthlyprogress/AdminMonthlyProgressController.java`

- `@RequestMapping("/api/v1/admin/monthly-progress")`
- `@GetMapping`
- `@GetMapping("/{reportId}")`
- `@GetMapping("/{reportId}/photo")`
- `@GetMapping("/{reportId}/pdf")`

### MentorMonthlyProgressController.java

`backend/src/main/java/edu/institution/ims/monthlyprogress/MentorMonthlyProgressController.java`

- `@RequestMapping("/api/v1/mentor/monthly-progress")`
- `@GetMapping`
- `@GetMapping("/{reportId}")`
- `@GetMapping("/{reportId}/photo")`
- `@GetMapping("/{reportId}/pdf")`

### StudentMonthlyProgressController.java

`backend/src/main/java/edu/institution/ims/monthlyprogress/StudentMonthlyProgressController.java`

- `@RequestMapping("/api/v1/student/monthly-progress")`
- `@GetMapping`
- `@PostMapping(value = "/{monthNumber}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`
- `@GetMapping("/{reportId}")`
- `@GetMapping("/{reportId}/photo")`
- `@GetMapping("/{reportId}/pdf")`

### AdminOpportunityAiController.java

`backend/src/main/java/edu/institution/ims/opportunity/AdminOpportunityAiController.java`

- `@RequestMapping("/api/v1/admin/opportunities/{opportunityId}/ai-analysis")`
- `@GetMapping`
- `@PostMapping`
- `@PostMapping("/suggestions/{suggestionId}/confirm")`
- `@PostMapping("/suggestions/{suggestionId}/reject")`
- `@PostMapping("/suggestions/confirm-all")`

### AdminOpportunityController.java

`backend/src/main/java/edu/institution/ims/opportunity/AdminOpportunityController.java`

- `@RequestMapping("/api/v1/admin/opportunities")`
- `@PostMapping`
- `@GetMapping`
- `@GetMapping("/{id}")`
- `@PutMapping("/{id}")`
- `@PostMapping("/{id}/publish")`
- `@PostMapping("/{id}/close")`

### AdminTalentDiscoveryController.java

`backend/src/main/java/edu/institution/ims/opportunity/AdminTalentDiscoveryController.java`

- `@RequestMapping("/api/v1/admin/opportunities/{opportunityId}/talent-discovery")`
- `@GetMapping`
- `@GetMapping("/skill-gaps")`

### StudentOpportunityController.java

`backend/src/main/java/edu/institution/ims/opportunity/StudentOpportunityController.java`

- `@RequestMapping("/api/v1/student/opportunities")`
- `@GetMapping`
- `@GetMapping("/{id}")`
- `@GetMapping("/{id}/match")`

### StudentSkillGapController.java

`backend/src/main/java/edu/institution/ims/opportunity/StudentSkillGapController.java`

- `@RequestMapping("/api/v1/student/skill-gaps")`
- `@GetMapping`

### AdminProfessionalProfileController.java

`backend/src/main/java/edu/institution/ims/professionalprofile/AdminProfessionalProfileController.java`

- `@RequestMapping("/api/v1/admin/students/{studentId}")`
- `@GetMapping("/professional-profile")`
- `@GetMapping("/professional-profile/skills")`
- `@GetMapping("/resume/download")`

### StudentProfessionalProfileController.java

`backend/src/main/java/edu/institution/ims/professionalprofile/StudentProfessionalProfileController.java`

- `@RequestMapping("/api/v1/student")`
- `@GetMapping("/professional-profile")`
- `@PutMapping("/professional-profile")`
- `@PostMapping(value = "/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`
- `@GetMapping("/resume")`
- `@GetMapping("/resume/download")`

### StudentResumeAnalysisController.java

`backend/src/main/java/edu/institution/ims/professionalprofile/StudentResumeAnalysisController.java`

- `@RequestMapping("/api/v1/student/resume/analysis")`
- `@GetMapping`
- `@PostMapping`

### StudentSkillController.java

`backend/src/main/java/edu/institution/ims/professionalprofile/StudentSkillController.java`

- `@RequestMapping("/api/v1/student/professional-profile")`
- `@GetMapping("/skills")`
- `@PostMapping("/skills")`
- `@PatchMapping("/skills/{skillId}")`
- `@DeleteMapping("/skills/{skillId}")`
- `@PostMapping("/skill-suggestions/{suggestionId}/confirm")`
- `@PostMapping("/skill-suggestions/{suggestionId}/reject")`
- `@PostMapping("/skill-suggestions/confirm-all")`

### StudentAccountImportController.java

`backend/src/main/java/edu/institution/ims/studentaccounts/StudentAccountImportController.java`

- `@RequestMapping("/api/v1/admin/student-accounts")`
- `@GetMapping("/template")`
- `@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`

### AdminStudentController.java

`backend/src/main/java/edu/institution/ims/studentprofile/AdminStudentController.java`

- `@RequestMapping("/api/v1/admin/students")`
- `@GetMapping("/options")`
- `@GetMapping`
- `@GetMapping("/{id}")`

### StudentProfileController.java

`backend/src/main/java/edu/institution/ims/studentprofile/StudentProfileController.java`

- `@RequestMapping("/api/v1/student/profile")`
- `@GetMapping`
- `@GetMapping("/prefill")`
- `@PostMapping`
- `@PutMapping`
- `@GetMapping("/options")`

## Persistence inventory

- `backend/src/main/java/edu/institution/ims/attendance/AttendanceEvidence.java`: `attendance_evidence`
- `backend/src/main/java/edu/institution/ims/attendance/AttendanceRecord.java`: `attendance_records`
- `backend/src/main/java/edu/institution/ims/attendance/AttendanceRepository.java`:
- `backend/src/main/java/edu/institution/ims/attendance/AttendanceVerificationSession.java`: `attendance_verification_sessions`
- `backend/src/main/java/edu/institution/ims/attendance/HybridWorkSchedule.java`: `hybrid_work_schedule`
- `backend/src/main/java/edu/institution/ims/attendance/InternshipLocation.java`: `internship_locations`
- `backend/src/main/java/edu/institution/ims/attendance/InternshipLocationRepository.java`:
- `backend/src/main/java/edu/institution/ims/internship/InternshipDetails.java`: `internship_details`
- `backend/src/main/java/edu/institution/ims/internship/InternshipDetailsRepository.java`:
- `backend/src/main/java/edu/institution/ims/internship/InternshipDocument.java`: `internship_documents`
- `backend/src/main/java/edu/institution/ims/internship/InternshipOnboarding.java`: `internship_onboarding`
- `backend/src/main/java/edu/institution/ims/mentor/MentorProfile.java`: `mentor_profiles`
- `backend/src/main/java/edu/institution/ims/mentor/MentorProfileRepository.java`:
- `backend/src/main/java/edu/institution/ims/mentor/MentorStudentAssignment.java`: `mentor_student_assignments`
- `backend/src/main/java/edu/institution/ims/mentor/MentorStudentAssignmentRepository.java`:
- `backend/src/main/java/edu/institution/ims/monthlyprogress/MonthlyProgressReport.java`: `monthly_progress_reports`
- `backend/src/main/java/edu/institution/ims/monthlyprogress/MonthlyProgressReportRepository.java`:
- `backend/src/main/java/edu/institution/ims/opportunity/Opportunity.java`: `opportunities`
- `backend/src/main/java/edu/institution/ims/opportunity/OpportunityAiAnalysis.java`: `opportunity_ai_analyses`
- `backend/src/main/java/edu/institution/ims/opportunity/OpportunityAiSignal.java`: `opportunity_ai_signals`
- `backend/src/main/java/edu/institution/ims/opportunity/OpportunityAiSkillSuggestion.java`: `opportunity_ai_skill_suggestions`
- `backend/src/main/java/edu/institution/ims/opportunity/OpportunitySkill.java`: `opportunity_skills`
- `backend/src/main/java/edu/institution/ims/professionalprofile/ResumeAnalysis.java`: `resume_analyses`
- `backend/src/main/java/edu/institution/ims/professionalprofile/ResumeExtractedCertification.java`: `resume_extracted_certifications`
- `backend/src/main/java/edu/institution/ims/professionalprofile/ResumeExtractedEducation.java`: `resume_extracted_education`
- `backend/src/main/java/edu/institution/ims/professionalprofile/ResumeExtractedExperience.java`: `resume_extracted_experiences`
- `backend/src/main/java/edu/institution/ims/professionalprofile/ResumeExtractedSkill.java`: `resume_extracted_skills`
- `backend/src/main/java/edu/institution/ims/professionalprofile/StudentProfessionalProfile.java`: `student_professional_profiles`
- `backend/src/main/java/edu/institution/ims/professionalprofile/StudentResume.java`: `student_resumes`
- `backend/src/main/java/edu/institution/ims/professionalprofile/StudentSkill.java`: `student_skills`
- `backend/src/main/java/edu/institution/ims/professionalprofile/StudentSkillSuggestion.java`: `student_skill_suggestions`
- `backend/src/main/java/edu/institution/ims/studentprofile/StudentProfile.java`: `student_profiles`
- `backend/src/main/java/edu/institution/ims/user/StudentAccount.java`: `student_accounts`
- `backend/src/main/java/edu/institution/ims/user/User.java`: `users`
- `backend/src/main/java/edu/institution/ims/user/UserRepository.java`:

## Migration chain

- `backend/src/main/resources/db/migration/V1__create_account_tables.sql`: creates users, student_accounts
- `backend/src/main/resources/db/migration/V2__create_student_profiles.sql`: creates student_profiles
- `backend/src/main/resources/db/migration/V3__create_internship_onboarding.sql`: creates internship_onboarding, internship_documents
- `backend/src/main/resources/db/migration/V4__create_internship_details.sql`: creates internship_details
- `backend/src/main/resources/db/migration/V5__add_dashboard_query_indexes.sql`: alters or backfills existing schema
- `backend/src/main/resources/db/migration/V6__add_bulk_student_account_management.sql`: alters or backfills existing schema
- `backend/src/main/resources/db/migration/V7__create_mentor_profiles.sql`: creates mentor_profiles
- `backend/src/main/resources/db/migration/V8__create_mentor_student_assignments.sql`: creates mentor_student_assignments
- `backend/src/main/resources/db/migration/V9__create_attendance.sql`: creates attendance_records
- `backend/src/main/resources/db/migration/V10__add_physical_attendance_verification.sql`: creates internship_locations, attendance_evidence
- `backend/src/main/resources/db/migration/V11__add_online_hybrid_attendance.sql`: creates hybrid_work_schedule
- `backend/src/main/resources/db/migration/V12__add_edge_ai_attendance_verification.sql`: creates attendance_verification_sessions
- `backend/src/main/resources/db/migration/V13__create_student_professional_profile.sql`: creates student_professional_profiles, student_resumes
- `backend/src/main/resources/db/migration/V14__preserve_resume_reupload_history.sql`: alters or backfills existing schema
- `backend/src/main/resources/db/migration/V15__add_resume_ai_analysis.sql`: creates resume_analyses, resume_extracted_skills, resume_extracted_experiences, resume_extracted_education, resume_extracted_certifications
- `backend/src/main/resources/db/migration/V16__add_student_skill_profile.sql`: creates student_skills, student_skill_suggestions
- `backend/src/main/resources/db/migration/V17__add_opportunity_management.sql`: creates opportunities, opportunity_eligible_branches, opportunity_eligible_semesters, opportunity_skills
- `backend/src/main/resources/db/migration/V18__add_opportunity_ai_analysis.sql`: creates opportunity_ai_analyses, opportunity_ai_skill_suggestions, opportunity_ai_signals
- `backend/src/main/resources/db/migration/V19__add_monthly_progress_reports.sql`: creates monthly_progress_reports
- `backend/src/main/resources/db/migration/V20__add_project_seminar_evaluations.sql`: creates project_seminar_evaluations
- `backend/src/main/resources/db/migration/V21__monthly_photo_verification.sql`: alters or backfills existing schema
- `backend/src/main/resources/db/migration/V22__complete_monthly_photo_verification.sql`: alters or backfills existing schema
- `backend/src/main/resources/db/migration/V23__password_token_version.sql`: alters or backfills existing schema
- `backend/src/main/resources/db/migration/V24__shared_workplace_verification.sql`: alters or backfills existing schema
- `backend/src/main/resources/db/migration/V25__mentor_record_deletion.sql`: creates mentor_record_deletion_audit
- `backend/src/main/resources/db/migration/V26__workplace_names_and_correction_audit.sql`: creates workplace_correction_audit
- `backend/src/main/resources/db/migration/V27__dual_workplace_approval.sql`: creates workplace_review_audit

V20 contains project/seminar schema, but no implemented controller/service/frontend workflow for those evaluations was found; do not count schema alone as an implemented feature. V24-V27 already exist in the local database and must remain unchanged.

## Frontend routes

- `frontend/app/admin/attendance/page.tsx`
- `frontend/app/admin/dashboard/page.tsx`
- `frontend/app/admin/mentor-accounts/page.tsx`
- `frontend/app/admin/monthly-progress/page.tsx`
- `frontend/app/admin/opportunities/[id]/talent/page.tsx`
- `frontend/app/admin/opportunities/page.tsx`
- `frontend/app/admin/reports/page.tsx`
- `frontend/app/admin/student-accounts/page.tsx`
- `frontend/app/admin/students/[id]/page.tsx`
- `frontend/app/admin/students/page.tsx`
- `frontend/app/forgot-password/page.tsx`
- `frontend/app/login/page.tsx`
- `frontend/app/mentor/attendance/page.tsx`
- `frontend/app/mentor/change-password/page.tsx`
- `frontend/app/mentor/dashboard/page.tsx`
- `frontend/app/mentor/documents/page.tsx`
- `frontend/app/mentor/monthly-progress/page.tsx`
- `frontend/app/mentor/reports/page.tsx`
- `frontend/app/mentor/students/[id]/page.tsx`
- `frontend/app/page.tsx`
- `frontend/app/student/change-password/page.tsx`
- `frontend/app/student/dashboard/page.tsx`
- `frontend/app/student/internship/details/page.tsx`
- `frontend/app/student/internship/page.tsx`
- `frontend/app/student/monthly-progress/page.tsx`
- `frontend/app/student/opportunities/[id]/page.tsx`
- `frontend/app/student/opportunities/page.tsx`
- `frontend/app/student/professional-profile/page.tsx`
- `frontend/app/student/profile/page.tsx`
- `frontend/app/student/skill-gaps/page.tsx`

## Backend service and test inventory


### Services

- `backend/src/main/java/edu/institution/ims/attendance/AiVerificationService.java`
- `backend/src/main/java/edu/institution/ims/attendance/AttendancePolicyService.java`
- `backend/src/main/java/edu/institution/ims/attendance/AttendanceService.java`
- `backend/src/main/java/edu/institution/ims/attendance/HybridScheduleService.java`
- `backend/src/main/java/edu/institution/ims/attendance/InternshipLocationService.java`
- `backend/src/main/java/edu/institution/ims/attendance/MentorAttendanceService.java`
- `backend/src/main/java/edu/institution/ims/attendance/PhysicalAttendanceService.java`
- `backend/src/main/java/edu/institution/ims/attendance/RemoteAttendanceService.java`
- `backend/src/main/java/edu/institution/ims/auth/AdminPasswordResetService.java`
- `backend/src/main/java/edu/institution/ims/auth/AuthService.java`
- `backend/src/main/java/edu/institution/ims/dashboard/DashboardService.java`
- `backend/src/main/java/edu/institution/ims/dashboard/report/DashboardReportService.java`
- `backend/src/main/java/edu/institution/ims/dashboard/report/InstitutionalReportService.java`
- `backend/src/main/java/edu/institution/ims/deletion/MentorRecordDeletionService.java`
- `backend/src/main/java/edu/institution/ims/internship/InternshipDetailsService.java`
- `backend/src/main/java/edu/institution/ims/internship/InternshipOnboardingService.java`
- `backend/src/main/java/edu/institution/ims/internship/storage/FileStorageService.java`
- `backend/src/main/java/edu/institution/ims/internship/storage/LocalFileStorageService.java`
- `backend/src/main/java/edu/institution/ims/mentor/MentorAccountService.java`
- `backend/src/main/java/edu/institution/ims/mentor/MentorAssignmentService.java`
- `backend/src/main/java/edu/institution/ims/mentor/MentorStudentProfileService.java`
- `backend/src/main/java/edu/institution/ims/monthlyprogress/MonthlyProgressPdfService.java`
- `backend/src/main/java/edu/institution/ims/monthlyprogress/MonthlyProgressService.java`
- `backend/src/main/java/edu/institution/ims/opportunity/AdminTalentDiscoveryService.java`
- `backend/src/main/java/edu/institution/ims/opportunity/OpportunityAiAnalysisService.java`
- `backend/src/main/java/edu/institution/ims/opportunity/OpportunityAiPersistenceService.java`
- `backend/src/main/java/edu/institution/ims/opportunity/OpportunityMatchService.java`
- `backend/src/main/java/edu/institution/ims/opportunity/OpportunityService.java`
- `backend/src/main/java/edu/institution/ims/opportunity/SkillGapService.java`
- `backend/src/main/java/edu/institution/ims/opportunity/StudentOpportunityService.java`
- `backend/src/main/java/edu/institution/ims/professionalprofile/ProfessionalProfileService.java`
- `backend/src/main/java/edu/institution/ims/professionalprofile/ResumeAnalysisPersistenceService.java`
- `backend/src/main/java/edu/institution/ims/professionalprofile/ResumeAnalysisService.java`
- `backend/src/main/java/edu/institution/ims/professionalprofile/StudentSkillService.java`
- `backend/src/main/java/edu/institution/ims/security/CustomUserDetailsService.java`
- `backend/src/main/java/edu/institution/ims/security/JwtService.java`
- `backend/src/main/java/edu/institution/ims/studentaccounts/StudentAccountImportService.java`
- `backend/src/main/java/edu/institution/ims/studentprofile/AdminStudentService.java`
- `backend/src/main/java/edu/institution/ims/studentprofile/security/AadhaarEncryptionService.java`
- `backend/src/main/java/edu/institution/ims/studentprofile/StudentProfileService.java`

### Tests

- `backend/src/test/java/edu/institution/ims/attendance/AttendanceIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/attendance/EdgeAiAttendanceIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/attendance/OnlineHybridAttendanceIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/attendance/PhysicalAttendanceIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/attendance/PhysicalAttendancePostgresIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/attendance/PhysicalVerificationPolicyTest.java`
- `backend/src/test/java/edu/institution/ims/auth/AuthIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/auth/PasswordResetIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/dashboard/DashboardIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/dashboard/report/DashboardReportIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/internship/InternshipDetailsIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/internship/InternshipOnboardingIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/mentor/MentorAccountIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/mentor/MentorAssignmentIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/monthlyprogress/DualWorkplaceApprovalIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/monthlyprogress/DualWorkplaceApprovalPostgresIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/monthlyprogress/MentorRecordDeletionIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/monthlyprogress/MentorRecordDeletionPostgresIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/monthlyprogress/MonthlyProgressIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/monthlyprogress/MonthlyProgressMultipartLimitTest.java`
- `backend/src/test/java/edu/institution/ims/monthlyprogress/MonthlyProgressPostgresIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/monthlyprogress/ProgressPhotoValidatorTest.java`
- `backend/src/test/java/edu/institution/ims/monthlyprogress/SharedWorkplaceIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/monthlyprogress/SharedWorkplacePostgresIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/monthlyprogress/WorkplaceApprovalMigrationPostgresTest.java`
- `backend/src/test/java/edu/institution/ims/opportunity/OpportunityAiAnalysisIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/opportunity/OpportunityManagementIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/opportunity/OpportunityMatchIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/opportunity/StudentOpportunityFeedIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/professionalprofile/ProfessionalProfileIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/professionalprofile/ResumeAnalysisIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/professionalprofile/StudentSkillIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/studentaccounts/StudentAccountImportIntegrationTest.java`
- `backend/src/test/java/edu/institution/ims/studentprofile/StudentProfileIntegrationTest.java`

## Commands to execute

Run Maven sequentially; PostgreSQL tests use fresh guarded local schemas.

```powershell
cd backend
mvn.cmd -DreuseForks=false test
mvn.cmd -DskipTests package
# Opt-in PostgreSQL classes receive EXIF_TEST_DB_* pointing only to a newly generated local test schema.
cd ../frontend
node --test tests/*.test.cjs
npm.cmd run typecheck
npm.cmd run build
cd ../ai-service
npm.cmd test
npm.cmd run typecheck
# AI_REAL_MODEL_TEST=true enables local model tests only when both cached model files exist.
git diff --check
```

Frontend has no lint script or lint configuration/dependency at inventory time. Record lint as not available unless an existing executable is discovered; type checks are not lint. Live Maps, browser camera/GPS hardware, hosted deployments and production data remain separate manual checks.
