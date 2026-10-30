package edu.institution.ims.internship;

import edu.institution.ims.security.UserPrincipal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="EXIF_TEST_DB_URL",matches="jdbc:postgresql://(localhost|127[.]0[.]0[.]1):.*")
@TestPropertySource(properties={"spring.datasource.url=${EXIF_TEST_DB_URL}","spring.datasource.username=${EXIF_TEST_DB_USERNAME}",
        "spring.datasource.password=${EXIF_TEST_DB_PASSWORD}","spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.schemas=${EXIF_TEST_DB_SCHEMA}","spring.jpa.properties.hibernate.default_schema=${EXIF_TEST_DB_SCHEMA}"})
class InternshipCompletionPostgresIntegrationTest extends InternshipCompletionIntegrationTest {
    @Autowired PlatformTransactionManager transactions;
    @BeforeAll static void isolated() {
        String schema=System.getenv("EXIF_TEST_DB_SCHEMA");
        if(schema==null || !schema.matches("ims_deletion_test_[0-9a-f]{32}") || !System.getenv("EXIF_TEST_DB_URL").contains("currentSchema="+schema)) throw new IllegalStateException("Disposable local schema required");
    }
    <T> T asAdmin(Supplier<T> operation) {
        var context=SecurityContextHolder.createEmptyContext(); context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor,null,actor.getAuthorities()));SecurityContextHolder.setContext(context);
        try { return operation.get(); } finally { SecurityContextHolder.clearContext(); }
    }
    void commitFixture() { TestTransaction.flagForCommit(); TestTransaction.end(); }
    @Test void concurrentCompletionCommitsOnlyOneApprovalAuditAndNotification() throws Exception {
        approveAll(); commitFixture(); var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            Callable<Integer> attempt=()->asAdmin(()->{ try { if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Start timed out");service.decide(actor,student.getId(),new InternshipCompletionService.Decision(0,null),false);return 200; }
                catch(org.springframework.web.server.ResponseStatusException e){return e.getStatusCode().value();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);} });
            var first=pool.submit(attempt);var second=pool.submit(attempt);start.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            assertThat(jdbc.queryForObject("select count(*) from internship_completion_events where internship_id=?",Long.class,internship.getId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from student_notifications where student_user_id=? and notification_type='INTERNSHIP_COMPLETED'",Long.class,student.getId())).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void notificationFailureRollsBackStatusAndAuditWithoutTouchingReports() {
        approveAll();commitFixture();
        String suffix=UUID.randomUUID().toString().replace("-","");String function="completion_failure_"+suffix,trigger="completion_failure_trigger_"+suffix;
        jdbc.execute("create function "+function+"() returns trigger language plpgsql as $$ begin if NEW.notification_type='INTERNSHIP_COMPLETED' then raise exception 'test completion notification failure'; end if; return NEW; end $$");
        jdbc.execute("create trigger "+trigger+" before insert on student_notifications for each row execute function "+function+"()");
        try {
            assertThatThrownBy(()->asAdmin(()->service.decide(actor,student.getId(),new InternshipCompletionService.Decision(0,null),false))).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(jdbc.queryForObject("select completion_status from internship_details where id=?",String.class,internship.getId())).isEqualTo("IN_PROGRESS");
            assertThat(jdbc.queryForObject("select count(*) from internship_completion_events where internship_id=?",Long.class,internship.getId())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from monthly_progress_reports where student_internship_id=?",Long.class,internship.getId())).isEqualTo(1);
        } finally { jdbc.execute("drop trigger "+trigger+" on student_notifications");jdbc.execute("drop function "+function+"()"); }
    }
    @Test void activationAndImportSerializeSoNewAccountsUseTheCommittedActiveSession() throws Exception {
        String name="NEXT-"+UUID.randomUUID();jdbc.update("insert into academic_sessions(name,start_date,end_date) values(?,DATE '2150-07-01',DATE '2151-06-30')",name);
        Long next=jdbc.queryForObject("select id from academic_sessions where name=?",Long.class,name);String uid="RACE"+UUID.randomUUID().toString().replace("-","");var upload=workbook(uid);commitFixture();
        var pool=Executors.newFixedThreadPool(2);var activated=new CountDownLatch(1);var release=new CountDownLatch(1);
        try {
            var activation=pool.submit(()->asAdmin(()->new TransactionTemplate(transactions).execute(tx->{sessions.activate(actor,next);activated.countDown();try {if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Release timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return next;})));
            assertThat(activated.await(10,TimeUnit.SECONDS)).isTrue();var importing=pool.submit(()->importer.importWorkbook(upload));
            // Activation owns the shared singleton; account creation must wait for its commit.
            assertThatThrownBy(()->importing.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);release.countDown();activation.get(15,TimeUnit.SECONDS);
            assertThat(importing.get(15,TimeUnit.SECONDS).accountsCreated()).isEqualTo(1);
            assertThat(jdbc.queryForObject("select academic_session_id from users where email=?",Long.class,uid.toLowerCase()+"@test.edu")).isEqualTo(next);
            assertThat(jdbc.queryForObject("select academic_session_id from users where id=?",Long.class,student.getId())).isEqualTo(sessionId);
        } finally { release.countDown();pool.shutdownNow(); }
    }
}
