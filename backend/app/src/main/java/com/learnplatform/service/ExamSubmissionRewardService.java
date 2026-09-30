package com.learnplatform.service;

import com.learnplatform.dto.GamificationSubmissionRewardVO;
import com.learnplatform.service.gamification.GamificationRewardService;
import com.learnplatform.service.gamification.GamificationRules;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Locates the reward ledger entries created by a completed exam submission. */
@Service
public class ExamSubmissionRewardService {
    private static final String EXAM_LEDGER_SQL = """
            SELECT ledger.id, ledger.event_id, ledger.xp, ledger.total_xp_before, ledger.total_xp_after
            FROM gamification_xp_ledger ledger
            JOIN course_learning_event learning_event
              ON learning_event.id = ledger.event_id AND learning_event.user_id = ledger.user_id
            JOIN exam_answer answer ON answer.id = learning_event.source_record_id
            JOIN exam_record record ON record.id = answer.exam_record_id
            WHERE ledger.user_id = ? AND record.id = ? AND record.user_id = ?
              AND learning_event.event_type = 'EXAM_ANSWERED' AND learning_event.event_source = 'EXAM'
              AND learning_event.subject_type = 'QUESTION'
              AND learning_event.subject_id = answer.question_id
            ORDER BY ledger.id
            """;

    private final JdbcTemplate jdbc;
    private final GamificationRewardService gamificationRewardService;

    public ExamSubmissionRewardService(JdbcTemplate jdbc,
                                       GamificationRewardService gamificationRewardService) {
        this.jdbc = jdbc;
        this.gamificationRewardService = gamificationRewardService;
    }

    public GamificationSubmissionRewardVO forExam(Long examRecordId, Long userId) {
        List<Map<String, Object>> entries = jdbc.queryForList(EXAM_LEDGER_SQL, userId, examRecordId, userId);
        if (entries.isEmpty()) {
            return null;
        }
        long totalBefore = number(entries.getFirst().get("total_xp_before"));
        long totalAfter = number(entries.getLast().get("total_xp_after"));
        int awardedXp = entries.stream().mapToInt(entry -> (int) number(entry.get("xp"))).sum();
        Set<Long> eventIds = entries.stream().map(entry -> number(entry.get("event_id")))
                .collect(java.util.stream.Collectors.toSet());
        List<Map<String, Object>> unlockedRows = jdbc.queryForList("""
                SELECT achievement_code FROM gamification_achievement
                WHERE user_id=? AND trigger_event_id IN (%s)
                """.formatted(placeholders(eventIds.size())), eventIdsWithUser(userId, eventIds));
        Set<String> newCodes = new HashSet<>();
        for (Map<String, Object> unlockedRow : unlockedRows) {
            newCodes.add((String) unlockedRow.get("achievement_code"));
        }

        GamificationSubmissionRewardVO reward = new GamificationSubmissionRewardVO();
        reward.setAwardedXp(awardedXp);
        reward.setEventCount(entries.size());
        reward.setLevelBefore(GamificationRules.levelFor(totalBefore));
        reward.setLevelAfter(GamificationRules.levelFor(totalAfter));
        reward.setLeveledUp(reward.getLevelAfter() > reward.getLevelBefore());
        reward.setNewAchievements(gamificationRewardService.achievements(userId).stream()
                .filter(achievement -> newCodes.contains(achievement.getCode()))
                .toList());
        reward.setSummary(gamificationRewardService.summary(userId));
        return reward;
    }

    private Object[] eventIdsWithUser(Long userId, Set<Long> eventIds) {
        Object[] parameters = new Object[eventIds.size() + 1];
        parameters[0] = userId;
        int index = 1;
        for (Long eventId : eventIds) {
            parameters[index++] = eventId;
        }
        return parameters;
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
