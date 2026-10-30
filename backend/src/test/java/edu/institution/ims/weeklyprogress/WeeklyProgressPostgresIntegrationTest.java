package edu.institution.ims.weeklyprogress;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import edu.institution.ims.internship.*;
import edu.institution.ims.mentor.MentorStudentAssignment;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="EXIF_TEST_DB_URL", matches="jdbc:postgresql://(localhost|127[.]0[.]0[.]1):.*")
@TestPropertySource(properties={"spring.datasource.url=${EXIF_TEST_DB_URL}","spring.datasource.username=${EXIF_TEST_DB_USERNAME}",
        "spring.datasource.password=${EXIF_TEST_DB_PASSWORD}","spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.schemas=${EXIF_TEST_DB_SCHEMA}","spring.jpa.properties.hibernate.default_schema=${EXIF_TEST_DB_SCHEMA}","logging.level.root=WARN"})
class WeeklyProgressPostgresIntegrationTest extends WeeklyProgressIntegrationTest {
    @Autowired PlatformTransactionManager transactionManager;
    @BeforeAll static void requireIsolatedSchema() {
        String schema=System.getenv("EXIF_TEST_DB_SCHEMA");
        if(schema==null || !schema.matches("ims_deletion_test_[0-9a-f]{32}") || !System.getenv("EXIF_TEST_DB_URL").contains("currentSchema="+schema))
            throw new IllegalStateException("Weekly tests require a newly created isolated local schema");
    }

    @Test void failedNotificationRollsBackGeneratedPeriodsReportHistoryAndRecipientsTogether() {
        var independent=new JdbcTemplate(new DriverManagerDataSource(System.getenv("EXIF_TEST_DB_URL"),System.getenv("EXIF_TEST_DB_USERNAME"),System.getenv("EXIF_TEST_DB_PASSWORD")));
        independent.execute("create function fail_weekly_submission_notification() returns trigger language plpgsql as $$ begin if NEW.notification_type='WEEKLY_REPORT_SUBMITTED' then raise exception 'simulated weekly notification failure'; end if; return NEW; end $$");
        String suffix=UUID.randomUUID().toString(); String email="weekly-rollback-"+suffix+"@example.test";
        var periodId=new AtomicLong(); var ownStudentId=new AtomicLong();
        try {
            independent.execute("create trigger fail_weekly_submission_notification before insert on mentor_notifications for each row execute function fail_weekly_submission_notification()");
            var transaction=new TransactionTemplate(transactionManager); transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            assertThatThrownBy(()->transaction.executeWithoutResult(status->{
                var ownStudent=users.saveAndFlush(new User(email,"unused",Role.STUDENT,true)); ownStudentId.set(ownStudent.getId());
                var ownMentor=users.saveAndFlush(new User("weekly-mentor-"+suffix+"@example.test","unused",Role.MENTOR,true));
                var ownAdmin=users.saveAndFlush(new User("weekly-admin-"+suffix+"@example.test","unused",Role.ADMIN,true));
                assignments.saveAndFlush(new MentorStudentAssignment(ownMentor,ownStudent,ownAdmin,Instant.now()));
                var onboarding=new InternshipOnboarding(ownStudent); onboarding.confirmDiary(); onboarding.markInternshipDetailsComplete(); onboardings.saveAndFlush(onboarding);
                var ownInternship=new InternshipDetails(onboarding);
                ownInternship.apply("Rollback Company",CompanyType.IT,null,"Address","9876543210","Supervisor","Manager","9876543211",1,LocalDate.of(2025,11,1),LocalDate.of(2025,11,14),2,InternshipMode.ONLINE,BigDecimal.ZERO,false);
                internships.saveAndFlush(ownInternship); service.synchronize(ownInternship);
                var first=service.ownOverview(UserPrincipal.from(ownStudent)).weeks().get(0); periodId.set(first.id());
                service.submit(UserPrincipal.from(ownStudent),first.id(),null,"Work that must roll back");
            })).hasStackTraceContaining("simulated weekly notification failure");
            assertThat(periodId.get()).isPositive();
            assertThat(jdbc.queryForObject("select count(*) from weekly_internship_periods where id=?",Long.class,periodId.get())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from weekly_progress_reports where period_id=?",Long.class,periodId.get())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from weekly_progress_events where period_id=?",Long.class,periodId.get())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from mentor_notifications where student_user_id=?",Long.class,ownStudentId.get())).isZero();
            assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
        } finally {
            // Release this test's read locks before independent DDL cleanup.
            if (org.springframework.test.context.transaction.TestTransaction.isActive()) {
                org.springframework.test.context.transaction.TestTransaction.flagForRollback();
                org.springframework.test.context.transaction.TestTransaction.end();
            }
            independent.execute("drop trigger if exists fail_weekly_submission_notification on mentor_notifications");
            independent.execute("drop function fail_weekly_submission_notification()");
        }
    }

