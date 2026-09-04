package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.SubmissionQualityCheckVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.QuestionSubmission;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.QuestionSubmissionMapper;
import com.learnplatform.service.ai.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 投稿 AI 质检服务
 * <p>
 * 调用 AI 对用户投稿的题目进行质量检查，输出结构化检查结果。
 * 不修改投稿状态，仅为管理员审核提供参考。
 */
@Service
public class SubmissionAiQualityService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionAiQualityService.class);

    private final AiProvider aiProvider;
    private final AiCallGovernanceService callGovernanceService;
    private final QuestionSubmissionMapper submissionMapper;
    private final CourseMapper courseMapper;
    private final SubmissionQualityResultService resultService;

    public SubmissionAiQualityService(AiProvider aiProvider,
                                       AiCallGovernanceService callGovernanceService,
                                       QuestionSubmissionMapper submissionMapper,
                                       CourseMapper courseMapper,
                                       SubmissionQualityResultService resultService) {
        this.aiProvider = aiProvider;
        this.callGovernanceService = callGovernanceService;
        this.submissionMapper = submissionMapper;
        this.courseMapper = courseMapper;
        this.resultService = resultService;
    }

    /**
     * 对指定投稿执行 AI 质检
     *
     * @param submissionId 投稿 ID
     * @param userId       操作用户 ID（管理员）
     * @return 质检结果
     */
    @Cacheable(value = "submissionQuality", key = "#submissionId")
    public SubmissionQualityCheckVO checkQuality(Long submissionId, Long userId) {
        QuestionSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿不存在");
        }

        callGovernanceService.checkDailyQuota(userId);

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(submission);

        long start = System.currentTimeMillis();
        boolean success = false;
        String aiContent = null;
        String errorMessage = null;
        try {
            aiContent = aiProvider.chat(systemPrompt, userPrompt);
            success = true;
            return resultService.parseAiResponse(aiContent);
        } catch (Exception e) {
            errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("AI 质检调用失败，回退到基础规则检查: {}", errorMessage);
            return resultService.buildFallbackResult(submission);
        } finally {
            int duration = (int) (System.currentTimeMillis() - start);
            callGovernanceService.logCall(userId, "submission_quality_check", success, errorMessage, duration);
        }
    }

    // ======================== Prompt 构建 ========================

    private String buildSystemPrompt() {
        return "你是一位专业的教育内容审核专家，负责对用户投稿的考试题目进行质量检查。"
                + "\n\n请从以下 5 个维度对题目进行检查，并输出严格的 JSON 格式结果："
                + "\n1. formatCheck - 格式规范：题干表述是否清晰、选项格式是否正确、有无错别字或语法问题"
                + "\n2. completenessCheck - 内容完整性：是否包含题干、选项（如有）、答案、解析，是否缺少必要信息"
                + "\n3. answerCheck - 答案正确性：答案是否与题目匹配、选项标识是否正确、有无矛盾"
                + "\n4. analysisCheck - 解析质量：解析是否充分、是否正确解释了答案原因、是否有遗漏"
                + "\n5. knowledgePointCheck - 知识点相关性：题目是否与指定知识点和课程相关、难度标注是否合理"
                + "\n\n每个检查项输出："
                + "\n- status: \"PASS\"（通过）、\"WARNING\"（有风险）或 \"FAIL\"（不通过）"
                + "\n- detail: 具体说明"
                + "\n\n此外还需输出："
                + "\n- qualityScore: 0-100 的综合质量评分"
                + "\n- summary: 一句话总评"
                + "\n- recommendation: \"APPROVE\"（推荐通过）、\"REVISE\"（建议修改）或 \"REJECT\"（建议拒绝）"
                + "\n- riskPoints: 风险点列表（数组，每项一个字符串，可以为空数组）"
                + "\n- suggestions: 修改建议列表（数组，每项一个字符串，可以为空数组）"
                + "\n\n重要：只输出纯 JSON，不要输出 Markdown 代码块标记，不要输出任何其他文字。JSON 结构如下："
                + "\n{\"qualityScore\":80,\"summary\":\"...\",\"recommendation\":\"APPROVE\","
                + "\"formatCheck\":{\"status\":\"PASS\",\"detail\":\"...\"},"
                + "\"completenessCheck\":{\"status\":\"PASS\",\"detail\":\"...\"},"
                + "\"answerCheck\":{\"status\":\"PASS\",\"detail\":\"...\"},"
                + "\"analysisCheck\":{\"status\":\"WARNING\",\"detail\":\"...\"},"
                + "\"knowledgePointCheck\":{\"status\":\"PASS\",\"detail\":\"...\"},"
                + "\"riskPoints\":[\"...\"],\"suggestions\":[\"...\"]}";
    }

    private String buildUserPrompt(QuestionSubmission submission) {
        StringBuilder sb = new StringBuilder();
        sb.append("请对以下投稿题目进行质量检查：\n\n");

        sb.append("题型：").append(getTypeLabel(submission.getQuestionType())).append("\n");
        sb.append("课程ID：").append(submission.getCourseId());
        if (submission.getCourseId() != null) {
            Course course = courseMapper.selectById(submission.getCourseId());
            if (course != null) {
                sb.append("（").append(course.getName()).append("）");
            }
        }
        sb.append("\n");
        sb.append("难度：").append(submission.getDifficulty() != null ? submission.getDifficulty() : "未设置").append("\n\n");

        sb.append("题干：\n").append(submission.getContent()).append("\n\n");

        if (submission.getOptionsJson() != null && !submission.getOptionsJson().isBlank()) {
            sb.append("选项JSON：\n").append(submission.getOptionsJson()).append("\n\n");
        }

        if (submission.getCorrectAnswer() != null && !submission.getCorrectAnswer().isBlank()) {
            sb.append("正确答案：\n").append(submission.getCorrectAnswer()).append("\n\n");
        }

        if (submission.getAnalysis() != null && !submission.getAnalysis().isBlank()) {
            sb.append("解析：\n").append(submission.getAnalysis()).append("\n\n");
        }

        if (submission.getKnowledgePointIds() != null && !submission.getKnowledgePointIds().isBlank()) {
            sb.append("关联知识点ID：").append(submission.getKnowledgePointIds()).append("\n\n");
        }

        if (submission.getTags() != null && !submission.getTags().isBlank()) {
            sb.append("标签：").append(submission.getTags()).append("\n\n");
        }

        if (submission.getSource() != null && !submission.getSource().isBlank()) {
            sb.append("来源：").append(submission.getSource()).append("\n\n");
        }

        return sb.toString();
    }


    // ======================== 审核意见生成 ========================

    /**
     * 基于 AI 质检结果生成审核意见建议文本。
     * 利用 checkQuality 的缓存，避免重复调用 AI。
     *
     * @param submissionId 投稿 ID
     * @param userId       操作用户 ID（管理员）
     * @return 审核意见文本，可直接填充到审核意见输入框
     */
    public String generateReviewComment(Long submissionId, Long userId) {
        // 复用缓存的质检结果
        SubmissionQualityCheckVO qc = checkQuality(submissionId, userId);

        StringBuilder sb = new StringBuilder();

        // 总评
        sb.append("【AI 质检报告】\n");
        sb.append("综合评分：").append(qc.getQualityScore()).append(" 分\n");
        sb.append("AI 建议：").append(qc.getSummary()).append("\n");

        // 不通过的检查项
        appendCheckItemIfNotPass(sb, "格式规范", qc.getFormatCheck());
        appendCheckItemIfNotPass(sb, "内容完整性", qc.getCompletenessCheck());
        appendCheckItemIfNotPass(sb, "答案正确性", qc.getAnswerCheck());
        appendCheckItemIfNotPass(sb, "解析质量", qc.getAnalysisCheck());
        appendCheckItemIfNotPass(sb, "知识点相关性", qc.getKnowledgePointCheck());

        // 风险点
        if (qc.getRiskPoints() != null && !qc.getRiskPoints().isEmpty()) {
            sb.append("\n风险点：\n");
            for (String point : qc.getRiskPoints()) {
                sb.append("- ").append(point).append("\n");
            }
        }

        // 修改建议
        if (qc.getSuggestions() != null && !qc.getSuggestions().isEmpty()) {
            sb.append("\n修改建议：\n");
            for (String sug : qc.getSuggestions()) {
                sb.append("- ").append(sug).append("\n");
            }
        }

        return sb.toString();
    }

    private void appendCheckItemIfNotPass(StringBuilder sb, String label, SubmissionQualityCheckVO.CheckItem item) {
        if (item != null && !"PASS".equals(item.getStatus())) {
            sb.append(label).append("：").append(item.getStatus())
                    .append(" — ").append(item.getDetail()).append("\n");
        }
    }

    // ======================== 工具方法 ========================

    private String getTypeLabel(String type) {
        if (type == null) { return "未知"; }
        switch (type) {
            case "SINGLE_CHOICE": return "单选题";
            case "MULTIPLE_CHOICE": return "多选题";
            case "TRUE_FALSE": return "判断题";
            case "FILL_BLANK": return "填空题";
            case "SHORT_ANSWER": return "简答题";
            default: return type;
        }
    }
}
