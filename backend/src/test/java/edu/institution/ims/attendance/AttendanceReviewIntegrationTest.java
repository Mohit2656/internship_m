package edu.institution.ims.attendance;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.*;
import edu.institution.ims.notification.NotificationService;
import edu.institution.ims.professionalprofile.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:attendance_review_tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "debug=false", "logging.level.root=WARN"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AttendanceReviewIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users; @Autowired MentorStudentAssignmentRepository assignments;
    @Autowired InternshipOnboardingRepository onboardings; @Autowired InternshipDetailsRepository internships;
    @Autowired AttendanceRepository attendance; @Autowired AttendanceEvidenceRepository evidence;
    @Autowired AttendanceReviewService service; @Autowired PlatformTransactionManager transactionManager; @Autowired Clock clock;
    @Autowired StudentProfessionalProfileRepository professionalProfiles; @Autowired StudentResumeRepository resumes;
    @Autowired ResumeAnalysisRepository analyses; @Autowired ResumeCorrectionService corrections;
    @MockitoSpyBean NotificationService notifications;
    User admin, mentor, otherMentor, student, otherStudent;
    MentorStudentAssignment assignment; InternshipDetails internship;

    @BeforeEach void setup() {
        admin=account(Role.ADMIN); mentor=account(Role.MENTOR); otherMentor=account(Role.MENTOR);
        student=account(Role.STUDENT); otherStudent=account(Role.STUDENT);
        assignment=assignments.saveAndFlush(new MentorStudentAssignment(mentor,student,admin,clock.instant()));
        internship=internship(student);
    }

    @Test void approvalPersistsReviewerAuditAndOneNotificationWithoutChangingSubmittedGpsOrLiveness() throws Exception {
        var record=record(student,internship,LocationVerificationStatus.VERIFIED,AttendanceSubmissionType.BASIC);
        var original=jdbc.queryForMap("select attendance_result,verification_status,submission_type,server_submitted_at from attendance_records where id=?",record.getId());
        var location=jdbc.queryForMap("select latitude,longitude,gps_accuracy_meters,distance_meters,location_verification_status from attendance_evidence where attendance_id=?",record.getId());
        decide(record,mentor,"APPROVED",0,null).andExpect(status().isOk())
                .andExpect(jsonPath("$.review.status").value("APPROVED")).andExpect(jsonPath("$.review.reviewerId").value(mentor.getId()))
                .andExpect(jsonPath("$.review.reviewedAt").isNotEmpty()).andExpect(jsonPath("$.review.revision").value(1));
        decide(record,mentor,"APPROVED",1,null).andExpect(status().isConflict());
        assertThat(jdbc.queryForMap("select attendance_result,verification_status,submission_type,server_submitted_at from attendance_records where id=?",record.getId())).isEqualTo(original);
        assertThat(jdbc.queryForMap("select latitude,longitude,gps_accuracy_meters,distance_meters,location_verification_status from attendance_evidence where attendance_id=?",record.getId())).isEqualTo(location);
        assertThat(jdbc.queryForObject("select count(*) from attendance_review_audit where attendance_id=?",Long.class,record.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=? and notification_type='ATTENDANCE_APPROVED'",Long.class,student.getId())).isEqualTo(1);
        mvc.perform(get("/api/v1/admin/attendance/students/{id}",student.getId()).with(user(UserPrincipal.from(admin))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.records[0].review.status").value("APPROVED"));
    }

    @Test void rejectionRequiresReasonAndPreservesItInStudentFeedbackAndAudit() throws Exception {
        var record=record(student,internship,LocationVerificationStatus.VERIFIED,AttendanceSubmissionType.BASIC);
        decide(record,mentor,"REJECTED",0," ").andExpect(status().isBadRequest());
        decide(record,mentor,"REJECTED",0,"Submitted workplace does not match supporting evidence.")
                .andExpect(status().isOk()).andExpect(jsonPath("$.review.reason").value("Submitted workplace does not match supporting evidence."));
        decide(record,mentor,"APPROVED",1,null).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/student/attendance/{id}/review/history",record.getId()).with(user(UserPrincipal.from(student))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].previousStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].status").value("REJECTED")).andExpect(jsonPath("$[0].reviewerId").value(mentor.getId()));
        assertThat(jdbc.queryForObject("select rejection_reason from student_notifications where student_user_id=? and notification_type='ATTENDANCE_REJECTED'",String.class,student.getId()))
                .isEqualTo("Submitted workplace does not match supporting evidence.");
    }

    @Test void onlyActiveAssignedMentorCanReviewOrReadAuditAndReassignmentRevokesAccess() throws Exception {
        var record=record(student,internship,LocationVerificationStatus.VERIFIED,AttendanceSubmissionType.BASIC);
        decide(record,otherMentor,"APPROVED",0,null).andExpect(status().isNotFound());
        decide(record,student,"APPROVED",0,null).andExpect(status().isForbidden());
        decide(record,admin,"APPROVED",0,null).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/mentor/attendance/{id}/review/history",record.getId()).with(user(UserPrincipal.from(otherMentor)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/student/attendance/{id}/review/history",record.getId()).with(user(UserPrincipal.from(otherStudent)))).andExpect(status().isNotFound());
        assignment.end(clock.instant()); assignments.flush();
        assignments.saveAndFlush(new MentorStudentAssignment(otherMentor,student,admin,clock.instant()));
        decide(record,mentor,"APPROVED",0,null).andExpect(status().isNotFound());
        decide(record,otherMentor,"APPROVED",0,null).andExpect(status().isOk());
    }

    @Test void gpsFailuresAndMissingWorkplaceApprovalCannotBeOverriddenByMentorApproval() throws Exception {
        for (LocationVerificationStatus status : List.of(LocationVerificationStatus.WORKPLACE_NOT_APPROVED,
                LocationVerificationStatus.OUTSIDE_RADIUS,LocationVerificationStatus.INSUFFICIENT_ACCURACY,
                LocationVerificationStatus.STALE_LOCATION,LocationVerificationStatus.LOCATION_UNAVAILABLE)) {
            var owner=account(Role.STUDENT); var details=internship(owner);
            assignments.saveAndFlush(new MentorStudentAssignment(mentor,owner,admin,clock.instant()));
            var record=record(owner,details,status,AttendanceSubmissionType.BASIC);
            decide(record,mentor,"APPROVED",0,null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTENDANCE_VERIFICATION_REQUIRED"));
            assertThat(attendance.findById(record.getId()).orElseThrow().getEvidence().getLocationVerificationStatus()).isEqualTo(status);
        }
        assertThat(jdbc.queryForObject("select count(*) from attendance_reviews",Long.class)).isZero();
    }

    @Test void captureAttendanceRequiresExistingLivenessVerificationEvenWhenGpsPassed() throws Exception {
        var record=record(student,internship,LocationVerificationStatus.VERIFIED,AttendanceSubmissionType.PHYSICAL_CAPTURE);
        decide(record,mentor,"APPROVED",0,null).andExpect(status().isConflict());
        decide(record,mentor,"MANUAL_REVIEW",0,"Please discuss the missing liveness verification.").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select message from student_notifications where student_user_id=? and notification_type='ATTENDANCE_MANUAL_REVIEW'",String.class,student.getId()))
                .contains("Contact your mentor","original submission remains available").doesNotContain("resubmit");
    }

    @Test void manualReviewCanBeResolvedButStaleAndRepeatedDecisionsCreateNoExtraEvents() throws Exception {
        var record=record(student,internship,LocationVerificationStatus.VERIFIED,AttendanceSubmissionType.BASIC);
        decide(record,mentor,"MANUAL_REVIEW",0,"Please clarify the submitted work details.").andExpect(status().isOk());
        decide(record,mentor,"MANUAL_REVIEW",1,"Please clarify the submitted work details.").andExpect(status().isConflict());
        decide(record,mentor,"APPROVED",0,null).andExpect(status().isConflict());
        decide(record,mentor,"APPROVED",1,null).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/attendance/{id}/review/history",record.getId()).with(user(UserPrincipal.from(admin))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].previousStatus").value("MANUAL_REVIEW"));
        assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=? and notification_type like 'ATTENDANCE_%'",Long.class,student.getId())).isEqualTo(2);
    }

    @Test void automaticAbsencesAndDeletedHistoryCannotBeReviewed() throws Exception {
        var absent=attendance.saveAndFlush(AttendanceRecord.automaticAbsent(student,internship,LocalDate.now(clock),clock.instant()));
        decide(absent,mentor,"APPROVED",0,null).andExpect(status().isConflict());
        var record=record(otherStudent,internship(otherStudent),LocationVerificationStatus.VERIFIED,AttendanceSubmissionType.BASIC);
        jdbc.update("update attendance_records set deleted_at=?,active_slot=null where id=?",java.sql.Timestamp.from(clock.instant()),record.getId());
        decide(record,mentor,"REJECTED",0,"Deleted record must remain unavailable.").andExpect(status().isNotFound());
    }

    @Test void failedNotificationTransactionRollsBackReviewAuditAndNoticeTogether() {
        var failedId=new AtomicLong();
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("Simulated review notification failure"); })
                .when(notifications).create(anyLong(),anyString(),anyString(),anyString(),anyString(),any(),any(),anyString(),any());
        try {
            var tx=new TransactionTemplate(transactionManager); tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                User ownAdmin=account(Role.ADMIN), ownMentor=account(Role.MENTOR), ownStudent=account(Role.STUDENT);
                assignments.saveAndFlush(new MentorStudentAssignment(ownMentor,ownStudent,ownAdmin,clock.instant()));
                var record=record(ownStudent,internship(ownStudent),LocationVerificationStatus.VERIFIED,AttendanceSubmissionType.BASIC); failedId.set(record.getId());
                service.review(record.getId(),UserPrincipal.from(ownMentor),new AttendanceReviewService.Decision(AttendanceReviewStatus.APPROVED,0,null));
            })).hasStackTraceContaining("Simulated review notification failure");
            assertThat(jdbc.queryForObject("select count(*) from attendance_reviews where attendance_id=?",Long.class,failedId.get())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from attendance_review_audit where attendance_id=?",Long.class,failedId.get())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from student_notifications where event_key like ?",Long.class,"attendance-review:"+failedId.get()+":%")).isZero();
        } finally { doCallRealMethod().when(notifications).create(anyLong(),anyString(),anyString(),anyString(),anyString(),any(),any(),anyString(),any()); }
    }

    @Test void assignedMentorCanReadOriginalAndCorrectedResumeButCannotWriteCorrections() throws Exception {
        byte[] pdf="%PDF-1.4\nmentor resume review\n%%EOF".getBytes();
        mvc.perform(multipart("/api/v1/student/resume").file(new MockMultipartFile("file","resume.pdf","application/pdf",pdf))
                .with(user(UserPrincipal.from(student)))).andExpect(status().isOk());
        var professional=professionalProfiles.findByStudentId(student.getId()).orElseThrow();
        var resume=resumes.findByProfessionalProfileIdAndActiveTrue(professional.getId()).orElseThrow(); resume.markAnalysisParsed(); resumes.saveAndFlush(resume);
        var parsed=new ResumeAnalysis(resume); parsed.complete(new AiResumeResult("test","1",List.of("Java"),List.of(),List.of(),List.of())); analyses.saveAndFlush(parsed);
        corrections.save(student.getId(),UserPrincipal.from(student),new ResumeCorrectionService.Save(resume.getId(),0,
                new ResumeCorrectionService.Values(List.of("Spring Boot"),List.of(),List.of(),List.of())));
        String base="/api/v1/mentor/students/"+student.getId();
        mvc.perform(get(base+"/resume/download").with(user(UserPrincipal.from(mentor)))).andExpect(status().isOk()).andExpect(content().bytes(pdf));
        mvc.perform(get(base+"/resume/analysis/corrections").with(user(UserPrincipal.from(mentor)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.original.skills[0]").value("Java")).andExpect(jsonPath("$.values.skills[0]").value("Spring Boot"));
        for (String suffix:List.of("/professional-profile","/resume/download","/resume/analysis","/resume/analysis/corrections"))
            mvc.perform(get(base+suffix).with(user(UserPrincipal.from(otherMentor)))).andExpect(status().isNotFound());
        mvc.perform(put(base+"/resume/analysis/corrections").with(user(UserPrincipal.from(mentor))).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        assignment.end(clock.instant()); assignments.flush();
        mvc.perform(get(base+"/resume/download").with(user(UserPrincipal.from(mentor)))).andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions decide(AttendanceRecord record,User actor,String status,int revision,String reason) throws Exception {
        var body=new HashMap<String,Object>(); body.put("status",status); body.put("revision",revision); body.put("reason",reason);
        return mvc.perform(post("/api/v1/mentor/attendance/{id}/review",record.getId()).with(user(UserPrincipal.from(actor)))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    private User account(Role role) { return users.saveAndFlush(new User(UUID.randomUUID()+"@review.example.test","unused",role,true)); }
    private InternshipDetails internship(User student) {
        var onboarding=new InternshipOnboarding(student); onboarding.markDocumentsComplete(); onboarding.confirmDiary(); onboarding.markInternshipDetailsComplete(); onboardings.saveAndFlush(onboarding);
        var details=new InternshipDetails(onboarding); details.apply("Review Company",CompanyType.IT,null,"Address","9876543210","Supervisor","Manager","9876543211",2,
                LocalDate.now(clock).minusDays(10),LocalDate.now(clock).plusDays(10),3,InternshipMode.COLLEGE_REPORTING,BigDecimal.ZERO,false);
        return internships.saveAndFlush(details);
    }
    private AttendanceRecord record(User owner,InternshipDetails details,LocationVerificationStatus gps,AttendanceSubmissionType type) {
        var record=attendance.saveAndFlush(new AttendanceRecord(owner,details,LocalDate.now(clock),AttendanceResult.PRESENT,VerificationStatus.UNVERIFIED,type,clock.instant()));
        var captured=new AttendanceEvidence(record,SubmissionLocation.require(new BigDecimal("18.52"),new BigDecimal("73.85"),BigDecimal.TEN),clock.instant());
        captured.recordLocationVerification(null,new LocationVerificationDecision(gps,BigDecimal.ONE,gps==LocationVerificationStatus.VERIFIED),clock.instant());
        evidence.saveAndFlush(captured); return record;
    }
}
