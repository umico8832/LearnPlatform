package com.learnplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.dto.SubmissionQualityCheckVO;
import com.learnplatform.entity.QuestionSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SubmissionQualityResultService {
    private static final Logger log = LoggerFactory.getLogger(SubmissionQualityResultService.class);
    private final ObjectMapper objectMapper;

    public SubmissionQualityResultService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SubmissionQualityCheckVO parseAiResponse(String aiContent) {
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

    public SubmissionQualityCheckVO buildFallbackResult(QuestionSubmission submission) {
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
}

