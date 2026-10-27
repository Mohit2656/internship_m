package edu.institution.ims.notification;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.TestPropertySource;

@EnabledIfEnvironmentVariable(named = "EXIF_TEST_DB_URL", matches = "jdbc:postgresql://(localhost|127[.]0[.]0[.]1):.*")
@TestPropertySource(properties = {"spring.datasource.url=${EXIF_TEST_DB_URL}",
        "spring.datasource.username=${EXIF_TEST_DB_USERNAME}", "spring.datasource.password=${EXIF_TEST_DB_PASSWORD}",
        "spring.datasource.driver-class-name=org.postgresql.Driver", "spring.flyway.schemas=${EXIF_TEST_DB_SCHEMA}",
        "spring.jpa.properties.hibernate.default_schema=${EXIF_TEST_DB_SCHEMA}"})
class MentorNotificationPostgresIntegrationTest extends MentorNotificationIntegrationTest {
    @BeforeAll static void requireIsolatedSchema() {
        String schema = System.getenv("EXIF_TEST_DB_SCHEMA");
        if (schema == null || !schema.matches("ims_deletion_test_[0-9a-f]{32}")
                || !System.getenv("EXIF_TEST_DB_URL").contains("currentSchema=" + schema))
            throw new IllegalStateException("Mentor notification tests require a newly created isolated local schema");
    }
}
