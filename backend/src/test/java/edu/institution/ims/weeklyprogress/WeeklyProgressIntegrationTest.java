package edu.institution.ims.weeklyprogress;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.internship.*;
import edu.institution.ims.internship.dto.InternshipDetailsRequest;
import edu.institution.ims.mentor.*;
import edu.institution.ims.notification.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class WeeklyProgressIntegrationTest {
    @Autowired WeeklyProgressService service;
    @Autowired InternshipDetailsService detailsService;
    @Autowired InternshipDetailsRepository internships;
    @Autowired InternshipOnboardingRepository onboardings;
    @Autowired UserRepository users;
    @Autowired StudentAccountRepository accounts;
    @Autowired MentorStudentAssignmentRepository assignments;
    @Autowired NotificationService studentNotifications;
    @Autowired MentorNotificationService mentorNotifications;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired edu.institution.ims.attendance.AttendanceRepository attendance;
    @Autowired edu.institution.ims.attendance.AttendanceEvidenceRepository evidence;
    User student, otherStudent, mentor, otherMentor, admin;
    InternshipDetails internship;
    UserPrincipal principal, reviewer;

    @BeforeEach void fixture() {
        student = accountUser(Role.STUDENT); otherStudent = accountUser(Role.STUDENT); mentor = accountUser(Role.MENTOR); otherMentor = accountUser(Role.MENTOR); admin = accountUser(Role.ADMIN);
        principal = UserPrincipal.from(student); reviewer = UserPrincipal.from(mentor);
        accounts.saveAndFlush(new StudentAccount(student, "Weekly Student", "W" + student.getId(), 7));
        assignments.saveAndFlush(new MentorStudentAssignment(mentor,student,admin,Instant.now()));
        var onboarding = new InternshipOnboarding(student);
        onboarding.confirmDiary(); onboarding.markInternshipDetailsComplete(); onboardings.saveAndFlush(onboarding);
        internship = new InternshipDetails(onboarding);
        internship.apply("Weekly Company",CompanyType.IT,null,"Company address","9876543210","Supervisor","Manager","9876543211",2,
                LocalDate.of(2025,11,1),LocalDate.of(2025,12,30),999,InternshipMode.ONLINE,BigDecimal.ZERO,false);
        internships.saveAndFlush(internship);
        service.synchronize(internship);
    }
    User accountUser(Role role) { return users.saveAndFlush(new User("weekly-"+UUID.randomUUID()+"@example.test","unused",role,true)); }
    WeeklyProgressService.Week first() { return service.ownOverview(principal).weeks().stream().filter(WeeklyProgressService.Week::active).findFirst().orElseThrow(); }
    long count(String table) { return jdbc.queryForObject("select count(*) from "+table,Long.class); }
    InternshipDetailsRequest request(LocalDate start, LocalDate end) {
        return new InternshipDetailsRequest("Weekly Company",CompanyType.IT,null,"Company address","9876543210","Supervisor","Manager","9876543211",2,start,end,-999,InternshipMode.ONLINE,BigDecimal.ZERO,false);
    }

    @Test void generatesNineInclusiveWeeksAndRepeatingRegistrationOrReadDoesNotDuplicate() {
        var overview=service.ownOverview(principal);
        assertThat(overview.totalWeeks()).isEqualTo(9);
        assertThat(jdbc.queryForObject("select total_weeks from internship_details where id=?",Integer.class,internship.getId())).isEqualTo(9);
        assertThat(overview.weeks()).hasSize(9);
        assertThat(overview.weeks().get(0).periodEnd()).isEqualTo(LocalDate.of(2025,11,7));
        assertThat(overview.weeks().get(8).periodStart()).isEqualTo(LocalDate.of(2025,12,27));
        assertThat(overview.weeks().get(8).periodEnd()).isEqualTo(LocalDate.of(2025,12,30));
        var ids=overview.weeks().stream().map(WeeklyProgressService.Week::id).toList();
        service.synchronize(internship); service.synchronize(internship);
        assertThat(service.ownOverview(principal).weeks()).extracting(WeeklyProgressService.Week::id).containsExactlyElementsOf(ids);
        assertThat(service.monitored(reviewer,false)).extracting(WeeklyProgressService.Week::id).containsAll(ids);
        assertThat(service.monitored(UserPrincipal.from(otherMentor),false)).isEmpty();
    }

    @Test void extendsEmptyTailAndArchivesChangedEmptyPeriodsWithoutRemovingHistory() {
        var first=first(); service.submit(principal,first.id(),null,"Original submitted first week");
        var oldLast=service.ownOverview(principal).weeks().get(8);
        var response=detailsService.updateOwn(principal,request(internship.getStartDate(),LocalDate.of(2026,1,5)));
        assertThat(response.totalWeeks()).isEqualTo(10); // Forged -999 input is ignored.
        var updated=service.ownOverview(principal);
        assertThat(updated.weeks().stream().filter(WeeklyProgressService.Week::active)).hasSize(10);
        assertThat(updated.weeks().stream().filter(w->w.id().equals(oldLast.id())).findFirst().orElseThrow().active()).isFalse();
        assertThat(updated.weeks().stream().filter(w->w.id().equals(first.id())).findFirst().orElseThrow().workDescription()).isEqualTo("Original submitted first week");
    }

    @Test void submittedPeriodBoundariesCannotBeChanged() {
        var first=first(); service.submit(principal,first.id(),null,"Protected work");
        assertThatThrownBy(()->detailsService.updateOwn(principal,request(LocalDate.of(2025,11,2),internship.getEndDate())))
                .isInstanceOfSatisfying(InternshipDetailsException.class,e->assertThat(e.getCode()).isEqualTo("SUBMITTED_WEEK_DATE_CONFLICT"));
    }

    @Test void rejectedReportCanBeCorrectedWithImmutableHistoryAndReviewNotifications() {
        var first=first(); var pending=service.submit(principal,first.id(),null,"Original work");
        assertThat(pending.status()).isEqualTo("PENDING_MENTOR_REVIEW");
        assertThat(mentorNotifications.list(reviewer,0).content()).filteredOn(n->n.type().equals("WEEKLY_REPORT_SUBMITTED")).hasSize(1);
        var rejected=service.review(reviewer,first.id(),1,false,"Explain the outcome");
        assertThat(rejected.rejectionReason()).isEqualTo("Explain the outcome");
        var resubmitted=service.submit(principal,first.id(),1,"Corrected work and outcome");
        assertThat(resubmitted.revision()).isEqualTo(2);
        var approved=service.review(reviewer,first.id(),2,true,null);
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.history()).extracting(WeeklyProgressService.Event::eventType).containsExactly("SUBMITTED","REJECTED","SUBMITTED","APPROVED");
        assertThat(approved.history().get(0).workDescription()).isEqualTo("Original work");
        assertThat(approved.history().get(2).workDescription()).isEqualTo("Corrected work and outcome");
        assertThat(studentNotifications.list(principal,0).content()).extracting(NotificationService.Notification::type).contains("WEEKLY_REPORT_REJECTED","WEEKLY_REPORT_APPROVED");
        assertThat(mentorNotifications.list(reviewer,0).content()).filteredOn(n->n.type().equals("WEEKLY_REPORT_SUBMITTED")).hasSize(2);
        assertThat(service.monitored(UserPrincipal.from(admin),true)).anyMatch(w->w.id().equals(first.id())&&w.status().equals("APPROVED"));
    }

    @Test void duplicateSubmissionAndReviewRequireValidTransitionsAndFreshRevision() {
        var first=first(); service.submit(principal,first.id(),null,"Work");
        assertThatThrownBy(()->service.submit(principal,first.id(),null,"Duplicate")).isInstanceOf(InternshipDetailsException.class);
        assertThatThrownBy(()->service.review(reviewer,first.id(),7,true,null)).isInstanceOfSatisfying(InternshipDetailsException.class,e->assertThat(e.getCode()).isEqualTo("WEEK_REVISION_CHANGED"));
        assertThatThrownBy(()->service.review(reviewer,first.id(),1,false," ")).isInstanceOfSatisfying(InternshipDetailsException.class,e->assertThat(e.getCode()).isEqualTo("REJECTION_REASON_REQUIRED"));
        service.review(reviewer,first.id(),1,true,null);
        assertThatThrownBy(()->service.review(reviewer,first.id(),1,true,null)).isInstanceOf(InternshipDetailsException.class);
        assertThat(jdbc.queryForObject("select count(*) from weekly_progress_events where period_id=?",Long.class,first.id())).isEqualTo(2);
    }

    @Test void routesEnforceOwnershipAndMentorAssignmentAndNeverGiveAdminReviewAuthority() throws Exception {
        var first=first(); service.submit(principal,first.id(),null,"Review me");
        mvc.perform(post("/api/v1/mentor/weekly-progress/{id}/approve",first.id()).with(user(UserPrincipal.from(otherMentor)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/student/weekly-progress/{id}",first.id()).with(user(UserPrincipal.from(otherStudent)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"workDescription\":\"Forged report\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/mentor/weekly-progress/{id}/approve",first.id()).with(user(UserPrincipal.from(admin)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/student/weekly-progress").with(user(principal))).andExpect(status().isOk()).andExpect(jsonPath("$.totalWeeks").value(9));
    }

    @Test void futureWeeksRejectSubmissionWithoutEventsOrNotifications() {
        var otherOnboarding=new InternshipOnboarding(otherStudent); otherOnboarding.confirmDiary(); otherOnboarding.markInternshipDetailsComplete(); onboardings.saveAndFlush(otherOnboarding);
        var future=new InternshipDetails(otherOnboarding); var start=LocalDate.now().plusYears(1);
        future.apply("Future Company",CompanyType.IT,null,"Address","9876543210","Supervisor","Manager","9876543211",1,start,start.plusDays(8),2,InternshipMode.ONLINE,BigDecimal.ZERO,false);
        internships.saveAndFlush(future); service.synchronize(future);
        var owner=UserPrincipal.from(otherStudent); var week=service.ownOverview(owner).weeks().get(0);
        assertThat(week.status()).isEqualTo("UPCOMING");
        assertThatThrownBy(()->service.submit(owner,week.id(),null,"Too early")).isInstanceOfSatisfying(InternshipDetailsException.class,e->assertThat(e.getCode()).isEqualTo("WEEK_UPCOMING"));
        assertThat(jdbc.queryForObject("select count(*) from weekly_progress_events where period_id=?",Long.class,week.id())).isZero();
    }

    @Test void attendanceSummaryUsesActualGpsSignalAndLeavesAttendanceUnchanged() {
        var at=Instant.now();
        var record=attendance.saveAndFlush(new edu.institution.ims.attendance.AttendanceRecord(student,internship,internship.getStartDate(),
                edu.institution.ims.attendance.AttendanceResult.PRESENT,edu.institution.ims.attendance.VerificationStatus.VERIFIED,
                edu.institution.ims.attendance.AttendanceSubmissionType.BASIC,at));
        attendance.saveAndFlush(edu.institution.ims.attendance.AttendanceRecord.automaticAbsent(student,internship,internship.getStartDate().plusDays(1),at));
        var summary=first();
        assertThat(summary.attendanceSubmitted()).isEqualTo(1);
        assertThat(summary.attendancePresent()).isEqualTo(1);
        assertThat(summary.attendanceGpsVerified()).isZero(); // Overall/liveness verification is not GPS verification.
        var location=new edu.institution.ims.attendance.SubmissionLocation(new BigDecimal("18.52"),new BigDecimal("73.85"),BigDecimal.TEN);
        var proof=evidence.saveAndFlush(new edu.institution.ims.attendance.AttendanceEvidence(record,location,at));
        jdbc.update("update attendance_evidence set location_verification_status='VERIFIED' where id=?",proof.getId());
        assertThat(first().attendanceGpsVerified()).isEqualTo(1);
        assertThat(attendance.findById(record.getId()).orElseThrow().getVerificationStatus()).isEqualTo(edu.institution.ims.attendance.VerificationStatus.VERIFIED);
    }

    @Test void legacyLongInternshipProducesWarningWithoutHidingOtherAssignedStudents() {
        assignments.saveAndFlush(new MentorStudentAssignment(mentor,otherStudent,admin,Instant.now()));
        var oldOnboarding=new InternshipOnboarding(otherStudent); oldOnboarding.confirmDiary(); oldOnboarding.markInternshipDetailsComplete(); onboardings.saveAndFlush(oldOnboarding);
        var legacy=new InternshipDetails(oldOnboarding);
        legacy.apply("Legacy Company",CompanyType.IT,null,"Address","9876543210","Supervisor","Manager","9876543211",240,
                LocalDate.of(2025,1,1),LocalDate.of(2045,1,1),1234,InternshipMode.ONLINE,BigDecimal.ZERO,false);
        internships.saveAndFlush(legacy);
        for(var viewer:List.of(reviewer,UserPrincipal.from(admin))) {
            var result=service.monitoring(viewer,"ADMIN".equals(viewer.role()));
            assertThat(result.weeks()).filteredOn(w->w.studentUserId().equals(student.getId())&&w.active()).hasSize(9);
            assertThat(result.warnings()).anyMatch(w->w.studentUserId().equals(otherStudent.getId())&&w.message().contains("520 weeks"));
        }
        assertThat(detailsService.getForAdmin(otherStudent.getId()).totalWeeks()).isGreaterThan(520);
        assertThat(internships.findById(legacy.getId()).orElseThrow().getEndDate()).isEqualTo(LocalDate.of(2045,1,1));
    }
}
