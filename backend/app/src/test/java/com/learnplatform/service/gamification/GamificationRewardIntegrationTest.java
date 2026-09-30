package com.learnplatform.service.gamification;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.entity.CourseLearningEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration") @SpringBootTest @ActiveProfiles("integration") @Transactional
class GamificationRewardIntegrationTest extends IntegrationTestBase {
 @Autowired GamificationRewardService rewards; @Autowired JdbcTemplate jdbc;
 @Test void eventReplayAndSameQuestionDayCannotFarmXp() {
  rewards.reward(event(990001L, 99L, 101L, LocalDateTime.of(2026, 9, 1, 8, 0)), true);
  rewards.reward(event(990001L, 99L, 101L, LocalDateTime.of(2026, 9, 1, 8, 0)), true);
  rewards.reward(event(990002L, 99L, 101L, LocalDateTime.of(2026, 9, 1, 9, 0)), true);
  assertEquals(10L, jdbc.queryForObject("SELECT total_xp FROM gamification_user_profile WHERE user_id=99", Long.class));
  assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM gamification_xp_ledger WHERE user_id=99", Integer.class));
 }
 private CourseLearningEvent event(long id,long user,long question,LocalDateTime at) { CourseLearningEvent e=new CourseLearningEvent(); e.setId(id);e.setUserId(user);e.setCourseId(1L);e.setSubjectType("QUESTION");e.setSubjectId(question);e.setEventType("PRACTICE_ANSWERED");e.setOccurredTime(at);return e; }
}
