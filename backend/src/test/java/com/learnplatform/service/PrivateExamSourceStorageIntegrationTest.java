package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.entity.UserExamSource;
import com.learnplatform.mapper.UserExamSourceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
@SpringBootTest(properties = "private-exam.source-storage-limit-bytes=12")
@ActiveProfiles("integration")
class PrivateExamSourceStorageIntegrationTest extends IntegrationTestBase {
    private static final String USERNAME = "source-quota-test";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserExamSourceMapper sourceMapper;
    @Autowired private PrivateExamSourceStorageService storageService;
    @Autowired private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM user_exam_source WHERE owner_user_id IN "
                + "(SELECT id FROM user WHERE username = ?)", USERNAME);
        jdbcTemplate.update("DELETE FROM user WHERE username = ?", USERNAME);
    }

    @Test
    void ownerLockPreventsConcurrentUploadsFromExceedingAggregateQuota() throws Exception {
        jdbcTemplate.update("INSERT INTO user (username,password,role,status,deleted) VALUES (?,'test','USER',1,0)",
                USERNAME);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, USERNAME);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<String>> results = List.of(
                    executor.submit(() -> storeEightBytes(userId, "first.pdf", ready, start)),
                    executor.submit(() -> storeEightBytes(userId, "second.pdf", ready, start)));
            ready.await();
            start.countDown();

            List<String> outcomes = List.of(results.get(0).get(), results.get(1).get());
            assertEquals(1, outcomes.stream().filter("OK"::equals).count());
            assertEquals(1, outcomes.stream()
                    .filter("私有试卷原文件存储空间不足"::equals).count());
        }

        assertEquals(8L, storageService.getUsage(userId).getUsedBytes());
        assertEquals(1L, storageService.getUsage(userId).getFileCount());
        jdbcTemplate.update("DELETE FROM user_exam_source WHERE owner_user_id = ?", userId);
        assertEquals(0L, storageService.getUsage(userId).getUsedBytes());
    }

    private String storeEightBytes(Long userId, String filename,
                                   CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                UserExamSource source = new UserExamSource();
                source.setOwnerUserId(userId);
                source.setSourceName(filename);
                source.setSourceFormat("PDF");
                source.setContentSha256("0".repeat(64));
                source.setOriginalContent("test");
                storageService.attachFileWithinQuota(source, userId, "12345678".getBytes(), "application/pdf");
                sourceMapper.insert(source);
            });
            return "OK";
        } catch (BusinessException exception) {
            return exception.getMessage();
        }
    }
}
