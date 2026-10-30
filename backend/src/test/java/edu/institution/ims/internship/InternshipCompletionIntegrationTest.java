package edu.institution.ims.internship;

import edu.institution.ims.academicsession.AcademicSessionService;
import edu.institution.ims.dashboard.DashboardService;
import edu.institution.ims.dashboard.report.*;
import edu.institution.ims.monthlyprogress.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentaccounts.StudentAccountImportService;
import edu.institution.ims.user.*;
import edu.institution.ims.weeklyprogress.WeeklyProgressService;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional @WithMockUser(roles="ADMIN")
class InternshipCompletionIntegrationTest {
    @Autowired InternshipCompletionService service;
    @Autowired AcademicSessionService sessions;
    @Autowired StudentAccountImportService importer;
    @Autowired UserRepository users;
    @Autowired StudentAccountRepository accounts;
    @Autowired InternshipOnboardingRepository onboardings;
    @Autowired InternshipDetailsRepository internships;
    @Autowired WeeklyProgressService weekly;
    @Autowired MonthlyProgressReportRepository monthly;
    @Autowired DashboardReportService reports;
    @Autowired InstitutionalReportService institutional;
    @Autowired DashboardService dashboard;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired edu.institution.ims.deletion.MentorRecordDeletionService deletions;
    @Autowired edu.institution.ims.mentor.MentorStudentAssignmentRepository assignments;
    User admin,student,mentor;
    Long sessionId;
    InternshipDetails internship;
    UserPrincipal actor;
    @BeforeEach void fixture() {
        admin=account(Role.ADMIN); mentor=account(Role.MENTOR); actor=UserPrincipal.from(admin);
        String name="LEGACY-"+UUID.randomUUID();
        jdbc.update("insert into academic_sessions(name) values(?)",name);
        sessionId=jdbc.queryForObject("select id from academic_sessions where name=?",Long.class,name);
        jdbc.update("update academic_session_settings set active_session_id=? where id=1",sessionId);
        student=new User("completion-"+UUID.randomUUID()+"@test.edu","unused",Role.STUDENT,true); student.assignAcademicSession(sessionId); users.saveAndFlush(student);
        accounts.saveAndFlush(new StudentAccount(student,"Completion Student","UID-"+student.getId(),8));
        var onboarding=new InternshipOnboarding(student); onboarding.confirmDiary(); onboarding.markInternshipDetailsComplete(); onboardings.saveAndFlush(onboarding);
        internship=new InternshipDetails(onboarding);
        internship.apply("Completion Company",CompanyType.IT,null,"Address","9876543210","Supervisor","Manager","9876543210",1,LocalDate.of(2025,1,1),LocalDate.of(2025,1,9),2,InternshipMode.ONLINE,BigDecimal.ZERO,false);
        internships.saveAndFlush(internship); weekly.synchronize(internship);
    }
    User account(Role role) { return users.saveAndFlush(new User("completion-"+UUID.randomUUID()+"@test.edu","unused",role,true)); }
    void approveAll() {
        jdbc.queryForList("select id from weekly_internship_periods where internship_id=? and active=true",Long.class,internship.getId()).forEach(id->
            jdbc.update("insert into weekly_progress_reports(period_id,work_description,revision,review_status,submitted_at,reviewer_id,reviewed_at) values(?,'Completed work',1,'APPROVED',current_timestamp,?,current_timestamp)",id,mentor.getId()));
        var report=new MonthlyProgressReport(internship,1,internship.getStartDate(),internship.getEndDate(),"Monthly project outcomes","test-file","photo.jpg","image/jpeg",100,null,null,null,Instant.now());
        report.decide(mentor,true,null,Instant.now()); monthly.saveAndFlush(report);
    }
    @Test void missingReportsAndUnresolvedReviewsBlockCompletionOnTheBackend() {
        var missing=service.forAdmin(student.getId());
        assertThat(missing.requiredReports()).isEqualTo(3); assertThat(missing.pendingReports()).isEqualTo(3); assertThat(missing.eligible()).isFalse();
        assertThatThrownBy(()->service.decide(actor,student.getId(),new InternshipCompletionService.Decision(0,null),false)).isInstanceOf(ResponseStatusException.class);
        approveAll();
        jdbc.update("update weekly_progress_reports set review_status='PENDING_MENTOR_REVIEW',reviewer_id=null,reviewed_at=null where period_id in(select id from weekly_internship_periods where internship_id=?)",internship.getId());
        var pending=service.forAdmin(student.getId()); assertThat(pending.submittedReports()).isEqualTo(3); assertThat(pending.pendingReports()).isZero(); assertThat(pending.eligible()).isFalse(); assertThat(pending.mentorApprovalStatus()).isEqualTo("PENDING");
    }
    @Test void successfulApprovalAuditsReviewerNotifiesOnceAndRejectsDuplicates() {
        approveAll(); assertThat(service.forAdmin(student.getId()).eligible()).isTrue();
        var completed=service.decide(actor,student.getId(),new InternshipCompletionService.Decision(0,"Reviewed all work"),false);
        assertThat(completed.status()).isEqualTo("COMPLETED"); assertThat(completed.reviewerId()).isEqualTo(admin.getId()); assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.history()).hasSize(1); assertThat(completed.history().get(0).previousStatus()).isEqualTo("COMPLETION_PENDING");
        assertThat(completed.history().get(0).finalStatus()).isEqualTo("COMPLETED");
        assertThatThrownBy(()->service.decide(actor,student.getId(),new InternshipCompletionService.Decision(1,null),false)).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=? and notification_type='INTERNSHIP_COMPLETED'",Long.class,student.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select workflow_status from internship_onboarding where id=?",String.class,internship.getOnboarding().getId())).isEqualTo("INTERNSHIP_DETAILS_COMPLETE");
    }
    @Test void reopeningRequiresReasonKeepsHistoryAndAllowsFreshCompletion() {
        approveAll(); service.decide(actor,student.getId(),new InternshipCompletionService.Decision(0,null),false);
        assertThatThrownBy(()->service.decide(actor,student.getId(),new InternshipCompletionService.Decision(1," "),true)).isInstanceOf(ResponseStatusException.class);
        var reopened=service.decide(actor,student.getId(),new InternshipCompletionService.Decision(1,"Correct administrative review"),true);
        assertThat(reopened.status()).isEqualTo("COMPLETION_PENDING"); assertThat(reopened.completedAt()).isNull(); assertThat(reopened.history()).hasSize(2); assertThat(reopened.history().get(1).remarks()).contains("Correct administrative");
        var again=service.decide(actor,student.getId(),new InternshipCompletionService.Decision(2,null),false);
        assertThat(again.history()).hasSize(3); assertThat(again.revision()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=? and notification_type='INTERNSHIP_REOPENED'",Long.class,student.getId())).isEqualTo(1);
    }
    @Test void routesEnforceAdminAuthorityStudentOwnershipAndStaleRevision() throws Exception {
        approveAll();
        mvc.perform(post("/api/v1/admin/students/{id}/internship-completion/complete",student.getId()).with(user(UserPrincipal.from(student))).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/students/{id}/internship-completion/complete",student.getId()).with(user(UserPrincipal.from(mentor))).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/students/{id}/internship-completion/complete",student.getId()).with(user(actor)).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":9}")).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/students/{id}/internship-completion/complete",student.getId()).with(user(actor)).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0,\"reviewerId\":999,\"status\":\"FORGED\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.reviewerId").value(admin.getId()));
        mvc.perform(get("/api/v1/student/internship-completion").with(user(UserPrincipal.from(student)))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/v1/student/internship-completion").with(user(UserPrincipal.from(account(Role.STUDENT))))).andExpect(status().isNotFound());
    }
    @Test void wrongMonthlyBoundsAndMissingGeneratedWeeksCannotPass() {
        approveAll(); jdbc.update("update monthly_progress_reports set period_end=period_end-1 where student_internship_id=?",internship.getId());
        assertThat(service.forAdmin(student.getId()).pendingReports()).isEqualTo(1);
        jdbc.update("update weekly_internship_periods set active=false where internship_id=?",internship.getId());
        assertThat(service.forAdmin(student.getId()).pendingReports()).isEqualTo(3);
    }
    @Test void completedMandatoryReportsCannotBeDeletedUntilIiicReopensTheInternship() {
        approveAll(); assignments.saveAndFlush(new edu.institution.ims.mentor.MentorStudentAssignment(mentor,student,admin,Instant.now()));
        Long reportId=jdbc.queryForObject("select id from monthly_progress_reports where student_internship_id=?",Long.class,internship.getId());
        service.decide(actor,student.getId(),new InternshipCompletionService.Decision(0,null),false);
        assertThatThrownBy(()->deletions.delete(UserPrincipal.from(mentor),reportId,true,"Correct submitted report")).isInstanceOf(edu.institution.ims.attendance.AttendanceException.class);
        assertThat(jdbc.queryForObject("select deleted_at from monthly_progress_reports where id=?",Object.class,reportId)).isNull();
        service.decide(actor,student.getId(),new InternshipCompletionService.Decision(1,"Review report correction"),true);
        deletions.delete(UserPrincipal.from(mentor),reportId,true,"Correct submitted report");
        assertThat(service.forAdmin(student.getId()).pendingReports()).isEqualTo(1);
    }
    @Test void completedInternshipDetailsCannotBeEdited() throws Exception {
        approveAll(); service.decide(actor,student.getId(),new InternshipCompletionService.Decision(0,null),false);
        mvc.perform(put("/api/v1/student/internship").with(user(UserPrincipal.from(student))).contentType(MediaType.APPLICATION_JSON).content("{\"companyName\":\"Changed\",\"companyType\":\"IT\",\"companyAddress\":\"Address\",\"companyPhone\":\"9876543210\",\"industrySupervisorName\":\"Supervisor\",\"industrySupervisorDesignation\":\"Manager\",\"industrySupervisorContact\":\"9876543210\",\"durationMonths\":1,\"startDate\":\"2025-01-01\",\"endDate\":\"2025-01-09\",\"internshipMode\":\"ONLINE\",\"stipendPerMonth\":0,\"fullTimeEmploymentOffered\":false}")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select company_name from internship_details where id=?",String.class,internship.getId())).isEqualTo("Completion Company");
    }
    @Test void sessionStatsSearchHistoricalCountsAndZeroDenominatorAreCorrect() {
        approveAll(); service.decide(actor,student.getId(),new InternshipCompletionService.Decision(0,null),false);
        var current=service.list(sessionId,"Completion","COMPLETED","name",0);
        assertThat(current.stats().totalStudents()).isEqualTo(1); assertThat(current.stats().completedInternships()).isEqualTo(1); assertThat(current.stats().completionPercentage()).isEqualTo(100); assertThat(current.content()).hasSize(1);
        var next=sessions.create(actor,new AcademicSessionService.Create("2027–28",LocalDate.of(2027,7,1),LocalDate.of(2028,6,30),null,true));
        assertThat(service.list(null,null,null,null,0).sessionId()).isEqualTo(next.id()); assertThat(service.list(null,null,null,null,0).stats().completionPercentage()).isZero();
        assertThat(service.list(sessionId,null,null,null,0).stats().completedInternships()).isEqualTo(1);
        assertThat(dashboard.summary(sessionId).students().total()).isEqualTo(1); assertThat(dashboard.summary(next.id()).students().total()).isZero();
    }
    @Test void sessionValidationCanonicalDuplicatesOverlapAndAuthorization() throws Exception {
        var first=sessions.create(actor,new AcademicSessionService.Create("2027–28",LocalDate.of(2027,7,1),LocalDate.of(2028,6,30),"New intake",true));
        assertThat(first.name()).isEqualTo("2027-2028"); assertThat(sessions.activeId()).isEqualTo(first.id());
        assertThatThrownBy(()->sessions.create(actor,new AcademicSessionService.Create(" 2027-2028 ",LocalDate.of(2029,7,1),LocalDate.of(2030,6,30),null,false))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->sessions.create(actor,new AcademicSessionService.Create("OVERLAP",LocalDate.of(2028,1,1),LocalDate.of(2029,1,1),null,false))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->sessions.create(actor,new AcademicSessionService.Create("INVALID",LocalDate.of(2028,1,1),LocalDate.of(2028,1,1),null,false))).isInstanceOf(ResponseStatusException.class);
        var second=sessions.create(actor,new AcademicSessionService.Create("2028-29",LocalDate.of(2028,7,1),LocalDate.of(2029,6,30),null,false)); sessions.activate(actor,second.id());
        assertThat(sessions.list().stream().filter(AcademicSessionService.Session::active)).hasSize(1); assertThat(sessions.activeId()).isEqualTo(second.id());
        mvc.perform(post("/api/v1/admin/academic-sessions/{id}/activate",first.id()).with(user(UserPrincipal.from(mentor)))).andExpect(status().isForbidden());
    }
    MockMultipartFile workbook(String uid) throws Exception {
        try(var book=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
            var sheet=book.createSheet("Students"); var header=sheet.createRow(0); String[] values={"Name","UID","Email ID","Semester"}; for(int c=0;c<4;c++)header.createCell(c).setCellValue(values[c]);
            var row=sheet.createRow(1); row.createCell(0).setCellValue("New Student");row.createCell(1).setCellValue(uid);row.createCell(2).setCellValue(uid.toLowerCase()+"@test.edu");row.createCell(3).setCellValue(8);book.write(output);
            return new MockMultipartFile("file","students.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",output.toByteArray());
        }
    }
    @Test void importUsesActiveSessionAndNeverMovesExistingStudents() throws Exception {
        var next=sessions.create(actor,new AcademicSessionService.Create("2027-28",LocalDate.of(2027,7,1),LocalDate.of(2028,6,30),null,true));
        String uid="N"+UUID.randomUUID().toString().replace("-","");
        assertThat(importer.importWorkbook(workbook(uid)).accountsCreated()).isEqualTo(1);
        assertThat(users.findByEmailIgnoreCase(uid.toLowerCase()+"@test.edu").orElseThrow().getAcademicSessionId()).isEqualTo(next.id());
        assertThat(users.findById(student.getId()).orElseThrow().getAcademicSessionId()).isEqualTo(sessionId);
        assertThat(admin.getAcademicSessionId()).isNull(); assertThat(mentor.getAcademicSessionId()).isNull();
    }
    @Test void importWithoutActiveSessionReturnsAdministrativeErrorAndCreatesNothing() throws Exception {
        jdbc.update("update academic_session_settings set active_session_id=null where id=1"); String uid="N"+UUID.randomUUID().toString().replace("-","");
        assertThatThrownBy(()->importer.importWorkbook(workbook(uid))).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(users.existsByEmailIgnoreCase(uid.toLowerCase()+"@test.edu")).isFalse();
    }
    @Test void historicalDatesCanBeSuppliedWithoutMovingStudentsAndBootstrapAlsoUsesActiveSession() {
        var dated=sessions.dateHistorical(actor,sessionId,new AcademicSessionService.Create("IGNORED CLIENT NAME",LocalDate.of(2025,7,1),LocalDate.of(2026,6,30),"Recorded calendar dates",true));
        assertThat(dated.name()).startsWith("LEGACY-"); assertThat(dated.studentCount()).isEqualTo(1);
        assertThatThrownBy(()->sessions.dateHistorical(actor,sessionId,new AcademicSessionService.Create(null,LocalDate.of(2030,1,1),LocalDate.of(2031,1,1),null,false))).isInstanceOf(ResponseStatusException.class);
        String email="bootstrap-"+UUID.randomUUID()+"@test.edu";
        var bootstrap=new AccountBootstrap(users,accounts,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(),"","",List.of(email),List.of("BootstrapPassword!23"));
        org.springframework.test.util.ReflectionTestUtils.setField(bootstrap,"sessions",sessions);
        bootstrap.run(new org.springframework.boot.DefaultApplicationArguments(new String[0]));
        assertThat(users.findByEmailIgnoreCase(email).orElseThrow().getAcademicSessionId()).isEqualTo(sessionId);
        jdbc.update("update academic_session_settings set active_session_id=null where id=1");
        String skipped="skipped-"+UUID.randomUUID()+"@test.edu";
        var noSession=new AccountBootstrap(users,accounts,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(),"","",List.of(skipped),List.of("BootstrapPassword!23"));
        org.springframework.test.util.ReflectionTestUtils.setField(noSession,"sessions",sessions); noSession.run(new org.springframework.boot.DefaultApplicationArguments(new String[0]));
        assertThat(users.existsByEmailIgnoreCase(skipped)).isFalse();
    }
    @Test void existingExcelPdfAndInstitutionalReportsIncludeCompletionAndSessionMembership() throws Exception {
        approveAll();service.decide(actor,student.getId(),new InternshipCompletionService.Decision(0,"Final review"),false);
        var fields=List.of("studentName","uid","companyName","mentorName","academicSession","completionStatus","completionDate","completionReviewer");
        var filters=Map.of("academicSessionId",sessionId.toString(),"completionStatus","COMPLETED","completionSearch","Completion Company");
        for(var format:List.of(DashboardReportRequest.Format.XLSX,DashboardReportRequest.Format.PDF)) {
            var download=reports.export(actor,new DashboardReportRequest(fields,filters,format));
            try { assertThat(download.studentCount()).isEqualTo(1);assertThat(Files.size(download.path())).isGreaterThan(100); }
            finally { Files.deleteIfExists(download.path()); }
        }
        var table=institutional.preview(actor,new ReportTableRequest("COMPLETIONS",null,filters,null,0,DashboardReportRequest.Format.XLSX));
        assertThat(table.totalRows()).isEqualTo(1);assertThat(table.rows().get(0).get("completionStatus")).isEqualTo("COMPLETED");
    }
}
