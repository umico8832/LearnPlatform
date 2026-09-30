package com.learnplatform.service.gamification;

import com.learnplatform.dto.GamificationAchievementVO;
import com.learnplatform.dto.GamificationRewardFeedback;
import com.learnplatform.dto.GamificationSummaryVO;
import com.learnplatform.entity.CourseLearningEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Incremental reward projection; first use replays server-graded facts in business-time order. */
@Service
public class GamificationRewardService {
  public static final ZoneId LEARNING_ZONE = ZoneId.of("Asia/Shanghai");
  /**
   * Course event {@code DATETIME} values were written from the application's existing no-zone JVM
   * clock. Read their fields in that same source clock before converting them to the learning day.
   */
  public static final ZoneId SOURCE_CLOCK_ZONE = ZoneId.systemDefault();
  private static final List<Definition> ACHIEVEMENTS =
      List.of(
          new Definition("FIRST_ANSWER", "启程一题", "完成首次真实作答", "answers", 1),
          new Definition("ANSWERS_10", "十题热身", "完成 10 道真实作答", "answers", 10),
          new Definition("ANSWERS_50", "稳步前行", "完成 50 道真实作答", "answers", 50),
          new Definition("ANSWERS_100", "百题积累", "完成 100 道真实作答", "answers", 100),
          new Definition("CORRECT_10", "初见锋芒", "答对 10 道题", "correct", 10),
          new Definition("CORRECT_50", "渐入佳境", "答对 50 道题", "correct", 50),
          new Definition("REVIEW_10", "复习成习", "完成 10 次复习作答", "review", 10),
          new Definition("STREAK_3", "三日成线", "连续学习 3 天", "streak", 3),
          new Definition("STREAK_7", "一周相伴", "连续学习 7 天", "streak", 7),
          new Definition("STREAK_30", "月度坚持", "连续学习 30 天", "streak", 30),
          new Definition("TUTOR_FIRST", "问有所获", "完成首次 Tutor 理解检查", "tutor", 1),
          new Definition("STAGE_FIRST", "阶段见证", "完成首次阶段测评作答", "stage", 1));
  private final JdbcTemplate jdbc;

  public GamificationRewardService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public GamificationRewardFeedback reward(CourseLearningEvent event, boolean correct) {
    if (event == null || event.getId() == null || !rewardable(event.getEventType())) {
      return null;
    }
    Map<Long, GamificationRewardFeedback> initial = initialize(event.getUserId());
    GamificationRewardFeedback result = initial.get(event.getId());
    if (result == null) {
      result = project(event, correct);
    }
    result.setSummary(summaryFromProfile(event.getUserId()));
    return result;
  }

  @Transactional
  public GamificationSummaryVO summary(Long userId) {
    initialize(userId);
    return summaryFromProfile(userId);
  }

  @Transactional
  public List<GamificationAchievementVO> achievements(Long userId) {
    initialize(userId);
    return achievementsFromProfile(userId);
  }

  @Transactional
  public GamificationSummaryVO updateDailyGoal(Long userId, int dailyGoal) {
    initialize(userId);
    jdbc.update(
        "UPDATE gamification_user_profile SET daily_goal=?,version=version+1 WHERE user_id=?",
        dailyGoal,
        userId);
    return summaryFromProfile(userId);
  }

  private Map<Long, GamificationRewardFeedback> initialize(Long userId) {
    jdbc.update(
        "INSERT INTO gamification_user_profile (user_id) VALUES (?)"
            + " ON DUPLICATE KEY UPDATE user_id=user_id",
        userId);
    if (Boolean.TRUE.equals(lockProfile(userId).get("backfill_complete"))) {
      return Map.of();
    }
    Map<Long, GamificationRewardFeedback> results = new LinkedHashMap<>();
    jdbc.query(
        "SELECT"
            + " id,user_id,course_id,event_type,event_source,subject_type,subject_id,source_record_id,"
            + "idempotency_key,event_version,payload_json,occurred_time,JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.isCorrect'))"
            + " correct_value FROM course_learning_event WHERE user_id=? AND event_type IN "
            + "('PRACTICE_ANSWERED','REVIEW_ANSWERED','EXAM_ANSWERED','STAGE_ASSESSMENT_ANSWERED','PAPER_LEARNING_ANSWERED','AI_VARIANT_ANSWERED','TUTOR_CHECK_ANSWERED')"
            + " AND JSON_TYPE(JSON_EXTRACT(payload_json,'$.isCorrect'))='BOOLEAN'"
            + " AND JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.isCorrect')) IN ('true','false')"
            + " ORDER BY occurred_time,id",
        rs -> {
          CourseLearningEvent event = event(rs);
          results.put(event.getId(), project(event, "true".equals(rs.getString("correct_value"))));
        },
        userId);
    Long cursor =
        jdbc.queryForObject(
            "SELECT COALESCE(MAX(id),0) FROM course_learning_event WHERE user_id=?",
            Long.class,
            userId);
    jdbc.update(
        "UPDATE gamification_user_profile SET"
            + " backfill_complete=TRUE,backfill_cursor=?,version=version+1 WHERE user_id=?",
        cursor,
        userId);
    return results;
  }

