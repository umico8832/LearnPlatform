<template>
  <div class="learning-diagnosis">
    <el-page-header @back="$router.back()">
      <template #content>
        <span class="page-title">🧠 学习诊断</span>
      </template>
    </el-page-header>

    <div v-if="loading" v-loading="true" style="height: 400px"></div>

    <template v-else-if="data">
      <!-- 每日建议 -->
      <el-card class="advice-card" shadow="hover">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <span>💡 每日学习建议</span>
            <el-button
              type="primary"
              size="small"
              :loading="aiAdviceLoading"
              @click="generateAiAdvice"
              :disabled="aiAdviceStreaming"
            >
              🤖 AI 个性化建议
            </el-button>
          </div>
        </template>
        <div class="advice-content">
          <p v-for="(line, i) in adviceLines" :key="i">{{ line }}</p>
        </div>
      </el-card>

      <!-- AI 个性化建议 -->
      <el-card v-if="aiAdviceContent || aiAdviceLoading" class="ai-advice-card" shadow="hover">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <span>🤖 AI 个性化学习建议</span>
            <el-tag v-if="aiAdviceStreaming" type="success" size="small" effect="light"> 生成中... </el-tag>
            <el-tag v-else-if="aiAdviceContent" type="info" size="small" effect="light"> AI 生成 </el-tag>
          </div>
        </template>
        <div v-if="aiAdviceLoading && !aiAdviceContent" v-loading="true" style="height: 100px"></div>
        <div v-else class="ai-advice-content">
          <MarkdownRenderer :content="aiAdviceContent" />
        </div>
      </el-card>

      <!-- 核心指标卡片 -->
      <el-row :gutter="16" class="stat-row">
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value">{{ data.totalPractice }}</div>
            <div class="stat-label">总刷题数</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value">{{ data.overallCorrectRate }}%</div>
            <div class="stat-label">总正确率</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value">{{ data.activeDaysLast30 }}天</div>
            <div class="stat-label">近30天活跃</div>
          </el-card>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-value">{{ data.streakDays }}天</div>
            <div class="stat-label">连续刷题</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 知识点薄弱诊断 -->
      <el-card class="section-card" shadow="hover" v-if="data.weakPoints.length">
        <template #header>
          <span>📚 知识点薄弱诊断</span>
        </template>
        <el-table :data="data.weakPoints" stripe>
          <el-table-column label="知识点" min-width="160">
            <template #default="{ row }">
              <div>
                <strong>{{ row.knowledgePointName }}</strong>
                <el-tag size="small" type="info" style="margin-left: 8px">{{ row.courseName }}</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="正确率" width="120">
            <template #default="{ row }">
              <el-progress
                :percentage="row.correctRate >= 0 ? Math.round(row.correctRate) : 0"
                :color="getRateColor(row.correctRate)"
                :stroke-width="18"
                :text-inside="true"
              />
            </template>
          </el-table-column>
          <el-table-column prop="totalAttempts" label="练习数" width="80" align="center" />
          <el-table-column prop="wrongCount" label="错题数" width="80" align="center" />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.masteryStatus)" size="small">
                {{ getStatusLabel(row.masteryStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="diagnosis" label="诊断" min-width="260" show-overflow-tooltip />
        </el-table>
      </el-card>

      <!-- 学习习惯 -->
      <el-card class="section-card" shadow="hover" v-if="data.learningHabit">
        <template #header>
          <span>📊 学习习惯分析</span>
        </template>
        <el-row :gutter="24">
          <el-col :xs="24" :sm="12">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="日均刷题">{{ data.learningHabit.avgDailyPractice }} 道</el-descriptions-item>
              <el-descriptions-item label="偏好题型">{{
                data.learningHabit.preferredQuestionType
              }}</el-descriptions-item>
              <el-descriptions-item label="偏好课程">{{ data.learningHabit.preferredCourse }}</el-descriptions-item>
              <el-descriptions-item label="学习频次">
                <el-tag
                  :type="
                    data.learningHabit.frequencyLevel === 'ACTIVE'
                      ? 'success'
                      : data.learningHabit.frequencyLevel === 'MODERATE'
                        ? 'warning'
                        : 'danger'
                  "
                  size="small"
                >
                  {{ data.learningHabit.frequencyDescription }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </el-col>
          <el-col :xs="24" :sm="12">
            <div class="chart-container">
              <h4>近 7 天刷题趋势</h4>
              <div class="mini-chart">
                <div v-for="(day, i) in data.learningHabit.weeklyTrend" :key="i" class="chart-bar-group">
                  <div class="chart-bar-wrapper">
                    <div class="chart-bar correct" :style="{ height: getBarHeight(day.correct) }"></div>
                    <div class="chart-bar wrong" :style="{ height: getBarHeight(day.wrong) }"></div>
                  </div>
                  <div class="chart-date">{{ day.date.slice(5) }}</div>
                  <div class="chart-total">{{ day.total }}</div>
                </div>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-card>

      <!-- 错因分析 -->
      <el-card class="section-card" shadow="hover" v-if="data.errorPatterns">
        <template #header>
          <span>⚠️ 错因分析</span>
        </template>
        <el-row :gutter="24">
          <el-col :xs="24" :sm="12">
            <h4>错题掌握程度分布</h4>
            <div class="mastery-bars">
              <div v-for="(count, label) in data.errorPatterns.masteryDistribution" :key="label" class="mastery-item">
                <span class="mastery-label">{{ label }}</span>
                <el-progress
                  :percentage="totalWrong > 0 ? Math.round((count / totalWrong) * 100) : 0"
                  :color="getMasteryColor(label as string)"
                  :stroke-width="20"
                  :text-inside="true"
                  :format="() => count + ' 道'"
                />
              </div>
            </div>
            <el-descriptions :column="1" border style="margin-top: 16px">
              <el-descriptions-item label="反复出错题目"
                >{{ data.errorPatterns.repeatedErrorCount }} 道</el-descriptions-item
              >
              <el-descriptions-item label="近7天新增错题"
                >{{ data.errorPatterns.recentNewWrongCount }} 道</el-descriptions-item
              >
            </el-descriptions>
          </el-col>
          <el-col :xs="24" :sm="12">
            <h4>高频错题课程</h4>
            <div v-if="data.errorPatterns.topErrorCourses.length">
              <div v-for="c in data.errorPatterns.topErrorCourses" :key="c.courseId" class="error-course-item">
                <span class="course-name">{{ c.courseName }}</span>
                <el-tag type="danger" size="small">{{ c.wrongCount }} 道错题</el-tag>
              </div>
            </div>
            <el-empty v-else description="暂无错题数据" :image-size="60" />
          </el-col>
        </el-row>

        <!-- 错题题型 & 难度分布 -->
        <el-row :gutter="24" style="margin-top: 20px">
          <el-col :xs="24" :sm="12">
            <h4>📊 错题题型分布</h4>
            <div
              v-if="
                data.errorPatterns.questionTypeDistribution &&
                Object.keys(data.errorPatterns.questionTypeDistribution).length
              "
            >
              <div
                v-for="(count, typeName) in data.errorPatterns.questionTypeDistribution"
                :key="typeName"
                class="mastery-item"
              >
                <span class="mastery-label">{{ typeName }}</span>
                <el-progress
                  :percentage="totalWrong > 0 ? Math.round((count / totalWrong) * 100) : 0"
                  color="#409eff"
                  :stroke-width="18"
                  :text-inside="true"
                  :format="() => count + ' 道'"
                />
              </div>
            </div>
            <el-empty v-else description="暂无数据" :image-size="40" />
          </el-col>
          <el-col :xs="24" :sm="12">
            <h4>⭐ 错题难度分布</h4>
            <div
              v-if="
                data.errorPatterns.difficultyDistribution &&
                Object.keys(data.errorPatterns.difficultyDistribution).length
              "
            >
              <div v-for="(count, diff) in data.errorPatterns.difficultyDistribution" :key="diff" class="mastery-item">
                <span class="mastery-label">{{ '⭐'.repeat(Number(diff)) }}</span>
                <el-progress
                  :percentage="totalWrong > 0 ? Math.round((count / totalWrong) * 100) : 0"
                  :color="getDifficultyColor(Number(diff))"
                  :stroke-width="18"
                  :text-inside="true"
                  :format="() => count + ' 道'"
                />
              </div>
            </div>
            <el-empty v-else description="暂无数据" :image-size="40" />
          </el-col>
        </el-row>

        <!-- 每周错题趋势 -->
        <div
          v-if="data.errorPatterns.weeklyErrorTrend && data.errorPatterns.weeklyErrorTrend.length"
          style="margin-top: 20px"
        >
          <h4>📈 近 4 周错题趋势</h4>
          <div class="mini-chart">
            <div v-for="(week, i) in data.errorPatterns.weeklyErrorTrend" :key="i" class="chart-bar-group">
              <div class="chart-bar-wrapper">
                <div class="chart-bar error-trend" :style="{ height: getWeeklyBarHeight(week.count) }"></div>
              </div>
              <div class="chart-date">{{ week.label }}</div>
              <div class="chart-total">{{ week.count }}</div>
            </div>
          </div>
        </div>

        <!-- 知识点错因排名 -->
        <div
          v-if="data.errorPatterns.knowledgePointErrors && data.errorPatterns.knowledgePointErrors.length"
          style="margin-top: 20px"
        >
          <h4>🎯 知识点错因排名</h4>
          <el-table :data="data.errorPatterns.knowledgePointErrors" stripe size="small">
            <el-table-column label="知识点" min-width="160">
              <template #default="{ row }">
                <div>
                  <strong>{{ row.knowledgePointName }}</strong>
                  <el-tag size="small" type="info" style="margin-left: 8px">{{ row.courseName }}</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="wrongCount" label="错题数" width="80" align="center">
              <template #default="{ row }">
                <el-tag type="danger" size="small">{{ row.wrongCount }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="totalAttempts" label="练习数" width="80" align="center" />
            <el-table-column label="正确率" width="120">
              <template #default="{ row }">
                <el-progress
                  :percentage="Math.round(row.correctRate)"
                  :color="getRateColor(row.correctRate)"
                  :stroke-width="14"
                  :text-inside="true"
                />
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 反复错题详情 -->
        <div
          v-if="data.errorPatterns.repeatedErrors && data.errorPatterns.repeatedErrors.length"
          style="margin-top: 20px"
        >
          <h4>🔄 反复错题详情</h4>
          <el-table :data="data.errorPatterns.repeatedErrors" stripe size="small">
            <el-table-column label="题目" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">
                <span>{{ row.questionContent }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="questionType" label="题型" width="80" align="center" />
            <el-table-column label="难度" width="80" align="center">
              <template #default="{ row }">
                <span v-if="row.difficulty">{{ '⭐'.repeat(row.difficulty) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="wrongCount" label="错次" width="70" align="center">
              <template #default="{ row }">
                <el-tag type="danger" size="small">{{ row.wrongCount }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="掌握度" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="getMasteryLevelType(row.masteryLevel)" size="small">
                  {{ getMasteryLevelLabel(row.masteryLevel) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="knowledgePointName" label="知识点" width="120" />
            <el-table-column label="操作" width="160" align="center">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  link
                  size="small"
                  @click="loadSimilarQuestions(row.questionId, row.questionContent)"
                >
                  找相似题
                </el-button>
                <el-button type="warning" link size="small" @click="loadQuestionErrorAnalysis(row.questionId)">
                  错因分析
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>

      <!-- 课程掌握概况 -->
      <el-card class="section-card" shadow="hover" v-if="data.courseMasteries.length">
        <template #header>
          <span>📖 课程掌握概况</span>
        </template>
        <el-table :data="data.courseMasteries" stripe>
          <el-table-column prop="courseName" label="课程" min-width="140" />
          <el-table-column label="正确率" width="140">
            <template #default="{ row }">
              <el-progress
                :percentage="Math.round(row.correctRate)"
                :color="getRateColor(row.correctRate)"
                :stroke-width="16"
                :text-inside="true"
              />
            </template>
          </el-table-column>
          <el-table-column prop="totalAttempts" label="练习数" width="80" align="center" />
          <el-table-column prop="wrongCount" label="错题数" width="80" align="center" />
          <el-table-column prop="knowledgePointCount" label="知识点总数" width="100" align="center" />
          <el-table-column prop="weakPointCount" label="薄弱知识点" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.weakPointCount > 0 ? 'danger' : 'success'" size="small">
                {{ row.weakPointCount }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 每日推荐题目 -->
      <el-card class="section-card" shadow="hover" v-if="data.dailyRecommendations.length">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <span>🎯 今日推荐题目</span>
            <el-button type="primary" size="small" @click="startRecommendPractice"> 开始练习 </el-button>
          </div>
        </template>
        <el-table :data="data.dailyRecommendations" stripe>
          <el-table-column label="题目内容" min-width="280" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.questionContent }}</span>
            </template>
          </el-table-column>
          <el-table-column label="推荐原因" width="160">
            <template #default="{ row }">
              <el-tag :type="getReasonType(row.reason)" size="small">{{ row.reasonDescription }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="questionType" label="题型" width="80" align="center" />
          <el-table-column label="难度" width="100" align="center">
            <template #default="{ row }">
              <span v-if="row.difficulty">{{ '⭐'.repeat(row.difficulty) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="courseName" label="课程" width="120" />
          <el-table-column prop="knowledgePointName" label="知识点" width="120" />
          <el-table-column label="操作" width="100" align="center">
            <template #default="{ row }">
              <el-button
                type="primary"
                link
                size="small"
                @click="loadSimilarQuestions(row.questionId, row.questionContent)"
              >
                找相似题
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <!-- 单题错因分析弹窗 -->
    <el-dialog v-model="errorAnalysisDialogVisible" title="🔍 单题错因分析" width="750px" destroy-on-close>
      <div v-if="errorAnalysisLoading" v-loading="true" style="height: 200px"></div>
      <template v-else-if="errorAnalysisData">
        <!-- 题目信息 -->
        <div class="error-analysis-header">
          <div class="error-analysis-question">{{ errorAnalysisData.questionContent }}</div>
          <div class="error-analysis-tags">
            <el-tag size="small">{{ errorAnalysisData.questionType }}</el-tag>
            <el-tag v-if="errorAnalysisData.difficulty" size="small" type="warning">
              {{ '⭐'.repeat(errorAnalysisData.difficulty) }}
            </el-tag>
            <el-tag v-if="errorAnalysisData.courseName" size="small" type="info">{{
              errorAnalysisData.courseName
            }}</el-tag>
            <el-tag v-if="errorAnalysisData.knowledgePointName" size="small" type="info">{{
              errorAnalysisData.knowledgePointName
            }}</el-tag>
          </div>
        </div>

        <!-- 核心指标 -->
        <el-row :gutter="16" style="margin-top: 16px">
          <el-col :span="6">
            <el-statistic title="总作答" :value="errorAnalysisData.totalAttempts" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="答对" :value="errorAnalysisData.correctCount" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="答错" :value="errorAnalysisData.wrongCount" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="正确率">
              <template #default>
                <span :style="{ color: getRateColor(errorAnalysisData.correctRate), fontWeight: 700 }">
                  {{ errorAnalysisData.correctRate }}%
                </span>
              </template>
            </el-statistic>
          </el-col>
        </el-row>

        <!-- 掌握趋势 -->
        <el-alert
          :title="errorAnalysisData.trendDescription"
          :type="
            errorAnalysisData.masteryTrend === 'IMPROVING'
              ? 'success'
              : errorAnalysisData.masteryTrend === 'DECLINING'
                ? 'error'
                : 'info'
          "
          :closable="false"
          show-icon
          style="margin-top: 16px"
        />

        <!-- 掌握程度 -->
        <div
          v-if="errorAnalysisData.currentMasteryLevel !== null && errorAnalysisData.currentMasteryLevel !== undefined"
          style="margin-top: 12px"
        >
          <span style="font-size: 13px; color: #606266">当前掌握程度：</span>
          <el-tag :type="getMasteryLevelType(errorAnalysisData.currentMasteryLevel)" size="small">
            {{ getMasteryLevelLabel(errorAnalysisData.currentMasteryLevel) }}
          </el-tag>
        </div>

        <!-- 错误模式描述 -->
        <div class="error-pattern-box" style="margin-top: 16px">
          <h4 style="margin: 0 0 8px; font-size: 14px; color: #303133">📋 错误模式分析</h4>
          <p style="font-size: 14px; line-height: 1.8; color: #606266; margin: 0">
            {{ errorAnalysisData.errorPattern }}
          </p>
        </div>

        <!-- 作答历史 -->
        <div v-if="errorAnalysisData.attempts && errorAnalysisData.attempts.length" style="margin-top: 16px">
          <h4 style="margin: 0 0 8px; font-size: 14px; color: #303133">
            📝 作答历史（共 {{ errorAnalysisData.attempts.length }} 次）
          </h4>
          <el-timeline>
            <el-timeline-item
              v-for="(attempt, i) in errorAnalysisData.attempts"
              :key="i"
              :type="attempt.isCorrect === 1 ? 'success' : attempt.isCorrect === 0 ? 'danger' : 'info'"
              :timestamp="attempt.createTime ? attempt.createTime.replace('T', ' ') : ''"
              placement="top"
            >
              <el-card shadow="never" body-style="padding: 10px 14px">
                <div style="display: flex; justify-content: space-between; align-items: center">
                  <span>
                    <el-tag :type="attempt.isCorrect === 1 ? 'success' : 'danger'" size="small">
                      {{ attempt.isCorrect === 1 ? '✓ 答对' : '✗ 答错' }}
                    </el-tag>
                    <span v-if="attempt.userAnswer" style="margin-left: 8px; font-size: 13px; color: #606266">
                      答案：{{ attempt.userAnswer }}
                    </span>
                  </span>
                  <span v-if="attempt.answerTime" style="font-size: 12px; color: #909399">
                    用时 {{ attempt.answerTime }}s
                  </span>
                </div>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </div>
      </template>
      <el-empty v-else description="暂无数据" />
      <template #footer>
        <el-button @click="errorAnalysisDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 相似题推荐弹窗 -->
    <el-dialog v-model="similarDialogVisible" title="🔍 相似题推荐" width="800px" destroy-on-close>
      <div v-if="similarLoading" v-loading="true" style="height: 200px"></div>
      <template v-else-if="similarData">
        <div class="similar-source"><strong>原题：</strong>{{ similarSourceContent }}</div>
        <el-table :data="similarData.similarQuestions" stripe style="margin-top: 12px">
          <el-table-column label="题目内容" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.questionContent }}</span>
            </template>
          </el-table-column>
          <el-table-column label="相似度" width="100" align="center">
            <template #default="{ row }">
              <el-progress
                :percentage="row.similarityScore"
                :stroke-width="14"
                :text-inside="true"
                :color="getSimilarityColor(row.similarityScore)"
              />
            </template>
          </el-table-column>
          <el-table-column label="相似原因" width="140">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ row.reason }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="题型" width="80" align="center">
            <template #default="{ row }">{{ row.questionType }}</template>
          </el-table-column>
          <el-table-column label="难度" width="80" align="center">
            <template #default="{ row }">
              <span v-if="row.difficulty">{{ '⭐'.repeat(row.difficulty) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="已练过" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.alreadyAttempted ? 'success' : 'info'" size="small">
                {{ row.alreadyAttempted ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else description="暂无相似题目" />
      <template #footer>
        <el-button @click="similarDialogVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!similarData?.similarQuestions?.length" @click="startSimilarPractice">
          开始练习相似题
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { errorMessage, isAbortError } from '@/utils/errors'
import { useRouter } from 'vue-router'
import {
  getLearningDiagnosis,
  getAiAdviceStream,
  getSimilarQuestions,
  getQuestionErrorAnalysis,
  type LearningDiagnosis,
  type SimilarQuestions,
  type QuestionErrorAnalysis,
} from '@/api/statistics'
import { getQuestionById } from '@/api/question'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(true)
const data = ref<LearningDiagnosis | null>(null)

// 单题错因分析
const errorAnalysisDialogVisible = ref(false)
const errorAnalysisLoading = ref(false)
const errorAnalysisData = ref<QuestionErrorAnalysis | null>(null)

async function loadQuestionErrorAnalysis(questionId: number) {
  errorAnalysisDialogVisible.value = true
  errorAnalysisLoading.value = true
  errorAnalysisData.value = null
  try {
    const res = await getQuestionErrorAnalysis(questionId)
    errorAnalysisData.value = res.data
  } catch (e) {
    ElMessage.error('加载错因分析失败: ' + errorMessage(e, '未知错误'))
  } finally {
    errorAnalysisLoading.value = false
  }
}

// 相似题推荐
const similarDialogVisible = ref(false)
const similarLoading = ref(false)
const similarData = ref<SimilarQuestions | null>(null)
const similarSourceContent = ref('')

async function loadSimilarQuestions(questionId: number, questionContent?: string) {
  similarDialogVisible.value = true
  similarLoading.value = true
  similarData.value = null
  similarSourceContent.value = questionContent || ''
  try {
    const res = await getSimilarQuestions(questionId, 8)
    similarData.value = res.data
  } catch (e) {
    ElMessage.error('加载相似题失败: ' + errorMessage(e, '未知错误'))
  } finally {
    similarLoading.value = false
  }
}

function startSimilarPractice() {
  if (!similarData.value?.similarQuestions?.length) return
  const similar = similarData.value.similarQuestions
  similarDialogVisible.value = false
  Promise.all(similar.map((item) => getQuestionById(item.questionId).then((res) => res.data)))
    .then((questions) => {
      sessionStorage.setItem('practice_questions', JSON.stringify(questions))
      sessionStorage.setItem('practice_mode', 'similar')
      router.push({ path: '/practice/session' })
    })
    .catch(() => {
      ElMessage.error('加载相似题失败，请重试')
    })
}

// AI 个性化建议
const aiAdviceLoading = ref(false)
const aiAdviceStreaming = ref(false)
const aiAdviceContent = ref('')
const aiAdviceAbortController = ref<AbortController | null>(null)

async function generateAiAdvice() {
  aiAdviceLoading.value = true
  aiAdviceStreaming.value = true
  aiAdviceContent.value = ''

  try {
    aiAdviceAbortController.value = new AbortController()
    const response = await getAiAdviceStream()

    if (!response.ok) {
      throw new Error(`请求失败: ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('无法读取响应流')
    }

    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          continue
        }
        if (line.startsWith('data:')) {
          const jsonStr = line.slice(5).trim()
          if (!jsonStr) continue
          try {
            const parsed = JSON.parse(jsonStr)
            if (parsed.content !== undefined) {
              aiAdviceContent.value += parsed.content
            }
          } catch {
            // skip non-JSON data
          }
        }
      }
    }
  } catch (e) {
    if (!isAbortError(e)) {
      ElMessage.error('AI 建议生成失败: ' + errorMessage(e, '未知错误'))
    }
  } finally {
    aiAdviceLoading.value = false
    aiAdviceStreaming.value = false
    aiAdviceAbortController.value = null
  }
}

const adviceLines = computed(() => {
  if (!data.value?.dailyAdvice) return []
  return data.value.dailyAdvice.split('\n').filter((l) => l.trim())
})

const totalWrong = computed(() => {
  if (!data.value?.errorPatterns?.masteryDistribution) return 0
  const dist = data.value.errorPatterns.masteryDistribution
  return Object.values(dist).reduce((sum, v) => sum + v, 0)
})

const maxBarValue = computed(() => {
  if (!data.value?.learningHabit?.weeklyTrend) return 1
  const max = Math.max(...data.value.learningHabit.weeklyTrend.map((d) => d.total))
  return max || 1
})

function getBarHeight(value: number): string {
  return Math.max(0, (value / maxBarValue.value) * 100) + 'px'
}

function getRateColor(rate: number): string {
  if (rate >= 80) return '#67c23a'
  if (rate >= 60) return '#e6a23c'
  return '#f56c6c'
}

function getStatusType(status: string): 'danger' | 'warning' | 'info' | undefined {
  switch (status) {
    case 'WEAK':
      return 'danger'
    case 'NEEDS_REVIEW':
      return 'warning'
    case 'NOT_STARTED':
      return 'info'
    default:
      return undefined
  }
}

function getStatusLabel(status: string): string {
  switch (status) {
    case 'WEAK':
      return '薄弱'
    case 'NEEDS_REVIEW':
      return '需复习'
    case 'NOT_STARTED':
      return '未开始'
    default:
      return status
  }
}

function getMasteryColor(label: string): string {
  if (label.includes('未掌握')) return '#f56c6c'
  if (label.includes('部分')) return '#e6a23c'
  return '#67c23a'
}

function getReasonType(reason: string): 'danger' | 'warning' | 'info' | undefined {
  switch (reason) {
    case 'ERROR_PRONE':
      return 'danger'
    case 'WEAK_POINT_REINFORCE':
      return 'warning'
    case 'SPACED_REVIEW':
      return undefined
    default:
      return 'info'
  }
}

function getSimilarityColor(score: number): string {
  if (score >= 80) return '#67c23a'
  if (score >= 60) return '#e6a23c'
  return '#409eff'
}

function getDifficultyColor(diff: number): string {
  if (diff <= 1) return '#67c23a'
  if (diff <= 2) return '#409eff'
  if (diff <= 3) return '#e6a23c'
  if (diff <= 4) return '#f56c6c'
  return '#909399'
}

const maxWeeklyBarValue = computed(() => {
  if (!data.value?.errorPatterns?.weeklyErrorTrend) return 1
  const max = Math.max(...data.value.errorPatterns.weeklyErrorTrend.map((d) => d.count))
  return max || 1
})

function getWeeklyBarHeight(value: number): string {
  return Math.max(4, (value / maxWeeklyBarValue.value) * 100) + 'px'
}

function getMasteryLevelType(level: number | null): 'danger' | 'warning' | 'success' | 'info' {
  if (level === 0) return 'danger'
  if (level === 1) return 'warning'
  if (level === 2) return 'success'
  return 'info'
}

function getMasteryLevelLabel(level: number | null): string {
  if (level === 0) return '未掌握'
  if (level === 1) return '部分掌握'
  if (level === 2) return '已掌握'
  return '未知'
}

function startRecommendPractice() {
  if (!data.value?.dailyRecommendations?.length) return
  const recommendations = data.value.dailyRecommendations
  Promise.all(recommendations.map((item) => getQuestionById(item.questionId).then((res) => res.data)))
    .then((questions) => {
      sessionStorage.setItem('practice_questions', JSON.stringify(questions))
      sessionStorage.setItem('practice_mode', 'recommended')
      router.push({ path: '/practice/session' })
    })
    .catch(() => {
      ElMessage.error('加载推荐练习失败，请重试')
    })
}

onMounted(async () => {
  try {
    const res = await getLearningDiagnosis()
    data.value = res.data
  } catch (e) {
    ElMessage.error('加载学习诊断失败: ' + errorMessage(e, '未知错误'))
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.learning-diagnosis {
  padding: 0 0 var(--lp-space-6);
}

.page-title {
  font-size: var(--lp-text-2xl);
  font-weight: var(--lp-weight-semibold);
}

.advice-card {
  margin-top: var(--lp-space-5);
}

.advice-content {
  font-size: var(--lp-text-md);
  line-height: var(--lp-leading-relaxed);
  color: var(--lp-text);
}

.advice-content p {
  margin: 0 0 var(--lp-space-1);
}

.stat-row {
  margin-top: var(--lp-space-4);
}

.stat-card {
  text-align: center;
  margin-bottom: var(--lp-space-2);
}

.stat-value {
  font-size: var(--lp-text-4xl);
  font-weight: var(--lp-weight-bold);
  color: var(--lp-primary);
}

.stat-label {
  font-size: var(--lp-text-sm);
  color: var(--lp-text-muted);
  margin-top: var(--lp-space-1);
}

.section-card {
  margin-top: var(--lp-space-4);
}

.chart-container h4 {
  margin: 0 0 var(--lp-space-3);
  font-size: var(--lp-text-base);
  color: var(--lp-text-secondary);
}

.mini-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  height: 160px;
  padding: 0 var(--lp-space-2);
}

.chart-bar-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
}

.chart-bar-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 100px;
}

.chart-bar {
  width: 16px;
  min-height: 2px;
  border-radius: var(--lp-radius-xs) var(--lp-radius-xs) 0 0;
  transition: height var(--lp-duration-slow) var(--lp-ease-out);
}

.chart-bar.correct {
  background: var(--lp-success);
}

.chart-bar.wrong {
  background: var(--lp-danger);
}

.chart-bar.error-trend {
  background: var(--lp-warning);
}

.chart-date {
  font-size: var(--lp-text-xs);
  color: var(--lp-text-muted);
  margin-top: var(--lp-space-1);
}

.chart-total {
  font-size: var(--lp-text-xs);
  font-weight: var(--lp-weight-semibold);
  color: var(--lp-text);
}

.mastery-bars {
  display: flex;
  flex-direction: column;
  gap: var(--lp-space-3);
}

.mastery-item {
  display: flex;
  align-items: center;
  gap: var(--lp-space-3);
}

.mastery-label {
  min-width: 60px;
  font-size: var(--lp-text-sm);
  color: var(--lp-text-secondary);
}

.error-course-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--lp-space-2) 0;
  border-bottom: var(--lp-border-hairline);
}

.error-course-item:last-child {
  border-bottom: none;
}

.course-name {
  font-weight: var(--lp-weight-medium);
}

.similar-source {
  padding: var(--lp-space-3);
  background: var(--lp-surface-soft);
  border-radius: var(--lp-radius-sm);
  font-size: var(--lp-text-sm);
  color: var(--lp-text-secondary);
  line-height: var(--lp-leading-body);
}

.error-analysis-header {
  padding: var(--lp-space-3);
  background: var(--lp-surface-soft);
  border-radius: var(--lp-radius-sm);
}

.error-analysis-question {
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-body);
  color: var(--lp-text);
  margin-bottom: var(--lp-space-2);
}

.error-analysis-tags {
  display: flex;
  gap: var(--lp-space-2);
  flex-wrap: wrap;
}

.error-pattern-box {
  padding: var(--lp-space-3) var(--lp-space-4);
  background: var(--lp-warning-soft);
  border-radius: var(--lp-radius-sm);
  border-left: 3px solid var(--lp-warning);
}

.ai-advice-card {
  margin-top: var(--lp-space-4);
  border-left: 3px solid var(--lp-primary);
}

.ai-advice-content {
  font-size: var(--lp-text-base);
  line-height: var(--lp-leading-relaxed);
  color: var(--lp-text);
}

.ai-advice-content :deep(h1),
.ai-advice-content :deep(h2),
.ai-advice-content :deep(h3) {
  margin-top: var(--lp-space-4);
  margin-bottom: var(--lp-space-2);
  color: var(--lp-text);
}

.ai-advice-content :deep(ul),
.ai-advice-content :deep(ol) {
  padding-left: var(--lp-space-5);
}

.ai-advice-content :deep(p) {
  margin: var(--lp-space-2) 0;
}

.ai-advice-content :deep(code) {
  background: var(--lp-surface-soft);
  padding: var(--lp-space-1) var(--lp-space-2);
  border-radius: var(--lp-radius-xs);
  font-size: var(--lp-text-sm);
}

@media (max-width: 768px) {
  .stat-value {
    font-size: var(--lp-text-2xl);
  }

  .mini-chart {
    height: 120px;
  }

  .chart-bar-wrapper {
    height: 70px;
  }

  .chart-bar {
    width: 10px;
  }
}
</style>
