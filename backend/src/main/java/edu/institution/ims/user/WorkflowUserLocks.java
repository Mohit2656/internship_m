package edu.institution.ims.user;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Serialize new workflow changes without blocking existing notification foreign-key checks. */
public final class WorkflowUserLocks {
    private WorkflowUserLocks() {}

    public static boolean student(JdbcTemplate jdbc, Long id, boolean requireActive) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("A workflow student lock requires a transaction");
        // PostgreSQL KEY SHARE (used when inserting an audit/notification FK) is compatible
        // with NO KEY UPDATE. FOR UPDATE would deadlock an existing monthly review that
        // already owns the assignment while this workflow waits for that assignment.
        // H2 has no separate key-lock modes; its ordinary update lock is the test fallback.
        String lock = jdbc.execute((ConnectionCallback<String>) connection ->
                "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())
                        ? " for no key update" : " for update");
        return !jdbc.queryForList("select id from users where id=? and role='STUDENT'"
                + (requireActive ? " and active=true" : "") + lock, Long.class, id).isEmpty();
    }
}
