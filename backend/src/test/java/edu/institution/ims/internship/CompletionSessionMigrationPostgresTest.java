package edu.institution.ims.internship;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="EXIF_TEST_DB_URL",matches="jdbc:postgresql://(localhost|127[.]0[.]0[.]1):.*")
class CompletionSessionMigrationPostgresTest {
    @Test void upgradeFromV39PreservesHistoryBackfillsOnlyExplicitProfileYearsAndDoesNotInventCompletions() {
        String outer=System.getenv("EXIF_TEST_DB_SCHEMA"),url=System.getenv("EXIF_TEST_DB_URL");
        if(outer==null || !outer.matches("ims_deletion_test_[0-9a-f]{32}") || !url.contains("currentSchema="+outer)) throw new IllegalStateException("Disposable local schema required");
        String schema="ims_completion_migration_"+UUID.randomUUID().toString().replace("-","");
        String username=System.getenv("EXIF_TEST_DB_USERNAME"),password=System.getenv("EXIF_TEST_DB_PASSWORD");
        var owner=new JdbcTemplate(new DriverManagerDataSource(url,username,password));owner.execute("create schema "+schema);
        try {
            String isolated=url.replace("currentSchema="+outer,"currentSchema="+schema);
            Flyway.configure().dataSource(isolated,username,password).schemas(schema).target("39").load().migrate();
            var jdbc=new JdbcTemplate(new DriverManagerDataSource(isolated,username,password));
            jdbc.update("insert into users(id,email,password_hash,role,active,created_at,updated_at) values(101,'legacy@test.edu','test','STUDENT',true,now(),now()),(102,'unknown@test.edu','test','STUDENT',true,now(),now()),(103,'mentor@test.edu','test','MENTOR',true,now(),now())");
            jdbc.update("insert into student_profiles(user_id,student_name,branch,uid,roll_number,permanent_address,personal_email,institute_email,phone_number,guardian_name,guardian_contact,emergency_contact,blood_group,aadhaar_encrypted,aadhaar_last_four,faculty_mentor_name,faculty_mentor_contact,internship_coordinator_name,internship_coordinator_contact,cdc_coordinator_name,cdc_coordinator_contact,academic_year,semester,created_at,updated_at) values(101,'Legacy Student','COMPUTER_ENGINEERING','LEGACY101','101','Address','personal@test.edu','legacy@college.edu','1234567890','Guardian','1234567890','1234567890','O+','test-reference-only','1234','Mentor','1234567890','IIIC','1234567890','CDC','1234567890','2025-2026',8,now(),now())");
            jdbc.update("insert into internship_onboarding(id,student_user_id,workflow_status,secured_at,diary_issued,diary_issued_at,documents_completed_at,created_at,updated_at) values(101,101,'INTERNSHIP_DETAILS_COMPLETE',now(),true,now(),now(),now(),now())");
            jdbc.update("insert into internship_details(id,onboarding_id,company_name,company_type,company_address,company_phone,industry_supervisor_name,industry_supervisor_designation,industry_supervisor_contact,duration_months,start_date,end_date,total_weeks,internship_mode,stipend_per_month,full_time_employment_offered,created_at,updated_at) values(101,101,'Legacy Company','IT','Address','1234567890','Supervisor','Manager','1234567890',1,'2025-01-01','2025-01-09',2,'ONLINE',0,false,now(),now())");
            var profile=jdbc.queryForMap("select * from student_profiles where user_id=101");
            var dates=jdbc.queryForMap("select start_date,end_date,created_at,updated_at from internship_details where id=101");
            var migration=Flyway.configure().dataSource(isolated,username,password).schemas(schema).load();
            assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);migration.validate();assertThat(migration.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForMap("select * from student_profiles where user_id=101")).isEqualTo(profile);
            assertThat(jdbc.queryForMap("select start_date,end_date,created_at,updated_at from internship_details where id=101")).isEqualTo(dates);
            assertThat(jdbc.queryForObject("select s.name from users u join academic_sessions s on s.id=u.academic_session_id where u.id=101",String.class)).isEqualTo("2025-2026");
            assertThat(jdbc.queryForObject("select count(*) from users where id in(102,103) and academic_session_id is null",Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select active_session_id from academic_session_settings where id=1",Long.class)).isNull();
            assertThat(jdbc.queryForObject("select completed_at from internship_details where id=101",Object.class)).isNull();
            assertThat(jdbc.queryForObject("select completion_status from internship_details where id=101",String.class)).isEqualTo("IN_PROGRESS");
            assertThat(jdbc.queryForObject("select count(*) from internship_completion_events",Long.class)).isZero();
        } finally { owner.execute("drop schema "+schema+" cascade"); }
    }
}
