<template>
  <el-card shadow="hover" class="learning-effect-card">
    <template #header>
      <div class="effect-card-header">
        <div>
          <span>AI 学习效果观察</span>
          <span class="report-subtitle">同题记忆与知识点跨题迁移对照</span>
        </div>
        <el-tag :type="conclusionTagType(effect.conclusionLevel)" effect="light">
          {{ effectTagLabel(effect.conclusionLevel) }}
        </el-tag>
      </div>
    </template>

    <div class="effect-context">
      <div>
        <strong>{{ effect.periodStart || '-' }} 至 {{ effect.periodEnd || '-' }}</strong>
        <p>
          只统计用户实际看到已缓存学习资产后的真实作答；方向判断同时要求作答量与独立学习者覆盖，避免少数高频用户主导结论。
        </p>
      </div>
      <div class="effect-coverage">
        <span
          ><b>{{ effect.assetViewCount }}</b> 次查看</span
        >
        <span
          ><b>{{ effect.engagedUserCount }}</b> 位用户</span
        >
        <span
          ><b>{{ effect.viewedQuestionCount }}</b> 道题</span
        >
      </div>
    </div>

    <div class="effect-comparison">
      <div class="effect-group is-after-view">
        <span class="effect-group-label">阅读后同题作答</span>
        <strong>{{ formatRate(effect.afterViewCorrectRate) }}</strong>
        <div class="effect-rate-track">
          <i :style="{ width: rateWidth(effect.afterViewCorrectRate) }"></i>
        </div>
        <small>{{ effect.afterViewPracticeCount }} 条作答 · {{ effect.afterViewUserCount }} 位学习者</small>
      </div>
      <div class="effect-lift">
        <span>正确率差异</span>
        <strong :class="liftClass(effect.correctRateLift)">{{ formatLift(effect.correctRateLift) }}</strong>
        <small>阅读后组 − 对照组</small>
      </div>
      <div class="effect-group is-baseline">
        <span class="effect-group-label">未阅读前 / 未阅读作答</span>
        <strong>{{ formatRate(effect.baselineCorrectRate) }}</strong>
        <div class="effect-rate-track">
          <i :style="{ width: rateWidth(effect.baselineCorrectRate) }"></i>
        </div>
        <small>{{ effect.baselinePracticeCount }} 条作答 · {{ effect.baselineUserCount }} 位学习者</small>
      </div>
    </div>

    <el-alert
      :title="effect.conclusion"
      :type="conclusionAlertType(effect.conclusionLevel)"
      :closable="false"
      show-icon
      class="effect-conclusion"
    />

    <section class="transfer-observation">
      <div class="transfer-header">
        <div>
          <strong>知识点跨题迁移</strong>
          <p>
            排除原题重答，仅比较共享知识点的另一道题；前后组均限制在相关阅读前后
            {{ effect.crossQuestionWindowDays }} 天，对照组不含更早暴露，且每组至少覆盖
            {{ effect.minimumDistinctUsers }} 位学习者。
          </p>
        </div>
        <el-tag :type="conclusionTagType(effect.crossQuestionConclusionLevel)" effect="plain">
          {{ transferTagLabel(effect.crossQuestionConclusionLevel) }}
        </el-tag>
      </div>

      <div class="effect-comparison is-transfer">
        <div class="effect-group is-after-view">
          <span class="effect-group-label">阅读后跨题作答</span>
          <strong>{{ formatRate(effect.crossQuestionAfterViewCorrectRate) }}</strong>
          <div class="effect-rate-track">
            <i :style="{ width: rateWidth(effect.crossQuestionAfterViewCorrectRate) }"></i>
          </div>
          <small>
            {{ effect.crossQuestionAfterViewPracticeCount }} 条作答 ·
            {{ effect.crossQuestionAfterViewUserCount }} 位学习者
          </small>
        </div>
        <div class="effect-lift">
          <span>正确率差异</span>
          <strong :class="liftClass(effect.crossQuestionCorrectRateLift)">
            {{ formatLift(effect.crossQuestionCorrectRateLift) }}
          </strong>
          <small>阅读后组 − 阅读前组</small>
        </div>
        <div class="effect-group is-baseline">
          <span class="effect-group-label">首次相关阅读前跨题作答</span>
          <strong>{{ formatRate(effect.crossQuestionBaselineCorrectRate) }}</strong>
          <div class="effect-rate-track">
            <i :style="{ width: rateWidth(effect.crossQuestionBaselineCorrectRate) }"></i>
          </div>
          <small>
            {{ effect.crossQuestionBaselinePracticeCount }} 条作答 ·
            {{ effect.crossQuestionBaselineUserCount }} 位学习者
          </small>
        </div>
      </div>

      <el-alert
        :title="effect.crossQuestionConclusion"
        :type="conclusionAlertType(effect.crossQuestionConclusionLevel)"
        :closable="false"
        show-icon
        class="effect-conclusion"
      />
    </section>

    <div class="effect-detail-grid">
      <div class="effect-feedback">
        <span>内容反馈</span>
        <strong>{{ formatRate(effect.helpfulRate) }}</strong>
        <small>{{ effect.feedbackCount }} 条反馈中的有帮助占比</small>
      </div>
      <div class="effect-feedback is-variant-training">
        <span>变式训练完成率</span>
        <strong>{{ formatRate(effect.variantTrainingCompletionRate) }}</strong>
        <small
          >{{ effect.variantTrainingCompletedCount }} / {{ effect.variantTrainingStartedCount }} 个周期内开始记录</small
        >
        <em>兼容旧版手动确认与新版提交判分</em>
      </div>
      <div class="effect-feedback is-variant-accuracy">
        <span>结构化变式正确率</span>
        <strong>{{ formatRate(effect.variantTrainingCorrectRate) }}</strong>
        <small>{{ effect.variantTrainingCorrectCount }} / {{ effect.variantTrainingAnsweredCount }} 次首次判分</small>
        <em>只统计服务端真实判分</em>
      </div>
      <el-table :data="effect.assetTypeStats" stripe size="small" class="effect-type-table">
        <el-table-column prop="assetTypeLabel" label="资产类型" min-width="120" />
        <el-table-column prop="viewCount" label="查看" width="76" align="right" />
        <el-table-column prop="userCount" label="用户" width="76" align="right" />
        <el-table-column prop="feedbackCount" label="反馈" width="76" align="right" />
        <el-table-column label="有帮助率" width="96" align="right">
          <template #default="{ row }">{{ formatRate(row.helpfulRate) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="当前周期暂无学习资产查看数据" :image-size="48" />
        </template>
      </el-table>
    </div>

    <AiAssetTypeObservation :effect="effect" />
    <AiVariantDifficultyObservation :effect="effect" />
  </el-card>
