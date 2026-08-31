package com.learnplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.SubmissionDifficultyVO;
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
 * 投稿 AI 难度评估服务
 * <p>
 * 基于题目内容、题型、选项和解析等信息，由 AI 自动评估题目难度。
 * 不修改投稿数据，仅为管理员审核提供参考。
 */
@Service
public class SubmissionDifficultyAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionDifficultyAssessmentService.class);

    private final AiProvider aiProvider;
    private final AiCallGovernanceService callGovernanceService;
    private final QuestionSubmissionMapper submissionMapper;
    private final CourseMapper courseMapper;
    private final ObjectMapper objectMapper;

    public SubmissionDifficultyAssessmentService(AiProvider aiProvider,
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
     * 对指定投稿执行 AI 难度评估
     *
     * @param submissionId 投稿 ID
     * @param userId       操作用户 ID（管理员）
     * @return 难度评估结果
     */
    @Cacheable(value = "submissionDifficulty", key = "#submissionId")
    public SubmissionDifficultyVO assessDifficulty(Long submissionId, Long userId) {
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
            return parseAiResponse(aiContent, submission.getDifficulty());
        } catch (Exception e) {
            errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("AI 难度评估调用失败，回退到基础规则评估: {}", errorMessage);
            return buildFallbackResult(submission);
        } finally {
            int duration = (int) (System.currentTimeMillis() - start);
            callGovernanceService.logCall(userId, "submission_difficulty_assessment", success, errorMessage, duration);
        }
    }

    // ======================== Prompt 构建 ========================

    private String buildSystemPrompt() {
        return "你是一位专业的教育测评专家，擅长根据题目内容评估考试题目的难度。"
                + "\n\n请基于以下因素综合评估题目难度（1-5 星）："
                + "\n1. 知识点深度：考察的是基础概念还是深层应用"
                + "\n2. 认知层次（布鲁姆分类法）：记忆→理解→应用→分析→评价→创建"
                + "\n3. 题干复杂度：信息量、干扰项、推理步骤"
                + "\n4. 选项设计（如有）：迷惑性、区分度"
                + "\n5. 解题步骤：一步推理还是多步推导"
                + "\n\n难度等级定义："
                + "\n- 1 星：基础识记，直接考查定义或事实"
                + "\n- 2 星：基础理解，需要简单解释或辨析"
                + "\n- 3 星：中等应用，需要运用知识点解决具体问题"
                + "\n- 4 星：较高难度，需要综合分析或多步推理"
                + "\n- 5 星：高难度，需要创造性思维或复杂综合"
                + "\n\n输出严格的纯 JSON，不要输出 Markdown 代码块标记，不要输出任何其他文字。JSON 结构如下："
                + "\n{\"suggestedDifficulty\":3,\"confidence\":\"HIGH\","
                + "\"reason\":\"评估理由...\","
                + "\"cognitiveLevel\":\"应用\","
                + "\"factors\":[{\"name\":\"因素名\",\"description\":\"说明\",\"impact\":\"INCREASE\"}],"
                + "\"summary\":\"总体评估一句话\"}"
                + "\n\n字段说明："
                + "\n- suggestedDifficulty: 1-5 整数"
                + "\n- confidence: \"HIGH\"（非常确定）/ \"MEDIUM\"（较确定）/ \"LOW\"（不太确定）"
                + "\n- reason: 详细的评估理由"
                + "\n- cognitiveLevel: 布鲁姆认知层次（记忆/理解/应用/分析/评价/创建）"
                + "\n- factors: 影响难度的因素数组，impact 取值 INCREASE（增难）/ DECREASE（降难）/ NEUTRAL"
                + "\n- summary: 一句话总结";
    }

    private String buildUserPrompt(QuestionSubmission submission) {
        StringBuilder sb = new StringBuilder();
        sb.append("请评估以下题目的难度：\n\n");

        sb.append("题型：").append(getTypeLabel(submission.getQuestionType())).append("\n");
        sb.append("课程ID：").append(submission.getCourseId());
        if (submission.getCourseId() != null) {
            Course course = courseMapper.selectById(submission.getCourseId());
            if (course != null) {
                sb.append("（").append(course.getName()).append("）");
            }
        }
        sb.append("\n");

        if (submission.getDifficulty() != null) {
            sb.append("投稿者标注难度：").append(submission.getDifficulty()).append(" 星\n");
        } else {
            sb.append("投稿者标注难度：未设置\n");
        }

        sb.append("\n题干：\n").append(submission.getContent()).append("\n\n");

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

        return sb.toString();
    }

    // ======================== 响应解析 ========================

    private SubmissionDifficultyVO parseAiResponse(String aiContent, Integer originalDifficulty) {
        try {
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
            SubmissionDifficultyVO vo = new SubmissionDifficultyVO();

            int suggested = getIntNode(root, "suggestedDifficulty", 3);
            vo.setSuggestedDifficulty(Math.max(1, Math.min(5, suggested)));
            vo.setOriginalDifficulty(originalDifficulty);
            vo.setDifficultyMatch(originalDifficulty != null && originalDifficulty.equals(vo.getSuggestedDifficulty()));
            vo.setConfidence(getStringNode(root, "confidence", "MEDIUM"));
            vo.setReason(getStringNode(root, "reason", "AI 已完成难度评估"));
            vo.setCognitiveLevel(getStringNode(root, "cognitiveLevel", "应用"));
            vo.setSummary(getStringNode(root, "summary", "难度评估完成"));

            // 解析因素列表
            List<SubmissionDifficultyVO.DifficultyFactor> factors = new ArrayList<>();
            JsonNode factorsNode = root.get("factors");
            if (factorsNode != null && factorsNode.isArray()) {
                for (JsonNode factorNode : factorsNode) {
                    String name = factorNode.has("name") ? factorNode.get("name").asText("") : "";
                    String description = factorNode.has("description") ? factorNode.get("description").asText("") : "";
                    String impact = factorNode.has("impact") ? factorNode.get("impact").asText("NEUTRAL") : "NEUTRAL";
                    factors.add(new SubmissionDifficultyVO.DifficultyFactor(name, description, impact));
                }
            }
            vo.setFactors(factors);

            return vo;
        } catch (Exception e) {
            log.warn("AI 难度评估结果 JSON 解析失败: {}", e.getMessage());
            return buildFallbackFromRaw(aiContent, originalDifficulty);
        }
    }

    // ======================== 回退方案（无 AI 时的规则评估）========================

    private SubmissionDifficultyVO buildFallbackResult(QuestionSubmission submission) {
        SubmissionDifficultyVO vo = new SubmissionDifficultyVO();
        vo.setOriginalDifficulty(submission.getDifficulty());

        int estimated = estimateDifficulty(submission);
        vo.setSuggestedDifficulty(estimated);
        vo.setDifficultyMatch(submission.getDifficulty() != null && submission.getDifficulty().equals(estimated));
        vo.setConfidence("LOW");
        vo.setCognitiveLevel("应用");
        vo.setReason("AI 不可用，基于规则粗略评估");
        vo.setSummary("基础规则评估：根据题型和内容长度粗略判断，建议结合 AI 评估或人工确认");

        List<SubmissionDifficultyVO.DifficultyFactor> factors = new ArrayList<>();
        String qt = submission.getQuestionType();

        // 题型因素
        if ("SINGLE_CHOICE".equals(qt) || "TRUE_FALSE".equals(qt)) {
            factors.add(new SubmissionDifficultyVO.DifficultyFactor(
                    "题型", "客观题通常难度较低", "DECREASE"));
        } else if ("MULTIPLE_CHOICE".equals(qt)) {
            factors.add(new SubmissionDifficultyVO.DifficultyFactor(
                    "题型", "多选题需要更全面理解，难度中等", "NEUTRAL"));
        } else if ("SHORT_ANSWER".equals(qt)) {
            factors.add(new SubmissionDifficultyVO.DifficultyFactor(
                    "题型", "简答题需要组织语言和深入理解，难度较高", "INCREASE"));
        }

        // 内容长度因素
        String content = submission.getContent();
        if (content != null && content.length() > 200) {
            factors.add(new SubmissionDifficultyVO.DifficultyFactor(
                    "题干长度", "题干较长，阅读理解要求较高", "INCREASE"));
        }

        // 解析因素
        if (submission.getAnalysis() != null && !submission.getAnalysis().isBlank()) {
            factors.add(new SubmissionDifficultyVO.DifficultyFactor(
                    "解析", "有详细解析，说明题目有一定深度", "NEUTRAL"));
        }

        vo.setFactors(factors);
        return vo;
    }

    /**
     * 基于规则的难度粗略估算
     */
    private int estimateDifficulty(QuestionSubmission submission) {
        int base = 2;
        String qt = submission.getQuestionType();

        // 题型基础难度
        if ("SINGLE_CHOICE".equals(qt) || "TRUE_FALSE".equals(qt)) {
            base = 2;
        } else if ("MULTIPLE_CHOICE".equals(qt)) {
            base = 3;
        } else if ("FILL_BLANK".equals(qt)) {
            base = 3;
        } else if ("SHORT_ANSWER".equals(qt)) {
            base = 4;
        }

        // 内容长度调整
        String content = submission.getContent();
        if (content != null && content.length() > 300) {
            base = Math.min(5, base + 1);
        }

        // 使用用户标注难度作为参考（如果合理）
        if (submission.getDifficulty() != null && submission.getDifficulty() >= 1 && submission.getDifficulty() <= 5) {
            // 取规则评估和用户标注的平均值
            base = (base + submission.getDifficulty() + 1) / 2;
        }

        return Math.max(1, Math.min(5, base));
    }

    private SubmissionDifficultyVO buildFallbackFromRaw(String aiContent, Integer originalDifficulty) {
        SubmissionDifficultyVO vo = new SubmissionDifficultyVO();
        vo.setOriginalDifficulty(originalDifficulty);
        vo.setSuggestedDifficulty(originalDifficulty != null ? originalDifficulty : 3);
        vo.setDifficultyMatch(false);
        vo.setConfidence("LOW");
        vo.setCognitiveLevel("应用");
        vo.setReason("AI 输出格式异常，无法结构化解析");
        vo.setSummary(aiContent.length() > 200 ? aiContent.substring(0, 200) + "..." : aiContent);
        vo.setFactors(List.of(new SubmissionDifficultyVO.DifficultyFactor(
                "系统", "AI 输出格式异常，建议人工复核", "NEUTRAL")));
        return vo;
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

    private int getIntNode(JsonNode root, String fieldName, int defaultValue) {
        JsonNode node = root.get(fieldName);
        return node != null && node.isInt() ? node.asInt(defaultValue) : defaultValue;
    }

    private String getStringNode(JsonNode root, String fieldName, String defaultValue) {
        JsonNode node = root.get(fieldName);
        return node != null && node.isTextual() ? node.asText(defaultValue) : defaultValue;
    }
}