  private GamificationRewardFeedback project(CourseLearningEvent event, boolean correct) {
    Map<String, Object> before = lockProfile(event.getUserId());
    long xpBefore = number(before, "total_xp");
    LocalDate day = learningDate(event.getOccurredTime());
    boolean repeated =
        jdbc.queryForObject(
                "SELECT COUNT(*) FROM gamification_xp_ledger WHERE user_id=? AND course_id=? AND"
                    + " subject_type=? AND subject_id=? AND event_date=?",
                Long.class,
                event.getUserId(),
                event.getCourseId(),
                event.getSubjectType(),
                event.getSubjectId(),
                day)
            > 0;
    int xp = repeated ? 0 : GamificationRules.answerXp(correct, event.getEventType());
    String reason =
        repeated
            ? "DUPLICATE_QUESTION_SAME_DAY"
            : correct && "REVIEW_ANSWERED".equals(event.getEventType())
                ? "REVIEW_CORRECT_BONUS"
                : correct ? "CORRECT_ANSWER" : "INCORRECT_ANSWER";
    try {
      jdbc.update(
          "INSERT INTO gamification_xp_ledger"
              + " (user_id,event_id,course_id,subject_type,subject_id,event_type,event_date,xp,reward_reason,occurred_time,total_xp_before,total_xp_after)"
              + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
          event.getUserId(),
          event.getId(),
          event.getCourseId(),
          event.getSubjectType(),
          event.getSubjectId(),
          event.getEventType(),
          day,
          xp,
          reason,
          event.getOccurredTime(),
          xpBefore,
          xpBefore + xp);
    } catch (DuplicateKeyException ignored) {
      return duplicate(event, xpBefore);
    }
    boolean eligible = xp > 0;
    int combo = integer(before, "current_combo");
    if (eligible && !"EXAM_ANSWERED".equals(event.getEventType())) {
      combo = correct ? combo + 1 : 0;
    }
    LocalDate lastDay = date(before, "last_answer_date");
    int streak = integer(before, "current_streak");
    if (eligible && (lastDay == null || !day.isBefore(lastDay))) {
      streak =
          lastDay == null || day.isAfter(lastDay.plusDays(1))
              ? 1
              : day.equals(lastDay) ? streak : streak + 1;
    }
    jdbc.update(
        "UPDATE gamification_user_profile SET"
            + " total_xp=?,answered_count=answered_count+?,correct_count=correct_count+?,review_count=review_count+?,tutor_count=tutor_count+?,stage_count=stage_count+?,current_combo=?,max_combo=GREATEST(max_combo,?),current_streak=?,max_streak=GREATEST(max_streak,?),last_answer_date=CASE"
            + " WHEN ? THEN GREATEST(COALESCE(last_answer_date,'1000-01-01'),?) ELSE"
            + " last_answer_date END,version=version+1,last_event_id=GREATEST(last_event_id,?)"
            + " WHERE user_id=?",
        xpBefore + xp,
        eligible ? 1 : 0,
        eligible && correct ? 1 : 0,
        eligible && "REVIEW_ANSWERED".equals(event.getEventType()) ? 1 : 0,
        eligible && "TUTOR_CHECK_ANSWERED".equals(event.getEventType()) ? 1 : 0,
        eligible && "STAGE_ASSESSMENT_ANSWERED".equals(event.getEventType()) ? 1 : 0,
        combo,
        combo,
        streak,
        streak,
        eligible,
        day,
        event.getId(),
        event.getUserId());
    Map<String, Object> after = lockProfile(event.getUserId());
    GamificationRewardFeedback result = feedback(event, xp, reason, xpBefore, after);
    result.setNewAchievements(unlock(event, after));
    return result;
  }

