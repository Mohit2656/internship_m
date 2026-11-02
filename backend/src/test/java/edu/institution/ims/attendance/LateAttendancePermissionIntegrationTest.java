package edu.institution.ims.attendance;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.*;
import edu.institution.ims.notification.MentorNotificationService;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:late_request_tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "app.notifications.reminders.enabled=false","logging.level.root=WARN","logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
@ContextConfiguration(classes={edu.institution.ims.ImsApplication.class,LateAttendancePermissionIntegrationTest.ClockConfiguration.class})
class LateAttendancePermissionIntegrationTest {
    static final Instant NOW=Instant.parse("2026-10-27T08:00:00Z");
    static final LocalDate TODAY=LocalDate.of(2026,10,27);
    static final LocalDate DATE=TODAY.minusDays(1);
    static final String STUDENT="/api/v1/student/attendance/late-requests";
    static final String MENTOR="/api/v1/mentor/attendance/late-requests";
    static final String ADMIN="/api/v1/admin/attendance/late-requests";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired StudentAccountRepository accounts;
    @Autowired InternshipOnboardingRepository onboardings;
    @Autowired InternshipDetailsRepository internships;
    @Autowired MentorStudentAssignmentRepository assignments;
    @Autowired AttendanceRepository attendance;
    @Autowired AttendanceEvidenceRepository evidence;
    @Autowired AttendanceService normalAttendance;
    @Autowired LateAttendanceRequestService service;
    @Autowired MentorNotificationService mentorNotifications;
    @Autowired MutableClock clock;
    @Autowired AttendanceCalendarProperties calendar;
    User admin,mentor,otherMentor,student,second,orphan;
    InternshipDetails internship,secondInternship,orphanInternship;
    MentorStudentAssignment assignment;

