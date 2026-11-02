package edu.institution.ims.studentprofile;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Real migration against old academic records; only a disposable H2 database. */
class StudentAcademicMigrationTest {
    @Test void standardizationAuditsOldValuesPreservesSectionsAndLeavesUnknownAssignmentsForReview() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:academics_"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        migrate(source, "50");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        // Simulate installations which already captured a section in an extension.
        jdbc.execute("alter table student_profiles add column section varchar(5)");
        jdbc.execute("alter table student_accounts add column section varchar(5)");
        fixture(jdbc, "one", "COMPUTER_ENGINEERING", 7, "A", "ROLL-1");
        fixture(jdbc, "two", "MECHANICAL", 6, "A", "ROLL-1");
        fixture(jdbc, "three", "CIVIL", 4, "B", "ROLL-3");
        fixture(jdbc, "four", "ELECTRICAL", 3, null, "ROLL-4");
        fixture(jdbc, "five", "INFORMATION_TECHNOLOGY", 2, "C", "ROLL-5");
        var identities = jdbc.queryForList("select id,user_id,uid,roll_number,academic_year,created_at,updated_at from student_profiles order by id");
        migrate(source, null);
        assertThat(jdbc.queryForList("select id,user_id,uid,roll_number,academic_year,created_at,updated_at from student_profiles order by id")).isEqualTo(identities);
        assertThat(jdbc.queryForObject("select count(*) from student_profiles where branch='COMPUTER_ENGINEERING' and semester=8", Long.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from student_accounts where semester=8", Long.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from student_profiles where section='A'", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from student_profiles where section='B'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from student_profiles where section is null", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from student_profiles where legacy_roll_identity_conflict=true", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from student_academic_normalization_audit", Long.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select previous_semester from student_academic_normalization_audit where entity_type='PROFILE' and previous_branch='MECHANICAL'", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select previous_section from student_academic_normalization_audit where entity_type='PROFILE' and previous_branch='INFORMATION_TECHNOLOGY'", String.class)).isEqualTo("C");
        assertThatThrownBy(() -> jdbc.update("update student_profiles set branch='MECHANICAL' where uid='UID-three'")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update student_accounts set semester=7")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update student_profiles set section='C' where uid='UID-four'")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private static void migrate(javax.sql.DataSource source, String target) {
        var config=Flyway.configure().dataSource(source).locations("classpath:db/migration");
        if (target!=null) config.target(target);
        config.load().migrate();
    }
    private static void fixture(JdbcTemplate jdbc, String suffix, String branch, int semester, String section, String roll) {
        String email=suffix+"@academic-test.example";
        jdbc.update("insert into users(email,password_hash,role,active,created_at,updated_at) values(?,'test-only','STUDENT',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",email);
        Long id=jdbc.queryForObject("select id from users where email=?",Long.class,email);
        jdbc.update("insert into student_accounts(user_id,student_name,uid,semester,section,created_at) values(?,?,?,?,?,CURRENT_TIMESTAMP)",id,"Student "+suffix,"UID-"+suffix,semester,section);
        jdbc.update("insert into student_profiles(user_id,student_name,branch,uid,roll_number,permanent_address,personal_email,institute_email,phone_number,guardian_name,guardian_contact,emergency_contact,blood_group,aadhaar_encrypted,aadhaar_last_four,faculty_mentor_name,faculty_mentor_contact,internship_coordinator_name,internship_coordinator_contact,cdc_coordinator_name,cdc_coordinator_contact,academic_year,semester,section,created_at,updated_at) "
                +"values(?,?,?,?,?,'Address',?,?,'9876543210','Guardian','9876543210','9876543210','O+','encrypted-test-only','1234','Mentor','9876543210','Coordinator','9876543210','CDC','9876543210','2026-2027',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,"Student "+suffix,branch,"UID-"+suffix,roll,email,email,semester,section);
    }
}
