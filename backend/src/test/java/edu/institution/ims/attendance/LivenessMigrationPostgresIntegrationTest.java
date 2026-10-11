package edu.institution.ims.attendance;

import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "EXIF_TEST_DB_URL", matches = "jdbc:postgresql://(localhost|127[.]0[.]0[.]1):.*")
class LivenessMigrationPostgresIntegrationTest {
    @Test
    void v28PreservesHistoricalStatusesEvidenceWorkplacesAndAuditsWithoutInferringLiveness() {
        String outer = System.getenv("EXIF_TEST_DB_SCHEMA"), url = System.getenv("EXIF_TEST_DB_URL");
        if (outer == null || !outer.matches("ims_deletion_test_[0-9a-f]{32}")
                || !url.contains("currentSchema=" + outer)) {
            throw new IllegalStateException("An isolated local test schema is required");
        }
        String schema = "ims_workplace_migration_" + UUID.randomUUID().toString().replace("-", "");
        if (!schema.matches("ims_workplace_migration_[0-9a-f]{32}")) throw new IllegalStateException("Invalid generated schema");
        String username = System.getenv("EXIF_TEST_DB_USERNAME"), password = System.getenv("EXIF_TEST_DB_PASSWORD");
        var owner = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
        owner.execute("create schema " + schema);
        try {
            String isolated = url.replace("currentSchema=" + outer, "currentSchema=" + schema);
            Flyway.configure().dataSource(isolated, username, password).schemas(schema).target("27").load().migrate();
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(isolated, username, password));
            seedHistoricalRecords(jdbc);
            List<Map<String, Object>> attendanceBefore = jdbc.queryForList("select * from attendance_records order by id");
            Map<String, List<Map<String, Object>>> retainedBefore = new LinkedHashMap<>();
            for (String table : List.of("attendance_evidence", "internship_locations", "workplace_review_audit",
                    "workplace_correction_audit", "mentor_record_deletion_audit", "monthly_progress_reports")) {
                retainedBefore.put(table, jdbc.queryForList("select * from " + table + " order by id"));
            }

            Flyway.configure().dataSource(isolated, username, password).schemas(schema).target("28").load().migrate();

            List<Map<String, Object>> attendanceAfter = jdbc.queryForList("select * from attendance_records order by id");
            assertThat(attendanceAfter).hasSize(3);
            for (Map<String, Object> row : attendanceAfter) {
                assertThat(row.remove("verification_basis")).isEqualTo("LEGACY");
            }
            assertThat(attendanceAfter).isEqualTo(attendanceBefore);
            retainedBefore.forEach((table, before) ->
                    assertThat(jdbc.queryForList("select * from " + table + " order by id")).as(table).isEqualTo(before));
            assertThat(jdbc.queryForObject("select ai_liveness_passed from attendance_evidence where attendance_id=102", Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("select verification_status from attendance_records where id=101", String.class)).isEqualTo("VERIFIED");
            assertThat(jdbc.queryForObject("select verification_status from attendance_records where id=102", String.class)).isEqualTo("UNVERIFIED");
            assertThat(jdbc.queryForObject("select count(*) from attendance_records where deleted_at is not null", Integer.class)).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update("update attendance_records set verification_basis='GPS' where id=101"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("update attendance_records set verification_basis=null where id=101"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            jdbc.update("update attendance_records set verification_basis='LIVENESS' where id=101");
            assertThat(jdbc.queryForObject("select verification_basis from attendance_records where id=101", String.class)).isEqualTo("LIVENESS");
        } finally {
            // Remove only this test's freshly generated local schema, never an existing application schema.
            owner.execute("drop schema " + schema + " cascade");
        }
    }

    private static void seedHistoricalRecords(JdbcTemplate jdbc) {
        jdbc.update("insert into users(id,email,password_hash,role,active,created_at,updated_at) values "
                + "(101,'legacy.student@example.test','test-only','STUDENT',true,now(),now()),"
                + "(102,'legacy.admin@example.test','test-only','ADMIN',true,now(),now()),"
                + "(103,'legacy.mentor@example.test','test-only','MENTOR',true,now(),now())");
        jdbc.update("insert into internship_onboarding(id,student_user_id,workflow_status,secured_at,created_at,updated_at) "
                + "values (101,101,'INTERNSHIP_SECURED',now(),now(),now())");
        jdbc.update("insert into internship_details(id,onboarding_id,company_name,company_type,company_address,company_phone,industry_supervisor_name,industry_supervisor_designation,industry_supervisor_contact,duration_months,start_date,end_date,total_weeks,internship_mode,stipend_per_month,full_time_employment_offered,created_at,updated_at) "
                + "values (101,101,'Legacy branch','IT','Legacy address','1234567890','Supervisor','Manager','1234567890',2,'2026-09-01','2026-10-31',8,'OFFICE_REPORTING',0,false,now(),now())");
        jdbc.update("insert into internship_locations(id,internship_id,latitude,longitude,geofence_radius_meters,status,created_at,updated_at,confirmed_by,confirmed_at) "
                + "values (101,101,21.125,79.0875,200,'CONFIRMED',now(),now(),102,now())");
        jdbc.update("insert into attendance_records(id,student_user_id,internship_id,attendance_date,attendance_result,verification_status,submission_type,server_submitted_at,created_at,updated_at,deleted_at,active_slot) values "
                + "(101,101,101,'2026-09-18','PRESENT','VERIFIED','PHYSICAL_CAPTURE',now(),now(),now(),null,1),"
                + "(102,101,101,'2026-09-19','PRESENT','UNVERIFIED','PHYSICAL_CAPTURE',now(),now(),now(),null,1),"
                + "(103,101,101,'2026-09-20','PRESENT','VERIFIED','PHYSICAL_CAPTURE',now(),now(),now(),now(),null)");
        jdbc.update("insert into attendance_evidence(attendance_id,latitude,longitude,expected_latitude,expected_longitude,distance_meters,geofence_radius_meters,camera_capture_used,verification_reason,image_media_type,image_size_bytes,created_at,ai_liveness_passed,ai_quality_passed) values "
                + "(101,21.125,79.0875,21.125,79.0875,10,200,true,'VERIFIED_GEOFENCE','image/jpeg',100,now(),false,false),"
                + "(102,21.125,79.0875,21.125,79.0875,10,200,true,'GPS_ACCURACY_TOO_LOW','image/jpeg',100,now(),true,true)");
        jdbc.update("insert into workplace_review_audit(location_id,revision,reviewer_id,reviewer_role,decision,reviewed_at,latitude,longitude,radius_meters,workplace_address) "
                + "values (101,1,103,'MENTOR','APPROVE',now(),21.125,79.0875,200,'Legacy workplace address')");
        jdbc.update("insert into workplace_correction_audit(internship_id,correcting_user_id,corrected_at,reason,old_latitude,old_longitude,new_latitude,new_longitude,new_place_id,new_radius_meters) "
                + "values (101,102,now(),'Historical branch correction',21,79,21.125,79.0875,'test-only-place',200)");
        jdbc.update("insert into mentor_record_deletion_audit(record_type,record_id,deleting_user_id,deleted_at,reason) "
                + "values ('ATTENDANCE',103,103,now(),'Historical mentor deletion')");
        jdbc.update("insert into monthly_progress_reports(student_internship_id,report_month_number,period_start,period_end,work_description,photo_storage_key,photo_original_name,photo_mime_type,photo_size,submitted_at,created_at,updated_at,workplace_latitude,workplace_longitude,workplace_verification_status) "
                + "values (101,1,'2026-09-01','2026-09-30','Legacy monthly work','test-reference-only','legacy.jpg','image/jpeg',100,now(),now(),now(),21.125,79.0875,'WITHIN_RADIUS')");
    }
}