  private GamificationRewardFeedback feedback(
      CourseLearningEvent event, int xp, String reason, long before, Map<String, Object> profile) {
    GamificationRewardFeedback result = new GamificationRewardFeedback();
    result.setEventId(event.getId());
    result.setAwardedXp(xp);
    result.setReason(reason);
    result.setEligible(xp > 0);
    result.setLevelBefore(GamificationRules.levelFor(before));
    result.setLevelAfter(GamificationRules.levelFor(number(profile, "total_xp")));
    result.setLeveledUp(result.getLevelAfter() > result.getLevelBefore());
    result.setStreakDays(
        activeStreak(integer(profile, "current_streak"), date(profile, "last_answer_date")));
    result.setCurrentCombo(integer(profile, "current_combo"));
    result.setMaxCombo(integer(profile, "max_combo"));
    return result;
  }

  private GamificationRewardFeedback duplicate(CourseLearningEvent event, long total) {
    GamificationRewardFeedback result = new GamificationRewardFeedback();
    result.setEventId(event.getId());
    result.setReason("EVENT_ALREADY_REWARDED");
    result.setLevelBefore(GamificationRules.levelFor(total));
    result.setLevelAfter(result.getLevelBefore());
    result.setNewAchievements(List.of());
    return result;
  }

  private List<GamificationAchievementVO> unlock(
      CourseLearningEvent event, Map<String, Object> profile) {
    List<GamificationAchievementVO> unlocked = new ArrayList<>();
    Map<String, Long> progress = progress(profile);
    for (Definition definition : ACHIEVEMENTS) {
      if (progress.get(definition.metric) < definition.target) {
        continue;
      }
      try {
        jdbc.update(
            "INSERT INTO gamification_achievement"
                + " (user_id,achievement_code,unlocked_at,trigger_event_id) VALUES (?,?,?,?)",
            event.getUserId(),
            definition.code,
            event.getOccurredTime(),
            event.getId());
        GamificationAchievementVO item = achievement(definition, progress.get(definition.metric));
        item.setUnlocked(true);
        item.setUnlockedAt(event.getOccurredTime());
        unlocked.add(item);
      } catch (DuplicateKeyException ignored) {
      }
    }
    return unlocked;
  }

