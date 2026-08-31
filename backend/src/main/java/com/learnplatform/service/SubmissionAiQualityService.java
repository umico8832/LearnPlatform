package com.learnplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.List;

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
    private final ObjectMapper objectMapper;

    public SubmissionAiQualityService(AiProvider aiProvider,
                                       AiCallGovernanceService callGovernanceService,
                                       QuestionSubmissionMapper submissionMapper,
                                       CourseMapper courseMapper,
                                       ObjectMapper objectMapper) {
        this.aiProvider = aiProvider;
        this.callGovernanceService = callGovernanceService;
        this.submissionMapper = submissionMapper;
        this.courseMapper = courseMapper;
        this.objectMapper = objectMapper;
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
            return parseAiResponse(aiContent);
        } catch (Exception e) {
            errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("AI 质检调用失败，回退到基础规则检查: {}", errorMessage);
            return buildFallbackResult(submission);
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

    // ======================== 响应解析 ========================

    private SubmissionQualityCheckVO parseAiResponse(String aiContent) {
        try {
            // 尝试去除可能的 Markdown 代码块标记
            String json = aiContent.trim();
            if (json.startsWith("```")) {
                int firstNewline = json.indexOf('\n');
                if (firstNewline > 0) {
                    json = json.substring(firstNewline + 1);
                }
                if (json.endsWith("```")) {
                    json = json.substring(0, json.length() - 3).trim();
                }
            }

            JsonNode root = objectMapper.readTree(json);
            SubmissionQualityCheckVO vo = new SubmissionQualityCheckVO();

            vo.setQualityScore(getIntNode(root, "qualityScore", 70));
            vo.setSummary(getStringNode(root, "summary", "AI 已完成质检"));
            vo.setRecommendation(getStringNode(root, "recommendation", "APPROVE"));

            vo.setFormatCheck(parseCheckItem(root, "formatCheck"));
            vo.setCompletenessCheck(parseCheckItem(root, "completenessCheck"));
            vo.setAnswerCheck(parseCheckItem(root, "answerCheck"));
            vo.setAnalysisCheck(parseCheckItem(root, "analysisCheck"));
            vo.setKnowledgePointCheck(parseCheckItem(root, "knowledgePointCheck"));

            vo.setRiskPoints(parseStringList(root, "riskPoints"));
            vo.setSuggestions(parseStringList(root, "suggestions"));

            return vo;
        } catch (Exception e) {
            log.warn("AI 质检结果 JSON 解析失败: {}", e.getMessage());
            // 回退：将 AI 原始输出作为 summary
            SubmissionQualityCheckVO fallback = new SubmissionQualityCheckVO();
            fallback.setQualityScore(60);
            fallback.setSummary(aiContent.length() > 200 ? aiContent.substring(0, 200) + "..." : aiContent);
            fallback.setRecommendation("REVISE");
            fallback.setFormatCheck(new SubmissionQualityCheckVO.CheckItem("WARNING", "AI 输出格式异常，建议人工复核"));
            fallback.setCompletenessCheck(new SubmissionQualityCheckVO.CheckItem("WARNING", "无法自动判定"));
            fallback.setAnswerCheck(new SubmissionQualityCheckVO.CheckItem("WARNING", "无法自动判定"));
            fallback.setAnalysisCheck(new SubmissionQualityCheckVO.CheckItem("WARNING", "无法自动判定"));
            fallback.setKnowledgePointCheck(new SubmissionQualityCheckVO.CheckItem("WARNING", "无法自动判定"));
            fallback.setRiskPoints(List.of("AI 输出格式异常，请人工复核"));
            fallback.setSuggestions(List.of("建议人工仔细审核此投稿"));
            return fallback;
        }
    }

    private SubmissionQualityCheckVO.CheckItem parseCheckItem(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull()) {
            return new SubmissionQualityCheckVO.CheckItem("WARNING", "未检查");
        }
        String status = "PASS";
        String detail = "";
        if (node.has("status")) {
            status = node.get("status").asText("PASS");
        }
        if (node.has("detail")) {
            detail = node.get("detail").asText("");
        }
        return new SubmissionQualityCheckVO.CheckItem(status, detail);
    }

    private List<String> parseStringList(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node == null || !node.isArray()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        node.forEach(item -> result.add(item.asText("")));
        return result;
    }

    private int getIntNode(JsonNode root, String fieldName, int defaultValue) {
        JsonNode node = root.get(fieldName);
        return node != null && node.isInt() ? node.asInt(defaultValue) : defaultValue;
    }

    private String getStringNode(JsonNode root, String fieldName, String defaultValue) {
        JsonNode node = root.get(fieldName);
        return node != null && node.isTextual() ? node.asText(defaultValue) : defaultValue;
    }

    // ======================== 回退方案（无 AI 时的规则检查）========================

    private SubmissionQualityCheckVO buildFallbackResult(QuestionSubmission submission) {
        SubmissionQualityCheckVO vo = new SubmissionQualityCheckVO();
        List<String> riskPoints = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        int score = 100;

        // 格式检查
        String formatStatus = "PASS";
        String formatDetail = "题干格式正常";
        if (submission.getContent() == null || submission.getContent().trim().length() < 10) {
            formatStatus = "FAIL";
            formatDetail = "题干内容过短或为空";
            riskPoints.add("题干内容过短，可能不完整");
            score -= 20;
        }
        vo.setFormatCheck(new SubmissionQualityCheckVO.CheckItem(formatStatus, formatDetail));

        // 完整性检查
        String completenessStatus = "PASS";
        String completenessDetail = "内容完整";
        boolean hasOptions = submission.getOptionsJson() != null && !submission.getOptionsJson().isBlank();
        boolean hasAnswer = submission.getCorrectAnswer() != null && !submission.getCorrectAnswer().isBlank();
        String qt = submission.getQuestionType();
        if (("SINGLE_CHOICE".equals(qt) || "MULTIPLE_CHOICE".equals(qt)) && !hasOptions) {
            completenessStatus = "FAIL";
            completenessDetail = "选择题缺少选项";
            riskPoints.add("选择题必须提供选项");
            score -= 25;
        }
        if ("TRUE_FALSE".equals(qt) && !hasAnswer) {
            completenessStatus = "FAIL";
            completenessDetail = "判断题缺少正确答案";
            riskPoints.add("判断题必须提供正确答案");
            score -= 25;
        }
        if (("FILL_BLANK".equals(qt) || "SHORT_ANSWER".equals(qt)) && !hasAnswer) {
            completenessStatus = "FAIL";
            completenessDetail = "填空题/简答题缺少参考答案";
            riskPoints.add("填空题/简答题必须提供参考答案");
            score -= 25;
        }
        vo.setCompletenessCheck(new SubmissionQualityCheckVO.CheckItem(completenessStatus, completenessDetail));

        // 答案检查
        String answerStatus = "PASS";
        String answerDetail = "答案格式正确";
        if (hasAnswer && submission.getCorrectAnswer().trim().length() < 1) {
            answerStatus = "WARNING";
            answerDetail = "答案内容可能过短";
            suggestions.add("建议检查答案是否完整");
            score -= 5;
        }
        vo.setAnswerCheck(new SubmissionQualityCheckVO.CheckItem(answerStatus, answerDetail));

        // 解析检查
        String analysisStatus = "PASS";
        String analysisDetail = "解析内容正常";
        boolean hasAnalysis = submission.getAnalysis() != null && !submission.getAnalysis().isBlank();
        if (!hasAnalysis) {
            analysisStatus = "WARNING";
            analysisDetail = "未提供解析，建议补充";
            suggestions.add("建议为题目添加解析，帮助学习者理解");
            score -= 10;
        }
        vo.setAnalysisCheck(new SubmissionQualityCheckVO.CheckItem(analysisStatus, analysisDetail));

        // 知识点检查
        String kpStatus = "PASS";
        String kpDetail = "知识点关联正常";
        if (submission.getKnowledgePointIds() == null || submission.getKnowledgePointIds().isBlank()) {
            kpStatus = "WARNING";
            kpDetail = "未关联知识点";
            suggestions.add("建议关联相关知识点，便于分类和检索");
            score -= 5;
        }
        vo.setKnowledgePointCheck(new SubmissionQualityCheckVO.CheckItem(kpStatus, kpDetail));

        // 总结
        vo.setQualityScore(Math.max(0, score));
        if (score >= 80) {
            vo.setSummary("基础规则检查通过，题目质量较好");
            vo.setRecommendation("APPROVE");
        } else if (score >= 50) {
            vo.setSummary("基础规则检查发现部分问题，建议修改后通过");
            vo.setRecommendation("REVISE");
        } else {
            vo.setSummary("基础规则检查发现较多问题，建议拒绝或大幅修改");
            vo.setRecommendation("REJECT");
        }
        vo.setRiskPoints(riskPoints);
        vo.setSuggestions(suggestions);

        return vo;
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
