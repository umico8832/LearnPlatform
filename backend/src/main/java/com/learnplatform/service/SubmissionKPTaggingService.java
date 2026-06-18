package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.SubmissionKPTaggingVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.QuestionSubmission;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionSubmissionMapper;
import com.learnplatform.service.ai.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 投稿 AI 知识点标注服务
 * <p>
 * 分析投稿题目内容，结合课程下已有知识点列表，推荐最相关的知识点。
 * 管理员可一键应用推荐结果到投稿的 knowledgePointIds 字段。
 */
@Service
public class SubmissionKPTaggingService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionKPTaggingService.class);

    private final AiProvider aiProvider;
    private final AiService aiService;
    private final QuestionSubmissionMapper submissionMapper;
    private final CourseMapper courseMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final ObjectMapper objectMapper;

    public SubmissionKPTaggingService(AiProvider aiProvider,
                                       AiService aiService,
                                       QuestionSubmissionMapper submissionMapper,
                                       CourseMapper courseMapper,
                                       KnowledgePointMapper knowledgePointMapper,
                                       ObjectMapper objectMapper) {
        this.aiProvider = aiProvider;
        this.aiService = aiService;
        this.submissionMapper = submissionMapper;
        this.courseMapper = courseMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 为指定投稿执行 AI 知识点标注
     *
     * @param submissionId 投稿 ID
     * @param userId       操作用户 ID（管理员）
     * @return 知识点标注结果
     */
    public SubmissionKPTaggingVO tagKnowledgePoints(Long submissionId, Long userId) {
        QuestionSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿不存在");
        }

        Long courseId = submission.getCourseId();
        if (courseId == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "投稿未关联课程，无法标注知识点");
        }

        // 查询该课程下的所有知识点
        List<KnowledgePoint> kps = queryKnowledgePoints(courseId);
        if (kps.isEmpty()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该课程下没有知识点，请先添加知识点");
        }

        aiService.checkDailyQuota(userId);

        String courseName = resolveCourseName(courseId);
        String systemPrompt = buildSystemPrompt(kps, courseName);
        String userPrompt = buildUserPrompt(submission);

        long start = System.currentTimeMillis();
        boolean success = false;
        String aiContent = null;
        String errorMessage = null;
        try {
            aiContent = aiProvider.chat(systemPrompt, userPrompt);
            success = true;
            return parseAiResponse(aiContent, kps);
        } catch (Exception e) {
            errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("AI 知识点标注调用失败，回退到关键词匹配: {}", errorMessage);
            return buildFallbackResult(submission, kps, courseName);
        } finally {
            int duration = (int) (System.currentTimeMillis() - start);
            aiService.logCall(userId, "submission_kp_tagging", success, errorMessage, duration);
        }
    }

    // ======================== 数据查询 ========================

    private List<KnowledgePoint> queryKnowledgePoints(Long courseId) {
        LambdaQueryWrapper<KnowledgePoint> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgePoint::getCourseId, courseId)
               .orderByAsc(KnowledgePoint::getSortOrder);
        return knowledgePointMapper.selectList(wrapper);
    }

    private String resolveCourseName(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        return course != null ? course.getName() : "未知课程";
    }

    // ======================== Prompt 构建 ========================

    private String buildSystemPrompt(List<KnowledgePoint> kps, String courseName) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位教育领域专家，擅长分析题目内容并匹配知识点。");
        sb.append("\n\n任务：根据以下题目的内容，从给定的知识点列表中推荐最相关的知识点（最多 5 个）。");
        sb.append("\n\n课程名称：").append(courseName);
        sb.append("\n\n可用知识点列表（格式：ID | 名称 | 描述）：");
        for (KnowledgePoint kp : kps) {
            sb.append("\n- ID=").append(kp.getId())
              .append(" | ").append(kp.getName());
            if (kp.getDescription() != null && !kp.getDescription().isBlank()) {
                sb.append(" | ").append(kp.getDescription());
            }
        }

        sb.append("\n\n请输出严格的 JSON 格式结果：");
        sb.append("\n- recommendations: 推荐知识点数组，每项包含：");
        sb.append("\n  - id: 知识点 ID（必须是上述列表中的 ID）");
        sb.append("\n  - confidence: \"HIGH\"（高度相关）/ \"MEDIUM\"（中等相关）/ \"LOW\"（可能相关）");
        sb.append("\n  - reason: 推荐理由（一句话）");
        sb.append("\n- analysis: 对题目涉及知识点的简要分析说明（2-3 句话）");
        sb.append("\n\n重要规则：");
        sb.append("\n1. 只从上述知识点列表中选择，不要创造新知识点");
        sb.append("\n2. 最多推荐 5 个，按相关性从高到低排序");
        sb.append("\n3. 只输出纯 JSON，不要输出 Markdown 代码块标记或任何其他文字");
        sb.append("\n4. JSON 结构如下：");
        sb.append("{\"recommendations\":[{\"id\":1,\"confidence\":\"HIGH\",\"reason\":\"...\"}],\"analysis\":\"...\"}");

        return sb.toString();
    }

    private String buildUserPrompt(QuestionSubmission submission) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下题目并推荐最相关的知识点：\n\n");
        sb.append("题型：").append(getTypeLabel(submission.getQuestionType())).append("\n");
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
        if (submission.getTags() != null && !submission.getTags().isBlank()) {
            sb.append("标签：").append(submission.getTags()).append("\n\n");
        }

        return sb.toString();
    }

    // ======================== 响应解析 ========================

    private SubmissionKPTaggingVO parseAiResponse(String aiContent, List<KnowledgePoint> kps) {
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
            String analysis = root.has("analysis") ? root.get("analysis").asText("") : "";

            // 建立知识点 ID -> KP 映射
            Map<Long, KnowledgePoint> kpMap = kps.stream()
                    .collect(Collectors.toMap(KnowledgePoint::getId, kp -> kp));

            List<SubmissionKPTaggingVO.TaggedKP> recommendations = new ArrayList<>();
            JsonNode recsNode = root.get("recommendations");
            if (recsNode != null && recsNode.isArray()) {
                for (JsonNode recNode : recsNode) {
                    long kpId = recNode.get("id").asLong(0);
                    KnowledgePoint kp = kpMap.get(kpId);
                    if (kp == null) {
                        // AI 可能给了不存在的 ID，跳过
                        log.warn("AI 推荐了不存在的知识点 ID: {}", kpId);
                        continue;
                    }
                    String confidence = recNode.has("confidence") ? recNode.get("confidence").asText("MEDIUM") : "MEDIUM";
                    String reason = recNode.has("reason") ? recNode.get("reason").asText("") : "";
                    String courseName = resolveCourseName(kp.getCourseId());
                    recommendations.add(new SubmissionKPTaggingVO.TaggedKP(kpId, kp.getName(), courseName, confidence, reason));
                }
            }

            // 生成 suggestedIds
            String suggestedIds = recommendations.stream()
                    .map(r -> String.valueOf(r.getId()))
                    .collect(Collectors.joining(","));

            return new SubmissionKPTaggingVO(recommendations, analysis, suggestedIds);
        } catch (Exception e) {
            log.warn("AI 知识点标注结果 JSON 解析失败: {}", e.getMessage());
            return new SubmissionKPTaggingVO(List.of(), "AI 输出解析失败，请人工判断知识点归属", "");
        }
    }

    // ======================== 回退方案（无 AI 时关键词匹配）========================

    private SubmissionKPTaggingVO buildFallbackResult(QuestionSubmission submission,
                                                       List<KnowledgePoint> kps,
                                                       String courseName) {
        String content = (submission.getContent() != null ? submission.getContent() : "").toLowerCase();
        String analysis = (submission.getAnalysis() != null ? submission.getAnalysis() : "").toLowerCase();
        String combined = content + " " + analysis;

        List<SubmissionKPTaggingVO.TaggedKP> recommendations = new ArrayList<>();

        for (KnowledgePoint kp : kps) {
            String kpName = kp.getName().toLowerCase();
            if (kpName.length() < 2) continue;

            // 简单关键词匹配：名称是否出现在题目内容中
            if (combined.contains(kpName)) {
                recommendations.add(new SubmissionKPTaggingVO.TaggedKP(
                        kp.getId(), kp.getName(), courseName, "MEDIUM", "题目内容包含知识点关键词"));
            }
        }

        // 限制最多 5 个
        if (recommendations.size() > 5) {
            recommendations = recommendations.subList(0, 5);
        }

        String suggestedIds = recommendations.stream()
                .map(r -> String.valueOf(r.getId()))
                .collect(Collectors.joining(","));

        String fallbackAnalysis = recommendations.isEmpty()
                ? "AI 服务暂不可用，关键词匹配未找到明确匹配的知识点，建议人工判断"
                : "AI 服务暂不可用，已通过关键词匹配推荐 " + recommendations.size() + " 个知识点";

        return new SubmissionKPTaggingVO(recommendations, fallbackAnalysis, suggestedIds);
    }

    // ======================== 工具方法 ========================

    private String getTypeLabel(String type) {
        if (type == null) return "未知";
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