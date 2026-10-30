package edu.institution.ims.opportunity;

import edu.institution.ims.professionalprofile.SkillNameRequest;
import edu.institution.ims.security.UserPrincipal;
import edu.institution.ims.studentprofile.Branch;
import edu.institution.ims.studentprofile.StudentProfile;
import edu.institution.ims.user.Role;
import edu.institution.ims.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Reuses the report/routing/history regression suite against real PostgreSQL. */
@EnabledIfEnvironmentVariable(named = "EXIF_TEST_DB_URL", matches = "jdbc:postgresql://(localhost|127[.]0[.]0[.]1):.*")
@TestPropertySource(properties = {"spring.datasource.url=${EXIF_TEST_DB_URL}",
        "spring.datasource.username=${EXIF_TEST_DB_USERNAME}", "spring.datasource.password=${EXIF_TEST_DB_PASSWORD}",
        "spring.datasource.driver-class-name=org.postgresql.Driver", "spring.flyway.schemas=${EXIF_TEST_DB_SCHEMA}",
        "spring.jpa.properties.hibernate.default_schema=${EXIF_TEST_DB_SCHEMA}", "logging.level.root=WARN", "debug=false"})
class OpportunityMatchingReportPostgresIntegrationTest extends OpportunityMatchingReportIntegrationTest {
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeAll static void requireIsolatedSchema() {
        String schema = System.getenv("EXIF_TEST_DB_SCHEMA");
        if (schema == null || !schema.matches("ims_deletion_test_[0-9a-f]{32}")
                || !System.getenv("EXIF_TEST_DB_URL").contains("currentSchema=" + schema))
            throw new IllegalStateException("Matching tests require a newly created isolated local schema");
    }

    @BeforeEach void requirePostgresConnection() {
        assertThat(jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName())).isEqualTo("PostgreSQL");
    }

    @Test void matchNotificationFailureRollsBackPublicationSnapshotAndAllAlerts() {
        // DDL uses its own connection so the trigger is visible to REQUIRES_NEW and can
        // be removed after rollback. The validated URL always targets a disposable schema.
        var independent = new JdbcTemplate(new DriverManagerDataSource(System.getenv("EXIF_TEST_DB_URL"),
                System.getenv("EXIF_TEST_DB_USERNAME"), System.getenv("EXIF_TEST_DB_PASSWORD")));
        independent.execute("create function fail_opportunity_match_notification() returns trigger language plpgsql as $$ "
                + "begin if NEW.notification_type='OPPORTUNITY_MATCH' then raise exception 'simulated match notification failure'; end if; return NEW; end $$");
        try {
            independent.execute("create trigger fail_opportunity_match_notification before insert on student_notifications "
                    + "for each row execute function fail_opportunity_match_notification()");
            String suffix = UUID.randomUUID().toString();
            String studentEmail = "match-rollback-" + suffix + "@example.test";
            var publishedId = new AtomicLong();
            var tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                User ownAdmin = users.saveAndFlush(new User("match-rollback-admin-" + suffix + "@example.test", "unused", Role.ADMIN, true));
                User ownStudent = users.saveAndFlush(new User(studentEmail, "unused", Role.STUDENT, true));
                var profile = new StudentProfile(ownStudent);
                profile.apply("Rollback Candidate", Branch.COMPUTER_ENGINEERING, "U" + ownStudent.getId(), "R" + ownStudent.getId(),
                        "Address", null, studentEmail, studentEmail, "9876543210", "Guardian", "9876543211", "9876543212",
                        "O+", "Mentor", "9876543213", "Coordinator", "9876543214", "CDC", "9876543215", "2026-2027", 7);
                profile.setEncryptedAadhaar("test-encrypted-value", "1234"); profiles.saveAndFlush(profile);
                studentSkills.addOwn(UserPrincipal.from(ownStudent), new SkillNameRequest("Java"));
                var request = new OpportunityRequest("Rollback Company", "Java Internship", "Build Java services", null,
                        "Pune", OpportunityMode.HYBRID, 3, BigDecimal.ZERO, "INR", LocalDate.now().plusDays(10), null,
                        List.of(Branch.COMPUTER_ENGINEERING), List.of(), List.of("Java"), List.of());
                long id = service.create(UserPrincipal.from(ownAdmin), request).id();
                publishedId.set(id);
                service.publish(id);
            })).hasStackTraceContaining("simulated match notification failure");
            assertThat(publishedId.get()).isPositive();
            assertThat(jdbc.queryForObject("select count(*) from opportunities where id=?", Long.class, publishedId.get())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from opportunity_matching_runs where opportunity_id=?", Long.class, publishedId.get())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from student_notifications where event_key=? or event_key like ?", Long.class,
                    "opportunity-published:" + publishedId.get(), "opportunity-match:" + publishedId.get() + ":%")).isZero();
            assertThat(users.findByEmailIgnoreCase(studentEmail)).isEmpty();
        } finally {
            // Release this test's read locks before independent DDL cleanup.
            if (org.springframework.test.context.transaction.TestTransaction.isActive()) {
                org.springframework.test.context.transaction.TestTransaction.flagForRollback();
                org.springframework.test.context.transaction.TestTransaction.end();
            }
            independent.execute("drop trigger if exists fail_opportunity_match_notification on student_notifications");
            independent.execute("drop function fail_opportunity_match_notification()");
        }
    }
}
