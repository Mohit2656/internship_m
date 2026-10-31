package edu.institution.ims.academicsession;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="EXIF_TEST_DB_URL",matches="jdbc:postgresql://(localhost|127[.]0[.]0[.]1):.*")
class AcademicSessionMigrationPostgresTest {
    @Test void v41UpgradePreservesActiveMembershipAndHistoryAndBackfillsOnlyKnownAuditMetadata() {
        String outer=System.getenv("EXIF_TEST_DB_SCHEMA"),url=System.getenv("EXIF_TEST_DB_URL");
        if(outer==null || !outer.matches("ims_deletion_test_[0-9a-f]{32}") || !url.contains("currentSchema="+outer))throw new IllegalStateException("Disposable local schema required");
        String schema="ims_session_migration_"+UUID.randomUUID().toString().replace("-","");
        String username=System.getenv("EXIF_TEST_DB_USERNAME"),password=System.getenv("EXIF_TEST_DB_PASSWORD");
        var owner=new JdbcTemplate(new DriverManagerDataSource(url,username,password));owner.execute("create schema "+schema);
        try {
            String isolated=url.replace("currentSchema="+outer,"currentSchema="+schema);
            Flyway.configure().dataSource(isolated,username,password).schemas(schema).target("41").load().migrate();
            var jdbc=new JdbcTemplate(new DriverManagerDataSource(isolated,username,password));
            jdbc.update("insert into users(id,email,password_hash,role,active,created_at,updated_at) values(101,'admin@test.edu','unused','ADMIN',true,current_timestamp,current_timestamp),(102,'student@test.edu','unused','STUDENT',true,current_timestamp,current_timestamp)");
            jdbc.update("insert into academic_sessions(id,name,start_date,end_date,created_by) values(101,'2026-2027',DATE '2026-07-01',DATE '2027-06-30',101),(102,'2025-2026',null,null,null)");
            jdbc.update("update academic_session_settings set active_session_id=101,activated_by=101,activated_at=current_timestamp where id=1");
            jdbc.update("update users set academic_session_id=101 where id=102");
            var students=jdbc.queryForList("select * from users");var settings=jdbc.queryForMap("select * from academic_session_settings");
            var migration=Flyway.configure().dataSource(isolated,username,password).schemas(schema).load();
            assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);migration.validate();assertThat(migration.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForList("select * from users")).isEqualTo(students);assertThat(jdbc.queryForMap("select * from academic_session_settings")).isEqualTo(settings);
            assertThat(jdbc.queryForObject("select count(*) from academic_sessions where closed_at is null and closed_by is null",Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from academic_session_events where action='CREATED'",Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select performed_by from academic_session_events where action='ACTIVATED'",Long.class)).isEqualTo(101);
            assertThat(jdbc.queryForObject("select count(*) from academic_session_events where action='CLOSED'",Long.class)).isZero();
        } finally { owner.execute("drop schema "+schema+" cascade"); }
    }
}