  private GamificationSummaryVO summaryFromProfile(Long userId) {
    Map<String, Object> profile = profile(userId);
    long xp = number(profile, "total_xp");
    int level = GamificationRules.levelFor(xp);
    int today =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM gamification_xp_ledger WHERE"
                + " user_id=? AND event_date=? AND xp>0",
            Integer.class,
            userId,
            LocalDate.now(LEARNING_ZONE));
    GamificationSummaryVO summary = new GamificationSummaryVO();
    summary.setTotalXp(xp);
    summary.setLevel(level);
    summary.setLevelStartXp(GamificationRules.levelStartXp(level));
    summary.setNextLevelXp(GamificationRules.nextLevelXp(level));
    summary.setXpIntoLevel(xp - summary.getLevelStartXp());
    summary.setXpToNextLevel(summary.getNextLevelXp() - xp);
    summary.setLevelTitle("学习者 Lv." + level);
    summary.setStreakDays(
        activeStreak(integer(profile, "current_streak"), date(profile, "last_answer_date")));
    summary.setCurrentCombo(integer(profile, "current_combo"));
    summary.setMaxCombo(integer(profile, "max_combo"));
    summary.setTodayAnsweredCount(today);
    summary.setDailyGoal(integer(profile, "daily_goal"));
    summary.setTodayGoalReached(today >= summary.getDailyGoal());
    summary.setZoneId(LEARNING_ZONE.getId());
    summary.setVersion(number(profile, "version"));
    summary.setLastEventId(number(profile, "last_event_id"));
    summary.setRecentAchievements(
        achievementsFromProfile(userId).stream()
            .filter(GamificationAchievementVO::isUnlocked)
            .sorted(
                java.util.Comparator.comparing(GamificationAchievementVO::getUnlockedAt).reversed())
            .limit(3)
            .toList());
    return summary;
  }

  private List<GamificationAchievementVO> achievementsFromProfile(Long userId) {
    Map<String, LocalDateTime> unlocked = new LinkedHashMap<>();
    jdbc.query(
        "SELECT achievement_code,unlocked_at FROM gamification_achievement WHERE user_id=?",
        (RowCallbackHandler)
            rs -> unlocked.put(rs.getString(1), rs.getObject(2, LocalDateTime.class)),
        userId);
    Map<String, Long> progress = progress(profile(userId));
    List<GamificationAchievementVO> result = new ArrayList<>();
    for (Definition definition : ACHIEVEMENTS) {
      GamificationAchievementVO item = achievement(definition, progress.get(definition.metric));
      item.setUnlocked(unlocked.containsKey(definition.code));
      item.setUnlockedAt(unlocked.get(definition.code));
      result.add(item);
    }
    return result;
  }

  private Map<String, Long> progress(Map<String, Object> profile) {
    Map<String, Long> out = new LinkedHashMap<>();
    out.put("answers", number(profile, "answered_count"));
    out.put("correct", number(profile, "correct_count"));
    out.put("review", number(profile, "review_count"));
    out.put("streak", (long) integer(profile, "max_streak"));
    out.put("tutor", number(profile, "tutor_count"));
    out.put("stage", number(profile, "stage_count"));
    return out;
  }

  private int activeStreak(int streak, LocalDate day) {
    LocalDate today = LocalDate.now(LEARNING_ZONE);
    return day != null && (day.equals(today) || day.equals(today.minusDays(1))) ? streak : 0;
  }

  private CourseLearningEvent event(java.sql.ResultSet rs) throws java.sql.SQLException {
    CourseLearningEvent e = new CourseLearningEvent();
    e.setId(rs.getLong("id"));
    e.setUserId(rs.getLong("user_id"));
    e.setCourseId(rs.getLong("course_id"));
    e.setEventType(rs.getString("event_type"));
    e.setEventSource(rs.getString("event_source"));
    e.setSubjectType(rs.getString("subject_type"));
    e.setSubjectId(rs.getLong("subject_id"));
    e.setSourceRecordId(rs.getLong("source_record_id"));
    e.setIdempotencyKey(rs.getString("idempotency_key"));
    e.setEventVersion(rs.getInt("event_version"));
    e.setPayloadJson(rs.getString("payload_json"));
    e.setOccurredTime(rs.getObject("occurred_time", LocalDateTime.class));
    return e;
  }

  static LocalDate learningDate(LocalDateTime sourceClockTime) {
    return sourceClockTime
        .atZone(SOURCE_CLOCK_ZONE)
        .withZoneSameInstant(LEARNING_ZONE)
        .toLocalDate();
  }

  private Map<String, Object> profile(Long user) {
    return jdbc.queryForMap("SELECT * FROM gamification_user_profile WHERE user_id=?", user);
  }

  private Map<String, Object> lockProfile(Long user) {
    return jdbc.queryForMap(
        "SELECT * FROM gamification_user_profile WHERE user_id=? FOR UPDATE", user);
  }

  private long number(Map<String, Object> p, String n) {
    return ((Number) p.get(n)).longValue();
  }

  private int integer(Map<String, Object> p, String n) {
    return ((Number) p.get(n)).intValue();
  }

  private LocalDate date(Map<String, Object> p, String n) {
    Object v = p.get(n);
    return v == null ? null : ((java.sql.Date) v).toLocalDate();
  }

  private boolean rewardable(String t) {
    return List.of(
            "PRACTICE_ANSWERED",
            "REVIEW_ANSWERED",
            "EXAM_ANSWERED",
            "STAGE_ASSESSMENT_ANSWERED",
            "PAPER_LEARNING_ANSWERED",
            "AI_VARIANT_ANSWERED",
            "TUTOR_CHECK_ANSWERED")
        .contains(t);
  }

  private GamificationAchievementVO achievement(Definition d, long value) {
    GamificationAchievementVO v = new GamificationAchievementVO();
    v.setCode(d.code);
    v.setName(d.name);
    v.setDescription(d.description);
    v.setTarget(d.target);
    v.setProgress(Math.min(value, d.target));
    return v;
  }

  private record Definition(
      String code, String name, String description, String metric, long target) {}
}
