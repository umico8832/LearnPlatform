package com.learnplatform.service.gamification;

/** Central, deterministic reward and level rules. */
public final class GamificationRules {
  private GamificationRules() {}

  public static int answerXp(boolean correct, String eventType) {
    if (correct) {
      return "REVIEW_ANSWERED".equals(eventType) ? 14 : 10;
    }
    return 2;
  }

  public static int levelFor(long totalXp) {
    int level = 1;
    while (totalXp >= levelStartXp(level + 1)) { level++; }
    return level;
  }

  public static long levelStartXp(int level) {
    if (level <= 1) { return 0; }
    return 25L * level * level + 25L * level - 50L;
  }

  public static long nextLevelXp(int level) {
    return levelStartXp(level + 1);
  }
}