    private record ConcurrentFixture(Long studentId,Long mentorId,Long nextMentorId,Long adminId,Long internshipId,Long periodId,UserPrincipal student,UserPrincipal mentor) {}
    private <T> T committed(java.util.function.Supplier<T> action) {
        var transaction=new TransactionTemplate(transactionManager); transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status->{
            jdbc.execute("set local lock_timeout='8s'"); jdbc.execute("set local statement_timeout='15s'");
            return action.get();
        });
    }
    private ConcurrentFixture committedFixture(boolean submitted) {
        return committed(()->{
            String suffix=UUID.randomUUID().toString();
            var s=users.saveAndFlush(new User("weekly-race-s-"+suffix+"@example.test","unused",Role.STUDENT,true));
            var m=users.saveAndFlush(new User("weekly-race-m-"+suffix+"@example.test","unused",Role.MENTOR,true));
            var n=users.saveAndFlush(new User("weekly-race-n-"+suffix+"@example.test","unused",Role.MENTOR,true));
            var a=users.saveAndFlush(new User("weekly-race-a-"+suffix+"@example.test","unused",Role.ADMIN,true));
            assignments.saveAndFlush(new MentorStudentAssignment(m,s,a,Instant.now()));
            var onboarding=new InternshipOnboarding(s); onboarding.confirmDiary(); onboarding.markInternshipDetailsComplete(); onboardings.saveAndFlush(onboarding);
            var i=new InternshipDetails(onboarding); i.apply("Race Company",CompanyType.IT,null,"Address","9876543210","Supervisor","Manager","9876543211",1,LocalDate.of(2025,11,1),LocalDate.of(2025,11,14),2,InternshipMode.ONLINE,BigDecimal.ZERO,false);
            internships.saveAndFlush(i); service.synchronize(i);
            var period=service.ownOverview(UserPrincipal.from(s)).weeks().get(0);
            if(submitted) service.submit(UserPrincipal.from(s),period.id(),null,"Awaiting faculty review");
            return new ConcurrentFixture(s.getId(),m.getId(),n.getId(),a.getId(),i.getId(),period.id(),UserPrincipal.from(s),UserPrincipal.from(m));
        });
    }
    private void cleanup(ConcurrentFixture fixture) {
        committed(()->{
            jdbc.update("delete from mentor_notifications where student_user_id=?",fixture.studentId());
            jdbc.update("delete from student_notifications where student_user_id=?",fixture.studentId());
            jdbc.update("delete from attendance_records where student_user_id=?",fixture.studentId());
            jdbc.update("delete from internship_details where id=?",fixture.internshipId());
            jdbc.update("delete from internship_onboarding where student_user_id=?",fixture.studentId());
            jdbc.update("delete from mentor_student_assignments where student_user_id=?",fixture.studentId());
            jdbc.update("delete from users where id in (?,?,?,?)",fixture.studentId(),fixture.mentorId(),fixture.nextMentorId(),fixture.adminId());
            return true;
        });
    }
    private static void await(CountDownLatch latch) {
        try { if(!latch.await(10,TimeUnit.SECONDS)) throw new AssertionError("Concurrent transaction did not reach its checkpoint"); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    private void awaitDatabaseLock(AtomicLong pid) throws InterruptedException {
        var independent=new JdbcTemplate(new DriverManagerDataSource(System.getenv("EXIF_TEST_DB_URL"),System.getenv("EXIF_TEST_DB_USERNAME"),System.getenv("EXIF_TEST_DB_PASSWORD")));
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);
        while(System.nanoTime()<deadline) {
            if(pid.get()>0 && Boolean.TRUE.equals(independent.queryForObject("select exists(select 1 from pg_stat_activity where pid=? and wait_event_type='Lock')",Boolean.class,pid.get()))) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Weekly transaction did not block behind the held student lock");
    }

    @Test void weeklySubmissionAndAttendanceCanCommitConcurrentlyWithoutForeignKeyDeadlock() throws Exception {
        var fixture=committedFixture(false); var pool=Executors.newFixedThreadPool(2);
        var studentLocked=new CountDownLatch(1); var continueAttendance=new CountDownLatch(1); var weeklyPid=new AtomicLong();
        try {
            var attendanceTask=pool.submit(()->committed(()->{
                users.findByIdForAttendanceUpdate(fixture.studentId()).orElseThrow(); studentLocked.countDown(); await(continueAttendance);
                var s=users.findById(fixture.studentId()).orElseThrow(); var i=internships.findById(fixture.internshipId()).orElseThrow();
                attendance.saveAndFlush(new edu.institution.ims.attendance.AttendanceRecord(s,i,LocalDate.of(2025,11,1),
                        edu.institution.ims.attendance.AttendanceResult.PRESENT,edu.institution.ims.attendance.VerificationStatus.UNVERIFIED,
                        edu.institution.ims.attendance.AttendanceSubmissionType.BASIC,Instant.now()));
                mentorNotifications.workflowSubmitted(fixture.studentId(),"weekly-race-attendance:"+fixture.periodId(),"ATTENDANCE_SUBMITTED","Attendance","Attendance submitted","/mentor/attendance",Instant.now());
                return true;
            }));
            await(studentLocked);
            var weeklyTask=pool.submit(()->committed(()->{
                weeklyPid.set(jdbc.queryForObject("select pg_backend_pid()",Long.class));
                return service.submit(fixture.student(),fixture.periodId(),null,"Concurrent weekly report");
            }));
            awaitDatabaseLock(weeklyPid); continueAttendance.countDown();
            assertThat(attendanceTask.get(20,TimeUnit.SECONDS)).isTrue();
            assertThat(weeklyTask.get(20,TimeUnit.SECONDS).status()).isEqualTo("PENDING_MENTOR_REVIEW");
            assertThat(jdbc.queryForObject("select count(*) from weekly_progress_events where period_id=?",Long.class,fixture.periodId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from mentor_notifications where event_key=?",Long.class,"weekly-submitted:"+fixture.periodId()+":1")).isEqualTo(1);
        } finally { continueAttendance.countDown(); pool.shutdown(); pool.awaitTermination(25,TimeUnit.SECONDS); cleanup(fixture); }
    }

    @Test void reassignmentCanFinishWhileOldMentorReviewWaitsAndRevokesThatReview() throws Exception {
        var fixture=committedFixture(true); var pool=Executors.newFixedThreadPool(2);
        var studentLocked=new CountDownLatch(1); var continueReassignment=new CountDownLatch(1); var weeklyPid=new AtomicLong();
        try {
            var assignmentTask=pool.submit(()->committed(()->{
                users.findByIdForPasswordUpdate(fixture.nextMentorId()).orElseThrow();
                users.findByIdForAttendanceUpdate(fixture.studentId()).orElseThrow(); studentLocked.countDown(); await(continueReassignment);
                jdbc.update("update mentor_student_assignments set active=false,active_student_user_id=null,ended_at=? where student_user_id=? and active=true",java.sql.Timestamp.from(Instant.now()),fixture.studentId());
                assignments.saveAndFlush(new MentorStudentAssignment(users.findById(fixture.nextMentorId()).orElseThrow(),users.findById(fixture.studentId()).orElseThrow(),users.findById(fixture.adminId()).orElseThrow(),Instant.now()));
                return true;
            }));
            await(studentLocked);
            var reviewTask=pool.submit(()->{
                try { committed(()->{ weeklyPid.set(jdbc.queryForObject("select pg_backend_pid()",Long.class)); return service.review(fixture.mentor(),fixture.periodId(),1,true,null); }); return "UNEXPECTED_APPROVAL"; }
                catch(InternshipDetailsException denied) { return denied.getCode(); }
            });
            awaitDatabaseLock(weeklyPid); continueReassignment.countDown();
            assertThat(assignmentTask.get(20,TimeUnit.SECONDS)).isTrue();
            assertThat(reviewTask.get(20,TimeUnit.SECONDS)).isEqualTo("WEEK_NOT_FOUND");
            assertThat(jdbc.queryForObject("select review_status from weekly_progress_reports where period_id=?",String.class,fixture.periodId())).isEqualTo("PENDING_MENTOR_REVIEW");
            assertThat(jdbc.queryForObject("select count(*) from weekly_progress_events where period_id=?",Long.class,fixture.periodId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=?",Long.class,fixture.studentId())).isZero();
        } finally { continueReassignment.countDown(); pool.shutdown(); pool.awaitTermination(25,TimeUnit.SECONDS); cleanup(fixture); }
    }

    @Test void existingAssignmentFirstReviewCanNotifyWhileWeeklyReviewWaits() throws Exception {
        var fixture=committedFixture(true); var pool=Executors.newFixedThreadPool(2);
        var assignmentLocked=new CountDownLatch(1); var continueExistingReview=new CountDownLatch(1); var weeklyPid=new AtomicLong();
        try {
            // The existing monthly review owns the assignment before inserting its student
            // notification. A new workflow's student lock must allow that FK KEY SHARE.
            var existingReview=pool.submit(()->committed(()->{
                jdbc.queryForList("select id from mentor_student_assignments where student_user_id=? and active=true for update",Long.class,fixture.studentId());
                assignmentLocked.countDown(); await(continueExistingReview);
                studentNotifications.create(fixture.studentId(),"monthly-compat:"+fixture.periodId(),"REPORT_REJECTED",
                        "Monthly feedback","Review the existing monthly feedback",null,null,"/student/monthly-progress",Instant.now());
                return true;
            }));
            await(assignmentLocked);
            var weeklyReview=pool.submit(()->committed(()->{
                weeklyPid.set(jdbc.queryForObject("select pg_backend_pid()",Long.class));
                return service.review(fixture.mentor(),fixture.periodId(),1,true,null);
            }));
            awaitDatabaseLock(weeklyPid); continueExistingReview.countDown();
            assertThat(existingReview.get(20,TimeUnit.SECONDS)).isTrue();
            assertThat(weeklyReview.get(20,TimeUnit.SECONDS).status()).isEqualTo("APPROVED");
            assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=?",Long.class,fixture.studentId())).isEqualTo(2);
        } finally { continueExistingReview.countDown(); pool.shutdown(); pool.awaitTermination(25,TimeUnit.SECONDS); cleanup(fixture); }
    }
}
