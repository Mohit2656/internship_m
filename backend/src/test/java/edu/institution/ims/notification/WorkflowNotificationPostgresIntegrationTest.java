package edu.institution.ims.notification;

import edu.institution.ims.mentor.MentorStudentAssignment;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.user.Role;
import edu.institution.ims.user.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "EXIF_TEST_DB_URL", matches = "jdbc:postgresql://(localhost|127[.]0[.]0[.]1):.*")
@TestPropertySource(properties = {"spring.datasource.url=${EXIF_TEST_DB_URL}",
        "spring.datasource.username=${EXIF_TEST_DB_USERNAME}", "spring.datasource.password=${EXIF_TEST_DB_PASSWORD}",
        "spring.datasource.driver-class-name=org.postgresql.Driver", "spring.flyway.schemas=${EXIF_TEST_DB_SCHEMA}",
        "spring.jpa.properties.hibernate.default_schema=${EXIF_TEST_DB_SCHEMA}", "debug=false"})
class WorkflowNotificationPostgresIntegrationTest extends MentorNotificationIntegrationTest {
    @BeforeAll static void requireIsolatedSchema() {
        String schema = System.getenv("EXIF_TEST_DB_SCHEMA");
        if (schema == null || !schema.matches("ims_deletion_test_[0-9a-f]{32}")
                || !System.getenv("EXIF_TEST_DB_URL").contains("currentSchema=" + schema))
            throw new IllegalStateException("Workflow notification tests require a newly created isolated local schema");
    }

    @Test void workflowInboxSupportsMultipleEventsWithoutAttendanceAndDeduplicatesRetries() {
        notifications.workflowSubmitted(student.getId(), "weekly:test:1", "WEEKLY_REPORT_SUBMITTED", "Week 1", "Review week 1", "/mentor/weekly-progress", NOW);
        notifications.workflowSubmitted(student.getId(), "weekly:test:1", "WEEKLY_REPORT_SUBMITTED", "Week 1", "Review week 1", "/mentor/weekly-progress", NOW);
        notifications.workflowSubmitted(student.getId(), "weekly:test:2", "WEEKLY_REPORT_SUBMITTED", "Week 2", "Review week 2", "/mentor/weekly-progress", NOW.plusSeconds(1));
        var inbox = notifications.list(UserPrincipal.from(mentor), 0);
        assertThat(inbox.unreadCount()).isEqualTo(2);
        assertThat(inbox.content()).extracting(NotificationService.Notification::title).containsExactly("Week 2", "Week 1");
        assertThat(jdbc.queryForObject("select count(*) from mentor_notifications where attendance_id is null", Long.class)).isEqualTo(2);
        assertThat(notifications.unread(UserPrincipal.from(otherMentor)).unreadCount()).isZero();
        notifications.markRead(UserPrincipal.from(mentor), inbox.content().get(0).id());
        assertThat(notifications.unread(UserPrincipal.from(mentor)).unreadCount()).isEqualTo(1);
        assignment.end(NOW.plusSeconds(2)); assignments.flush();
        assertThat(notifications.list(UserPrincipal.from(mentor), 0).content()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from mentor_notifications", Long.class)).isEqualTo(2);
    }

    @Test void failedWorkflowTransactionCommitsNeitherUsersAssignmentNorNotification() {
        var tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            User ownAdmin = users.saveAndFlush(new User("workflow.rollback.admin@example.test", "unused", Role.ADMIN, true));
            User ownMentor = users.saveAndFlush(new User("workflow.rollback.mentor@example.test", "unused", Role.MENTOR, true));
            User ownStudent = users.saveAndFlush(new User("workflow.rollback.student@example.test", "unused", Role.STUDENT, true));
            assignments.saveAndFlush(new MentorStudentAssignment(ownMentor, ownStudent, ownAdmin, NOW));
            notifications.workflowSubmitted(ownStudent.getId(), "workflow:rollback", "WEEKLY_REPORT_SUBMITTED", "Review week", "Review report", "/mentor/weekly-progress", NOW);
            throw new IllegalStateException("Simulated workflow failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from mentor_notifications where event_key='workflow:rollback'", Long.class)).isZero();
        assertThat(users.findByEmailIgnoreCase("workflow.rollback.student@example.test")).isEmpty();
    }
}
