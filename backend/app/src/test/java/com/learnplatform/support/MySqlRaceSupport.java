package com.learnplatform.support;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Coordinates real mapper statements without replacing database operations or service beans. */
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class MySqlRaceSupport implements Interceptor {
    private final ThreadLocal<StatementObserver> observer = new ThreadLocal<>();
    private final JdbcTemplate administration;

    public MySqlRaceSupport(Environment environment) {
        administration = new JdbcTemplate(new DriverManagerDataSource(
                environment.getRequiredProperty("spring.datasource.url"),
                environment.getRequiredProperty("spring.flyway.user"),
                environment.getRequiredProperty("spring.flyway.password")));
    }

    public <T> T observe(StatementObserver listener, Callable<T> operation) throws Exception {
        observer.set(listener);
        try {
            return operation.call();
        } finally {
            observer.remove();
        }
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementObserver listener = observer.get();
        if (listener == null) {
            return invocation.proceed();
        }
        String id = ((MappedStatement) invocation.getArgs()[0]).getId();
        Connection connection = ((Executor) invocation.getTarget()).getTransaction().getConnection();
        listener.before(id, connection);
        Object result = invocation.proceed();
        listener.after(id, connection);
        return result;
    }

    public void awaitDatabaseLock(long blockingConnection, long requestingConnection) {
        try {
            await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(20)).until(() ->
                    administration.queryForObject("""
                            SELECT COUNT(*) FROM (
                                SELECT 1 FROM sys.innodb_lock_waits
                                WHERE blocking_pid = ? AND waiting_pid = ?
                                UNION ALL
                                SELECT 1 FROM performance_schema.data_lock_waits w
                                JOIN performance_schema.threads b ON b.THREAD_ID = w.BLOCKING_THREAD_ID
                                JOIN performance_schema.threads r ON r.THREAD_ID = w.REQUESTING_THREAD_ID
                                WHERE b.PROCESSLIST_ID = ? AND r.PROCESSLIST_ID = ?
                                UNION ALL
                                SELECT 1 FROM performance_schema.data_lock_waits w
                                JOIN information_schema.innodb_trx b
                                  ON b.trx_id = w.BLOCKING_ENGINE_TRANSACTION_ID
                                JOIN information_schema.innodb_trx r
                                  ON r.trx_id = w.REQUESTING_ENGINE_TRANSACTION_ID
                                WHERE b.trx_mysql_thread_id = ? AND r.trx_mysql_thread_id = ?
                            ) observed_locks
                            """, Integer.class, blockingConnection, requestingConnection,
                            blockingConnection, requestingConnection,
                            blockingConnection, requestingConnection) > 0);
        } catch (ConditionTimeoutException exception) {
            throw new AssertionError("Expected MySQL lock wait from connection " + requestingConnection
                    + " behind " + blockingConnection + "; transactions="
                    + administration.queryForList("""
                            SELECT trx_mysql_thread_id, trx_state, trx_requested_lock_id, trx_query
                            FROM information_schema.innodb_trx
                            """) + "; waits=" + administration.queryForList("""
                            SELECT requesting_thread_id, blocking_thread_id,
                                   requesting_engine_transaction_id, blocking_engine_transaction_id
                            FROM performance_schema.data_lock_waits
                            """), exception);
        }
    }

    public static long connectionId(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT CONNECTION_ID()")) {
            result.next();
            return result.getLong(1);
        }
    }

    public static void awaitLatch(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Timed out waiting for the coordinated SQL statement");
    }

    public interface StatementObserver {
        default void before(String statement, Connection connection) throws Exception { }

        default void after(String statement, Connection connection) throws Exception { }
    }
}
