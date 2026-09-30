package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.GamificationAchievementVO;
import com.learnplatform.dto.GamificationDailyGoalRequest;
import com.learnplatform.dto.GamificationHeatmapDayVO;
import com.learnplatform.dto.GamificationSummaryVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.gamification.GamificationBackfillService;
import com.learnplatform.service.gamification.GamificationRewardService;
import com.learnplatform.service.gamification.GamificationHeatmapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "学习激励", description = "由真实课程学习事实派生的投入奖励")
@RestController
@RequestMapping("/api/gamification")
public class GamificationController {
  private final GamificationRewardService rewards;
  private final GamificationBackfillService backfill;
  private final GamificationHeatmapService heatmap;

  public GamificationController(
      GamificationRewardService rewards, GamificationBackfillService backfill, GamificationHeatmapService heatmap) {
    this.rewards = rewards;
    this.backfill = backfill;
    this.heatmap = heatmap;
  }

  @Operation(summary = "学习激励概况")
  @GetMapping("/summary")
  public R<GamificationSummaryVO> summary(@AuthenticationPrincipal CustomUserDetails user) {
    return R.ok(rewards.summary(user.getUserId()));
  }

  @Operation(summary = "学习成就列表")
  @GetMapping("/achievements")
  public R<List<GamificationAchievementVO>> achievements(
      @AuthenticationPrincipal CustomUserDetails user) {
    return R.ok(rewards.achievements(user.getUserId()));
  }

  @Operation(summary = "调整每日真实作答目标")
  @PostMapping("/daily-goal")
  public R<GamificationSummaryVO> dailyGoal(
      @AuthenticationPrincipal CustomUserDetails user,
      @Valid @RequestBody GamificationDailyGoalRequest request) {
    return R.ok(rewards.updateDailyGoal(user.getUserId(), request.getDailyGoal()));
  }

  @Operation(summary = "学习投入热力日历")
  @GetMapping("/heatmap")
  public R<List<GamificationHeatmapDayVO>> heatmap(
      @AuthenticationPrincipal CustomUserDetails user,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to) {
    if (to.isBefore(from) || from.plusDays(366).isBefore(to)) {
      return R.fail(ResultCode.VALIDATION_ERROR, "日期范围应在一年以内");
    }
    return R.ok(heatmap.days(user.getUserId(), from, to));
  }

  @Operation(summary = "补算历史学习奖励")
  @PostMapping("/backfill")
  public R<GamificationSummaryVO> backfill(@AuthenticationPrincipal CustomUserDetails user) {
    return R.ok(backfill.backfill(user.getUserId()));
  }
}
