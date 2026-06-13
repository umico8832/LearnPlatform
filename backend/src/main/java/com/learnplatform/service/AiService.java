package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.AiResponse;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import com.learnplatform.service.ai.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * AI 业务服务
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final AiProvider aiProvider;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final CourseMapper courseMapper;
    private final WrongQuestionMapper wrongQuestionMapper;

    public AiService(AiProvider aiProvider,
                     QuestionMapper questionMapper,
                     QuestionOptionMapper questionOptionMapper,
                     QuestionKnowledgePointMapper questionKnowledgePointMapper,
                     KnowledgePointMapper knowledgePointMapper,
                     CourseMapper courseMapper,
                     WrongQuestionMapper wrongQuestionMapper) {
        this.aiProvider = aiProvider;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.courseMapper = courseMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
    }

    /**
     * AI 生成题目解析
     */
    public AiResponse generateExplanation(Long questionId) {
        AiPrompt prompt = buildExplanationPrompt(questionId);
        String content = aiProvider.chat(prompt.systemPrompt(), prompt.userPrompt());
        return new AiResponse(content, "ai");
    }

    public void generateExplanationStream(Long questionId, Consumer<String> onContent) {
        AiPrompt prompt = buildExplanationPrompt(questionId);
        aiProvider.chatStream(prompt.systemPrompt(), prompt.userPrompt(), onContent);
    }

    /**
     * AI 生成变式题
     */
    public AiResponse generateVariant(Long questionId) {
        AiPrompt prompt = buildVariantPrompt(questionId);
        String content = aiProvider.chat(prompt.systemPrompt(), prompt.userPrompt());
        return new AiResponse(content, "ai");
    }

    public void generateVariantStream(Long questionId, Consumer<String> onContent) {
        AiPrompt prompt = buildVariantPrompt(questionId);
        aiProvider.chatStream(prompt.systemPrompt(), prompt.userPrompt(), onContent);
    }

    private AiPrompt buildExplanationPrompt(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null) throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");

        String questionContext = buildQuestionContext(question);

        String systemPrompt = "你是一位专业的教育辅导老师。请为以下题目提供详细、清晰的解析。"
                + "要求：\n1. 解释题目考查的知识点\n2. 分析正确答案的原因\n3. 如果有错误选项，解释为什么是错误的\n"
                + "4. 用简洁易懂的语言\n5. 使用 Markdown 格式输出";
        return new AiPrompt(systemPrompt, "请解析这道题目：\n\n" + questionContext);
    }

    private AiPrompt buildVariantPrompt(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null) throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");

        String questionContext = buildQuestionContext(question);

        String systemPrompt = "你是一位专业的出题老师。请基于给定的题目，生成 1-2 道变式题（类似知识点但不同问法）。"
                + "要求：\n1. 考查相同知识点但换个角度\n2. 难度与原题相近\n3. 包含题目、选项和正确答案\n"
                + "4. 使用 Markdown 格式输出";
        return new AiPrompt(systemPrompt, "基于以下题目生成变式题：\n\n" + questionContext);
    }

    /**
     * AI 生成复习建议
     */
    public AiResponse generateReviewSuggestion(Long userId, Long courseId) {
        LambdaQueryWrapper<WrongQuestion> wqWrapper = new LambdaQueryWrapper<>();
        wqWrapper.eq(WrongQuestion::getUserId, userId).eq(WrongQuestion::getDeleted, 0);
        List<WrongQuestion> wrongQuestions = wrongQuestionMapper.selectList(wqWrapper);

        StringBuilder userContext = new StringBuilder();
        userContext.append("用户错题数量：").append(wrongQuestions.size()).append(" 道\n");

        if (!wrongQuestions.isEmpty()) {
            userContext.append("错题详情：\n");
            for (WrongQuestion wq : wrongQuestions) {
                Question q = questionMapper.selectById(wq.getQuestionId());
                if (q != null) {
                    userContext.append("- 题目ID:").append(q.getId())
                            .append("，题型:").append(q.getQuestionType())
                            .append("，答错次数:").append(wq.getWrongCount())
                            .append("，掌握程度:").append(wq.getMasteryLevel()).append("\n");
                }
            }
        }

        if (courseId != null) {
            Course course = courseMapper.selectById(courseId);
            if (course != null) {
                userContext.append("针对课程：").append(course.getName()).append("\n");
            }
        }

        String systemPrompt = "你是一位专业的学习顾问。请根据用户的错题情况，给出个性化的复习建议。"
                + "要求：\n1. 分析用户的薄弱环节\n2. 建议重点复习的知识点\n3. 推荐复习方法和计划\n"
                + "4. 给予鼓励和指导\n5. 使用 Markdown 格式输出";

        String content = aiProvider.chat(systemPrompt, "请根据以下学习数据给出复习建议：\n\n" + userContext);
        return new AiResponse(content, "ai");
    }

    /**
     * AI 生成知识点总结
     */
    public AiResponse generateSummary(Long knowledgePointId) {
        KnowledgePoint kp = knowledgePointMapper.selectById(knowledgePointId);
        if (kp == null) throw new BusinessException(ResultCode.NOT_FOUND, "知识点不存在");

        String courseName = "";
        if (kp.getCourseId() != null) {
            Course course = courseMapper.selectById(kp.getCourseId());
            if (course != null) courseName = course.getName();
        }

        String systemPrompt = "你是一位专业的教育内容创作者。请为以下知识点生成一份简洁的知识总结。"
                + "要求：\n1. 清晰解释知识点的定义和概念\n2. 列出核心要点\n3. 提供实际例子\n"
                + "4. 如果有相关公式或规则请列出\n5. 使用 Markdown 格式输出";

        String userPrompt = String.format("请总结以下知识点：\n课程：%s\n知识点：%s", courseName, kp.getName());
        String content = aiProvider.chat(systemPrompt, userPrompt);
        return new AiResponse(content, "ai");
    }

    // ======================== 私有方法 ========================

    private String buildQuestionContext(Question question) {
        StringBuilder sb = new StringBuilder();
        sb.append("题型：").append(getTypeLabel(question.getQuestionType())).append("\n");
        sb.append("题目：").append(question.getContent()).append("\n");

        LambdaQueryWrapper<QuestionOption> optWrapper = new LambdaQueryWrapper<>();
        optWrapper.eq(QuestionOption::getQuestionId, question.getId()).orderByAsc(QuestionOption::getSortOrder);
        List<QuestionOption> options = questionOptionMapper.selectList(optWrapper);
        if (!options.isEmpty()) {
            sb.append("选项：\n");
            for (QuestionOption opt : options) {
                sb.append("  ").append(opt.getOptionLabel()).append(". ").append(opt.getContent());
                if (opt.getIsCorrect() != null && opt.getIsCorrect() == 1) {
                    sb.append(" [正确答案]");
                }
                sb.append("\n");
            }
        }

        if (question.getAnalysis() != null && !question.getAnalysis().isBlank()) {
            sb.append("解析：").append(question.getAnalysis()).append("\n");
        }

        LambdaQueryWrapper<QuestionKnowledgePoint> kpWrapper = new LambdaQueryWrapper<>();
        kpWrapper.eq(QuestionKnowledgePoint::getQuestionId, question.getId());
        List<QuestionKnowledgePoint> kps = questionKnowledgePointMapper.selectList(kpWrapper);
        if (!kps.isEmpty()) {
            List<Long> kpIds = kps.stream().map(QuestionKnowledgePoint::getKnowledgePointId).collect(Collectors.toList());
            List<KnowledgePoint> kpList = knowledgePointMapper.selectBatchIds(kpIds);
            if (!kpList.isEmpty()) {
                sb.append("知识点：").append(kpList.stream().map(KnowledgePoint::getName).collect(Collectors.joining("、"))).append("\n");
            }
        }

        return sb.toString();
    }

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

    private record AiPrompt(String systemPrompt, String userPrompt) {}
}
