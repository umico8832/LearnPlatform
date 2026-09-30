package com.learnplatform.service;

import com.learnplatform.dto.GamificationAchievementVO;
import com.learnplatform.dto.GamificationSummaryVO;
import com.learnplatform.service.gamification.GamificationRewardService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamSubmissionRewardServiceTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private GamificationRewardService gamificationRewardService;

    @Test
    void aggregatesOnlyLedgerFactsLinkedToTheRequestedExamAndUser() {
        GamificationAchievementVO achievement = new GamificationAchievementVO();
        achievement.setCode("FIRST_ANSWER");
        GamificationSummaryVO summary = new GamificationSummaryVO();
        summary.setTotalXp(32);
        when(jdbc.queryForList(contains("FROM gamification_xp_ledger ledger"), eq(7L), eq(44L), eq(7L)))
                .thenReturn(List.of(Map.of("id", 90L, "event_id", 501L, "xp", 10, "total_xp_before", 95L, "total_xp_after", 105L),
                        Map.of("id", 91L, "event_id", 502L, "xp", 2, "total_xp_before", 105L, "total_xp_after", 107L)));
        when(jdbc.queryForList(contains("FROM gamification_achievement"), any(Object[].class)))
                .thenReturn(List.of(Map.of("achievement_code", "FIRST_ANSWER")));
        when(gamificationRewardService.achievements(7L)).thenReturn(List.of(achievement));
        when(gamificationRewardService.summary(7L)).thenReturn(summary);

        var reward = new ExamSubmissionRewardService(jdbc, gamificationRewardService).forExam(44L, 7L);

        assertEquals(12, reward.getAwardedXp());
        assertEquals(2, reward.getEventCount());
        assertEquals(1, reward.getLevelBefore());
        assertEquals(2, reward.getLevelAfter());
        assertEquals(List.of("FIRST_ANSWER"), reward.getNewAchievements().stream()
                .map(GamificationAchievementVO::getCode).toList());
        assertEquals(summary, reward.getSummary());
        verify(jdbc).queryForList(contains("learning_event.source_record_id"), eq(7L), eq(44L), eq(7L));
    }

    @Test
    void returnsNullWhenTheExamHasNoRewardableLedgerFacts() {
        when(jdbc.queryForList(any(String.class), eq(7L), eq(44L), eq(7L))).thenReturn(List.of());

        assertNull(new ExamSubmissionRewardService(jdbc, gamificationRewardService).forExam(44L, 7L));
    }
}