    @BeforeEach void setup() {
        clock.at=NOW;
        calendar.setHolidays(Set.of()); calendar.setApprovedLeave(Map.of());
        admin=account("admin",Role.ADMIN); mentor=account("mentor",Role.MENTOR); otherMentor=account("other-mentor",Role.MENTOR);
        student=student("student","Request Student","LATE001"); second=student("second","No Request Student","LATE002"); orphan=student("orphan","Unassigned Student","LATE003");
        internship=internship(student); secondInternship=internship(second); orphanInternship=internship(orphan);
        assignment=assignments.saveAndFlush(new MentorStudentAssignment(mentor,student,admin,NOW));
        assignments.saveAndFlush(new MentorStudentAssignment(mentor,second,admin,NOW));
    }
    @AfterEach void resetCalendar() { calendar.setHolidays(Set.of()); calendar.setApprovedLeave(Map.of()); }
    @Test void onlySubmittedPendingRequestsAppearAndSubmissionNotifiesExactlyTheAssignedMentor() throws Exception {
        list(MENTOR,mentor,"pending").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        long id=submit(student,internship,DATE);
        list(MENTOR,mentor,"pending").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentName").value("Request Student")).andExpect(jsonPath("$[0].uid").value("LATE001"))
                .andExpect(jsonPath("$[0].attendanceDate").value(DATE.toString())).andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].requestedAt").value(NOW.toString())).andExpect(jsonPath("$[0].supportingDetails").value("Task notes and supervisor reference"));
        list(MENTOR,mentor,"history").andExpect(jsonPath("$.length()").value(0));
        list(MENTOR,otherMentor,"pending").andExpect(jsonPath("$.length()").value(0));
        list(ADMIN,admin,"pending").andExpect(jsonPath("$.length()").value(0));
        var inbox=mentorNotifications.list(UserPrincipal.from(mentor),0);
        assertThat(inbox.content()).hasSize(1);
        assertThat(inbox.content().get(0).message()).contains("Request Student","LATE001",DATE.toString());
        assertThat(inbox.content().get(0).actionUrl()).isEqualTo("/mentor/attendance#late-attendance-permissions");
        assertThat(inbox.content().get(0).readAt()).isNull();
        submission(student,internship,DATE).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LATE_REQUEST_ALREADY_EXISTS"));
        assertThat(mentorNotifications.unread(UserPrincipal.from(mentor)).unreadCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from late_attendance_requests",Long.class)).isEqualTo(1);
        assertThat(service.studentRequests(UserPrincipal.from(student),internship.getId()).get(0).id()).isEqualTo(id);
        assertThat(service.studentRequests(UserPrincipal.from(second),internship.getId())).isEmpty();
    }
    @Test void notificationReadStateIsOwnedAndIdempotent() throws Exception {
        submit(student,internship,DATE);
        long notification=mentorNotifications.list(UserPrincipal.from(mentor),0).content().get(0).id();
        mvc.perform(patch("/api/v1/mentor/notifications/{id}/read",notification).with(user(UserPrincipal.from(otherMentor)))).andExpect(status().isNotFound());
        for(int n=0;n<2;n++) mvc.perform(patch("/api/v1/mentor/notifications/{id}/read",notification).with(user(UserPrincipal.from(mentor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(0));
        assertThat(mentorNotifications.list(UserPrincipal.from(mentor),0).content().get(0).readAt()).isEqualTo(NOW);
    }
    @Test void unassignedMentorAndAdministratorCannotApproveAssignedStudents() throws Exception {
        long id=submit(student,internship,DATE);
        decision(MENTOR,id,otherMentor,"APPROVED",null,false).andExpect(status().isNotFound());
        decision(ADMIN,id,admin,"APPROVED",null,false).andExpect(status().isNotFound());
        decision(MENTOR,id,student,"APPROVED",null,false).andExpect(status().isForbidden());
        mvc.perform(get(MENTOR)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select status from late_attendance_requests where id=?",String.class,id)).isEqualTo("PENDING");
        assertThat(attendance.count()).isZero();
    }
    @Test void historicalApprovalReusesAutomaticAbsenceAndCannotBeRepeated() throws Exception {
        var absent=attendance.saveAndFlush(AttendanceRecord.automaticAbsent(student,internship,DATE,NOW.minusSeconds(100)));
        long id=submit(student,internship,DATE);
        decision(MENTOR,id,mentor,"APPROVED",null,false).andExpect(status().isOk()).andExpect(jsonPath("$.attendanceId").value(absent.getId()));
        var record=attendance.findById(absent.getId()).orElseThrow();
        assertThat(record.getAttendanceDate()).isEqualTo(DATE);
        assertThat(record.getAttendanceResult()).isEqualTo(AttendanceResult.PRESENT);
        assertThat(record.getSubmissionType()).isEqualTo(AttendanceSubmissionType.LATE_REQUEST);
        assertThat(record.getVerificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
        assertThat(record.getVerificationBasis()).isEqualTo(AttendanceVerificationBasis.MANUAL);
        assertThat(record.getLivenessStatus()).isEqualTo(LivenessStatus.NOT_PERFORMED);
        assertThat(record.getEvidence()).isNull();
        assertThat(record.getAutomaticAbsentAt()).isEqualTo(NOW.minusSeconds(100));
        assertThat(record.getReview().getStatus()).isEqualTo(AttendanceReviewStatus.APPROVED);
        assertThat(record.getLateAuthorizedBy().getId()).isEqualTo(mentor.getId());
        decision(MENTOR,id,mentor,"APPROVED",null,false).andExpect(status().isConflict());
        assertThat(attendance.countIncludingDeleted(internship.getId(),DATE)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from attendance_submission_audit where attendance_id=?",Long.class,record.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from student_notifications where event_key=?",Long.class,"late-attendance-decision:"+id)).isEqualTo(1);
        list(MENTOR,mentor,"pending").andExpect(jsonPath("$.length()").value(0));
        list(MENTOR,mentor,"history").andExpect(jsonPath("$[0].status").value("APPROVED"));
        mvc.perform(get("/api/v1/student/attendance").param("internshipId",internship.getId().toString()).with(user(UserPrincipal.from(student))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].attendanceDate").value(DATE.toString())).andExpect(jsonPath("$[0].review.status").value("APPROVED"));
        mvc.perform(get("/api/v1/student/attendance/{id}/review/history",record.getId()).with(user(UserPrincipal.from(student))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[0].reason").value(org.hamcrest.Matchers.containsString("Late attendance request #"+id)));
    }
    @Test void historicalApprovalWithoutPriorRecordCreatesOnlyTheRequestedDate() throws Exception {
        long id=submit(student,internship,DATE);
        decision(MENTOR,id,mentor,"APPROVED",null,false).andExpect(status().isOk());
        assertThat(attendance.count()).isEqualTo(1);
        assertThat(attendance.findByStudentIdAndInternshipIdAndAttendanceDate(student.getId(),internship.getId(),TODAY)).isEmpty();
        assertThat(attendance.findByStudentIdAndInternshipIdAndAttendanceDate(student.getId(),internship.getId(),DATE)).isPresent();
        submission(student,internship,DATE).andExpect(status().isConflict());
    }
    @Test void rejectionIsHistoricalAndResubmissionRequiresExplicitPermission() throws Exception {
        long first=submit(student,internship,DATE);
        decision(MENTOR,first,mentor,"REJECTED",null,false).andExpect(status().isOk()).andExpect(jsonPath("$.resubmissionAllowed").value(false));
        submission(student,internship,DATE).andExpect(status().isConflict());
        list(MENTOR,mentor,"pending").andExpect(jsonPath("$.length()").value(0));
        list(MENTOR,mentor,"history").andExpect(jsonPath("$[0].status").value("REJECTED"));
        long retryable=submit(student,internship,TODAY);
        decision(MENTOR,retryable,mentor,"REJECTED","Please include your supervisor reference",true).andExpect(status().isOk())
                .andExpect(jsonPath("$.resubmissionAllowed").value(true));
        long resubmitted=submit(student,internship,TODAY);
        assertThat(resubmitted).isNotEqualTo(retryable);
        submission(student,internship,TODAY).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from late_attendance_requests",Long.class)).isEqualTo(3);
        list(MENTOR,mentor,"pending").andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(resubmitted));
        list(MENTOR,mentor,"history").andExpect(jsonPath("$.length()").value(2));
        assertThat(jdbc.queryForObject("select count(*) from mentor_notifications",Long.class)).isEqualTo(3);
        assertThat(attendance.count()).isZero();
    }
    @Test void orphanRequestsNotifyOnlyAuthorizedIiicAndCanBeReviewedThere() throws Exception {
        long id=submit(orphan,orphanInternship,DATE);
        list(ADMIN,admin,"pending").andExpect(status().isOk()).andExpect(jsonPath("$[0].mentorAssigned").value(false));
        list(MENTOR,mentor,"pending").andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/admin/notifications").with(user(UserPrincipal.from(admin)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].message").value(org.hamcrest.Matchers.containsString("LATE003")))
                .andExpect(jsonPath("$.content[0].actionUrl").value("/admin/attendance#late-attendance-requests"));
        assertThat(jdbc.queryForObject("select count(*) from mentor_notifications",Long.class)).isZero();
        decision(MENTOR,id,mentor,"APPROVED",null,false).andExpect(status().isNotFound());
        decision(ADMIN,id,admin,"APPROVED",null,false).andExpect(status().isOk());
        list(ADMIN,admin,"history").andExpect(jsonPath("$[0].status").value("APPROVED"));
    }
    @Test void reassignmentRevokesOldMentorAccessAndNewMentorSeesPendingRequest() throws Exception {
        long id=submit(student,internship,DATE);
        assignment.end(NOW); assignments.flush();
        assignments.saveAndFlush(new MentorStudentAssignment(otherMentor,student,admin,NOW));
        list(MENTOR,mentor,"pending").andExpect(jsonPath("$.length()").value(0));
        assertThat(mentorNotifications.list(UserPrincipal.from(mentor),0).content()).isEmpty();
        decision(MENTOR,id,mentor,"APPROVED",null,false).andExpect(status().isNotFound());
        list(MENTOR,otherMentor,"pending").andExpect(jsonPath("$[0].id").value(id));
        decision(MENTOR,id,otherMentor,"REJECTED","Assignment changed",false).andExpect(status().isOk());
    }
    @Test void inactiveAssignedMentorRoutesNewRequestToIiicAndCannotReview() throws Exception {
        jdbc.update("update users set active=false where id=?",mentor.getId());
        long id=submit(student,internship,DATE);
        list(ADMIN,admin,"pending").andExpect(status().isOk()).andExpect(jsonPath("$[0].mentorAssigned").value(false));
        list(MENTOR,mentor,"pending").andExpect(status().isNotFound());
        decision(MENTOR,id,mentor,"APPROVED",null,false).andExpect(status().isNotFound());
        decision(ADMIN,id,admin,"APPROVED",null,false).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=? and notification_type='LATE_ATTENDANCE_REQUEST'",Long.class,admin.getId())).isEqualTo(1);
    }
    @Test void submittedAttendanceIsProtectedAtBothRequestAndApprovalTime() throws Exception {
        var present=attendance.saveAndFlush(new AttendanceRecord(student,internship,DATE,AttendanceResult.PRESENT,VerificationStatus.VERIFIED,AttendanceSubmissionType.BASIC,NOW));
        var gps=evidence.saveAndFlush(new AttendanceEvidence(present,new SubmissionLocation(new BigDecimal("21.12"),new BigDecimal("79.08"),BigDecimal.TEN),NOW));
        submission(student,internship,DATE).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTENDANCE_ALREADY_SUBMITTED"));
        long id=submit(second,secondInternship,DATE);
        attendance.saveAndFlush(new AttendanceRecord(second,secondInternship,DATE,AttendanceResult.PENDING_MENTOR_REVIEW,VerificationStatus.UNVERIFIED,AttendanceSubmissionType.BASIC,NOW));
        decision(MENTOR,id,mentor,"APPROVED",null,false).andExpect(status().isConflict());
        assertThat(evidence.findById(gps.getId()).orElseThrow().getLatitude()).isEqualByComparingTo("21.12");
        assertThat(present.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(jdbc.queryForObject("select status from late_attendance_requests where id=?",String.class,id)).isEqualTo("PENDING");
    }
    @Test void futureForeignOutOfPeriodAndBlankRequestsAreRejected() throws Exception {
        submission(student,internship,TODAY.plusDays(1)).andExpect(status().isBadRequest());
        submission(student,internship,TODAY.minusDays(20)).andExpect(status().isBadRequest());
        submission(student,secondInternship,DATE).andExpect(status().isConflict());
        mvc.perform(post(STUDENT).with(user(UserPrincipal.from(student))).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("internshipId",internship.getId(),"attendanceDate",DATE.toString(),"reason","   ")))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from late_attendance_requests",Long.class)).isZero();
    }
    @Test void regularGpsSubmissionStillWorksBeforeDeadlineAndCannotBeReplacedByLateRequest() throws Exception {
        clock.at=TODAY.atTime(10,0).atZone(clock.getZone()).toInstant();
        submission(student,internship,TODAY).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTENDANCE_WINDOW_OPEN"));
        var response=normalAttendance.submitToday(UserPrincipal.from(student),internship.getId(),new BigDecimal("21.12"),new BigDecimal("79.08"),BigDecimal.TEN,clock.instant());
        assertThat(response.attendanceDate()).isEqualTo(TODAY);
        assertThat(response.attendanceResult()).isEqualTo(AttendanceResult.PENDING_MENTOR_REVIEW);
        assertThat(response.evidence().locationVerificationStatus()).isEqualTo(LocationVerificationStatus.WORKPLACE_NOT_APPROVED);
        assertThat(response.evidence().latitude()).isEqualByComparingTo("21.12");
        clock.at=NOW;
        submission(student,internship,TODAY).andExpect(status().isConflict());
        assertThat(attendance.count()).isEqualTo(1);
    }
    @Test void blanketPermissionsCannotAuthorizeStudentsWithoutSubmittedRequests() throws Exception {
        mvc.perform(get("/api/v1/mentor/attendance/late-permissions").with(user(UserPrincipal.from(mentor)))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/api/v1/mentor/attendance/late-permissions/{id}",student.getId()).with(user(UserPrincipal.from(mentor)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LATE_ATTENDANCE_REQUEST_REQUIRED"));
        mvc.perform(post("/api/v1/student/attendance/today").with(user(UserPrincipal.from(student))).param("latitude","21").param("longitude","79"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTENDANCE_WINDOW_CLOSED"));
        assertThat(attendance.count()).isZero();
    }
    @Test void excludedCalendarDaysAndUnconfiguredHybridSchedulesAreNotEligible() throws Exception {
        submission(student,internship,TODAY.minusDays(2)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LATE_REQUEST_DATE_NOT_ELIGIBLE"));
        calendar.setHolidays(Set.of(DATE));
        submission(student,internship,DATE).andExpect(status().isConflict());
        calendar.setHolidays(Set.of());
        calendar.setApprovedLeave(Map.of(student.getId(),List.of(new AttendanceCalendarProperties.LeavePeriod(DATE,DATE))));
        submission(student,internship,DATE).andExpect(status().isConflict());
        calendar.setApprovedLeave(Map.of());
        configure(internship,InternshipMode.HYBRID,TODAY.minusDays(10),TODAY.plusDays(10));
        submission(student,internship,DATE).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from late_attendance_requests",Long.class)).isZero();
    }
    @Test void approvalRechecksCalendarAndChangedInternshipDates() throws Exception {
        long id=submit(student,internship,DATE);
        calendar.setHolidays(Set.of(DATE));
        decision(MENTOR,id,mentor,"APPROVED",null,false).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LATE_REQUEST_DATE_NOT_ELIGIBLE"));
        calendar.setHolidays(Set.of());
        configure(internship,InternshipMode.COLLEGE_REPORTING,TODAY,TODAY.plusDays(10));
        decision(MENTOR,id,mentor,"APPROVED",null,false).andExpect(status().isConflict());
        assertThat(attendance.count()).isZero();
        assertThat(jdbc.queryForObject("select status from late_attendance_requests where id=?",String.class,id)).isEqualTo("PENDING");
    }
    ResultActions list(String base,User actor,String view) throws Exception { return mvc.perform(get(base).param("view",view).with(user(UserPrincipal.from(actor)))); }
    ResultActions submission(User actor,InternshipDetails target,LocalDate date) throws Exception {
        return mvc.perform(post(STUDENT).with(user(UserPrincipal.from(actor))).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("internshipId",target.getId(),"attendanceDate",date.toString(),"reason","Attendance could not be submitted due to a connectivity issue", "supportingDetails","Task notes and supervisor reference"))));
    }
    long submit(User actor,InternshipDetails target,LocalDate date) throws Exception { return json.readTree(submission(actor,target,date).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong(); }
    ResultActions decision(String base,long id,User actor,String status,String reason,boolean allow) throws Exception {
        Map<String,Object> body=new HashMap<>(); body.put("status",status); body.put("rejectionReason",reason); body.put("allowResubmission",allow);
        return mvc.perform(post(base+"/{id}/decision",id).with(user(UserPrincipal.from(actor))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    User account(String name,Role role) { return users.saveAndFlush(new User(name+"@late-request.example.edu","unused-hash",role,true)); }
    User student(String email,String name,String uid) { User result=account(email,Role.STUDENT); accounts.saveAndFlush(new StudentAccount(result,name,uid,8)); return result; }
    InternshipDetails internship(User owner) {
        var onboarding=onboardings.saveAndFlush(new InternshipOnboarding(owner)); onboarding.markDocumentsComplete(); onboarding.confirmDiary();
        var details=new InternshipDetails(onboarding);
        configure(details,InternshipMode.COLLEGE_REPORTING,TODAY.minusDays(10),TODAY.plusDays(10));
        onboarding.markInternshipDetailsComplete(); onboardings.saveAndFlush(onboarding); return details;
    }
    void configure(InternshipDetails details,InternshipMode mode,LocalDate start,LocalDate end) {
        details.apply("Request Test Ltd",CompanyType.IT,null,"Company address","9876543210","Supervisor","Manager","9876543211",2,start,end,8,mode,BigDecimal.ZERO,false);
        internships.saveAndFlush(details);
    }
    @TestConfiguration static class ClockConfiguration { @Bean @Primary MutableClock lateRequestClock() { return new MutableClock(); } }
    static class MutableClock extends Clock {
        Instant at=NOW;
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Kolkata"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(at,zone); }
        @Override public Instant instant() { return at; }
    }
}
