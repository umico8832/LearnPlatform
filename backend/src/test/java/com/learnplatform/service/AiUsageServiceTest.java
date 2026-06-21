package com.learnplatform.service;

import com.learnplatform.dto.AiUsageOverviewVO;
import com.learnplatform.entity.AiCallLog;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.AiCallLogMapper;
import com.learnplatform.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AiUsageService 单元测试
 * Phase 19：AI 调用分析与成本控制面板
 */
@ExtendWith(MockitoExtension.class)
class AiUsageServiceTest {

    @Mock
    private AiCallLogMapper aiCallLogMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AiUsageService aiUsageService;

    private List<AiCallLog> sampleLogs;

    @BeforeEach
    void setUp() {
        sampleLogs = new ArrayList<>();

        // 成功日志
        AiCallLog successLog = new AiCallLog();
        successLog.setId(1L);
        successLog.setUserId(1L);
        successLog.setFunctionType("explanation");
        successLog.setModel("gpt-4o");
        successLog.setTokensUsed(1500);
        successLog.setCostUsd(new BigDecimal("0.00150000"));
        successLog.setStatus(1);
        successLog.setDuration(2000);
        successLog.setCreateTime(LocalDateTime.now().minusHours(1));
        sampleLogs.add(successLog);

        // 流式日志
        AiCallLog streamLog = new AiCallLog();
        streamLog.setId(2L);
        streamLog.setUserId(1L);
        streamLog.setFunctionType("variant_question");
        streamLog.setModel("gpt-4o");
        streamLog.setTokensUsed(800);
        streamLog.setCostUsd(new BigDecimal("0.00080000"));
        streamLog.setStatus(1);
        streamLog.setDuration(3500);
        streamLog.setCreateTime(LocalDateTime.now().minusDays(1));
        sampleLogs.add(streamLog);

        // 失败日志
        AiCallLog failLog = new AiCallLog();
        failLog.setId(3L);
        failLog.setUserId(2L);
        failLog.setFunctionType("review_suggestion");
        failLog.setModel("gpt-3.5-turbo");
        failLog.setTokensUsed(null);
        failLog.setStatus(0);
        failLog.setErrorMessage("API timeout");
        failLog.setDuration(30000);
        failLog.setCreateTime(LocalDateTime.now().minusHours(5));
        sampleLogs.add(failLog);

        // 复习建议成功日志
        AiCallLog reviewLog = new AiCallLog();
        reviewLog.setId(4L);
        reviewLog.setUserId(2L);
        reviewLog.setFunctionType("review_suggestion");
        reviewLog.setModel("gpt-4o");
        reviewLog.setTokensUsed(2000);
        reviewLog.setCostUsd(new BigDecimal("0.00200000"));
        reviewLog.setStatus(1);
        reviewLog.setDuration(1800);
        reviewLog.setCreateTime(LocalDateTime.now().minusDays(2));
        sampleLogs.add(reviewLog);
    }

    @Nested
    @DisplayName("getOverview 测试")
    class GetOverviewTests {

        @Test
        @DisplayName("正常返回总览数据")
        void shouldReturnOverviewWithData() {
            when(aiCallLogMapper.selectList(any())).thenReturn(sampleLogs);
            User user1 = new User();
            user1.setId(1L);
            user1.setUsername("alice");
            User user2 = new User();
            user2.setId(2L);
            user2.setUsername("bob");
            when(userMapper.selectBatchIds(any())).thenReturn(List.of(user1, user2));

            AiUsageOverviewVO vo = aiUsageService.getOverview(30);

            assertNotNull(vo);
            assertEquals(4L, vo.getTotalCalls());
            assertEquals(3L, vo.getSuccessCalls());
            assertEquals(1L, vo.getFailedCalls());
            assertEquals(75.0, vo.getSuccessRate());
            assertEquals(4300L, vo.getTotalTokens());
            assertEquals(new BigDecimal("0.00430000"), vo.getTotalCostUsd());
            assertEquals(new BigDecimal("0.00150000"), vo.getTodayCostUsd());
            assertNotNull(vo.getFunctionStats());
            assertNotNull(vo.getModelStats());
            assertNotNull(vo.getDailyTrends());
            assertNotNull(vo.getTopUsers());
            assertNotNull(vo.getRecentFailures());
        }

        @Test
        @DisplayName("空数据时返回零值")
        void shouldReturnZeroValuesWhenNoData() {
            when(aiCallLogMapper.selectList(any())).thenReturn(Collections.emptyList());

            AiUsageOverviewVO vo = aiUsageService.getOverview(30);

            assertNotNull(vo);
            assertEquals(0L, vo.getTotalCalls());
            assertEquals(0L, vo.getSuccessCalls());
            assertEquals(0L, vo.getFailedCalls());
            assertEquals(0.0, vo.getSuccessRate());
            assertEquals(0L, vo.getTotalTokens());
            assertNull(vo.getTotalCostUsd());
            assertTrue(vo.getFunctionStats().isEmpty());
            assertTrue(vo.getModelStats().isEmpty());
            assertFalse(vo.getDailyTrends().isEmpty()); // 趋势图仍有 30 个日期
            assertTrue(vo.getTopUsers().isEmpty());
            assertTrue(vo.getRecentFailures().isEmpty());
        }

