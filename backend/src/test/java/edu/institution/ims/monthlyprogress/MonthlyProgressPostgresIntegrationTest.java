package edu.institution.ims.monthlyprogress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="EXIF_TEST_DB_URL",matches="jdbc:postgresql://(localhost|127[.]0[.]0[.]1):.*")
@TestPropertySource(properties={"spring.datasource.url=${EXIF_TEST_DB_URL}","spring.datasource.username=${EXIF_TEST_DB_USERNAME}","spring.datasource.password=${EXIF_TEST_DB_PASSWORD}","spring.datasource.driver-class-name=org.postgresql.Driver","spring.flyway.schemas=${EXIF_TEST_DB_SCHEMA}","spring.jpa.properties.hibernate.default_schema=${EXIF_TEST_DB_SCHEMA}"})
class MonthlyProgressPostgresIntegrationTest extends MonthlyProgressIntegrationTest {
    @org.junit.jupiter.api.BeforeAll static void requireIsolatedSchema() {
        String schema = System.getenv("EXIF_TEST_DB_SCHEMA");
        if (schema == null || !schema.matches("ims_deletion_test_[0-9a-f]{32}")
                || !System.getenv("EXIF_TEST_DB_URL").contains("currentSchema=" + schema))
            throw new IllegalStateException("Monthly report tests require a newly created isolated local schema");
    }
    @Autowired JdbcTemplate jdbc;
    @Test void completeMetadataAndExactHashArePersistedInLocalPostgres() throws Exception {
        byte[] original=ProgressPhotoValidatorTest.fixture(true,true,21);
        var session=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new edu.institution.ims.auth.LoginRequest(studentA.getEmail(),"StudentPassword!23")))).andExpect(status().isOk()).andReturn().getResponse().getCookie("ims_auth");
        var created=mvc.perform(multipart("/api/v1/student/monthly-progress/1").file(new MockMultipartFile("photo","complete.jpg","image/jpeg",original)).file(new MockMultipartFile("workDescription","","text/plain","Local PostgreSQL test".getBytes(StandardCharsets.UTF_8))).cookie(session)).andExpect(status().isCreated()).andReturn();
        long id=json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        var row=jdbc.queryForMap("select * from monthly_progress_reports where id=?",id);
        assertThat(row.get("geo_verification_status")).isEqualTo("VERIFIED");assertThat(row.get("capture_time_status")).isEqualTo("PRESENT");
        assertThat(row.get("exif_latitude").toString()).isEqualTo("21.125000000");assertThat(row.get("exif_longitude").toString()).isEqualTo("79.087500000");
        assertThat(row.get("gps_altitude").toString()).isEqualTo("317.000000");assertThat(row.get("gps_timestamp")).isNotNull();assertThat(row.get("exif_captured_at")).isNotNull();
        assertThat(row.get("camera_make")).isEqualTo("Test Camera");assertThat(row.get("camera_model")).isEqualTo("Model, \"One\"");
        assertThat(row.get("image_width")).isEqualTo(16);assertThat(row.get("image_height")).isEqualTo(12);assertThat(row.get("gps_present")).isEqualTo(true);assertThat(row.get("exif_present")).isEqualTo(true);
        assertThat(row.get("image_sha256")).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)));
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version='22' and success=true",Integer.class)).isEqualTo(1);
    }
}
