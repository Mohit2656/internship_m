package edu.institution.ims.professionalprofile;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "EXIF_TEST_DB_URL", matches = "jdbc:postgresql://(localhost|127[.]0[.]0[.]1):.*")
class ResumeWorkflowMigrationPostgresIntegrationTest {
    @Test void upgradePreservesUploadedResumeAttendanceAndReadInboxAndAllowsNewWorkflowEvents() {
        String outer = System.getenv("EXIF_TEST_DB_SCHEMA"), url = System.getenv("EXIF_TEST_DB_URL");
        if (outer == null || !outer.matches("ims_deletion_test_[0-9a-f]{32}") || !url.contains("currentSchema=" + outer))
            throw new IllegalStateException("An isolated local test schema is required");
        String schema = "ims_resume_migration_" + UUID.randomUUID().toString().replace("-", "");
        if (!schema.matches("ims_resume_migration_[0-9a-f]{32}")) throw new IllegalStateException("Invalid generated schema");
        String username = System.getenv("EXIF_TEST_DB_USERNAME"), password = System.getenv("EXIF_TEST_DB_PASSWORD");
        var admin = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
        admin.execute("create schema " + schema);
        try {
            String isolated = url.replace("currentSchema=" + outer, "currentSchema=" + schema);
            Flyway.configure().dataSource(isolated, username, password).schemas(schema).target("35").load().migrate();
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(isolated, username, password));
            jdbc.update("insert into users(id,email,password_hash,role,active,created_at,updated_at) values "
                    + "(101,'legacy.student@example.test','test-only','STUDENT',true,now(),now()),"
                    + "(102,'legacy.admin@example.test','test-only','ADMIN',true,now(),now()),"
                    + "(103,'legacy.mentor@example.test','test-only','MENTOR',true,now(),now())");
            jdbc.update("insert into student_professional_profiles(id,student_user_id,headline,created_at,updated_at) values (101,101,'Legacy headline',now(),now())");
            jdbc.update("insert into student_resumes(id,professional_profile_id,original_filename,storage_key,content_type,file_size,uploaded_at,is_active,parsing_status) values (101,101,'legacy.pdf','test-reference-only','application/pdf',100,now(),true,'NOT_PARSED')");
            jdbc.update("insert into internship_onboarding(id,student_user_id,workflow_status,secured_at,created_at,updated_at) values (101,101,'INTERNSHIP_SECURED',now(),now(),now())");
            jdbc.update("insert into internship_details(id,onboarding_id,company_name,company_type,company_address,company_phone,industry_supervisor_name,industry_supervisor_designation,industry_supervisor_contact,duration_months,start_date,end_date,total_weeks,internship_mode,stipend_per_month,full_time_employment_offered,created_at,updated_at) values (101,101,'Legacy company','IT','Legacy address','1234567890','Supervisor','Manager','1234567890',2,'2026-09-01','2026-10-31',8,'OFFICE_REPORTING',0,false,now(),now())");
            jdbc.update("insert into attendance_records(id,student_user_id,internship_id,attendance_date,attendance_result,verification_status,submission_type,server_submitted_at,created_at,updated_at) values (101,101,101,'2026-09-18','PRESENT','VERIFIED','BASIC',now(),now(),now())");
            jdbc.update("insert into mentor_notifications(id,mentor_user_id,student_user_id,attendance_id,notification_type,title,message,action_url,created_at,read_at) values (101,103,101,101,'ATTENDANCE_SUBMITTED','Legacy attendance','Legacy message','/mentor/attendance',now(),now())");
            var resume = jdbc.queryForMap("select * from student_resumes where id=101");
            var attendance = jdbc.queryForMap("select * from attendance_records where id=101");
            var inbox = jdbc.queryForMap("select id,mentor_user_id,student_user_id,attendance_id,title,message,created_at,read_at from mentor_notifications where id=101");
            Flyway migration = Flyway.configure().dataSource(isolated, username, password).schemas(schema).target("39").load();
            assertThat(migration.migrate().migrationsExecuted).isEqualTo(4);
            migration.validate();
            assertThat(migration.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForMap("select * from student_resumes where id=101")).isEqualTo(resume);
            assertThat(jdbc.queryForMap("select * from attendance_records where id=101")).isEqualTo(attendance);
            assertThat(jdbc.queryForMap("select id,mentor_user_id,student_user_id,attendance_id,title,message,created_at,read_at from mentor_notifications where id=101")).isEqualTo(inbox);
            assertThat(jdbc.queryForObject("select count(*) from resume_corrections", Long.class)).isZero();
            jdbc.update("insert into mentor_notifications(mentor_user_id,student_user_id,event_key,notification_type,title,message,action_url,created_at) values (103,101,'workflow:1','WEEKLY_REPORT_SUBMITTED','Week 1','Review week','/mentor/weekly-progress',now()),(103,101,'workflow:2','WEEKLY_REPORT_SUBMITTED','Week 2','Review week','/mentor/weekly-progress',now())");
            assertThat(jdbc.queryForObject("select count(*) from mentor_notifications where attendance_id is null", Long.class)).isEqualTo(2);
            assertThatThrownBy(() -> jdbc.update("insert into mentor_notifications(mentor_user_id,student_user_id,event_key,notification_type,title,message,action_url,created_at) values (103,101,'workflow:1','WEEKLY_REPORT_SUBMITTED','Duplicate','Review week','/mentor/weekly-progress',now())"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            // Only this exact generated schema, successfully created above, is removed.
            admin.execute("drop schema " + schema + " cascade");
        }
    }
}
