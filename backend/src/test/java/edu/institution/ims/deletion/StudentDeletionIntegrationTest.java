package edu.institution.ims.deletion;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.attendance.*;
import edu.institution.ims.dashboard.DashboardService;
import edu.institution.ims.dashboard.report.*;
import edu.institution.ims.internship.*;
import edu.institution.ims.internship.storage.FileStorageService;
import edu.institution.ims.mentor.*;
import edu.institution.ims.monthlyprogress.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentprofile.*;
import edu.institution.ims.user.*;
import edu.institution.ims.weeklyprogress.WeeklyProgressService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class StudentDeletionIntegrationTest {
    @Autowired StudentDeletionService service;
    @Autowired UserRepository users;
    @Autowired StudentAccountRepository accounts;
    @Autowired MentorProfileRepository mentors;
    @Autowired MentorStudentAssignmentRepository assignments;
    @Autowired MentorAssignmentService assignmentService;
    @Autowired InternshipOnboardingRepository onboardings;
    @Autowired InternshipDetailsRepository internships;
    @Autowired WeeklyProgressService weekly;
    @Autowired MonthlyProgressReportRepository monthly;
    @Autowired MonthlyProgressService monthlyService;
    @Autowired InternshipCompletionService completions;
    @Autowired StudentAttendanceDirectoryService attendanceDirectory;
    @Autowired AdminStudentService directory;
    @Autowired DashboardService dashboard;
    @Autowired DashboardReportService reports;
    @Autowired AttendanceRepository attendance;
    @Autowired InternshipLocationService workplaces;
    @Autowired FileStorageService storage;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager entities;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    User admin,student,other,mentor;
    InternshipDetails internship;
    Long sessionId,monthId;
    String storageKey;
    static final String PASSWORD="DeleteStudentPassword!23";
    @BeforeEach void fixture() {
        admin=account(Role.ADMIN);mentor=account(Role.MENTOR);student=account(Role.STUDENT);other=account(Role.STUDENT);
        mentors.saveAndFlush(new MentorProfile(mentor,"Faculty Mentor","FAC"+mentor.getId(),"Computer Engineering","Professor","9876543210"));
        accounts.saveAndFlush(new StudentAccount(student,"Student To Delete","DEL"+student.getId(),8));accounts.saveAndFlush(new StudentAccount(other,"Unrelated Student","OTHER"+other.getId(),8));
        assignments.saveAndFlush(new MentorStudentAssignment(mentor,student,admin,Instant.now()));
        String name="DELETE-"+UUID.randomUUID();jdbc.update("insert into academic_sessions(name) values(?)",name);sessionId=jdbc.queryForObject("select id from academic_sessions where name=?",Long.class,name);
        jdbc.update("update users set academic_session_id=? where id in (?,?)",sessionId,student.getId(),other.getId());jdbc.update("update academic_session_settings set active_session_id=? where id=1",sessionId);
        var onboarding=new InternshipOnboarding(student);onboarding.confirmDiary();onboarding.markInternshipDetailsComplete();onboardings.saveAndFlush(onboarding);
        internship=new InternshipDetails(onboarding);internship.apply("Retained Company",CompanyType.IT,null,"Address","9876543210","Supervisor","Manager","9876543210",1,LocalDate.of(2025,1,1),LocalDate.of(2025,1,9),2,InternshipMode.ONLINE,BigDecimal.ZERO,false);internships.saveAndFlush(internship);weekly.synchronize(internship);
        var report=monthly.saveAndFlush(new MonthlyProgressReport(internship,1,internship.getStartDate(),internship.getEndDate(),"Retained monthly work","test-reference-only","photo.jpg","image/jpeg",100,null,null,null,Instant.now()));monthId=report.getId();
        jdbc.queryForList("select id from weekly_internship_periods where internship_id=?",Long.class,internship.getId()).forEach(id->jdbc.update("insert into weekly_progress_reports(period_id,work_description,revision,review_status,submitted_at) values(?,'Retained weekly work',1,'PENDING_MENTOR_REVIEW',current_timestamp)",id));
    }
    @AfterEach void deleteOwnFixtureFileOnly() { if(storageKey!=null) storage.delete(storageKey); }
    User account(Role role) { return users.saveAndFlush(new User("delete-"+UUID.randomUUID()+"@test.edu",encoder.encode(PASSWORD),role,true)); }
    <T> T asAdmin(Supplier<T> operation) {
        var previous=SecurityContextHolder.getContext();var context=SecurityContextHolder.createEmptyContext();var principal=UserPrincipal.from(admin);
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));SecurityContextHolder.setContext(context);
        try{return operation.get();}finally{SecurityContextHolder.setContext(previous);}
    }
    StudentDeletionService.Result remove() { return asAdmin(()->service.deactivate(student.getId(),UserPrincipal.from(admin),new StudentDeletionService.Request(true,student.getEmail(),"Duplicate student account confirmed"))); }
    StudentDeletionService.Preview preview() { return asAdmin(()->service.preview(student.getId(),UserPrincipal.from(admin))); }

    @Test void deactivationRecordsActorTimeReasonAndInvalidatesTokensWithoutTouchingOtherAccounts() {
        var otherBefore=jdbc.queryForMap("select * from users where id=?",other.getId());long version=student.getTokenVersion();
        var result=remove();assertThat(result.active()).isFalse();assertThat(result.initiatedBy()).isEqualTo(admin.getId());assertThat(result.deactivatedAt()).isNotNull();
        assertThat(jdbc.queryForObject("select active from users where id=?",Boolean.class,student.getId())).isFalse();assertThat(jdbc.queryForObject("select token_version from users where id=?",Long.class,student.getId())).isEqualTo(version+1);
        var audit=asAdmin(()->service.audit(student.getId(),UserPrincipal.from(admin)));assertThat(audit.get("reason")).isEqualTo("Duplicate student account confirmed");assertThat(((Number)audit.get("initiated_by")).longValue()).isEqualTo(admin.getId());
        assertThat(jdbc.queryForMap("select * from users where id=?",other.getId())).isEqualTo(otherBefore);
        assertThatThrownBy(this::remove).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(jdbc.queryForObject("select count(*) from student_account_deactivation_audit where student_user_id=?",Long.class,student.getId())).isEqualTo(1);
    }
    @Test void explicitConfirmationIdentityAndValidReasonAreRequired() throws Exception {
        for(String body:List.of("{\"confirmed\":false,\"reason\":\"Valid reason\"}","{\"confirmed\":true,\"reason\":\"  \"}"))
            mvc.perform(delete("/api/v1/admin/students/{id}",student.getId()).with(user(UserPrincipal.from(admin))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/admin/students/{id}",student.getId()).with(user(UserPrincipal.from(admin))).contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true,\"confirmationEmail\":\"wrong@test.edu\",\"reason\":\"Valid reason\"}")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select active from users where id=?",Boolean.class,student.getId())).isTrue();assertThat(jdbc.queryForObject("select count(*) from student_account_deactivation_audit",Long.class)).isZero();
    }
    @Test void authorizationProtectsStudentsMentorsOwnAdministratorAndTheLastAdministrator() throws Exception {
        mvc.perform(delete("/api/v1/admin/students/{id}",student.getId()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        for(User denied:List.of(student,mentor,account(Role.HOD))) mvc.perform(delete("/api/v1/admin/students/{id}",student.getId()).with(user(UserPrincipal.from(denied))).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        for(User protectedUser:List.of(admin,mentor)) mvc.perform(delete("/api/v1/admin/students/{id}",protectedUser.getId()).with(user(UserPrincipal.from(admin))).contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true,\"reason\":\"Valid reason\"}")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from users where role='ADMIN' and active=true",Long.class)).isEqualTo(1);
        mvc.perform(get("/api/v1/admin/students/{id}/deletion-preview",student.getId()).with(user(UserPrincipal.from(student)))).andExpect(status().isForbidden());
    }
    @Test void staleOrForgedAdministratorPrincipalsAreRejectedByTheService() {
        var forged=new UserPrincipal(student.getId(),student.getEmail(),"unused",true,"ADMIN",false,student.getTokenVersion());
        assertThatThrownBy(()->asAdmin(()->service.deactivate(student.getId(),forged,new StudentDeletionService.Request(true,student.getEmail(),"Valid reason")))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        var stale=UserPrincipal.from(admin);jdbc.update("update users set token_version=token_version+1 where id=?",admin.getId());
        assertThatThrownBy(()->asAdmin(()->service.deactivate(student.getId(),stale,new StudentDeletionService.Request(true,student.getEmail(),"Valid reason")))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void deactivatedStudentCannotLogInOrReuseAnExistingJwt() throws Exception {
        Cookie cookie=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new edu.institution.ims.auth.LoginRequest(student.getEmail(),PASSWORD)))).andExpect(status().isOk()).andReturn().getResponse().getCookie("ims_auth");
        remove();entities.clear();
        mvc.perform(get("/api/v1/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/student/internship").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new edu.institution.ims.auth.LoginRequest(student.getEmail(),PASSWORD)))).andExpect(status().isForbidden());
    }
    @Test void relatedHistoryOriginalFilesAndSessionCompletionReportsSurviveUnchanged() throws Exception {
        seedHistory();
        List<String> tables=List.of("student_accounts","student_profiles","student_professional_profiles","student_resumes","resume_analyses","resume_extracted_skills","resume_corrections","internship_onboarding","internship_details","internship_locations","attendance_records","attendance_evidence","weekly_internship_periods","weekly_progress_reports","monthly_progress_reports","mentor_student_assignments","opportunity_matching_runs","student_notifications","mentor_notifications","project_seminar_evaluations","internship_completion_events");
        Map<String,List<Map<String,Object>>> before=new LinkedHashMap<>();for(String table:tables)before.put(table,jdbc.queryForList("select * from "+table));
        var checked=preview();assertThat(checked.name()).isEqualTo("Student To Delete");assertThat(checked.uid()).isEqualTo("DEL"+student.getId());assertThat(checked.dependencies().get("Opportunity matching history")).isEqualTo(1);
        assertThat(checked.dependencies().get("Resume versions and extracted information")).isGreaterThan(0);
        assertThat(asAdmin(()->completions.list(sessionId,null,null,null,0)).stats().completedInternships()).isEqualTo(1);
        remove();entities.clear();
        for(String table:tables)assertThat(jdbc.queryForList("select * from "+table)).as(table).containsExactlyInAnyOrderElementsOf(before.get(table));
        try(var input=storage.load(storageKey).resource().getInputStream()) {assertThat(input.readAllBytes()).isEqualTo("Original resume fixture bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        assertThat(asAdmin(()->completions.list(sessionId,null,null,null,0)).stats().completedInternships()).isEqualTo(1);
        var historical=asAdmin(()->reports.preview(UserPrincipal.from(admin),new DashboardReportRequest(List.of("studentName","completionStatus"),Map.of("studentId",student.getId().toString(),"academicSessionId",sessionId.toString()),DashboardReportRequest.Format.XLSX)));
        assertThat(historical.studentCount()).isEqualTo(1);assertThat(historical.students().get(0).get("studentName")).isEqualTo("Student To Delete");
    }
    @Test void activeDirectoriesCountsAndNewOperationalWorkflowsExcludeDeactivatedStudents() {
        assertThat(dashboard.summary(sessionId).students().total()).isEqualTo(2);remove();entities.clear();
        assertThat(dashboard.summary(sessionId).students().total()).isEqualTo(1);
        var list=directory.list(null,null,null,null,null,null,null,null,null,null,null,null,0,20,sessionId);
        assertThat(list.content()).extracting(row->row.id()).doesNotContain(student.getId());assertThat(list.totalElements()).isEqualTo(1);
        assertThat(attendanceDirectory.students(UserPrincipal.from(admin))).extracting(StudentAttendanceDirectoryService.StudentRow::studentUserId).doesNotContain(student.getId());
        assertThat(assignmentService.workspace(mentor.getId()).availableStudents()).extracting(AssignmentStudentResponse::userId).doesNotContain(student.getId());
        assertThatThrownBy(()->assignmentService.assign(mentor.getId(),new AssignStudentsRequest(List.of(student.getId())),UserPrincipal.from(admin))).isInstanceOf(MentorAssignmentException.class);
        assertThat(weekly.monitoring(UserPrincipal.from(admin),true).weeks()).extracting(WeeklyProgressService.Week::studentUserId).doesNotContain(student.getId());
        assertThatThrownBy(()->monthlyService.review(UserPrincipal.from(mentor),monthId,1,true,null)).isInstanceOf(MonthlyProgressException.class);
        assertThat(asAdmin(()->completions.forAdmin(student.getId())).blockers()).anyMatch(message->message.contains("Student account is deactivated"));
        assertThatThrownBy(()->asAdmin(()->completions.decide(UserPrincipal.from(admin),student.getId(),new InternshipCompletionService.Decision(0,null),false))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->workplaces.radiusForAdmin(student.getId(),UserPrincipal.from(admin),BigDecimal.valueOf(100))).isInstanceOf(AttendanceException.class);
        assertThat(attendanceDirectory.history(UserPrincipal.from(admin),student.getId())).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from mentor_student_assignments where student_user_id=?",Long.class,student.getId())).isEqualTo(1);
    }
    void seedHistory() throws Exception {
        jdbc.update("insert into student_profiles(user_id,student_name,branch,uid,roll_number,permanent_address,personal_email,institute_email,phone_number,guardian_name,guardian_contact,emergency_contact,blood_group,aadhaar_encrypted,aadhaar_last_four,faculty_mentor_name,faculty_mentor_contact,internship_coordinator_name,internship_coordinator_contact,cdc_coordinator_name,cdc_coordinator_contact,academic_year,semester,created_at,updated_at) values(?,'Student To Delete','COMPUTER_ENGINEERING',?,?,'Address',?,?,'1234567890','Guardian','1234567890','1234567890','O+','fixture-only','1234','Mentor','1234567890','IIIC','1234567890','CDC','1234567890','2025-2026',8,current_timestamp,current_timestamp)",student.getId(),"DEL"+student.getId(),"R"+student.getId(),student.getEmail(),student.getEmail());
        jdbc.update("insert into student_professional_profiles(student_user_id,headline,created_at,updated_at) values(?,'Retained headline',current_timestamp,current_timestamp)",student.getId());Long professional=jdbc.queryForObject("select id from student_professional_profiles where student_user_id=?",Long.class,student.getId());
        storageKey=storage.store(new ByteArrayInputStream("Original resume fixture bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"pdf");
        jdbc.update("insert into student_resumes(professional_profile_id,original_filename,storage_key,content_type,file_size,uploaded_at,is_active,active_profile_id,parsing_status) values(?,'original.pdf',?,'application/pdf',29,current_timestamp,true,?,'PARSED')",professional,storageKey,professional);Long resume=jdbc.queryForObject("select id from student_resumes where storage_key=?",Long.class,storageKey);
        jdbc.update("insert into resume_analyses(resume_id,status,created_at,updated_at) values(?,'PARSED',current_timestamp,current_timestamp)",resume);Long analysis=jdbc.queryForObject("select id from resume_analyses where resume_id=?",Long.class,resume);
        jdbc.update("insert into resume_extracted_skills(analysis_id,name,sort_order) values(?,'Java',0)",analysis);jdbc.update("insert into resume_corrections(resume_id,revision,values_json,source,editor_user_id,editor_name,edited_at) values(?,1,'{}','STUDENT_CORRECTED',?,'Student',current_timestamp)",resume,student.getId());
        var record=attendance.saveAndFlush(new AttendanceRecord(student,internship,internship.getStartDate(),AttendanceResult.PRESENT,VerificationStatus.VERIFIED,AttendanceSubmissionType.BASIC,Instant.now()));
        jdbc.update("insert into attendance_evidence(attendance_id,latitude,longitude,gps_accuracy_meters,camera_capture_used,verification_reason,image_size_bytes,ai_face_detected,ai_single_face,ai_quality_passed,ai_liveness_passed,created_at) values(?,18.52,73.85,10,false,'PHYSICAL_VERIFICATION_NOT_APPLICABLE',0,false,false,false,false,current_timestamp)",record.getId());
        jdbc.update("insert into internship_locations(internship_id,latitude,longitude,geofence_radius_meters,status,revision,created_at,updated_at) values(?,18.52,73.85,100,'PENDING',1,current_timestamp,current_timestamp)",internship.getId());
        jdbc.update("insert into opportunities(company_name,title,stipend_amount,currency,status,created_by,created_at,updated_at) values('Existing Company','Retained opportunity',0,'INR','DRAFT',?,current_timestamp,current_timestamp)",admin.getId());Long opportunity=jdbc.queryForObject("select max(id) from opportunities",Long.class);
        jdbc.update("insert into opportunity_matching_runs(opportunity_id,trigger_type,algorithm_version,created_at,report_json) values(?,'TEST','test',current_timestamp,?)",opportunity,"{\"candidates\":[{\"studentId\":"+student.getId()+"}]}");
        jdbc.update("insert into student_notifications(student_user_id,event_key,notification_type,title,message,action_url,created_at) values(?,'retained','TEST','Retained notice','Original message','/student/dashboard',current_timestamp)",student.getId());
        jdbc.update("insert into mentor_notifications(mentor_user_id,student_user_id,event_key,notification_type,title,message,action_url,created_at) values(?,?,'retained','TEST','Retained notice','Original message','/mentor/dashboard',current_timestamp)",mentor.getId(),student.getId());
        jdbc.update("insert into project_seminar_evaluations(student_user_id,mentor_user_id,score,maximum_score,evaluation_date,finalized,created_at,updated_at) values(?,?,80,100,DATE '2025-01-09',false,current_timestamp,current_timestamp)",student.getId(),mentor.getId());
        jdbc.update("update weekly_progress_reports set review_status='APPROVED',reviewer_id=?,reviewed_at=current_timestamp where period_id in(select id from weekly_internship_periods where internship_id=?)",mentor.getId(),internship.getId());
        jdbc.update("update monthly_progress_reports set review_status='APPROVED',reviewer_id=?,reviewed_at=current_timestamp where id=?",mentor.getId(),monthId);
        asAdmin(()->completions.decide(UserPrincipal.from(admin),student.getId(),new InternshipCompletionService.Decision(0,"Historical approval"),false));
    }
}
