package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.QuestionReviewSuggestionVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.service.ai.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 正式题目 AI 复审建议服务。
 * <p>
 * 只提供审核辅助建议，不直接修改题目或复审状态。
 */
@Service
public class QuestionReviewSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(QuestionReviewSuggestionService.class);

    private final AiProvider aiProvider;
    private final AiCallGovernanceService callGovernanceService;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final CourseMapper courseMapper;
    private final ObjectMapper objectMapper;

    public QuestionReviewSuggestionService(AiProvider aiProvider,
                                           AiCallGovernanceService callGovernanceService,
                                           QuestionMapper questionMapper,
                                           QuestionOptionMapper questionOptionMapper,
                                           QuestionKnowledgePointMapper questionKnowledgePointMapper,
                                           KnowledgePointMapper knowledgePointMapper,
                                           CourseMapper courseMapper,
                                           ObjectMapper objectMapper) {
        this.aiProvider = aiProvider;
        this.callGovernanceService = callGovernanceService;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.courseMapper = courseMapper;
        this.objectMapper = objectMapper;
    }

    @Cacheable(value = "questionReviewSuggestion", key = "#questionId")
    public QuestionReviewSuggestionVO generateSuggestion(Long questionId, Long reviewerId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }

        callGovernanceService.checkDailyQuota(reviewerId);

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(question);

        long start = System.currentTimeMillis();
        boolean success = false;
        String aiContent = null;
        String errorMessage = null;
        try {
            aiContent = aiProvider.chat(systemPrompt, userPrompt);
            success = true;
            return parseAiResponse(aiContent, question);
        } catch (Exception e) {
            errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("AI 题目复审建议调用失败，回退到基础规则检查: {}", errorMessage);
            return buildFallbackResult(question);
        } finally {
            int duration = (int) (System.currentTimeMillis() - start);
            callGovernanceService.logCall(reviewerId, "question_re_review_suggestion", success,
                    errorMessage, duration);
        }
    }

    private String buildSystemPrompt() {
        return "你是一位专业的题库内容复审专家，负责检查已经入库的正式题目是否仍适合继续使用。"
                + "\n请从题干清晰度、答案正确性、解析质量、知识点相关性、难度合理性、内容时效性六个角度复审。"
                + "\n输出严格 JSON，不要输出 Markdown 代码块或额外文字。JSON 结构如下："
                + "\n{\"recommendation\":\"APPROVE\",\"confidenceScore\":85,\"summary\":\"...\","
                + "\"suggestedContent\":\"...\",\"suggestedDifficulty\":3,"
                + "\"riskPoints\":[\"...\"],\"suggestions\":[\"...\"],"
                + "\"answerAnalysis\":\"...\",\"knowledgeAnalysis\":\"...\"}"
                + "\nrecommendation 只能为 APPROVE（可继续使用）、REVISE（建议修订）或 REJECT（建议废弃）。"
                + "\nsuggestedDifficulty 必须是 1-5 的整数；无修订题干时 suggestedContent 返回空字符串。";
    }

    private String buildUserPrompt(Question question) {
        StringBuilder sb = new StringBuilder();
        sb.append("请复审以下正式题目：\n\n");
        sb.append("题目ID：").append(question.getId()).append("\n");
        sb.append("题型：").append(typeLabel(question.getQuestionType())).append("\n");
        sb.append("难度：").append(question.getDifficulty()).append("\n");
        sb.append("来源：").append(question.getSourceType() != null ? question.getSourceType() : "MANUAL").append("\n");
        sb.append("累计复审轮次：").append(question.getReviewRounds() != null ? question.getReviewRounds() : 0).append("\n");

        Course course = question.getCourseId() != null ? courseMapper.selectById(question.getCourseId()) : null;
        if (course != null) {
            sb.append("课程：").append(course.getName()).append("\n");
        }

        sb.append("\n题干：\n").append(question.getContent()).append("\n\n");

        List<QuestionOption> options = questionOptionMapper.selectList(
                new LambdaQueryWrapper<QuestionOption>()
                        .eq(QuestionOption::getQuestionId, question.getId())
                        .orderByAsc(QuestionOption::getSortOrder));
        if (!options.isEmpty()) {
            sb.append("选项：\n");
            for (QuestionOption option : options) {
                sb.append(option.getOptionLabel()).append(". ").append(option.getContent());
                if (Integer.valueOf(1).equals(option.getIsCorrect())) {
                    sb.append("（正确）");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (question.getAnalysis() != null && !question.getAnalysis().isBlank()) {
            sb.append("解析：\n").append(question.getAnalysis()).append("\n\n");
        }
        if (question.getTags() != null && !question.getTags().isBlank()) {
            sb.append("标签：").append(question.getTags()).append("\n\n");
        }

        List<String> kpNames = queryKnowledgePointNames(question.getId());
        if (!kpNames.isEmpty()) {
            sb.append("关联知识点：").append(String.join("、", kpNames)).append("\n\n");
        }

        return sb.toString();
    }

    private List<String> queryKnowledgePointNames(Long questionId) {
        List<QuestionKnowledgePoint> relations = questionKnowledgePointMapper.selectList(
                new LambdaQueryWrapper<QuestionKnowledgePoint>()
                        .eq(QuestionKnowledgePoint::getQuestionId, questionId));
        return relations.stream()
                .map(QuestionKnowledgePoint::getKnowledgePointId)
                .map(knowledgePointMapper::selectById)
                .filter(kp -> kp != null && kp.getName() != null)
                .map(KnowledgePoint::getName)
                .collect(Collectors.toList());
    }

    private QuestionReviewSuggestionVO parseAiResponse(String aiContent, Question question) {
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
            QuestionReviewSuggestionVO vo = new QuestionReviewSuggestionVO();
            vo.setRecommendation(normalizeRecommendation(text(root, "recommendation", "APPROVE")));
            vo.setConfidenceScore(Math.max(0, Math.min(100, integer(root, "confidenceScore", 70))));
            vo.setSummary(text(root, "summary", "AI 已完成复审建议"));
            vo.setSuggestedContent(text(root, "suggestedContent", ""));
            vo.setSuggestedDifficulty(clampDifficulty(integer(root, "suggestedDifficulty",
                    question.getDifficulty() != null ? question.getDifficulty() : 3)));
            vo.setRiskPoints(stringList(root, "riskPoints"));
            vo.setSuggestions(stringList(root, "suggestions"));
            vo.setAnswerAnalysis(text(root, "answerAnalysis", "AI 未提供答案检查说明"));
            vo.setKnowledgeAnalysis(text(root, "knowledgeAnalysis", "AI 未提供知识点检查说明"));
            return vo;
        } catch (Exception e) {
            log.warn("AI 题目复审建议 JSON 解析失败: {}", e.getMessage());
            QuestionReviewSuggestionVO fallback = buildFallbackResult(question);
            fallback.setSummary(aiContent.length() > 200 ? aiContent.substring(0, 200) + "..." : aiContent);
            fallback.setRecommendation("REVISE");
            fallback.setRiskPoints(List.of("AI 输出格式异常，请人工复核"));
            fallback.setSuggestions(List.of("建议人工检查题干、答案和解析后再提交复审"));
            return fallback;
        }
    }

    private QuestionReviewSuggestionVO buildFallbackResult(Question question) {
        List<String> riskPoints = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        String recommendation = "APPROVE";
        int confidence = 65;

        if (question.getContent() == null || question.getContent().trim().length() < 10) {
            riskPoints.add("题干内容过短");
            suggestions.add("补充完整题干和必要上下文");
            recommendation = "REVISE";
            confidence -= 15;
        }
        if (question.getAnalysis() == null || question.getAnalysis().trim().length() < 10) {
            riskPoints.add("解析缺失或过短");
            suggestions.add("补充解析，说明答案依据和易错点");
            recommendation = "REVISE";
            confidence -= 10;
        }
        if (question.getDifficulty() == null || question.getDifficulty() < 1 || question.getDifficulty() > 5) {
            riskPoints.add("难度不在 1-5 范围内");
            suggestions.add("重新标注题目难度");
            recommendation = "REVISE";
            confidence -= 10;
        }
        if (Integer.valueOf(0).equals(question.getStatus())) {
            riskPoints.add("题目当前已禁用");
            recommendation = "REJECT";
        }

        QuestionReviewSuggestionVO vo = new QuestionReviewSuggestionVO();
        vo.setRecommendation(recommendation);
        vo.setConfidenceScore(Math.max(30, confidence));
        vo.setSummary(riskPoints.isEmpty() ? "基础规则检查未发现明显问题" : "基础规则检查发现需要人工确认的问题");
        vo.setSuggestedContent("REVISE".equals(recommendation) ? question.getContent() : "");
        vo.setSuggestedDifficulty(clampDifficulty(question.getDifficulty() != null ? question.getDifficulty() : 3));
        vo.setRiskPoints(riskPoints);
        vo.setSuggestions(suggestions);
        vo.setAnswerAnalysis("AI 不可用，暂未进行深度答案一致性检查");
        vo.setKnowledgeAnalysis("AI 不可用，暂未进行深度知识点与时效性检查");
        return vo;
    }

    private String normalizeRecommendation(String value) {
        if ("REVISE".equalsIgnoreCase(value)) { return "REVISE"; }
        if ("REJECT".equalsIgnoreCase(value)) { return "REJECT"; }
        return "APPROVE";
    }

    private int clampDifficulty(int value) {
        return Math.max(1, Math.min(5, value));
    }

    private int integer(JsonNode root, String field, int defaultValue) {
        JsonNode node = root.get(field);
        return node != null && node.isNumber() ? node.asInt(defaultValue) : defaultValue;
    }

    private String text(JsonNode root, String field, String defaultValue) {
        JsonNode node = root.get(field);
        return node != null && node.isTextual() ? node.asText(defaultValue) : defaultValue;
    }

    private List<String> stringList(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        node.forEach(item -> result.add(item.asText("")));
        return result;
    }

    private String typeLabel(String type) {
        if ("SINGLE_CHOICE".equals(type)) { return "单选题"; }
        if ("MULTIPLE_CHOICE".equals(type)) { return "多选题"; }
        if ("TRUE_FALSE".equals(type)) { return "判断题"; }
        if ("FILL_BLANK".equals(type)) { return "填空题"; }
        if ("SHORT_ANSWER".equals(type)) { return "简答题"; }
        return type != null ? type : "未知题型";
    }
}
