<template>
  <div class="question-manage admin-page">
    <header class="admin-page-header">
      <div>
        <p class="admin-page-kicker">QUESTION BANK</p>
        <h2>题目管理</h2>
        <p class="admin-page-description">维护正式题库、导入导出和内容复审，重点关注题目来源、状态与 AI 学习资产缓存。</p>
      </div>
      <div class="admin-header-actions">
        <el-dropdown trigger="click">
          <el-button :icon="Download">下载模板 <el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="handleDownloadTemplate">Excel 模板 (.xlsx)</el-dropdown-item>
              <el-dropdown-item @click="handleDownloadMdTemplate">Markdown 模板 (.md)</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button :icon="Upload" @click="importDialogVisible = true">导入题目</el-button>
        <el-button :icon="FolderOpened" @click="handleExport">导出题目</el-button>
        <el-button :icon="Warning" @click="openCorrectionDrawer">纠错记录</el-button>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增题目</el-button>
      </div>
    </header>

    <section class="admin-summary-grid">
      <el-card v-for="item in questionStats" :key="item.label" shadow="never" class="admin-summary-card">
        <span class="admin-summary-icon">
          <el-icon><component :is="item.icon" /></el-icon>
        </span>
        <div class="admin-summary-copy">
          <p class="admin-summary-label">{{ item.label }}</p>
          <div class="admin-summary-value">{{ item.value }}</div>
          <div class="admin-summary-note">{{ item.note }}</div>
        </div>
      </el-card>
    </section>

    <el-card shadow="never" class="admin-table-card">
      <div class="admin-toolbar">
        <div class="admin-filter-group">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索题干内容"
            :prefix-icon="Search"
            clearable
            style="width: 220px"
            @clear="fetchQuestions"
            @keyup.enter="fetchQuestions"
          />
          <el-select v-model="filters.questionType" placeholder="题型" clearable style="width: 130px" @change="fetchQuestions">
            <el-option label="单选题" value="SINGLE_CHOICE" />
            <el-option label="多选题" value="MULTIPLE_CHOICE" />
            <el-option label="判断题" value="TRUE_FALSE" />
            <el-option label="填空题" value="FILL_BLANK" />
            <el-option label="简答题" value="SHORT_ANSWER" />
          </el-select>
          <el-select v-model="filters.courseId" placeholder="所属课程" clearable style="width: 180px" @change="fetchQuestions">
            <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-select v-model="filters.difficulty" placeholder="难度" clearable style="width: 110px" @change="fetchQuestions">
            <el-option v-for="d in 5" :key="d" :label="'⭐'.repeat(d)" :value="d" />
          </el-select>
          <el-select v-model="filters.sourceType" placeholder="来源" clearable style="width: 130px" @change="fetchQuestions">
            <el-option label="手动创建" value="MANUAL" />
            <el-option label="投稿入库" value="SUBMISSION" />
            <el-option label="Excel导入" value="EXCEL_IMPORT" />
            <el-option label="Markdown导入" value="MARKDOWN_IMPORT" />
            <el-option label="AI生成" value="AI_GENERATED" />
          </el-select>
          <el-button :icon="Search" @click="fetchQuestions">查询</el-button>
          <el-button :icon="Connection" :loading="duplicateLoading" @click="handleDetectDuplicates">重复检测</el-button>
        </div>
        <span class="table-summary">当前筛选 {{ total }} 道题</span>
      </div>

      <div v-if="selectedQuestions.length" class="admin-bulk-bar">
        <span class="admin-bulk-copy">已选择 <strong>{{ selectedQuestions.length }}</strong> 道题目</span>
        <div class="admin-bulk-actions">
          <el-button size="small" :icon="DeleteFilled" @click="handleBulkClearAiCache">批量清缓存</el-button>
          <el-button size="small" type="danger" :icon="Delete" @click="handleBulkDelete">批量删除</el-button>
          <el-button size="small" @click="clearQuestionSelection">清空选择</el-button>
        </div>
      </div>

      <el-table
        ref="questionTableRef"
        :data="questions"
        v-loading="loading"
        stripe
        class="admin-data-table"
        @selection-change="handleQuestionSelectionChange"
      >
        <el-table-column type="selection" width="44" />
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="content" label="题干" min-width="240" show-overflow-tooltip />
        <el-table-column prop="questionType" label="题型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="questionTypeTag((row as QuestionVO).questionType)">
              {{ questionTypeLabel((row as QuestionVO).questionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="courseName" label="课程" width="130" show-overflow-tooltip />
        <el-table-column prop="difficulty" label="难度" width="90" align="center">
          <template #default="{ row }">
            {{ '⭐'.repeat((row as QuestionVO).difficulty) }}
          </template>
        </el-table-column>
        <el-table-column prop="sourceType" label="来源" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="sourceTypeTag((row as QuestionVO).sourceType)">
              {{ sourceTypeLabel((row as QuestionVO).sourceType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="分值" width="70" align="center" />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="(row as QuestionVO).status === 1 ? 'success' : 'info'" size="small">
              {{ (row as QuestionVO).status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="185" fixed="right">
          <template #default="{ row }">
            <div class="admin-row-actions">
              <el-button type="primary" link size="small" :icon="RefreshRight" @click="openReReview(row as QuestionVO)">复审</el-button>
              <el-button type="primary" link size="small" :icon="Edit" @click="openDialog(row as QuestionVO)">编辑</el-button>
              <el-dropdown trigger="click" @command="command => handleQuestionRowCommand(command as string, row as QuestionVO)">
                <el-button link size="small" :icon="MoreFilled">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="versions" :icon="Clock">版本记录</el-dropdown-item>
                    <el-dropdown-item command="cache" :icon="DeleteFilled">清缓存</el-dropdown-item>
                    <el-dropdown-item command="delete" :icon="Delete" class="danger-dropdown-item">删除题目</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty class="admin-table-empty" description="没有匹配的题目">
            <el-button type="primary" :icon="Plus" @click="openDialog()">新增题目</el-button>
          </el-empty>
        </template>
      </el-table>

      <div class="admin-pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="fetchQuestions"
          @size-change="fetchQuestions"
        />
      </div>

    <!-- 导入结果弹窗 -->
    <el-dialog v-model="importResultVisible" title="导入结果" width="500px" destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="总行数">{{ importResult.totalRows }}</el-descriptions-item>
        <el-descriptions-item label="成功数">
          <el-tag type="success">{{ importResult.successCount }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="失败数">
          <el-tag :type="importResult.failCount > 0 ? 'danger' : 'success'">{{ importResult.failCount }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <div v-if="importResult.errors.length > 0" style="margin-top: 12px;">
        <p style="color: #f56c6c; margin-bottom: 8px;">错误详情：</p>
        <el-scrollbar max-height="200px">
          <p v-for="(err, idx) in importResult.errors" :key="idx" style="font-size: 13px; color: #606266; margin: 4px 0;">
            {{ err }}
          </p>
        </el-scrollbar>
      </div>
      <template #footer>
        <el-button type="primary" @click="importResultVisible = false">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入弹窗 -->
    <el-dialog v-model="importDialogVisible" title="导入题目" width="540px" destroy-on-close>
      <el-tabs v-model="importTab">
        <el-tab-pane label="Excel 导入" name="excel">
          <el-upload
            ref="uploadRef"
            drag
            accept=".xlsx,.xls"
            :auto-upload="false"
            :limit="1"
            :on-change="onImportFileChange"
            :on-exceed="() => ElMessage.warning('只能上传一个文件')"
          >
            <el-icon style="font-size: 40px; color: #c0c4cc; margin-bottom: 8px;"><Upload /></el-icon>
            <div>将 Excel 文件拖到此处，或<em>点击上传</em></div>
            <template #tip>
              <div style="color: #909399; font-size: 12px; margin-top: 4px;">
                仅支持 .xlsx / .xls 文件，可先<a href="javascript:void(0)" @click.stop="handleDownloadTemplate" style="color: #409eff">下载模板</a>
              </div>
            </template>
          </el-upload>
        </el-tab-pane>
        <el-tab-pane label="Markdown 导入" name="markdown">
          <el-upload
            ref="mdUploadRef"
            drag
            accept=".md,.markdown"
            :auto-upload="false"
            :limit="1"
            :on-change="onMdFileChange"
            :on-exceed="() => ElMessage.warning('只能上传一个文件')"
          >
            <el-icon style="font-size: 40px; color: #c0c4cc; margin-bottom: 8px;"><Upload /></el-icon>
            <div>将 Markdown 文件拖到此处，或<em>点击上传</em></div>
            <template #tip>
              <div style="color: #909399; font-size: 12px; margin-top: 4px;">
                仅支持 .md / .markdown 文件，可先<a href="javascript:void(0)" @click.stop="handleDownloadMdTemplate" style="color: #409eff">下载模板</a>
              </div>
            </template>
          </el-upload>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImport">开始导入</el-button>
      </template>
    </el-dialog>
    </el-card>

    <!-- 复审弹窗 -->
    <el-dialog v-model="reReviewVisible" title="题目复审" width="700px" destroy-on-close>
      <div v-if="reReviewQuestion" style="margin-bottom: 16px;">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="题目ID">{{ reReviewQuestion.id }}</el-descriptions-item>
          <el-descriptions-item label="来源">
            <el-tag size="small" :type="sourceTypeTag(reReviewQuestion.sourceType)">
              {{ sourceTypeLabel(reReviewQuestion.sourceType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="题型">{{ questionTypeLabel(reReviewQuestion.questionType) }}</el-descriptions-item>
          <el-descriptions-item label="难度">{{ '⭐'.repeat(reReviewQuestion.difficulty) }}</el-descriptions-item>
          <el-descriptions-item label="累计复审">{{ reReviewQuestion.reviewRounds ?? 0 }} 次</el-descriptions-item>
          <el-descriptions-item label="下次复审">{{ reReviewQuestion.nextReviewTime ?? '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="题干" :span="2">
            <div style="max-height: 120px; overflow-y: auto; white-space: pre-wrap;">{{ reReviewQuestion.content }}</div>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <el-form :model="reReviewForm" label-width="90px">
        <div class="review-suggestion-actions">
          <el-button :icon="DataAnalysis" :loading="reviewSuggestionLoading" @click="handleReviewSuggestion">
            AI 复审建议
          </el-button>
          <span v-if="reviewSuggestion" class="review-suggestion-meta">
            建议：{{ reviewActionLabel(reviewSuggestion.recommendation) }} · 置信分 {{ reviewSuggestion.confidenceScore }}
          </span>
        </div>

        <el-alert
          v-if="reviewSuggestion"
          class="review-suggestion-panel"
          :type="reviewSuggestion.recommendation === 'REJECT' ? 'error' : reviewSuggestion.recommendation === 'REVISE' ? 'warning' : 'success'"
          :closable="false"
          show-icon
        >
          <template #title>{{ reviewSuggestion.summary }}</template>
          <div class="review-suggestion-content">
            <p v-if="reviewSuggestion.answerAnalysis">{{ reviewSuggestion.answerAnalysis }}</p>
            <p v-if="reviewSuggestion.knowledgeAnalysis">{{ reviewSuggestion.knowledgeAnalysis }}</p>
            <div v-if="reviewSuggestion.riskPoints?.length">
              <strong>风险点</strong>
              <ul>
                <li v-for="item in reviewSuggestion.riskPoints" :key="item">{{ item }}</li>
              </ul>
            </div>
            <div v-if="reviewSuggestion.suggestions?.length">
              <strong>修订建议</strong>
              <ul>
                <li v-for="item in reviewSuggestion.suggestions" :key="item">{{ item }}</li>
              </ul>
            </div>
            <el-button size="small" text type="primary" @click="applyReviewSuggestion">应用到表单</el-button>
          </div>
        </el-alert>

        <el-form-item label="复审动作">
          <el-radio-group v-model="reReviewForm.action">
            <el-radio-button value="APPROVE">通过</el-radio-button>
            <el-radio-button value="REVISE">修订</el-radio-button>
            <el-radio-button value="REJECT">废弃</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="reReviewForm.action === 'REVISE'" label="修订内容">
          <el-input v-model="reReviewForm.newContent" type="textarea" :rows="3" placeholder="修订后的题干" />
        </el-form-item>
        <el-form-item v-if="reReviewForm.action === 'REVISE'" label="修订难度">
          <el-rate v-model="reReviewForm.newDifficulty" :max="5" />
        </el-form-item>
        <el-form-item label="复审意见">
          <el-input v-model="reReviewForm.comment" type="textarea" :rows="2" placeholder="请输入复审意见" />
        </el-form-item>
      </el-form>

      <div v-if="reviewRecords.length > 0" style="margin-top: 12px;">
        <h4 style="margin-bottom: 8px;">历史复审记录</h4>
        <el-timeline>
          <el-timeline-item
            v-for="record in reviewRecords"
            :key="record.id"
            :timestamp="record.createTime"
            placement="top"
          >
            <el-card shadow="never" body-style="padding: 8px 12px;">
              <div style="display: flex; gap: 8px; align-items: center;">
                <el-tag size="small" :type="record.action === 'APPROVE' ? 'success' : record.action === 'REJECT' ? 'danger' : 'warning'">
                  {{ record.action === 'APPROVE' ? '通过' : record.action === 'REVISE' ? '修订' : '废弃' }}
                </el-tag>
                <span style="font-size: 12px; color: #909399;">{{ record.reviewerName }} · {{ record.reviewType }}</span>
              </div>
              <div style="margin-top: 4px; font-size: 13px;">{{ record.comment }}</div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>

      <template #footer>
        <el-button @click="reReviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reReviewLoading" @click="handleReReview">提交复审</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingQuestion ? '编辑题目' : '新增题目'"
      width="780px"
      destroy-on-close
      top="5vh"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="90px"
        @submit.prevent
      >
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属课程" prop="courseId">
              <el-select v-model="form.courseId" placeholder="选择课程" style="width: 100%">
                <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="题型" prop="questionType">
              <el-select v-model="form.questionType" placeholder="选择题型" style="width: 100%" @change="onTypeChange">
                <el-option label="单选题" value="SINGLE_CHOICE" />
                <el-option label="多选题" value="MULTIPLE_CHOICE" />
                <el-option label="判断题" value="TRUE_FALSE" />
                <el-option label="填空题" value="FILL_BLANK" />
                <el-option label="简答题" value="SHORT_ANSWER" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="题干内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="3"
            placeholder="请输入题干内容（支持 Markdown）"
          />
        </el-form-item>

        <!-- 选项区域（单选/多选/判断题） -->
        <el-form-item
          v-if="showOptions"
          label="选项"
          prop="options"
        >
          <div class="options-area">
            <div v-for="(opt, idx) in form.options" :key="idx" class="option-row">
              <el-input
                v-model="opt.content"
                :placeholder="'选项 ' + opt.optionLabel"
                style="flex: 1"
              />
              <el-checkbox v-model="opt.isCorrect" :true-value="1" :false-value="0" class="option-correct">
                正确
              </el-checkbox>
              <el-button
                :icon="Delete"
                circle
                size="small"
                type="danger"
                @click="removeOption(idx)"
                :disabled="form.options.length <= 2"
              />
            </div>
            <el-button type="primary" link @click="addOption" :disabled="form.options.length >= 8">
              + 添加选项
            </el-button>
          </div>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="难度" prop="difficulty">
              <el-rate v-model="form.difficulty" :max="5" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="分值" prop="score">
              <el-input-number v-model="form.score" :min="1" :max="100" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="标签" prop="tags">
              <el-input v-model="form.tags" placeholder="逗号分隔" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="题目解析">
          <el-input
            v-model="form.analysis"
            type="textarea"
            :rows="3"
            placeholder="请输入题目解析（选填）"
          />
        </el-form-item>

        <el-form-item label="关联知识点">
          <el-tree-select
            v-model="form.knowledgePointIds"
            :data="kpTreeData"
            :props="{ label: 'name', value: 'id', children: 'children' } as any"
            multiple
            check-strictly
            clearable
            placeholder="选择关联知识点"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ editingQuestion ? '更新' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 疑似重复题目抽屉 -->
    <el-drawer v-model="duplicateDrawerVisible" title="疑似重复题目" size="560px" destroy-on-close>
      <div class="duplicate-drawer">
        <div class="duplicate-summary">
          <span>按当前课程与题型筛选检测</span>
          <strong>{{ duplicateGroups.length }}</strong>
          <span>组疑似重复</span>
        </div>
        <el-empty v-if="duplicateGroups.length === 0" description="当前筛选下暂未发现疑似重复题目" />
        <div v-else class="duplicate-group-list">
          <el-card v-for="group in duplicateGroups" :key="group.questions.map(q => q.id).join('-')" shadow="never" class="duplicate-group-card">
            <div class="duplicate-group-head">
              <el-tag :type="group.matchType === 'EXACT' ? 'danger' : 'warning'" size="small">
                {{ group.matchType === 'EXACT' ? '精确重复' : '高相似' }}
              </el-tag>
              <span>相似度 {{ group.similarityScore }}%</span>
            </div>
            <p class="duplicate-representative">{{ group.representativeContent }}</p>
            <div class="duplicate-question-list">
              <div v-for="question in group.questions" :key="question.id" class="duplicate-question-item">
                <div>
                  <strong>#{{ question.id }}</strong>
                  <span>{{ question.courseName || '未命名课程' }} · {{ questionTypeLabel(question.questionType) }}</span>
                </div>
                <p>{{ question.content }}</p>
                <div class="duplicate-question-actions">
                  <el-button size="small" text type="primary" :icon="Edit" @click="openDialog(question)">编辑</el-button>
                  <el-button size="small" text type="primary" :icon="RefreshRight" @click="openReReview(question)">复审</el-button>
                </div>
              </div>
            </div>
          </el-card>
        </div>
      </div>
    </el-drawer>

    <!-- 题目纠错记录抽屉 -->
    <el-drawer v-model="correctionDrawerVisible" title="题目纠错记录" size="560px" destroy-on-close>
      <div class="correction-drawer">
        <div class="correction-toolbar">
          <el-select v-model="correctionFilters.status" placeholder="处理状态" clearable style="width: 150px" @change="fetchCorrectionReports">
            <el-option label="待处理" value="OPEN" />
            <el-option label="已处理" value="RESOLVED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
          <el-button :icon="RefreshRight" :loading="correctionLoading" @click="fetchCorrectionReports">刷新</el-button>
        </div>
        <div v-loading="correctionLoading" class="correction-list">
          <el-empty v-if="correctionReports.length === 0 && !correctionLoading" description="暂无题目纠错记录" />
          <el-card v-for="report in correctionReports" :key="report.id" shadow="never" class="correction-card">
            <div class="correction-card-head">
              <div>
                <strong>#{{ report.questionId }}</strong>
                <span>{{ reportTypeLabel(report.reportType) }} · {{ report.reporterName || '用户' }}</span>
              </div>
              <el-tag size="small" :type="correctionStatusTag(report.status)">
                {{ correctionStatusLabel(report.status) }}
              </el-tag>
            </div>
            <p class="correction-question-content">{{ report.questionContent || '题目内容不可用' }}</p>
            <p class="correction-description">{{ report.description }}</p>
            <div v-if="report.handlerComment" class="correction-handler">
              {{ report.handlerName || '管理员' }}：{{ report.handlerComment }}
            </div>
            <div class="correction-card-actions">
              <span>{{ report.createTime }}</span>
              <div v-if="report.status === 'OPEN'">
                <el-button size="small" text type="primary" @click="handleProcessCorrection(report, 'RESOLVED')">标记已处理</el-button>
                <el-button size="small" text type="danger" @click="handleProcessCorrection(report, 'REJECTED')">驳回</el-button>
              </div>
            </div>
          </el-card>
        </div>
        <div class="admin-pagination">
          <el-pagination
            v-model:current-page="correctionPageNum"
            v-model:page-size="correctionPageSize"
            :total="correctionTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, prev, pager, next"
            @current-change="fetchCorrectionReports"
            @size-change="fetchCorrectionReports"
          />
        </div>
      </div>
    </el-drawer>

    <!-- 题目版本记录抽屉 -->
    <el-drawer v-model="versionDrawerVisible" title="题目版本记录" size="620px" destroy-on-close>
      <div class="version-drawer">
        <div class="version-summary">
          <span v-if="versionQuestion">#{{ versionQuestion.id }} · {{ versionQuestion.content }}</span>
          <el-button :icon="RefreshRight" :loading="versionLoading" @click="fetchQuestionVersions">刷新</el-button>
        </div>
        <div v-loading="versionLoading" class="version-list">
          <el-empty v-if="questionVersions.length === 0 && !versionLoading" description="暂无版本记录" />
          <el-timeline v-else>
            <el-timeline-item
              v-for="version in questionVersions"
              :key="version.id"
              :timestamp="version.createTime"
              placement="top"
            >
              <el-card shadow="never" class="version-card">
                <div class="version-card-head">
                  <div>
                    <strong>v{{ version.versionNo }}</strong>
                    <span>{{ changeTypeLabel(version.changeType) }} · {{ version.operatorName || '系统' }}</span>
                  </div>
                  <el-tag size="small" :type="changeTypeTag(version.changeType)">
                    {{ changeTypeLabel(version.changeType) }}
                  </el-tag>
                </div>
                <p v-if="version.changeSummary" class="version-summary-text">{{ version.changeSummary }}</p>
                <el-collapse>
                  <el-collapse-item v-if="version.snapshotBefore" title="变更前快照" name="before">
                    <pre class="snapshot-block">{{ formatSnapshot(version.snapshotBefore) }}</pre>
                  </el-collapse-item>
                  <el-collapse-item v-if="version.snapshotAfter" title="变更后快照" name="after">
                    <pre class="snapshot-block">{{ formatSnapshot(version.snapshotAfter) }}</pre>
                  </el-collapse-item>
                </el-collapse>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus, Search, Delete, Download, Upload, FolderOpened, ArrowDown, Edit, RefreshRight, DeleteFilled, Collection, DataAnalysis, DocumentChecked, MoreFilled, Connection, Warning, Clock } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, TableInstance, UploadFile } from 'element-plus'
import {
  getAdminQuestionPage,
  createQuestion,
  updateQuestion,
  deleteQuestion,
  exportQuestions,
  downloadTemplate,
  importQuestions,
  importQuestionsMarkdown,
  downloadMarkdownTemplate,
  getReviewRecords,
  getReviewSuggestion,
  performReReview,
  detectDuplicateQuestions,
  getQuestionVersions,
  getAdminQuestionCorrectionReports,
  processQuestionCorrectionReport,
  type QuestionVO,
  type QuestionForm,
  type OptionItem,
  type QuestionImportResult,
  type QuestionReviewRecordVO,
  type QuestionReviewSuggestionVO,
  type QuestionDuplicateGroupVO,
  type QuestionCorrectionReportVO,
  type QuestionVersionVO,
} from '@/api/question'
import { clearAssetCache } from '@/api/ai'
import { getAllCourses, type CourseVO } from '@/api/course'
import { getKnowledgeTree, type KnowledgePointVO } from '@/api/knowledgePoint'

const questions = ref<QuestionVO[]>([])
const questionTableRef = ref<TableInstance>()
const selectedQuestions = ref<QuestionVO[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({
  keyword: '',
  questionType: '' as string,
  courseId: null as number | null,
  difficulty: null as number | null,
  sourceType: '' as string,
})

// 课程列表
const courseList = ref<CourseVO[]>([])

// 知识点树
const kpTreeData = ref<KnowledgePointVO[]>([])

// 导入/导出相关
const importDialogVisible = ref(false)
const importResultVisible = ref(false)
const importLoading = ref(false)
const importFile = ref<File | null>(null)
const uploadRef = ref()
const mdUploadRef = ref()
const importTab = ref('excel')
const importResult = reactive<QuestionImportResult>({
  totalRows: 0,
  successCount: 0,
  failCount: 0,
  errors: [],
})

// 复审相关
const reReviewVisible = ref(false)
const reReviewQuestion = ref<QuestionVO | null>(null)
const reReviewLoading = ref(false)
const reviewRecords = ref<QuestionReviewRecordVO[]>([])
const reviewSuggestion = ref<QuestionReviewSuggestionVO | null>(null)
const reviewSuggestionLoading = ref(false)
const duplicateDrawerVisible = ref(false)
const duplicateLoading = ref(false)
const duplicateGroups = ref<QuestionDuplicateGroupVO[]>([])
const correctionDrawerVisible = ref(false)
const correctionLoading = ref(false)
const correctionReports = ref<QuestionCorrectionReportVO[]>([])
const correctionPageNum = ref(1)
const correctionPageSize = ref(10)
const correctionTotal = ref(0)
const correctionFilters = reactive({
  status: 'OPEN',
})
const versionDrawerVisible = ref(false)
const versionLoading = ref(false)
const versionQuestion = ref<QuestionVO | null>(null)
const questionVersions = ref<QuestionVersionVO[]>([])
const reReviewForm = reactive({
  action: 'APPROVE',
  newContent: '',
  newDifficulty: 3,
  comment: '',
})

// 弹窗相关
const dialogVisible = ref(false)
const editingQuestion = ref<QuestionVO | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  content: '',
  questionType: '' as string,
  courseId: null as number | null,
  difficulty: 3,
  analysis: '',
  tags: '',
  score: 1,
  options: [] as OptionItem[],
  knowledgePointIds: [] as number[],
})

const rules: FormRules = {
  content: [{ required: true, message: '请输入题干内容', trigger: 'blur' }],
  questionType: [{ required: true, message: '请选择题型', trigger: 'change' }],
  courseId: [{ required: true, message: '请选择所属课程', trigger: 'change' }],
}

const questionStats = computed(() => {
  const enabled = questions.value.filter(q => q.status === 1).length
  const reviewable = questions.value.filter(q => q.sourceType && q.sourceType !== 'MANUAL').length
  const avgScore = questions.value.length
    ? Math.round(questions.value.reduce((sum, q) => sum + (q.score || 0), 0) / questions.value.length)
    : 0
  return [
    { label: '筛选总量', value: total.value, note: `当前页 ${questions.value.length} 道`, icon: Collection },
    { label: '当前页启用', value: enabled, note: `${questions.value.length - enabled} 道禁用`, icon: DocumentChecked },
    { label: '来源追踪', value: reviewable, note: '当前页非手动来源', icon: RefreshRight },
    { label: '平均分值', value: avgScore || '-', note: '当前页题目均分', icon: DataAnalysis },
  ]
})

const handleQuestionRowCommand = async (command: string, question: QuestionVO) => {
  if (command === 'cache') {
    try {
      await ElMessageBox.confirm('确定清除该题目的 AI 学习资产缓存？', '清除缓存', {
        type: 'warning',
        confirmButtonText: '清除',
        cancelButtonText: '取消',
      })
      await handleClearAiCache(question.id)
    } catch {
      // 用户取消确认时不提示错误。
    }
    return
  }
  if (command === 'versions') {
    await openVersionDrawer(question)
    return
  }
  if (command === 'delete') {
    try {
      await ElMessageBox.confirm('确定删除该题目？', '删除题目', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      })
      await handleDelete(question.id)
    } catch {
      // 用户取消确认时不提示错误。
    }
  }
}

const handleQuestionSelectionChange = (selection: QuestionVO[]) => {
  selectedQuestions.value = selection
}

const clearQuestionSelection = () => {
  questionTableRef.value?.clearSelection()
}

// 是否显示选项区域
const showOptions = computed(() => {
  return ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'].includes(form.questionType)
})

// 题型标签
function questionTypeLabel(type: string) {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选',
    MULTIPLE_CHOICE: '多选',
    TRUE_FALSE: '判断',
    FILL_BLANK: '填空',
    SHORT_ANSWER: '简答',
  }
  return map[type] || type
}

function questionTypeTag(type: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    SINGLE_CHOICE: 'primary',
    MULTIPLE_CHOICE: 'success',
    TRUE_FALSE: 'warning',
    FILL_BLANK: 'info',
    SHORT_ANSWER: 'danger',
  }
  return map[type] || 'primary'
}

// 来源标签
function sourceTypeLabel(type?: string) {
  const map: Record<string, string> = {
    MANUAL: '手动创建',
    SUBMISSION: '投稿入库',
    EXCEL_IMPORT: 'Excel导入',
    MARKDOWN_IMPORT: 'MD导入',
    AI_GENERATED: 'AI生成',
  }
  return map[type || ''] || '手动创建'
}

function sourceTypeTag(type?: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    MANUAL: 'primary',
    SUBMISSION: 'success',
    EXCEL_IMPORT: 'warning',
    MARKDOWN_IMPORT: 'info',
    AI_GENERATED: 'danger',
  }
  return map[type || ''] || 'info'
}

function reviewActionLabel(action: string) {
  const map: Record<string, string> = {
    APPROVE: '通过',
    REVISE: '修订',
    REJECT: '废弃',
  }
  return map[action] || action
}

function reportTypeLabel(type: string) {
  const map: Record<string, string> = {
    CONTENT: '题干问题',
    ANSWER: '答案问题',
    ANALYSIS: '解析问题',
    KNOWLEDGE_POINT: '知识点问题',
    OTHER: '其他问题',
  }
  return map[type] || type
}

function correctionStatusLabel(status: string) {
  const map: Record<string, string> = {
    OPEN: '待处理',
    RESOLVED: '已处理',
    REJECTED: '已驳回',
  }
  return map[status] || status
}

function correctionStatusTag(status: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    OPEN: 'warning',
    RESOLVED: 'success',
    REJECTED: 'info',
  }
  return map[status] || 'info'
}

function changeTypeLabel(type: string) {
  const map: Record<string, string> = {
    CREATE: '创建',
    UPDATE: '更新',
    DELETE: '删除',
    REVIEW_APPROVE: '复审通过',
    REVIEW_REVISE: '复审修订',
    REVIEW_REJECT: '复审废弃',
  }
  return map[type] || type
}

function changeTypeTag(type: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    CREATE: 'success',
    UPDATE: 'primary',
    DELETE: 'danger',
    REVIEW_APPROVE: 'success',
    REVIEW_REVISE: 'warning',
    REVIEW_REJECT: 'danger',
  }
  return map[type] || 'info'
}

function formatSnapshot(snapshot?: string) {
  if (!snapshot) return ''
  try {
    return JSON.stringify(JSON.parse(snapshot), null, 2)
  } catch {
    return snapshot
  }
}

// 打开复审弹窗
async function openReReview(question: QuestionVO) {
  reReviewQuestion.value = question
  reReviewForm.action = 'APPROVE'
  reReviewForm.newContent = question.content
  reReviewForm.newDifficulty = question.difficulty
  reReviewForm.comment = ''
  reviewRecords.value = []
  reviewSuggestion.value = null
  reReviewVisible.value = true
  try {
    const res = await getReviewRecords(question.id)
    reviewRecords.value = res.data
  } catch {
    // ignore
  }
}

async function handleReviewSuggestion() {
  if (!reReviewQuestion.value) return
  reviewSuggestionLoading.value = true
  try {
    const res = await getReviewSuggestion(reReviewQuestion.value.id)
    reviewSuggestion.value = res.data
    ElMessage.success('AI 复审建议已生成')
  } catch {
    // error handled by interceptor
  } finally {
    reviewSuggestionLoading.value = false
  }
}

function applyReviewSuggestion() {
  if (!reviewSuggestion.value) return
  reReviewForm.action = reviewSuggestion.value.recommendation
  if (reviewSuggestion.value.recommendation === 'REVISE') {
    reReviewForm.newContent = reviewSuggestion.value.suggestedContent || reReviewQuestion.value?.content || ''
    reReviewForm.newDifficulty = reviewSuggestion.value.suggestedDifficulty || reReviewQuestion.value?.difficulty || 3
  }
  reReviewForm.comment = reviewSuggestion.value.summary
  ElMessage.success('已填入复审表单')
}

// 提交复审
async function handleReReview() {
  if (!reReviewForm.comment.trim()) {
    ElMessage.warning('请输入复审意见')
    return
  }
  if (reReviewForm.action === 'REVISE' && !reReviewForm.newContent.trim()) {
    ElMessage.warning('修订时新题干不能为空')
    return
  }
  reReviewLoading.value = true
  try {
    await performReReview(reReviewQuestion.value!.id, {
      action: reReviewForm.action,
      newContent: reReviewForm.action === 'REVISE' ? reReviewForm.newContent : undefined,
      newDifficulty: reReviewForm.action === 'REVISE' ? reReviewForm.newDifficulty : undefined,
      comment: reReviewForm.comment,
    })
    ElMessage.success('复审完成')
    reReviewVisible.value = false
    fetchQuestions()
  } catch {
    // error handled by interceptor
  } finally {
    reReviewLoading.value = false
  }
}

// 获取选项标签 A/B/C/D...
function getOptionLabel(index: number): string {
  return String.fromCharCode(65 + index)
}

// 题型切换时重置选项
function onTypeChange(type: string) {
  if (type === 'TRUE_FALSE') {
    form.options = [
      { content: '正确', optionLabel: 'A', isCorrect: 1, sortOrder: 0 },
      { content: '错误', optionLabel: 'B', isCorrect: 0, sortOrder: 1 },
    ]
  } else if (['SINGLE_CHOICE', 'MULTIPLE_CHOICE'].includes(type)) {
    if (form.options.length < 2 || form.options[0].content === '正确') {
      form.options = [
        { content: '', optionLabel: 'A', isCorrect: 0, sortOrder: 0 },
        { content: '', optionLabel: 'B', isCorrect: 0, sortOrder: 1 },
        { content: '', optionLabel: 'C', isCorrect: 0, sortOrder: 2 },
        { content: '', optionLabel: 'D', isCorrect: 0, sortOrder: 3 },
      ]
    }
  } else {
    form.options = []
  }
}

function addOption() {
  const idx = form.options.length
  form.options.push({
    content: '',
    optionLabel: getOptionLabel(idx),
    isCorrect: 0,
    sortOrder: idx,
  })
}

function removeOption(idx: number) {
  form.options.splice(idx, 1)
  // 重新分配标签
  form.options.forEach((opt, i) => {
    opt.optionLabel = getOptionLabel(i)
    opt.sortOrder = i
  })
}

async function fetchQuestions() {
  loading.value = true
  try {
    const res = await getAdminQuestionPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: filters.keyword || undefined,
      questionType: filters.questionType || undefined,
      courseId: filters.courseId || undefined,
      difficulty: filters.difficulty || undefined,
      sourceType: filters.sourceType || undefined,
    })
    questions.value = res.data.records
    total.value = res.data.total
    selectedQuestions.value = []
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

async function handleDetectDuplicates() {
  duplicateLoading.value = true
  try {
    const res = await detectDuplicateQuestions({
      courseId: filters.courseId || undefined,
      questionType: filters.questionType || undefined,
      minSimilarity: 92,
      limit: 20,
    })
    duplicateGroups.value = res.data
    duplicateDrawerVisible.value = true
    if (res.data.length > 0) {
      ElMessage.warning(`发现 ${res.data.length} 组疑似重复题目`)
    } else {
      ElMessage.success('未发现疑似重复题目')
    }
  } catch {
    // 错误已在拦截器中处理
  } finally {
    duplicateLoading.value = false
  }
}

async function openCorrectionDrawer() {
  correctionDrawerVisible.value = true
  correctionPageNum.value = 1
  await fetchCorrectionReports()
}

async function fetchCorrectionReports() {
  correctionLoading.value = true
  try {
    const res = await getAdminQuestionCorrectionReports({
      pageNum: correctionPageNum.value,
      pageSize: correctionPageSize.value,
      status: correctionFilters.status || undefined,
    })
    correctionReports.value = res.data.records
    correctionTotal.value = res.data.total
  } catch {
    // 错误已在拦截器中处理
  } finally {
    correctionLoading.value = false
  }
}

async function handleProcessCorrection(report: QuestionCorrectionReportVO, status: 'RESOLVED' | 'REJECTED') {
  const title = status === 'RESOLVED' ? '标记已处理' : '驳回纠错'
  const message = status === 'RESOLVED' ? '请输入处理说明' : '请输入驳回原因'
  try {
    const result = await ElMessageBox.prompt(message, title, {
      confirmButtonText: '提交',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPattern: /\S+/,
      inputErrorMessage: '处理说明不能为空',
    })
    await processQuestionCorrectionReport(report.id, {
      status,
      handlerComment: result.value.trim(),
    })
    ElMessage.success(status === 'RESOLVED' ? '已标记处理完成' : '已驳回纠错')
    await fetchCorrectionReports()
  } catch {
    // 用户取消确认时不提示错误。
  }
}

async function openVersionDrawer(question: QuestionVO) {
  versionQuestion.value = question
  versionDrawerVisible.value = true
  await fetchQuestionVersions()
}

async function fetchQuestionVersions() {
  if (!versionQuestion.value) return
  versionLoading.value = true
  try {
    const res = await getQuestionVersions(versionQuestion.value.id)
    questionVersions.value = res.data
  } catch {
    // 错误已在拦截器中处理
  } finally {
    versionLoading.value = false
  }
}

async function fetchCourses() {
  try {
    const res = await getAllCourses()
    courseList.value = res.data
  } catch {
    // ignore
  }
}

async function fetchKPTree(courseId?: number) {
  try {
    if (!courseId) {
      kpTreeData.value = []
      return
    }
    const res = await getKnowledgeTree(courseId)
    kpTreeData.value = res.data
  } catch {
    kpTreeData.value = []
  }
}

function openDialog(question?: QuestionVO) {
  editingQuestion.value = question || null
  if (question) {
    form.content = question.content
    form.questionType = question.questionType
    form.courseId = question.courseId
    form.difficulty = question.difficulty
    form.analysis = question.analysis || ''
    form.tags = question.tags || ''
    form.score = question.score
    form.options = question.options.map(o => ({ ...o }))
    form.knowledgePointIds = [...(question.knowledgePointIds || [])]
  } else {
    form.content = ''
    form.questionType = ''
    form.courseId = null
    form.difficulty = 3
    form.analysis = ''
    form.tags = ''
    form.score = 1
    form.options = []
    form.knowledgePointIds = []
  }
  dialogVisible.value = true
  // 加载知识点树
  if (form.courseId) {
    fetchKPTree(form.courseId)
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  // 校验选项
  if (showOptions.value) {
    const hasCorrect = form.options.some(o => o.isCorrect === 1)
    if (!hasCorrect) {
      ElMessage.warning('请至少设置一个正确选项')
      return
    }
    const hasEmpty = form.options.some(o => !o.content.trim())
    if (hasEmpty) {
      ElMessage.warning('选项内容不能为空')
      return
    }
  }

  submitting.value = true
  try {
    const data: QuestionForm = {
      content: form.content,
      questionType: form.questionType,
      courseId: form.courseId!,
      difficulty: form.difficulty,
      analysis: form.analysis || undefined,
      tags: form.tags || undefined,
      score: form.score,
      options: showOptions.value ? form.options : undefined,
      knowledgePointIds: form.knowledgePointIds.length > 0 ? form.knowledgePointIds : undefined,
    }

    if (editingQuestion.value) {
      await updateQuestion(editingQuestion.value.id, data)
      ElMessage.success('更新成功')
    } else {
      await createQuestion(data)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchQuestions()
  } catch {
    // 错误已在拦截器中处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await deleteQuestion(id)
    ElMessage.success('删除成功')
    fetchQuestions()
  } catch {
    // 错误已在拦截器中处理
  }
}

async function handleBulkDelete() {
  if (!selectedQuestions.value.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedQuestions.value.length} 道题目？此操作不可恢复。`, '批量删除题目', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    loading.value = true
    const targets = [...selectedQuestions.value]
    const results = await Promise.allSettled(targets.map(question => deleteQuestion(question.id)))
    const failed = results.filter(result => result.status === 'rejected').length
    if (failed > 0) {
      ElMessage.warning(`已删除 ${targets.length - failed} 道题，${failed} 道处理失败`)
    } else {
      ElMessage.success(`已删除 ${targets.length} 道题`)
    }
    await fetchQuestions()
  } catch {
    // 用户取消确认时不提示错误。
  } finally {
    loading.value = false
  }
}

async function handleClearAiCache(questionId: number) {
  try {
    await clearAssetCache(questionId)
    ElMessage.success('AI 学习资产缓存已清除')
  } catch {
    ElMessage.error('清除失败')
  }
}

async function handleBulkClearAiCache() {
  if (!selectedQuestions.value.length) return
  try {
    await ElMessageBox.confirm(`确定清除选中 ${selectedQuestions.value.length} 道题目的 AI 学习资产缓存？`, '批量清除缓存', {
      type: 'warning',
      confirmButtonText: '清除',
      cancelButtonText: '取消',
    })
    loading.value = true
    const targets = [...selectedQuestions.value]
    const results = await Promise.allSettled(targets.map(question => clearAssetCache(question.id)))
    const failed = results.filter(result => result.status === 'rejected').length
    if (failed > 0) {
      ElMessage.warning(`已清除 ${targets.length - failed} 道题缓存，${failed} 道处理失败`)
    } else {
      ElMessage.success(`已清除 ${targets.length} 道题缓存`)
    }
    clearQuestionSelection()
  } catch {
    // 用户取消确认时不提示错误。
  } finally {
    loading.value = false
  }
}

// 导出题目
async function handleExport() {
  try {
    const res = await exportQuestions({
      questionType: filters.questionType || undefined,
      courseId: filters.courseId || undefined,
      difficulty: filters.difficulty || undefined,
    })
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '题目导出.xlsx'
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

// 下载导入模板
async function handleDownloadTemplate() {
  try {
    const res = await downloadTemplate()
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '题目导入模板.xlsx'
    link.click()
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('模板下载失败')
  }
}

// 文件选择
function onImportFileChange(file: UploadFile) {
  importFile.value = file.raw || null
}

function onMdFileChange(file: UploadFile) {
  importFile.value = file.raw || null
}

// 下载 Markdown 导入模板
async function handleDownloadMdTemplate() {
  try {
    const res = await downloadMarkdownTemplate()
    const blob = new Blob([res.data], {
      type: 'text/markdown; charset=utf-8',
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '题目导入模板.md'
    link.click()
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('模板下载失败')
  }
}

// 导入题目（根据当前 Tab 选择 Excel 或 Markdown）
async function handleImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  importLoading.value = true
  try {
    const isMarkdown = importTab.value === 'markdown'
    const res = isMarkdown
      ? await importQuestionsMarkdown(importFile.value)
      : await importQuestions(importFile.value)
    const result = res.data
    importResult.totalRows = result.totalRows
    importResult.successCount = result.successCount
    importResult.failCount = result.failCount
    importResult.errors = result.errors || []
    importDialogVisible.value = false
    importResultVisible.value = true
    importFile.value = null
    if (uploadRef.value) {
      uploadRef.value.clearFiles()
    }
    if (mdUploadRef.value) {
      mdUploadRef.value.clearFiles()
    }
    if (result.successCount > 0) {
      fetchQuestions()
    }
  } catch {
    ElMessage.error('导入失败')
  } finally {
    importLoading.value = false
  }
}

onMounted(() => {
  fetchQuestions()
  fetchCourses()
  fetchKPTree()
})
</script>

<style scoped>
.table-summary {
  color: var(--lp-text-muted);
  font-size: 13px;
}

.options-area {
  width: 100%;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.option-correct {
  white-space: nowrap;
}

.review-suggestion-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.review-suggestion-meta {
  color: var(--lp-text-muted);
  font-size: 13px;
}

.review-suggestion-panel {
  margin-bottom: 16px;
}

.review-suggestion-content {
  color: var(--lp-text-regular);
  font-size: 13px;
  line-height: 1.6;
}

.review-suggestion-content p {
  margin: 6px 0;
}

.review-suggestion-content ul {
  margin: 6px 0 8px;
  padding-left: 18px;
}

.duplicate-drawer {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.duplicate-summary {
  align-items: center;
  background: var(--lp-bg-soft);
  border: 1px solid var(--lp-border-light);
  border-radius: 8px;
  color: var(--lp-text-muted);
  display: flex;
  gap: 8px;
  padding: 12px 14px;
}

.duplicate-summary strong {
  color: var(--lp-text-primary);
  font-size: 20px;
}

.duplicate-group-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.duplicate-group-card {
  border-radius: 8px;
}

.duplicate-group-head {
  align-items: center;
  color: var(--lp-text-muted);
  display: flex;
  gap: 10px;
  font-size: 13px;
  margin-bottom: 8px;
}

.duplicate-representative {
  color: var(--lp-text-primary);
  font-weight: 600;
  line-height: 1.6;
  margin: 0 0 10px;
}

.duplicate-question-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.duplicate-question-item {
  background: var(--lp-bg-soft);
  border: 1px solid var(--lp-border-light);
  border-radius: 8px;
  padding: 10px;
}

.duplicate-question-item > div:first-child {
  align-items: center;
  display: flex;
  gap: 8px;
  justify-content: space-between;
}

.duplicate-question-item span {
  color: var(--lp-text-muted);
  font-size: 12px;
}

.duplicate-question-item p {
  color: var(--lp-text-regular);
  line-height: 1.5;
  margin: 8px 0;
}

.duplicate-question-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.correction-drawer,
.correction-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.correction-toolbar {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.correction-card {
  border-radius: 8px;
}

.correction-card-head,
.correction-card-actions {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.correction-card-head strong {
  color: var(--lp-text-primary);
  margin-right: 8px;
}

.correction-card-head span,
.correction-card-actions span {
  color: var(--lp-text-muted);
  font-size: 12px;
}

.correction-question-content {
  color: var(--lp-text-primary);
  font-weight: 600;
  line-height: 1.6;
  margin: 10px 0 6px;
}

.correction-description {
  background: var(--lp-bg-soft);
  border: 1px solid var(--lp-border-light);
  border-radius: 8px;
  color: var(--lp-text-regular);
  line-height: 1.6;
  margin: 0 0 10px;
  padding: 10px;
}

.correction-handler {
  color: var(--lp-text-muted);
  font-size: 13px;
  line-height: 1.5;
  margin-bottom: 10px;
}

.version-drawer,
.version-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.version-summary {
  align-items: center;
  background: var(--lp-bg-soft);
  border: 1px solid var(--lp-border-light);
  border-radius: 8px;
  color: var(--lp-text-muted);
  display: flex;
  gap: 12px;
  justify-content: space-between;
  line-height: 1.5;
  padding: 12px 14px;
}

.version-summary span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-card {
  border-radius: 8px;
}

.version-card-head {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
  margin-bottom: 8px;
}

.version-card-head strong {
  color: var(--lp-text-primary);
  margin-right: 8px;
}

.version-card-head span {
  color: var(--lp-text-muted);
  font-size: 12px;
}

.version-summary-text {
  color: var(--lp-text-regular);
  font-size: 13px;
  line-height: 1.5;
  margin: 0 0 10px;
}

.snapshot-block {
  background: #101418;
  border-radius: 8px;
  color: #d6e2ee;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  margin: 0;
  max-height: 260px;
  overflow: auto;
  padding: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 720px) {
  .review-suggestion-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .correction-card-head,
  .correction-card-actions,
  .correction-toolbar,
  .version-card-head,
  .version-summary {
    align-items: flex-start;
    flex-direction: column;
  }

  .version-summary span {
    white-space: normal;
  }
}
</style>
