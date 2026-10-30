package edu.institution.ims.deletion;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="EXIF_TEST_DB_URL",matches="jdbc:postgresql://(localhost|127[.]0[.]0[.]1):.*")
@TestPropertySource(properties={"spring.datasource.url=${EXIF_TEST_DB_URL}","spring.datasource.username=${EXIF_TEST_DB_USERNAME}","spring.datasource.password=${EXIF_TEST_DB_PASSWORD}","spring.datasource.driver-class-name=org.postgresql.Driver","spring.flyway.schemas=${EXIF_TEST_DB_SCHEMA}","spring.jpa.properties.hibernate.default_schema=${EXIF_TEST_DB_SCHEMA}"})
class StudentDeletionPostgresIntegrationTest extends StudentDeletionIntegrationTest {
    @BeforeAll static void requireIsolatedSchema() {
        String schema=System.getenv("EXIF_TEST_DB_SCHEMA");
        if(schema==null || !schema.matches("ims_deletion_test_[0-9a-f]{32}") || !System.getenv("EXIF_TEST_DB_URL").contains("currentSchema="+schema)) throw new IllegalStateException("Disposable local PostgreSQL schema required");
    }
    void commitFixture() { TestTransaction.flagForCommit();TestTransaction.end(); }
    @Test void concurrentDeletionCommitsOnlyOnceAndRetainedForeignKeysPreventPhysicalRemoval() throws Exception {
        commitFixture();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Integer> task=()->{if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Start timed out");try{remove();return 200;}catch(org.springframework.web.server.ResponseStatusException e){return e.getStatusCode().value();}};
            var first=pool.submit(task);var second=pool.submit(task);start.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            assertThat(jdbc.queryForObject("select count(*) from student_account_deactivation_audit where student_user_id=?",Long.class,student.getId())).isEqualTo(1);
            long reports=jdbc.queryForObject("select count(*) from monthly_progress_reports where student_internship_id=?",Long.class,internship.getId());
            assertThatThrownBy(()->jdbc.update("delete from users where id=?",student.getId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("select count(*) from monthly_progress_reports where student_internship_id=?",Long.class,internship.getId())).isEqualTo(reports);
            assertThat(jdbc.queryForObject("select count(*) from users where id=?",Long.class,student.getId())).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void databaseFailureRollsBackBothAuditAndAccountDeactivation() {
        commitFixture();String suffix=UUID.randomUUID().toString().replace("-","");String function="student_delete_fail_"+suffix,trigger="student_delete_trigger_"+suffix;
        jdbc.execute("create function "+function+"() returns trigger language plpgsql as $$ begin if NEW.id="+student.getId()+" and NEW.active=false then raise exception 'test account update failure'; end if; return NEW; end $$");
        jdbc.execute("create trigger "+trigger+" before update on users for each row execute function "+function+"()");
        try {
            assertThatThrownBy(this::remove).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(jdbc.queryForObject("select active from users where id=?",Boolean.class,student.getId())).isTrue();
            assertThat(jdbc.queryForObject("select count(*) from student_account_deactivation_audit where student_user_id=?",Long.class,student.getId())).isZero();
        } finally {jdbc.execute("drop trigger "+trigger+" on users");jdbc.execute("drop function "+function+"()");}
    }
}
