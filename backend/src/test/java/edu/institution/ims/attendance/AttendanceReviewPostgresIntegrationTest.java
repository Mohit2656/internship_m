package edu.institution.ims.attendance;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="EXIF_TEST_DB_URL",matches="jdbc:postgresql://(localhost|127[.]0[.]0[.]1):.*")
@TestPropertySource(properties={"spring.datasource.url=${EXIF_TEST_DB_URL}","spring.datasource.username=${EXIF_TEST_DB_USERNAME}",
        "spring.datasource.password=${EXIF_TEST_DB_PASSWORD}","spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.schemas=${EXIF_TEST_DB_SCHEMA}","spring.jpa.properties.hibernate.default_schema=${EXIF_TEST_DB_SCHEMA}"})
class AttendanceReviewPostgresIntegrationTest extends AttendanceReviewIntegrationTest {
    @BeforeAll static void requireIsolatedSchema() {
        String schema=System.getenv("EXIF_TEST_DB_SCHEMA");
        if(schema==null || !schema.matches("ims_deletion_test_[0-9a-f]{32}") || !System.getenv("EXIF_TEST_DB_URL").contains("currentSchema="+schema))
            throw new IllegalStateException("Attendance review tests require a disposable local schema");
    }
    @BeforeEach void requirePostgresConnection() {
        assertThat(jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName())).isEqualTo("PostgreSQL");
    }
}
