package edu.institution.ims.academicsession;

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
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="EXIF_TEST_DB_URL",matches="jdbc:postgresql://(localhost|127[.]0[.]0[.]1):.*")
@TestPropertySource(properties={"spring.datasource.url=${EXIF_TEST_DB_URL}","spring.datasource.username=${EXIF_TEST_DB_USERNAME}",
        "spring.datasource.password=${EXIF_TEST_DB_PASSWORD}","spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.schemas=${EXIF_TEST_DB_SCHEMA}","spring.jpa.properties.hibernate.default_schema=${EXIF_TEST_DB_SCHEMA}"})
class AcademicSessionPostgresIntegrationTest extends AcademicSessionIntegrationTest {
    @Autowired PlatformTransactionManager transactions;
    @BeforeAll static void isolated() {
        String schema=System.getenv("EXIF_TEST_DB_SCHEMA");
        if(schema==null || !schema.matches("ims_deletion_test_[0-9a-f]{32}") || !System.getenv("EXIF_TEST_DB_URL").contains("currentSchema="+schema))
            throw new IllegalStateException("Disposable local schema required");
    }
    <T> T asAdmin(Supplier<T> operation) {
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor,null,actor.getAuthorities()));SecurityContextHolder.setContext(context);
        try { return operation.get(); } finally { SecurityContextHolder.clearContext(); }
    }
    void commitFixture() { TestTransaction.flagForCommit();TestTransaction.end(); }
    void await(CountDownLatch latch) {
        try { if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Latch timed out"); }
        catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException(e); }
    }
    @Test void simultaneousActivationsLeaveExactlyOneActiveSessionAndAuditAutomaticClosure() throws Exception {
        var next=create("RACE-A-"+UUID.randomUUID(),year+1,false);var later=create("RACE-B-"+UUID.randomUUID(),year+2,false);commitFixture();
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var first=pool.submit(()->asAdmin(()->{await(start);return sessions.activate(actor,next.id());}));
            var second=pool.submit(()->asAdmin(()->{await(start);return sessions.activate(actor,later.id());}));
            start.countDown();first.get(20,TimeUnit.SECONDS);second.get(20,TimeUnit.SECONDS);
            var all=sessions.list();assertThat(all.stream().filter(AcademicSessionService.Session::active)).hasSize(1);
            assertThat(sessions.activeId()).isIn(next.id(),later.id());
            assertThat(all.stream().filter(s->Set.of(original.id(),next.id(),later.id()).contains(s.id()) && s.status().equals("CLOSED"))).hasSize(2);
        } finally { pool.shutdownNow(); }
    }
    @Test void studentImportWaitsForClosureAndCannotUseTheClosedSession() throws Exception {
        String uid="RACECLOSE"+UUID.randomUUID().toString().replace("-","");var upload=workbook(uid);commitFixture();
        var pool=Executors.newFixedThreadPool(2);var closed=new CountDownLatch(1);var release=new CountDownLatch(1);
        try {
            var closing=pool.submit(()->asAdmin(()->new TransactionTemplate(transactions).execute(tx->{var result=sessions.close(actor,original.id(),confirmed(original));closed.countDown();await(release);return result;})));
            assertThat(closed.await(10,TimeUnit.SECONDS)).isTrue();
            var importing=pool.submit(()->{try {importer.importWorkbook(upload);return 200;}catch(ResponseStatusException e){return e.getStatusCode().value();}});
            assertThatThrownBy(()->importing.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();closing.get(20,TimeUnit.SECONDS);assertThat(importing.get(20,TimeUnit.SECONDS)).isEqualTo(409);
            assertThat(users.existsByEmailIgnoreCase(uid.toLowerCase()+"@test.edu")).isFalse();assertThat(sessions.activeId()).isNull();
        } finally { release.countDown();pool.shutdownNow(); }
    }
    @Test void deletionWaitsForConcurrentMembershipThenRejectsItWithoutDeletingTheStudent() throws Exception {
        var empty=create("RACEDELETE-"+UUID.randomUUID(),year+1,false);assertThat(sessions.deletionPreview(empty.id()).canDelete()).isTrue();commitFixture();
        var pool=Executors.newFixedThreadPool(2);var inserted=new CountDownLatch(1);var release=new CountDownLatch(1);String email="racing-"+UUID.randomUUID()+"@test.edu";
        try {
            var insertion=pool.submit(()->new TransactionTemplate(transactions).execute(tx->{jdbc.update("insert into users(email,password_hash,role,active,academic_session_id,created_at,updated_at) values(?,'unused','STUDENT',true,?,current_timestamp,current_timestamp)",email,empty.id());inserted.countDown();await(release);return true;}));
            assertThat(inserted.await(10,TimeUnit.SECONDS)).isTrue();
            var deletion=pool.submit(()->asAdmin(()->{try {sessions.delete(actor,empty.id(),confirmed(empty));return 204;}catch(ResponseStatusException e){return e.getStatusCode().value();}}));
            assertThatThrownBy(()->deletion.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();insertion.get(20,TimeUnit.SECONDS);assertThat(deletion.get(20,TimeUnit.SECONDS)).isEqualTo(409);
            assertThat(jdbc.queryForObject("select academic_session_id from users where email=?",Long.class,email)).isEqualTo(empty.id());
            assertThat(jdbc.queryForObject("select count(*) from academic_sessions where id=?",Long.class,empty.id())).isEqualTo(1);
        } finally { release.countDown();pool.shutdownNow(); }
    }
    @Test void failedClosureAuditRollsBackTheClosureAndActivePointer() {
        commitFixture();String suffix=UUID.randomUUID().toString().replace("-","");String function="session_audit_failure_"+suffix,trigger="session_audit_trigger_"+suffix;
        jdbc.execute("create function "+function+"() returns trigger language plpgsql as $$ begin if NEW.action='CLOSED' then raise exception 'test audit failure'; end if; return NEW; end $$");
        jdbc.execute("create trigger "+trigger+" before insert on academic_session_events for each row execute function "+function+"()");
        try {
            assertThatThrownBy(()->sessions.close(actor,original.id(),confirmed(original))).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(sessions.activeId()).isEqualTo(original.id());
            assertThat(jdbc.queryForObject("select closed_at from academic_sessions where id=?",Object.class,original.id())).isNull();
            assertThat(sessions.history(original.id())).extracting(AcademicSessionService.Event::action).containsExactly("CREATED","ACTIVATED");
        } finally { jdbc.execute("drop trigger "+trigger+" on academic_session_events");jdbc.execute("drop function "+function+"()"); }
    }
    @Test void restrictiveForeignKeyProtectsFutureDependentTablesAndReturnsAConflict() {
        var empty=create("FUTURE-"+UUID.randomUUID(),year+1,false);commitFixture();String table="session_dependency_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("create table "+table+"(session_id bigint references academic_sessions(id))");jdbc.update("insert into "+table+" values(?)",empty.id());
        try {
            conflict(()->sessions.delete(actor,empty.id(),confirmed(empty)));
            assertThat(jdbc.queryForObject("select count(*) from academic_sessions where id=?",Long.class,empty.id())).isEqualTo(1);
            assertThat(sessions.history(empty.id())).extracting(AcademicSessionService.Event::action).containsExactly("CREATED");
        } finally { jdbc.execute("drop table "+table); }
    }
}