        @Test
        @DisplayName("功能分组统计正确")
        void shouldGroupByFunctionType() {
            when(aiCallLogMapper.selectList(any())).thenReturn(sampleLogs);
            when(userMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());

            AiUsageOverviewVO vo = aiUsageService.getOverview(30);

            List<AiUsageOverviewVO.FunctionStats> fs = vo.getFunctionStats();
            assertEquals(3, fs.size());
            // 按调用次数降序，review_suggestion 有 2 次
            assertEquals("review_suggestion", fs.get(0).getFunctionType());
            assertEquals(2L, fs.get(0).getCount());
            assertEquals(1L, fs.get(0).getSuccessCount());
            assertEquals(1L, fs.get(0).getFailedCount());
        }

        @Test
        @DisplayName("模型分组统计正确")
        void shouldGroupByModel() {
            when(aiCallLogMapper.selectList(any())).thenReturn(sampleLogs);
            when(userMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());

            AiUsageOverviewVO vo = aiUsageService.getOverview(30);

            List<AiUsageOverviewVO.ModelStats> ms = vo.getModelStats();
            assertEquals(2, ms.size());
            // gpt-4o 有 3 次
            assertEquals("gpt-4o", ms.get(0).getModel());
            assertEquals(3L, ms.get(0).getCount());
        }

        @Test
        @DisplayName("Top 用户排序正确")
        void shouldReturnTopUsersSorted() {
            when(aiCallLogMapper.selectList(any())).thenReturn(sampleLogs);
            User user1 = new User();
            user1.setId(1L);
            user1.setUsername("alice");
            User user2 = new User();
            user2.setId(2L);
            user2.setUsername("bob");
            when(userMapper.selectBatchIds(any())).thenReturn(List.of(user1, user2));

            AiUsageOverviewVO vo = aiUsageService.getOverview(30);

            List<AiUsageOverviewVO.TopUser> topUsers = vo.getTopUsers();
            assertEquals(2, topUsers.size());
            // user1 有 2 次调用
            assertEquals("alice", topUsers.get(0).getUsername());
            assertEquals(2L, topUsers.get(0).getCallCount());
        }

        @Test
        @DisplayName("最近失败调用只包含失败记录")
        void shouldOnlyIncludeFailures() {
            when(aiCallLogMapper.selectList(any())).thenReturn(sampleLogs);
            when(userMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());

            AiUsageOverviewVO vo = aiUsageService.getOverview(30);

            List<AiUsageOverviewVO.RecentFailure> failures = vo.getRecentFailures();
            assertEquals(1, failures.size());
            assertEquals("review_suggestion", failures.get(0).getFunctionType());
            assertEquals("API timeout", failures.get(0).getErrorMessage());
        }

        @Test
        @DisplayName("days 参数为 null 时默认 30 天")
        void shouldDefaultTo30Days() {
            when(aiCallLogMapper.selectList(any())).thenReturn(Collections.emptyList());

            AiUsageOverviewVO vo = aiUsageService.getOverview(null);

            assertNotNull(vo);
            assertEquals(30, vo.getDailyTrends().size());
        }

        @Test
        @DisplayName("days 参数为 0 时默认 30 天")
        void shouldDefaultTo30DaysWhenZero() {
            when(aiCallLogMapper.selectList(any())).thenReturn(Collections.emptyList());

            AiUsageOverviewVO vo = aiUsageService.getOverview(0);

            assertNotNull(vo);
            assertEquals(30, vo.getDailyTrends().size());
        }

        @Test
        @DisplayName("days 为 7 时趋势图有 7 个数据点")
        void shouldReturn7DayTrends() {
            when(aiCallLogMapper.selectList(any())).thenReturn(Collections.emptyList());

            AiUsageOverviewVO vo = aiUsageService.getOverview(7);

            assertEquals(7, vo.getDailyTrends().size());
        }

        @Test
        @DisplayName("Top 用户限制最多 10 人")
        void shouldLimitTopUsersTo10() {
            List<AiCallLog> manyUsersLogs = new ArrayList<>();
            for (long i = 1; i <= 15; i++) {
                AiCallLog log = new AiCallLog();
                log.setId(i);
                log.setUserId(i);
                log.setFunctionType("explanation");
                log.setTokensUsed(100);
                log.setStatus(1);
                log.setDuration(1000);
                log.setCreateTime(LocalDateTime.now());
                manyUsersLogs.add(log);
            }
            when(aiCallLogMapper.selectList(any())).thenReturn(manyUsersLogs);
            List<User> users = new ArrayList<>();
            for (long i = 1; i <= 15; i++) {
                User u = new User();
                u.setId(i);
                u.setUsername("user" + i);
                users.add(u);
            }
            when(userMapper.selectBatchIds(any())).thenReturn(users);

            AiUsageOverviewVO vo = aiUsageService.getOverview(30);

            assertTrue(vo.getTopUsers().size() <= 10);
        }

        @Test
        @DisplayName("平均耗时计算正确（排除 null duration）")
        void shouldCalculateAvgDurationExcludingNulls() {
            AiCallLog nullDurationLog = new AiCallLog();
            nullDurationLog.setId(5L);
            nullDurationLog.setUserId(1L);
            nullDurationLog.setFunctionType("explanation");
            nullDurationLog.setStatus(1);
            nullDurationLog.setDuration(null);
            nullDurationLog.setCreateTime(LocalDateTime.now());

            List<AiCallLog> logs = new ArrayList<>(sampleLogs);
            logs.add(nullDurationLog);
            when(aiCallLogMapper.selectList(any())).thenReturn(logs);
            when(userMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());

            AiUsageOverviewVO vo = aiUsageService.getOverview(30);

            // 4 个有效 duration: 2000, 3500, 30000, 1800 => avg = 9325.0
            assertTrue(vo.getAvgDuration() > 0);
        }
    }
}
