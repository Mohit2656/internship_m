package edu.institution.ims.academicsession;

import edu.institution.ims.attendance.*;
import edu.institution.ims.internship.*;
import edu.institution.ims.monthlyprogress.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentaccounts.StudentAccountImportService;
import edu.institution.ims.user.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional @WithMockUser(roles="ADMIN")
@TestPropertySource(properties="spring.datasource.url=jdbc:h2:mem:session_lifecycle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class AcademicSessionIntegrationTest {
    @Autowired AcademicSessionService sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired StudentAccountRepository accounts;
    @Autowired StudentAccountImportService importer;
    @Autowired InternshipOnboardingRepository onboardings;
    @Autowired InternshipDetailsRepository internships;
    @Autowired AttendanceRepository attendance;
    @Autowired MonthlyProgressReportRepository monthly;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    static final AtomicInteger YEARS=new AtomicInteger(3000);
    int year;
    User admin,mentor,student;
    UserPrincipal actor;
    AcademicSessionService.Session original;

    @BeforeEach void fixture() {
        year=YEARS.getAndAdd(4);
        admin=account(Role.ADMIN); mentor=account(Role.MENTOR); actor=UserPrincipal.from(admin);
        original=create("ORIGINAL-"+UUID.randomUUID(),year,true);
    }
    User account(Role role) { return users.saveAndFlush(new User("session-"+UUID.randomUUID()+"@test.edu","unused",role,true)); }
    AcademicSessionService.Session create(String name,int startYear,boolean active) {
        return sessions.create(actor,new AcademicSessionService.Create(name,LocalDate.of(startYear,7,1),LocalDate.of(startYear+1,6,30),null,active));
    }
    AcademicSessionService.Confirmation confirmed(AcademicSessionService.Session session) { return new AcademicSessionService.Confirmation(true,session.name()); }
    void authenticateAdmin() {
        var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(actor,null,actor.getAuthorities()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
    }
    User addStudent() {
        var user=new User("student-"+UUID.randomUUID()+"@test.edu","unused",Role.STUDENT,true);user.assignAcademicSession(original.id());users.saveAndFlush(user);
        accounts.saveAndFlush(new StudentAccount(user,"Historical student","S-"+user.getId(),8));student=user;return user;
    }
    InternshipDetails addHistory() {
        addStudent();
        var onboarding=new InternshipOnboarding(student);onboarding.confirmDiary();onboarding.markInternshipDetailsComplete();onboardings.saveAndFlush(onboarding);
        var internship=new InternshipDetails(onboarding);
        internship.apply("Historical company",CompanyType.IT,null,"Address","9876543210","Supervisor","Manager","9876543210",1,LocalDate.of(2025,1,1),LocalDate.of(2025,1,9),2,InternshipMode.ONLINE,BigDecimal.ZERO,false);
        internships.saveAndFlush(internship);
        attendance.saveAndFlush(new AttendanceRecord(student,internship,LocalDate.of(2025,1,2),AttendanceResult.PRESENT,VerificationStatus.UNVERIFIED,AttendanceSubmissionType.BASIC,Instant.now()));
        monthly.saveAndFlush(new MonthlyProgressReport(internship,1,internship.getStartDate(),internship.getEndDate(),"Historical report","stored-file","photo.jpg","image/jpeg",100,null,null,null,Instant.now()));
        return internship;
    }
    MockMultipartFile workbook(String uid) throws Exception {
        try(var workbook=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet("Students");var header=sheet.createRow(0);
            var labels=List.of("Name","UID","Email ID","Semester");for(int i=0;i<labels.size();i++)header.createCell(i).setCellValue(labels.get(i));
            var row=sheet.createRow(1);row.createCell(0).setCellValue("New student");row.createCell(1).setCellValue(uid);row.createCell(2).setCellValue(uid.toLowerCase()+"@test.edu");row.createCell(3).setCellValue("8");
            workbook.write(output);return new MockMultipartFile("file","students.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",output.toByteArray());
        }
    }
    void conflict(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));
    }
    @Test void closePreservesMembershipInternshipsAttendanceAndReportsAndStopsNewImports() throws Exception {
        var internship=addHistory();
        var before=jdbc.queryForMap("select * from users where id=?",student.getId());
        var attendanceBefore=jdbc.queryForList("select * from attendance_records where internship_id=?",internship.getId());
        var reportsBefore=jdbc.queryForList("select * from monthly_progress_reports where student_internship_id=?",internship.getId());
        mvc.perform(post("/api/v1/admin/academic-sessions/{id}/close",original.id()).with(user(actor)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmed\":true,\"sessionName\":\""+original.name()+"\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED")).andExpect(jsonPath("$.active").value(false));
        authenticateAdmin();
        var closed=sessions.list().stream().filter(s->s.id().equals(original.id())).findFirst().orElseThrow();
        assertThat(closed.closedAt()).isNotNull();assertThat(closed.closedById()).isEqualTo(admin.getId());assertThat(closed.closedBy()).isEqualTo(admin.getEmail());assertThat(sessions.activeId()).isNull();
        assertThat(jdbc.queryForMap("select * from users where id=?",student.getId())).isEqualTo(before);
        assertThat(jdbc.queryForList("select * from attendance_records where internship_id=?",internship.getId())).isEqualTo(attendanceBefore);
        assertThat(jdbc.queryForList("select * from monthly_progress_reports where student_internship_id=?",internship.getId())).isEqualTo(reportsBefore);
        assertThat(jdbc.queryForObject("select count(*) from internship_details where id=?",Long.class,internship.getId())).isEqualTo(1);
        var upload=workbook("CLOSED"+UUID.randomUUID().toString().replace("-",""));conflict(()->importer.importWorkbook(upload));
        assertThat(sessions.history(original.id())).extracting(AcademicSessionService.Event::action).containsExactly("CREATED","ACTIVATED","CLOSED");
    }
    @Test void activationAfterClosureAssignsOnlyNewStudentsAndNeverReopensTheOldSession() throws Exception {
        addStudent();sessions.close(actor,original.id(),confirmed(original));
        var next=create("NEXT-"+UUID.randomUUID(),year+1,false);sessions.activate(actor,next.id());
        String uid="NEW"+UUID.randomUUID().toString().replace("-","");assertThat(importer.importWorkbook(workbook(uid)).accountsCreated()).isEqualTo(1);
        assertThat(users.findByEmailIgnoreCase(uid.toLowerCase()+"@test.edu").orElseThrow().getAcademicSessionId()).isEqualTo(next.id());
        assertThat(users.findById(student.getId()).orElseThrow().getAcademicSessionId()).isEqualTo(original.id());
        conflict(()->sessions.activate(actor,original.id()));
        assertThat(sessions.activeId()).isEqualTo(next.id());
    }
    @Test void activatingNewSessionClosesThePreviousOneWithAuditAndExactlyOneActivePointer() {
        addStudent();var next=create("NEXT-"+UUID.randomUUID(),year+1,true);
        assertThat(sessions.list().stream().filter(AcademicSessionService.Session::active)).hasSize(1);
        assertThat(sessions.activeId()).isEqualTo(next.id());
        var old=sessions.list().stream().filter(s->s.id().equals(original.id())).findFirst().orElseThrow();assertThat(old.status()).isEqualTo("CLOSED");assertThat(old.closedById()).isEqualTo(admin.getId());
        assertThat(sessions.history(original.id())).extracting(AcademicSessionService.Event::action).containsExactly("CREATED","ACTIVATED","CLOSED");
        assertThat(jdbc.queryForObject("select academic_session_id from users where id=?",Long.class,student.getId())).isEqualTo(original.id());
    }
    @Test void explicitReopeningIsConfirmedAuthorizedAuditedAndClosesTheCurrentSession() {
        sessions.close(actor,original.id(),confirmed(original));var next=create("NEXT-"+UUID.randomUUID(),year+1,true);
        conflict(()->sessions.activate(actor,original.id()));
        var reopened=sessions.reopen(actor,original.id(),confirmed(original));assertThat(reopened.active()).isTrue();assertThat(reopened.closedAt()).isNull();
        assertThat(sessions.history(original.id())).extracting(AcademicSessionService.Event::action).containsExactly("CREATED","ACTIVATED","CLOSED","REOPENED","ACTIVATED");
        assertThat(sessions.list().stream().filter(s->s.id().equals(next.id())).findFirst().orElseThrow().status()).isEqualTo("CLOSED");
        assertThat(sessions.history(original.id())).allSatisfy(e->{assertThat(e.performedBy()).isEqualTo(admin.getId());assertThat(e.performedAt()).isNotNull();});
    }
    @Test void emptyInactiveSessionCanBeDeletedWhileAuditSurvives() throws Exception {
        var empty=create("EMPTY-"+UUID.randomUUID(),year+1,false);
        assertThat(sessions.deletionPreview(empty.id()).canDelete()).isTrue();
        mvc.perform(delete("/api/v1/admin/academic-sessions/{id}",empty.id()).with(user(actor)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmed\":true,\"sessionName\":\""+empty.name()+"\"}")).andExpect(status().isNoContent());
        authenticateAdmin();
        assertThat(jdbc.queryForObject("select count(*) from academic_sessions where id=?",Long.class,empty.id())).isZero();
        assertThat(sessions.activeId()).isEqualTo(original.id());
        assertThat(sessions.history(empty.id())).extracting(AcademicSessionService.Event::action).containsExactly("CREATED","DELETED");
        mvc.perform(get("/api/v1/admin/academic-sessions/{id}/history",empty.id()).with(user(actor))).andExpect(status().isOk()).andExpect(jsonPath("$[1].sessionName").value(empty.name()));
    }
    @Test void emptyClosedSessionCanBeDeletedButAnActiveSessionCannot() {
        assertThat(sessions.deletionPreview(original.id()).canDelete()).isFalse();conflict(()->sessions.delete(actor,original.id(),confirmed(original)));
        sessions.close(actor,original.id(),confirmed(original));sessions.delete(actor,original.id(),confirmed(original));
        assertThat(sessions.history(original.id())).extracting(AcademicSessionService.Event::action).endsWith("CLOSED","DELETED");
    }
    @Test void studentsAndSoftDeletedRecordsBlockDeletionAndAreIncludedInPreview() {
        var internship=addHistory();
        jdbc.update("update users set active=false where id=?",student.getId());
        jdbc.update("update attendance_records set deleted_at=current_timestamp,active_slot=null where internship_id=?",internship.getId());
        sessions.close(actor,original.id(),confirmed(original));var preview=sessions.deletionPreview(original.id());
        assertThat(preview.canDelete()).isFalse();assertThat(preview.session().studentCount()).isEqualTo(1);
        assertThat(preview.records()).containsEntry("Internships",1L).containsEntry("Attendance records",1L).containsEntry("Monthly reports",1L);
        conflict(()->sessions.delete(actor,original.id(),confirmed(original)));
        assertThat(jdbc.queryForObject("select count(*) from monthly_progress_reports where student_internship_id=?",Long.class,internship.getId())).isEqualTo(1);
    }
    @Test void independentHistoricalSnapshotBlocksDeletionEvenWithoutAnyAssignedStudents() {
        var internship=addHistory();var historical=create("SNAPSHOT-"+UUID.randomUUID(),year+1,false);
        jdbc.update("insert into internship_completion_events(internship_id,student_user_id,academic_session_id,revision,previous_status,final_status,reviewer_id,performed_at) values(?,?,?,1,'IN_PROGRESS','COMPLETED',?,current_timestamp)",internship.getId(),student.getId(),historical.id(),admin.getId());
        var preview=sessions.deletionPreview(historical.id());assertThat(preview.session().studentCount()).isZero();assertThat(preview.records()).containsEntry("Completion history",1L);assertThat(preview.canDelete()).isFalse();
        conflict(()->sessions.delete(actor,historical.id(),confirmed(historical)));
    }
    @Test void confirmationAndIdentityAreCheckedBeforeAnyMutation() throws Exception {
        mvc.perform(post("/api/v1/admin/academic-sessions/{id}/close",original.id()).with(user(actor)).contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":false}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/academic-sessions/{id}/close",original.id()).with(user(actor)).contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true,\"sessionName\":\"wrong session\"}")).andExpect(status().isConflict());
        authenticateAdmin();
        assertThat(sessions.activeId()).isEqualTo(original.id());
        assertThat(sessions.history(original.id())).hasSize(2);
    }
    @Test void historicalDatesCannotSilentlyReopenAClosedSession() {
        jdbc.update("insert into academic_sessions(name) values('UNDATED-"+year+"')");Long id=jdbc.queryForObject("select id from academic_sessions where name=?",Long.class,"UNDATED-"+year);
        var undated=sessions.list().stream().filter(s->s.id().equals(id)).findFirst().orElseThrow();sessions.close(actor,id,confirmed(undated));
        conflict(()->sessions.dateHistorical(actor,id,new AcademicSessionService.Create(null,LocalDate.of(year+1,7,1),LocalDate.of(year+2,6,30),null,true)));
        var dated=sessions.dateHistorical(actor,id,new AcademicSessionService.Create(null,LocalDate.of(year+1,7,1),LocalDate.of(year+2,6,30),null,false));
        assertThat(dated.status()).isEqualTo("CLOSED");assertThat(sessions.activeId()).isEqualTo(original.id());
    }
    @Test void studentMentorAndUnauthenticatedUsersCannotManageSessions() throws Exception {
        for(Role role:List.of(Role.STUDENT,Role.MENTOR)) {
            var principal=UserPrincipal.from(account(role));
            for(String action:List.of("activate","close","reopen"))mvc.perform(post("/api/v1/admin/academic-sessions/{id}/"+action,original.id()).with(user(principal)).contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}")).andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/admin/academic-sessions/{id}",original.id()).with(user(principal)).contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}")).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/admin/academic-sessions").with(user(principal)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
            for(String suffix:List.of("","/deletion-preview","/history"))mvc.perform(get("/api/v1/admin/academic-sessions/{id}"+suffix,original.id()).with(user(principal))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/admin/academic-sessions/{id}/close",original.id()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()).contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}")).andExpect(status().isUnauthorized());
        assertThat(sessions.activeId()).isEqualTo(original.id());
    }
}
