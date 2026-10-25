package edu.institution.ims.notification;

import edu.institution.ims.internship.*;
import edu.institution.ims.opportunity.*;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:notification_tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "app.notifications.reminders.enabled=false", "logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN", "debug=false"})
@ActiveProfiles("test")
@Transactional
class NotificationIntegrationTest {
    @MockitoSpyBean NotificationService notifications;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired InternshipOnboardingRepository onboardings;
    @Autowired InternshipDetailsRepository internships;
    @Autowired OpportunityService opportunities;
    @Autowired OpportunityRepository opportunityRecords;
    private static final Instant NOW=Instant.parse("2026-10-22T07:00:00Z");
    private static final LocalDate TODAY=LocalDate.of(2026,10,22);
    private User first,second;
    @BeforeEach void setup() {
        first=users.saveAndFlush(new User("notifications.first@example.edu","unused",Role.STUDENT,true));
        second=users.saveAndFlush(new User("notifications.second@example.edu","unused",Role.STUDENT,true));
    }
    private UserPrincipal principal(User student) { return UserPrincipal.from(student); }
    private void create(User owner,String key,Instant at) { notifications.create(owner.getId(),key,"REPORT_REJECTED","Month 1 rejected","Correct the report","Explain actual work","Faculty Mentor","/student/monthly-progress#report-1",at); }
    private OpportunityRequest opportunityRequest(String description) {
        return new OpportunityRequest("Example Company", "Software Intern", description, null, "Pune",
                OpportunityMode.HYBRID, 3, BigDecimal.ZERO, "INR", LocalDate.now().plusDays(7),
                null, List.of(), List.of(), List.of(), List.of());
    }

