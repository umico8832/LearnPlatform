package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.AiResponse;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.service.question.QuestionAccessPolicy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class AiQuestionAssistanceService {
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final AiInvocationService invocationService;

    public AiQuestionAssistanceService(
            QuestionMapper questionMapper,
            QuestionOptionMapper questionOptionMapper,
            QuestionKnowledgePointMapper questionKnowledgePointMapper,
            KnowledgePointMapper knowledgePointMapper,
            AiInvocationService invocationService) {
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.invocationService = invocationService;
    }

    public AiResponse generateExplanation(Long questionId) {
        return invocationService.callUnlogged(buildExplanationPrompt(questionId, null, false));
    }

    public AiResponse generateExplanation(Long questionId, Long userId) {
        AiService.AiPrompt prompt = buildExplanationPrompt(questionId, userId, true);
        return invocationService.call("explanation", userId, prompt);
    }

    public void generateExplanationStream(Long questionId, Consumer<String> onContent) {
        invocationService.streamUnlogged(buildExplanationPrompt(questionId, null, false), onContent);
    }

    public void generateExplanationStream(Long questionId, Long userId, Consumer<String> onContent) {
        AiService.AiPrompt prompt = buildExplanationPrompt(questionId, userId, true);
        invocationService.stream("explanation_stream", userId, prompt, onContent);
    }

    public AiResponse generateVariant(Long questionId) {
        return invocationService.callUnlogged(buildVariantPrompt(questionId, null, false));
    }

    public AiResponse generateVariant(Long questionId, Long userId) {
        AiService.AiPrompt prompt = buildVariantPrompt(questionId, userId, true);
        return invocationService.call("variant", userId, prompt);
    }

    public void generateVariantStream(Long questionId, Consumer<String> onContent) {
        invocationService.streamUnlogged(buildVariantPrompt(questionId, null, false), onContent);
    }

    public void generateVariantStream(Long questionId, Long userId, Consumer<String> onContent) {
        AiService.AiPrompt prompt = buildVariantPrompt(questionId, userId, true);
        invocationService.stream("variant_stream", userId, prompt, onContent);
    }

    public void generatePaperLearningAssistanceStream(Long questionId, String assistanceType,
                                                       String learningContext, Long userId,
                                                       Consumer<String> onContent) {
        Question question = findQuestion(questionId, userId, true);
        boolean variant = "VARIANT".equals(assistanceType);
        String systemPrompt = variant
                ? "你是一位试卷学习辅导老师。请基于原题和用户真实作答生成一道同知识点练习，"
                + "明确说明它是 AI 生成练习而非官方原题，给出答案与解析，并使用 Markdown。"
                : "你是一位试卷学习辅导老师。请结合原试卷位置和用户最近一次真实作答，"
                + "针对错误或不完整理解给出清晰讲解，分析关键步骤与选项，并使用 Markdown。";
        AiService.AiPrompt prompt = new AiService.AiPrompt(systemPrompt,
                "## 试卷学习上下文\n" + learningContext + "\n\n## 当前原题\n" + buildQuestionContext(question));
        invocationService.stream(variant
                ? "paper_learning_variant_stream" : "paper_learning_explanation_stream",
                userId, prompt, onContent);
    }

    private AiService.AiPrompt buildExplanationPrompt(Long questionId, Long userId, boolean ownerAware) {
        Question question = findQuestion(questionId, userId, ownerAware);
        String systemPrompt = "你是一位专业的教育辅导老师。请为以下题目提供详细、清晰的解析。"
                + "要求：\n1. 解释题目考查的知识点\n2. 分析正确答案的原因\n3. 如果有错误选项，解释为什么是错误的\n"
                + "4. 用简洁易懂的语言\n5. 使用 Markdown 格式输出";
        return new AiService.AiPrompt(systemPrompt, "请解析这道题目：\n\n" + buildQuestionContext(question));
    }

    private AiService.AiPrompt buildVariantPrompt(Long questionId, Long userId, boolean ownerAware) {
        Question question = findQuestion(questionId, userId, ownerAware);
        String systemPrompt = "你是一位专业的出题老师。请基于给定的题目，生成 1-2 道变式题（类似知识点但不同问法）。"
                + "要求：\n1. 考查相同知识点但换个角度\n2. 难度与原题相近\n3. 包含题目、选项和正确答案\n"
                + "4. 使用 Markdown 格式输出";
        return new AiService.AiPrompt(systemPrompt, "基于以下题目生成变式题：\n\n" + buildQuestionContext(question));
    }

    private Question findQuestion(Long questionId, Long userId, boolean ownerAware) {
        Question question = questionMapper.selectById(questionId);
        boolean accessible = ownerAware
                ? QuestionAccessPolicy.canAccess(question, userId)
                : QuestionAccessPolicy.isPublic(question);
        if (!accessible) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }
        return question;
    }

    private String buildQuestionContext(Question question) {
        StringBuilder context = new StringBuilder();
        context.append("题型：").append(typeLabel(question.getQuestionType())).append("\n");
        context.append("题目：").append(question.getContent()).append("\n");
        appendOptions(context, question.getId());
        if (question.getAnalysis() != null && !question.getAnalysis().isBlank()) {
            context.append("解析：").append(question.getAnalysis()).append("\n");
        }
        appendKnowledgePoints(context, question.getId());
        return context.toString();
    }

    private void appendOptions(StringBuilder context, Long questionId) {
        List<QuestionOption> options = questionOptionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, questionId)
                .orderByAsc(QuestionOption::getSortOrder));
        if (options.isEmpty()) {
            return;
        }
        context.append("选项：\n");
        for (QuestionOption option : options) {
            context.append("  ").append(option.getOptionLabel()).append(". ").append(option.getContent());
            if (Integer.valueOf(1).equals(option.getIsCorrect())) {
                context.append(" [正确答案]");
            }
            context.append("\n");
        }
    }

    private void appendKnowledgePoints(StringBuilder context, Long questionId) {
        List<QuestionKnowledgePoint> relations = questionKnowledgePointMapper.selectList(
                new LambdaQueryWrapper<QuestionKnowledgePoint>()
                        .eq(QuestionKnowledgePoint::getQuestionId, questionId));
        if (relations.isEmpty()) {
            return;
        }
        List<Long> knowledgePointIds = relations.stream()
                .map(QuestionKnowledgePoint::getKnowledgePointId).toList();
        List<KnowledgePoint> knowledgePoints = knowledgePointMapper.selectBatchIds(knowledgePointIds);
        if (!knowledgePoints.isEmpty()) {
            context.append("知识点：").append(knowledgePoints.stream()
                    .map(KnowledgePoint::getName).collect(Collectors.joining("、"))).append("\n");
        }
    }

    private String typeLabel(String type) {
        if (type == null) {
            return "未知";
        }
        return switch (type) {
            case "SINGLE_CHOICE" -> "单选题";
            case "MULTIPLE_CHOICE" -> "多选题";
            case "TRUE_FALSE" -> "判断题";
            case "FILL_BLANK" -> "填空题";
            case "SHORT_ANSWER" -> "简答题";
            default -> type;
        };
    }
}