</template>

<script setup lang="ts">
import type { AiLearningEffect } from '@/api/aiUsage'
import AiAssetTypeObservation from './AiAssetTypeObservation.vue'
import AiVariantDifficultyObservation from './AiVariantDifficultyObservation.vue'
import {
  conclusionAlertType,
  conclusionTagType,
  effectTagLabel,
  formatLift,
  formatRate,
  liftClass,
  rateWidth,
  transferTagLabel,
} from './aiUsageDisplay'

defineProps<{ effect: AiLearningEffect }>()
</script>

<style scoped>
.learning-effect-card {
  margin-bottom: 16px;
  border-left: 3px solid var(--lp-success);
}
.effect-card-header,
.effect-context,
.effect-comparison,
.effect-detail-grid {
  display: flex;
  align-items: center;
}
.effect-card-header,
.effect-context {
  justify-content: space-between;
  gap: 20px;
}
.report-subtitle {
  margin-left: 8px;
  color: #909399;
  font-size: 13px;
  font-weight: normal;
}
.effect-context {
  padding: 4px 2px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.effect-context strong {
  color: var(--lp-text-primary);
  font-size: 14px;
}
.effect-context p {
  max-width: 720px;
  margin: 6px 0 0;
  color: var(--lp-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.effect-coverage {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}
.effect-coverage span {
  padding: 8px 10px;
  border: 1px solid #dbe7e0;
  border-radius: 8px;
  background: #f4faf6;
  color: var(--lp-text-secondary);
  font-size: 12px;
}
.effect-coverage b {
  color: #25794d;
  font-size: 15px;
}
.effect-comparison {
  justify-content: center;
  gap: 28px;
  padding: 24px 0;
}
.effect-group {
  width: min(300px, 32%);
  padding: 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: #fafcfe;
}
.effect-group.is-after-view {
  border-color: #cce8d7;
  background: #f3fbf6;
}
.effect-group-label,
.effect-group small,
.effect-lift span,
.effect-lift small,
.effect-feedback span,
.effect-feedback small {
  display: block;
  color: var(--lp-text-secondary);
  font-size: 12px;
}
.effect-group strong {
  display: block;
  margin: 8px 0 10px;
  color: var(--lp-text-primary);
  font-size: 28px;
  line-height: 1;
}
.effect-rate-track {
  height: 7px;
  margin-bottom: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #e9eef4;
}
.effect-rate-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #8aa1b8;
}
.is-after-view .effect-rate-track i {
  background: var(--lp-success);
}
.effect-lift {
  min-width: 150px;
  text-align: center;
}
.effect-lift strong {
  display: block;
  margin: 8px 0;
  font-size: 18px;
}
.positive {
  color: #67c23a;
}
.negative {
  color: #f56c6c;
}
.neutral {
  color: #909399;
}
.effect-conclusion {
  margin-bottom: 18px;
}
.transfer-observation {
  margin-bottom: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.transfer-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.transfer-header strong {
  color: var(--lp-text-primary);
  font-size: 15px;
}
.transfer-header p {
  margin: 6px 0 0;
  color: var(--lp-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.effect-comparison.is-transfer {
  padding-top: 18px;
}
.effect-detail-grid {
  align-items: stretch;
  gap: 16px;
}
.effect-feedback {
  display: flex;
  width: 180px;
  flex: 0 0 180px;
  flex-direction: column;
  justify-content: center;
  padding: 20px;
  border-radius: 10px;
  background: var(--lp-primary-soft);
}
.effect-feedback strong {
  margin: 10px 0 8px;
  color: var(--lp-primary);
  font-size: 28px;
}
.effect-feedback.is-variant-training {
  background: #f3faf5;
}
.effect-feedback.is-variant-training strong {
  color: var(--lp-success);
}
.effect-feedback.is-variant-accuracy {
  background: #fff7e8;
}
.effect-feedback.is-variant-accuracy strong {
  color: #a96812;
}
.effect-feedback em {
  margin-top: 10px;
  color: var(--lp-text-tertiary);
  font-size: 11px;
  font-style: normal;
  line-height: 1.4;
}
.effect-type-table {
  min-width: 0;
  flex: 1;
}
@media (max-width: 768px) {
  .effect-card-header,
  .effect-context,
  .effect-comparison,
  .effect-detail-grid,
  .transfer-header {
    align-items: stretch;
    flex-direction: column;
  }
  .effect-coverage {
    flex-wrap: wrap;
  }
  .effect-group,
  .effect-feedback {
    width: auto;
  }
  .effect-lift {
    min-width: 0;
  }
}
</style>