    @Test void publicationPersistsForVisibleStudentsAndRetriesEditsAndNewServiceDoNotDuplicateIt() {
        var admin=users.saveAndFlush(new User("notifications.publisher@example.edu","unused",Role.ADMIN,true));
        var inactive=users.saveAndFlush(new User("notifications.inactive@example.edu","unused",Role.STUDENT,false));
        var mentor=users.saveAndFlush(new User("notifications.mentor@example.edu","unused",Role.MENTOR,true));
        var draft=opportunities.create(principal(admin),opportunityRequest("Build\n useful software."));
        assertThat(notifications.unread(principal(first)).unreadCount()).isZero();

        var published=opportunities.publish(draft.id());
        var inbox=notifications.list(principal(first),0);
        assertThat(inbox.content()).hasSize(1);
        var notification=inbox.content().get(0);
        assertThat(notification.type()).isEqualTo("OPPORTUNITY_PUBLISHED");
        assertThat(notification.title()).isEqualTo("Software Intern");
        assertThat(notification.message()).isEqualTo("Example Company: Build useful software.");
        // The database timestamp column retains microseconds rather than Instant nanoseconds.
        assertThat(Duration.between(notification.createdAt(),published.publishedAt()).abs()).isLessThan(Duration.ofMillis(1));
        assertThat(notification.actionUrl()).isEqualTo("/student/opportunities/"+draft.id());
        assertThat(notifications.unread(principal(second)).unreadCount()).isEqualTo(1);
        for(var excluded:List.of(admin,inactive,mentor))
            assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=?",Long.class,excluded.getId())).isZero();

        opportunities.publish(draft.id());
        opportunities.update(draft.id(),opportunityRequest("Edited description."));
        var freshService=new NotificationService(jdbc,Clock.systemUTC());
        freshService.opportunityPublished(opportunityRecords.findById(draft.id()).orElseThrow());
        assertThat(freshService.list(principal(first),0).content()).containsExactly(notification);
        assertThat(freshService.markRead(principal(first),notification.id()).unreadCount()).isZero();
        assertThat(new NotificationService(jdbc,Clock.systemUTC()).list(principal(first),0).content().get(0).readAt()).isNotNull();
        assertThat(notifications.unread(principal(second)).unreadCount()).isEqualTo(1);
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void failedNotificationWriteRollsBackPublicationAndEveryRecipient() {
        var admin=users.saveAndFlush(new User("notifications.rollback@example.edu","unused",Role.ADMIN,true));
        Long opportunityId=null;
        try {
            var draft=opportunities.create(principal(admin),opportunityRequest("Build useful software."));
            opportunityId=draft.id();
            doAnswer(invocation -> {
                invocation.callRealMethod();
                throw new IllegalStateException("Simulated notification persistence failure");
            }).when(notifications).opportunityPublished(any(Opportunity.class));

            assertThatThrownBy(() -> opportunities.publish(draft.id())).isInstanceOf(IllegalStateException.class);
            assertThat(opportunityRecords.findById(draft.id()).orElseThrow().getStatus()).isEqualTo(OpportunityStatus.DRAFT);
            assertThat(jdbc.queryForObject("select count(*) from student_notifications where event_key=?",Long.class,"opportunity-published:"+draft.id())).isZero();
            doCallRealMethod().when(notifications).opportunityPublished(any(Opportunity.class));
            opportunities.publish(draft.id());
            assertThat(notifications.unread(principal(first)).unreadCount()).isEqualTo(1);
        } finally {
            doCallRealMethod().when(notifications).opportunityPublished(any(Opportunity.class));
            if(opportunityId!=null) {
                jdbc.update("delete from student_notifications where event_key=?","opportunity-published:"+opportunityId);
                opportunityRecords.deleteById(opportunityId);
            }
            users.deleteAll(List.of(first,second,admin));
        }
    }

    @Test void incompleteDraftCannotPublishAndCreatesNoNotification() {
        var admin=users.saveAndFlush(new User("notifications.invalid@example.edu","unused",Role.ADMIN,true));
        var draft=opportunities.create(principal(admin),opportunityRequest(null));
        assertThatThrownBy(() -> opportunities.publish(draft.id())).isInstanceOf(OpportunityException.class);
        assertThat(notifications.unread(principal(first)).unreadCount()).isZero();
    }

    @Test void inboxIsOwnedNewestFirstAndReadCountsAreIdempotent() {
        create(first,"one",NOW); create(first,"two",NOW.plusSeconds(1)); create(second,"other",NOW);
        var inbox=notifications.list(principal(first),0);
        assertThat(inbox.content()).hasSize(2); assertThat(inbox.unreadCount()).isEqualTo(2);
        assertThat(inbox.content().get(0).createdAt()).isEqualTo(NOW.plusSeconds(1));
        Long id=inbox.content().get(0).id();
        assertThatThrownBy(() -> notifications.markRead(principal(second),id)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(notifications.markRead(principal(first),id).unreadCount()).isEqualTo(1);
        assertThat(notifications.markRead(principal(first),id).unreadCount()).isEqualTo(1);
        assertThat(notifications.markAllRead(principal(first)).unreadCount()).isZero();
        assertThat(notifications.unread(principal(second)).unreadCount()).isEqualTo(1);
        assertThatThrownBy(() -> notifications.list(UserPrincipal.from(new User("mentor@example.edu","unused",Role.MENTOR,true)),0)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void storedOpportunityDeadlinesCreateOnlyOneReminderPerStudentAndInterval() {
        var admin=users.saveAndFlush(new User("notifications.admin@example.edu","unused",Role.ADMIN,true));
        jdbc.update("insert into opportunities(company_name,title,stipend_amount,currency,status,created_by,published_at,created_at,updated_at,application_deadline) values ('Example','Existing deadline',0,'INR','PUBLISHED',?,?,?,?,?)",
                admin.getId(),Timestamp.from(NOW),Timestamp.from(NOW),Timestamp.from(NOW),java.sql.Date.valueOf(TODAY.plusDays(3)));
        jdbc.update("insert into opportunities(company_name,title,stipend_amount,currency,status,created_by,created_at,updated_at) values ('Example','No deadline',0,'INR','PUBLISHED',?,?,?)",admin.getId(),Timestamp.from(NOW),Timestamp.from(NOW));
        var settings=new NotificationProperties();
        var scheduler=new DeadlineNotificationScheduler(jdbc,notifications,settings,Clock.fixed(NOW,ZoneId.of("Asia/Kolkata")));
        scheduler.run(); scheduler.run();
        assertThat(notifications.list(principal(first),0).totalElements()).isEqualTo(1);
        assertThat(notifications.list(principal(second),0).totalElements()).isEqualTo(1);
        assertThat(notifications.list(principal(first),0).content().get(0).message()).contains("2026-10-25");
        new DeadlineNotificationScheduler(jdbc,notifications,settings,Clock.fixed(NOW.plus(Duration.ofDays(3)),ZoneId.of("Asia/Kolkata"))).run();
        assertThat(notifications.list(principal(first),0).totalElements()).isEqualTo(2);
    }
    @Test void monthlyRemindersRequireConfiguredDateAndOnlyTargetTheRegisteredStudent() {
        var onboarding=new InternshipOnboarding(first); onboarding.confirmDiary(); onboarding.markInternshipDetailsComplete(); onboardings.saveAndFlush(onboarding);
        var internship=new InternshipDetails(onboarding);
        internship.apply("Example",CompanyType.IT,null,"Address","1234567890","Supervisor","Manager","1234567890",1,TODAY.minusDays(5),TODAY.plusDays(50),8,InternshipMode.ONLINE,BigDecimal.ZERO,false);
        internships.saveAndFlush(internship);
        var settings=new NotificationProperties();
        var scheduler=new DeadlineNotificationScheduler(jdbc,notifications,settings,Clock.fixed(NOW,ZoneId.of("Asia/Kolkata")));
        scheduler.run(); assertThat(notifications.unread(principal(first)).unreadCount()).isZero();
        settings.setMonthlyReportDeadlines(List.of(new NotificationProperties.MonthlyDeadline(internship.getId(),1,TODAY)));
        scheduler.run(); scheduler.run();
        assertThat(notifications.unread(principal(first)).unreadCount()).isEqualTo(1);
        assertThat(notifications.unread(principal(second)).unreadCount()).isZero();
        assertThat(notifications.list(principal(first),0).content().get(0).message()).contains("due today","2026-10-22");
    }
    @Test void reminderIntervalsDoNotFabricateDatesOutsideTheirWindows() {
        assertThat(DeadlineNotificationScheduler.stage(TODAY,TODAY.plusDays(7))).isEqualTo("UPCOMING");
        assertThat(DeadlineNotificationScheduler.stage(TODAY,TODAY.plusDays(3))).isEqualTo("APPROACHING");
        assertThat(DeadlineNotificationScheduler.stage(TODAY,TODAY)).isEqualTo("DUE_TODAY");
        assertThat(DeadlineNotificationScheduler.stage(TODAY,TODAY.minusDays(1))).isEqualTo("OVERDUE");
        assertThat(DeadlineNotificationScheduler.stage(TODAY,TODAY.minusDays(2))).isNull();
    }
}
