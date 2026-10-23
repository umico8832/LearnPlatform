package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.KnowledgePointVO;
import com.learnplatform.support.MySqlRaceSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.core.NestedExceptionUtils;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.learnplatform.support.MySqlRaceSupport.awaitLatch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parent rows remain authoritative while dependent rows are created or deleted concurrently. */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Import(ContentReferenceLockingIntegrationTest.RaceConfiguration.class)
class ContentReferenceLockingIntegrationTest extends IntegrationTestBase {

    @Autowired private CourseService courses;
    @Autowired private KnowledgePointService knowledgePoints;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MySqlRaceSupport race;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private ContentReferenceLockService locks;

    private Long courseId;
    private final Set<Long> ownedCourses = new LinkedHashSet<>();

    @AfterEach
    void cleanUp() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS content_reference_lock_test_guard "
                + "(id BIGINT PRIMARY KEY, version_no INT NOT NULL) ENGINE=InnoDB");
        jdbc.update("DELETE FROM content_reference_lock_test_guard");
        for (Long ownedCourse : ownedCourses) {
            jdbc.update("DELETE FROM knowledge_point WHERE course_id = ?", ownedCourse);
            jdbc.update("DELETE FROM course WHERE id = ?", ownedCourse);
        }
        ownedCourses.clear();
    }

    @Test
    void creatingKnowledgePointCannotPassValidationThenReferenceDeletedCourse() throws Exception {
        courseId = createCourse();
        CountDownLatch validatedBeforeInsert = new CountDownLatch(1);
        CountDownLatch allowInsert = new CountDownLatch(1);
        CountDownLatch deleteRequested = new CountDownLatch(1);
        AtomicLong creatorConnection = new AtomicLong(), deleteConnection = new AtomicLong();
        TransactionTemplate creatorTransaction = new TransactionTemplate(transactions);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<KnowledgePointVO> creator = executor.submit(() -> race.observe(
                    pauseImmediatelyBeforeKnowledgePointInsert(validatedBeforeInsert, allowInsert, creatorConnection),
                    () -> creatorTransaction.execute(status -> knowledgePoints.createKnowledgePoint(
                            courseId, 0L, "并发引用保护知识点", "test", 0))));
            try {
                awaitLatch(validatedBeforeInsert);
                Future<?> deleter = executor.submit(() -> race.observe(new MySqlRaceSupport.StatementObserver() {
                    @Override public void before(String statement, Connection connection) throws Exception {
                        if (statement.endsWith("CourseMapper.selectActiveForUpdate")) {
                            deleteConnection.set(MySqlRaceSupport.connectionId(connection)); deleteRequested.countDown();
                        }
                    }
                }, () -> { courses.deleteCourse(courseId); return null; }));
                awaitLatch(deleteRequested);
                race.awaitTransactionLockWait(deleteConnection.get());

                allowInsert.countDown();
                KnowledgePointVO created = creator.get(30, TimeUnit.SECONDS);
                assertEquals(courseId, created.getCourseId());
                ExecutionException deletion = assertThrows(ExecutionException.class,
                        () -> deleter.get(30, TimeUnit.SECONDS));
                assertInstanceOf(BusinessException.class, deletion.getCause());

                assertEquals(0, count("SELECT deleted FROM course WHERE id = ?", courseId));
                assertEquals(1, count("SELECT COUNT(*) FROM knowledge_point WHERE course_id = ? AND deleted = 0",
                        courseId));
                assertTrue(creatorConnection.get() != deleteConnection.get());
            } finally {
                allowInsert.countDown();
            }
        }
    }

    @Test
    void deletingCourseFirstMakesConcurrentReferenceWaitThenReject() throws Exception {
        courseId = createCourse();
        CountDownLatch deleteLocked = new CountDownLatch(1), releaseDelete = new CountDownLatch(1), createRequested = new CountDownLatch(1);
        AtomicLong blocker = new AtomicLong(), requester = new AtomicLong();
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> deleter = executor.submit(() -> race.observe(new MySqlRaceSupport.StatementObserver() {
                @Override public void after(String statement, Connection connection) throws Exception {
                    if (statement.endsWith("CourseMapper.selectActiveForUpdate")) {
                        blocker.set(MySqlRaceSupport.connectionId(connection)); deleteLocked.countDown(); awaitLatch(releaseDelete);
                    }
                }
            }, () -> { courses.deleteCourse(courseId); return null; }));
            try {
                awaitLatch(deleteLocked);
                Future<?> creator = executor.submit(() -> race.observe(new MySqlRaceSupport.StatementObserver() {
                    @Override public void before(String statement, Connection connection) throws Exception {
                        if (statement.endsWith("CourseMapper.selectActiveForShare")) {
                            requester.set(MySqlRaceSupport.connectionId(connection)); createRequested.countDown();
                        }
                    }
                }, () -> knowledgePoints.createKnowledgePoint(courseId, 0L, "被删除课程的子点", "test", 0)));
                awaitLatch(createRequested);
                race.awaitTransactionLockWait(requester.get());
                releaseDelete.countDown();
                deleter.get(30, TimeUnit.SECONDS);
                ExecutionException rejected = assertThrows(ExecutionException.class, () -> creator.get(30, TimeUnit.SECONDS));
                assertInstanceOf(BusinessException.class, rejected.getCause());
                assertTrue(blocker.get() != requester.get());
            } finally { releaseDelete.countDown(); }
        }
        assertEquals(0, count("SELECT COUNT(*) FROM course WHERE id=? AND deleted=0", courseId));
        assertEquals(0, count("SELECT COUNT(*) FROM knowledge_point WHERE course_id=? AND deleted=0", courseId));
    }

    @Test
    void rollbackOfDeletingCourseReleasesWaitingReferenceCreation() throws Exception {
        courseId = createCourse();
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1), requested = new CountDownLatch(1);
        AtomicLong requester = new AtomicLong();
        TransactionTemplate rollback = new TransactionTemplate(transactions);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> deleter = executor.submit(() -> race.observe(pauseAfterCourseDeleteLock(locked, release), () ->
                    rollback.execute(status -> { courses.deleteCourse(courseId); status.setRollbackOnly(); return null; })));
            try {
                awaitLatch(locked);
                Future<KnowledgePointVO> creator = executor.submit(() -> race.observe(waitingForCourseShare(requested, requester),
                        () -> knowledgePoints.createKnowledgePoint(courseId, 0L, "回滚后允许创建", "test", 0)));
                awaitLatch(requested);
                race.awaitTransactionLockWait(requester.get());
                release.countDown();
                deleter.get(30, TimeUnit.SECONDS);
                assertEquals(courseId, creator.get(30, TimeUnit.SECONDS).getCourseId());
            } finally { release.countDown(); }
        }
        assertEquals(1, count("SELECT COUNT(*) FROM course WHERE id=? AND deleted=0", courseId));
        assertEquals(1, count("SELECT COUNT(*) FROM knowledge_point WHERE course_id=? AND deleted=0", courseId));
    }

    @Test
    void repeatableReadDeleteUsesFreshLockingReadAfterConcurrentReferenceCommit() throws Exception {
        courseId = createCourse();
        TransactionTemplate rr = new TransactionTemplate(transactions);
        rr.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        CountDownLatch snapshot = new CountDownLatch(1), created = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> delete = executor.submit(() -> assertThrows(BusinessException.class, () -> rr.execute(status -> {
                assertEquals(1, count("SELECT COUNT(*) FROM course WHERE id=? AND deleted=0", courseId));
                snapshot.countDown();
                try { awaitLatch(created); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
                courses.deleteCourse(courseId);
                return null;
            })));
            awaitLatch(snapshot);
            knowledgePoints.createKnowledgePoint(courseId, 0L, "RR 后提交的引用", "test", 0);
            created.countDown();
            delete.get(30, TimeUnit.SECONDS);
        }
        assertEquals(1, count("SELECT COUNT(*) FROM course WHERE id=? AND deleted=0", courseId));
        assertEquals(1, count("SELECT COUNT(*) FROM knowledge_point WHERE course_id=? AND deleted=0", courseId));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void parentKnowledgePointDeletionCommitsOrRollsBackBeforeWaitingChildCreation(boolean rollback) throws Exception {
        courseId = createCourse();
        Long parentId = knowledgePoints.createKnowledgePoint(courseId, 0L, "并发父点", "test", 0).getId();
        CountDownLatch parentLocked = new CountDownLatch(1), releaseDelete = new CountDownLatch(1), childRequested = new CountDownLatch(1);
        AtomicLong requester = new AtomicLong();
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> deleter = executor.submit(() -> race.observe(new MySqlRaceSupport.StatementObserver() {
                @Override public void after(String statement, Connection connection) throws Exception {
                    if (statement.endsWith("KnowledgePointMapper.selectActiveForUpdate")) {
                        parentLocked.countDown(); awaitLatch(releaseDelete);
                    }
                }
            }, () -> new TransactionTemplate(transactions).execute(status -> {
                knowledgePoints.deleteKnowledgePoint(parentId);
                if (rollback) { status.setRollbackOnly(); }
                return null;
            })));
            try {
                awaitLatch(parentLocked);
                Future<?> creator = executor.submit(() -> race.observe(new MySqlRaceSupport.StatementObserver() {
                    @Override public void before(String statement, Connection connection) throws Exception {
                        if (statement.endsWith("KnowledgePointMapper.selectActiveForShare")) {
                            requester.set(MySqlRaceSupport.connectionId(connection)); childRequested.countDown();
                        }
                    }
                }, () -> knowledgePoints.createKnowledgePoint(courseId, parentId, "不能落下的子点", "test", 0)));
                awaitLatch(childRequested);
                race.awaitTransactionLockWait(requester.get());
                releaseDelete.countDown();
                deleter.get(30, TimeUnit.SECONDS);
                if (rollback) {
                    assertEquals(courseId, ((KnowledgePointVO) creator.get(30, TimeUnit.SECONDS)).getCourseId());
                } else {
                    assertInstanceOf(BusinessException.class,
                            assertThrows(ExecutionException.class, () -> creator.get(30, TimeUnit.SECONDS)).getCause());
                }
            } finally { releaseDelete.countDown(); }
        }
        assertEquals(rollback ? 1 : 0, count("SELECT COUNT(*) FROM knowledge_point WHERE id=? AND deleted=0", parentId));
        assertEquals(rollback ? 1 : 0, count("SELECT COUNT(*) FROM knowledge_point WHERE parent_id=? AND deleted=0", parentId));
    }

    @Test
    void childCreationHoldsParentShareUntilCommitThenParentDeletionRejectsTheNewReference() throws Exception {
        courseId = createCourse();
        long parent = knowledgePoints.createKnowledgePoint(courseId, 0L, "先建子点的父点", "test", 0).getId();
        var inserted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var requested = new CountDownLatch(1);
        var requester = new AtomicLong();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var creator = pool.submit(() -> race.observe(
                    pauseImmediatelyBeforeKnowledgePointInsert(inserted, release, new AtomicLong()),
                    () -> knowledgePoints.createKnowledgePoint(courseId, parent, "已建立的新子点", "test", 0)));
            try {
                awaitLatch(inserted);
                var deleter = pool.submit(() -> race.observe(new MySqlRaceSupport.StatementObserver() {
                    @Override public void before(String statement, Connection connection) throws Exception {
                        if (statement.endsWith("KnowledgePointMapper.selectActiveForUpdate")) {
                            requester.set(MySqlRaceSupport.connectionId(connection));
                            requested.countDown();
                        }
                    }
                }, () -> { knowledgePoints.deleteKnowledgePoint(parent); return null; }));
                awaitLatch(requested);
                race.awaitTransactionLockWait(requester.get());
                release.countDown();
                assertEquals(parent, creator.get(30, TimeUnit.SECONDS).getParentId());
                assertInstanceOf(BusinessException.class,
                        assertThrows(ExecutionException.class, () -> deleter.get(30, TimeUnit.SECONDS)).getCause());
            } finally { release.countDown(); }
        }
        assertEquals(1, count("SELECT COUNT(*) FROM knowledge_point WHERE id=? AND deleted=0", parent));
        assertEquals(1, count("SELECT COUNT(*) FROM knowledge_point WHERE parent_id=? AND deleted=0", parent));
    }

    @Test
    void rollbackOfCreatingKnowledgePointLetsWaitingCourseDeleteSucceedWithoutHalfRow() throws Exception {
        courseId = createCourse();
        CountDownLatch inserted = new CountDownLatch(1), releaseCreate = new CountDownLatch(1), deleteRequested = new CountDownLatch(1);
        AtomicLong requester = new AtomicLong();
        TransactionTemplate rollback = new TransactionTemplate(transactions);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> creator = executor.submit(() -> race.observe(
                    pauseImmediatelyBeforeKnowledgePointInsert(inserted, releaseCreate, new AtomicLong()), () ->
                    rollback.execute(status -> {
                        knowledgePoints.createKnowledgePoint(courseId, 0L, "回滚时不应残留", "test", 0);
                        status.setRollbackOnly();
                        return null;
                    })));
            try {
                awaitLatch(inserted);
                Future<?> deleter = executor.submit(() -> race.observe(new MySqlRaceSupport.StatementObserver() {
                    @Override public void before(String statement, Connection connection) throws Exception {
                        if (statement.endsWith("CourseMapper.selectActiveForUpdate")) {
                            requester.set(MySqlRaceSupport.connectionId(connection)); deleteRequested.countDown();
                        }
                    }
                }, () -> { courses.deleteCourse(courseId); return null; }));
                awaitLatch(deleteRequested);
                race.awaitTransactionLockWait(requester.get());
                releaseCreate.countDown();
                creator.get(30, TimeUnit.SECONDS);
                deleter.get(30, TimeUnit.SECONDS);
            } finally { releaseCreate.countDown(); }
        }
        assertEquals(0, count("SELECT COUNT(*) FROM course WHERE id=? AND deleted=0", courseId));
        assertEquals(0, count("SELECT COUNT(*) FROM knowledge_point WHERE course_id=?", courseId));
    }

    @Test
    void mysqlDeadlockRollsBackWholeKnowledgePointWriteAndRetryCreatesOnlyCompleteRows() throws Exception {
        courseId = createCourse();
        jdbc.execute("CREATE TABLE IF NOT EXISTS content_reference_lock_test_guard "
                + "(id BIGINT PRIMARY KEY, version_no INT NOT NULL) ENGINE=InnoDB");
        jdbc.update("INSERT INTO content_reference_lock_test_guard(id,version_no) VALUES(1,0),(2,0)");
        CountDownLatch bothCreated = new CountDownLatch(2), firstGuardsLocked = new CountDownLatch(2);
        TransactionTemplate tx = new TransactionTemplate(transactions);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> left = executor.submit(() -> deadlockingKnowledgePointWrite(tx, "死锁左侧知识点", 1L, 2L,
                    bothCreated, firstGuardsLocked));
            Future<?> right = executor.submit(() -> deadlockingKnowledgePointWrite(tx, "死锁右侧知识点", 2L, 1L,
                    bothCreated, firstGuardsLocked));
            int failures = completedOrFailed(left) + completedOrFailed(right);
            assertEquals(1, failures, "MySQL 必须选择一个事务作为死锁牺牲者");
        }
        int leftRows = count("SELECT COUNT(*) FROM knowledge_point WHERE course_id=? AND name=? AND deleted=0",
                courseId, "死锁左侧知识点");
        int rightRows = count("SELECT COUNT(*) FROM knowledge_point WHERE course_id=? AND name=? AND deleted=0",
                courseId, "死锁右侧知识点");
        assertEquals(1, leftRows + rightRows, "死锁输家的业务写入必须整体回滚");
        if (leftRows == 0) knowledgePoints.createKnowledgePoint(courseId, 0L, "死锁左侧知识点", "test", 0);
        if (rightRows == 0) knowledgePoints.createKnowledgePoint(courseId, 0L, "死锁右侧知识点", "test", 0);
        assertEquals(2, count("SELECT COUNT(*) FROM knowledge_point WHERE course_id=? AND name IN (?,?) AND deleted=0",
                courseId, "死锁左侧知识点", "死锁右侧知识点"));
    }

    @Test
    void anotherCoursesWriterCompletesWhileTheFirstCourseHasAnUncommittedReference() throws Exception {
        long firstCourse = createCourse();
        long otherCourse = createCourse();
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> race.observe(
                    pauseImmediatelyBeforeKnowledgePointInsert(held, release, new AtomicLong()),
                    () -> new TransactionTemplate(transactions).execute(status ->
                            knowledgePoints.createKnowledgePoint(firstCourse, 0L, "并行课程一", "test", 0))));
            try {
                awaitLatch(held);
                var other = executor.submit(() -> knowledgePoints.createKnowledgePoint(otherCourse, 0L,
                        "并行课程二", "test", 0));
                assertEquals(otherCourse, other.get(5, TimeUnit.SECONDS).getCourseId());
                assertFalse(first.isDone());
            } finally { release.countDown(); }
            assertEquals(firstCourse, first.get(30, TimeUnit.SECONDS).getCourseId());
        }
        assertEquals(1, count("SELECT COUNT(*) FROM knowledge_point WHERE course_id=? AND deleted=0", firstCourse));
        assertEquals(1, count("SELECT COUNT(*) FROM knowledge_point WHERE course_id=? AND deleted=0", otherCourse));
    }

    @Test
    void reversedMultiParentInputsAcquireCoursesThenPointsInAscendingOrderAndBothWritersCommit() throws Exception {
        long one = createCourse();
        long two = createCourse();
        long pointOne = knowledgePoints.createKnowledgePoint(one, 0L, "多父一", "test", 0).getId();
        long pointTwo = knowledgePoints.createKnowledgePoint(two, 0L, "多父二", "test", 0).getId();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> multiParentWrite(List.of(two, one), List.of(pointTwo, pointOne),
                    one, two, pointOne, pointTwo, "甲"));
            var second = executor.submit(() -> multiParentWrite(List.of(one, two), List.of(pointOne, pointTwo),
                    one, two, pointOne, pointTwo, "乙"));
            var expected = List.of("course:" + one, "course:" + two, "point:" + pointOne, "point:" + pointTwo);
            assertEquals(expected, first.get(30, TimeUnit.SECONDS));
            assertEquals(expected, second.get(30, TimeUnit.SECONDS));
        }
        assertEquals(3, count("SELECT COUNT(*) FROM knowledge_point WHERE course_id=? AND deleted=0", one));
        assertEquals(3, count("SELECT COUNT(*) FROM knowledge_point WHERE course_id=? AND deleted=0", two));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM knowledge_point child LEFT JOIN knowledge_point parent ON parent.id=child.parent_id
                WHERE child.course_id IN (?,?) AND child.deleted=0 AND child.parent_id<>0
                  AND (parent.id IS NULL OR parent.deleted<>0)
                """, one, two));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM knowledge_point child JOIN course parent ON parent.id=child.course_id
                WHERE child.course_id IN (?,?) AND child.deleted=0 AND parent.deleted<>0
                """, one, two));
    }

    private List<String> multiParentWrite(List<Long> coursesToLock, List<Long> pointsToLock,
                                          long one, long two, long pointOne, long pointTwo, String suffix) throws Exception {
        var order = new ArrayList<String>();
        return race.observe(new MySqlRaceSupport.StatementObserver() {
            @Override public void before(String statement, Connection connection, Object parameters) {
                if (statement.endsWith("CourseMapper.selectActiveForShare")) {
                    order.add("course:" + ((Map<?, ?>) parameters).get("courseId"));
                } else if (statement.endsWith("KnowledgePointMapper.selectActiveForShare")) {
                    order.add("point:" + ((Map<?, ?>) parameters).get("knowledgePointId"));
                }
            }
        }, () -> new TransactionTemplate(transactions).execute(status -> {
            locks.lockForReference(coursesToLock, pointsToLock);
            var firstLocks = List.copyOf(order);
            knowledgePoints.createKnowledgePoint(one, pointOne, "多父子一" + suffix, "test", 0);
            knowledgePoints.createKnowledgePoint(two, pointTwo, "多父子二" + suffix, "test", 0);
            return firstLocks;
        }));
    }

    private Object deadlockingKnowledgePointWrite(TransactionTemplate tx, String name, long firstGuard, long secondGuard,
                                                   CountDownLatch bothCreated, CountDownLatch firstGuardsLocked) {
        return tx.execute(status -> {
            knowledgePoints.createKnowledgePoint(courseId, 0L, name, "test", 0);
            bothCreated.countDown();
            try { awaitLatch(bothCreated); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
            jdbc.update("UPDATE content_reference_lock_test_guard SET version_no=version_no+1 WHERE id=?", firstGuard);
            firstGuardsLocked.countDown();
            try { awaitLatch(firstGuardsLocked); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
            jdbc.update("UPDATE content_reference_lock_test_guard SET version_no=version_no+1 WHERE id=?", secondGuard);
            return null;
        });
    }

    private int completedOrFailed(Future<?> future) throws Exception {
        try { future.get(30, TimeUnit.SECONDS); return 0; }
        catch (ExecutionException expectedDeadlockLoser) {
            assertInstanceOf(PessimisticLockingFailureException.class, expectedDeadlockLoser.getCause());
            SQLException sql = assertInstanceOf(SQLException.class,
                    NestedExceptionUtils.getMostSpecificCause(expectedDeadlockLoser.getCause()));
            assertEquals(1213, sql.getErrorCode());
            return 1;
        }
    }

    private MySqlRaceSupport.StatementObserver pauseAfterCourseDeleteLock(
            CountDownLatch locked, CountDownLatch release) {
        return new MySqlRaceSupport.StatementObserver() {
            @Override public void after(String statement, Connection connection) throws Exception {
                if (statement.endsWith("CourseMapper.selectActiveForUpdate")) {
                    locked.countDown();
                    awaitLatch(release);
                }
            }
        };
    }

    private MySqlRaceSupport.StatementObserver waitingForCourseShare(
            CountDownLatch requested, AtomicLong requester) {
        return new MySqlRaceSupport.StatementObserver() {
            @Override public void before(String statement, Connection connection) throws Exception {
                if (statement.endsWith("CourseMapper.selectActiveForShare")) {
                    requester.set(MySqlRaceSupport.connectionId(connection));
                    requested.countDown();
                }
            }
        };
    }

    private MySqlRaceSupport.StatementObserver pauseImmediatelyBeforeKnowledgePointInsert(
            CountDownLatch validatedBeforeInsert, CountDownLatch allowInsert, AtomicLong connectionId) {
        return new MySqlRaceSupport.StatementObserver() {
            @Override
            public void before(String statement, Connection connection) throws Exception {
                if (statement.endsWith("KnowledgePointMapper.insert")) {
                    connectionId.set(MySqlRaceSupport.connectionId(connection));
                    validatedBeforeInsert.countDown();
                    awaitLatch(allowInsert);
                }
            }
        };
    }

    private long createCourse() {
        jdbc.update("INSERT INTO course (name,status,deleted) VALUES (?,1,0)", "D3 父锁竞态课程");
        Long id = jdbc.queryForObject("SELECT id FROM course WHERE name = ? ORDER BY id DESC LIMIT 1", Long.class,
                "D3 父锁竞态课程");
        assertFalse(id == null);
        ownedCourses.add(id);
        return id;
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RaceConfiguration {
        @Bean
        MySqlRaceSupport contentReferenceRaceSupport(Environment environment) {
            return new MySqlRaceSupport(environment);
        }
    }
}
