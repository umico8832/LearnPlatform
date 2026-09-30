package com.learnplatform.service.gamification;

import com.learnplatform.dto.GamificationSummaryVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit API alias for the same locked, chronological initialization used by normal reads. */
@Service
public class GamificationBackfillService {
  private final GamificationRewardService rewards;

  public GamificationBackfillService(GamificationRewardService rewards) {
    this.rewards = rewards;
  }

  @Transactional
  public GamificationSummaryVO backfill(Long userId) {
    return rewards.summary(userId);
  }
}
