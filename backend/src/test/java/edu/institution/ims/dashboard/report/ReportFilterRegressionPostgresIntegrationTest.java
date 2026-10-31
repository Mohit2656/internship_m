package edu.institution.ims.dashboard.report;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.TestPropertySource;

@EnabledIfEnvironmentVariable(named="EXIF_TEST_DB_URL",matches="jdbc:postgresql://(localhost|127[.]0[.]0[.]1):.*")
@TestPropertySource(properties={"spring.datasource.url=${EXIF_TEST_DB_URL}","spring.datasource.username=${EXIF_TEST_DB_USERNAME}","spring.datasource.password=${EXIF_TEST_DB_PASSWORD}","spring.datasource.driver-class-name=org.postgresql.Driver","spring.flyway.schemas=${EXIF_TEST_DB_SCHEMA}","spring.jpa.properties.hibernate.default_schema=${EXIF_TEST_DB_SCHEMA}"})
class ReportFilterRegressionPostgresIntegrationTest extends ReportFilterRegressionIntegrationTest {}
