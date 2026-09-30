package com.learnplatform.service.gamification;

import com.learnplatform.dto.GamificationHeatmapDayVO;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class GamificationHeatmapService {
  private final JdbcTemplate jdbc;
  private final GamificationRewardService rewards;

  public GamificationHeatmapService(JdbcTemplate jdbc, GamificationRewardService rewards) {
    this.jdbc = jdbc;
    this.rewards = rewards;
  }

  public List<GamificationHeatmapDayVO> days(Long userId, LocalDate from, LocalDate to) {
    rewards.summary(userId);
    return jdbc.query(
        "SELECT event_date, COUNT(*) answered_count, COALESCE(SUM(xp),0) earned_xp"
            + " FROM gamification_xp_ledger WHERE user_id=? AND event_date BETWEEN ? AND ?"
            + " AND xp>0 GROUP BY event_date ORDER BY event_date",
        (rs, rowNum) -> {
          GamificationHeatmapDayVO day = new GamificationHeatmapDayVO();
          day.setDate(rs.getDate(1).toLocalDate());
          day.setAnsweredCount(rs.getInt(2));
          day.setEarnedXp(rs.getInt(3));
          return day;
        }, userId, from, to);
  }
}
