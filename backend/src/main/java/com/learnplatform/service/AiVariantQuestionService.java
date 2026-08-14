package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.AiVariantQuestionVO;
import com.learnplatform.dto.AiVariantTrainingVO;
import com.learnplatform.entity.AiVariantQuestion;
import com.learnplatform.entity.AiVariantTraining;
import com.learnplatform.entity.QuestionAiAsset;
import com.learnplatform.mapper.AiVariantQuestionMapper;
import com.learnplatform.mapper.AiVariantTrainingMapper;
import com.learnplatform.mapper.QuestionAiAssetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 结构化变式题的解析、私有答案保存和首次作答判分。 */
@Service
public class AiVariantQuestionService {

    private static final Logger log = LoggerFactory.getLogger(AiVariantQuestionService.class);
    private static final String SAFE_ASSET_CONTENT = "结构化变式题已生成，请在下方独立作答后提交判分。";

    private final ObjectMapper objectMapper;
    private final QuestionAiAssetMapper questionAiAssetMapper;
    private final AiVariantQuestionMapper aiVariantQuestionMapper;
    private final AiVariantTrainingMapper aiVariantTrainingMapper;
    private final AnswerEvaluator answerEvaluator;
    private final CourseLearningEventService courseLearningEventService;

    public AiVariantQuestionService(ObjectMapper objectMapper,
                                    QuestionAiAssetMapper questionAiAssetMapper,
                                    AiVariantQuestionMapper aiVariantQuestionMapper,
                                    AiVariantTrainingMapper aiVariantTrainingMapper,
                                    AnswerEvaluator answerEvaluator) {
        this(objectMapper, questionAiAssetMapper, aiVariantQuestionMapper, aiVariantTrainingMapper,
                answerEvaluator, null);
    }

    @Autowired
    public AiVariantQuestionService(ObjectMapper objectMapper,
                                    QuestionAiAssetMapper questionAiAssetMapper,
                                    AiVariantQuestionMapper aiVariantQuestionMapper,
                                    AiVariantTrainingMapper aiVariantTrainingMapper,
                                    AnswerEvaluator answerEvaluator,
                                    CourseLearningEventService courseLearningEventService) {
        this.objectMapper = objectMapper;
        this.questionAiAssetMapper = questionAiAssetMapper;
        this.aiVariantQuestionMapper = aiVariantQuestionMapper;
        this.aiVariantTrainingMapper = aiVariantTrainingMapper;
        this.answerEvaluator = answerEvaluator;
        this.courseLearningEventService = courseLearningEventService;
    }

    /** 先校验完整 AI JSON，再在同一事务中保存公开资产与私有答案。 */
    @Transactional
    public QuestionAiAsset saveGeneratedAsset(Long questionId, String model, String rawContent) {
        ParsedVariant parsed = parseAndValidate(rawContent);

        QuestionAiAsset asset = new QuestionAiAsset();
        asset.setQuestionId(questionId);
        asset.setAssetType("VARIANT");
        asset.setContent(SAFE_ASSET_CONTENT);
        asset.setModel(model);
        questionAiAssetMapper.insert(asset);

        AiVariantQuestion question = new AiVariantQuestion();
        question.setAssetId(asset.getId());
        question.setQuestionType("SINGLE_CHOICE");
        question.setQuestionContent(parsed.questionContent());
        question.setOptionsJson(writeOptions(parsed.options()));
        question.setCorrectAnswer(parsed.correctAnswer());
        question.setAnalysis(parsed.analysis());
        question.setDifficulty(parsed.difficulty());
        question.setReviewStatus("PENDING");
        aiVariantQuestionMapper.insert(question);
        return asset;
    }

    public AiVariantQuestionVO getPublicQuestion(Long assetId) {
        if (assetId == null) return null;
        AiVariantQuestion question = findByAssetId(assetId);
        if (question == null) return null;
        try {
            AiVariantQuestionVO vo = new AiVariantQuestionVO();
            vo.setId(question.getId());
            vo.setQuestionType(question.getQuestionType());
            vo.setQuestionContent(question.getQuestionContent());
            vo.setOptions(objectMapper.readValue(question.getOptionsJson(),
                    new TypeReference<List<AiVariantQuestionVO.Option>>() {}));
            vo.setDifficulty(question.getDifficulty());
            return vo;
        } catch (JsonProcessingException ex) {
            log.error("结构化变式题选项解析失败: assetId={}", assetId, ex);
            return null;
        }
    }

    public boolean hasStructuredQuestion(Long assetId) {
        return assetId != null && findByAssetId(assetId) != null;
    }

    /** 首次提交即完成训练；后续重复提交返回首次判分结果，不覆盖行为样本。 */
    @Transactional
    public AiVariantTrainingVO submitAnswer(Long questionId, Long userId, String rawUserAnswer) {
        QuestionAiAsset asset = questionAiAssetMapper.selectOne(new LambdaQueryWrapper<QuestionAiAsset>()
                .eq(QuestionAiAsset::getQuestionId, questionId)
                .eq(QuestionAiAsset::getAssetType, "VARIANT"));
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "变式题学习资产不存在");
        }
        AiVariantQuestion variantQuestion = findByAssetId(asset.getId());
        if (variantQuestion == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "当前为旧版变式题，请先清除缓存后重新生成");
        }

        AiVariantTraining training = aiVariantTrainingMapper.selectOne(new LambdaQueryWrapper<AiVariantTraining>()
                .eq(AiVariantTraining::getUserId, userId)
                .eq(AiVariantTraining::getAssetId, asset.getId())
                .last("FOR UPDATE"));
        if (training == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "请先查看变式题，再提交答案");
        }
        if (training.getAnsweredTime() != null) {
            return enrichTrainingVO(training, newTrainingVO(training));
        }

        String userAnswer = rawUserAnswer == null ? "" : rawUserAnswer.trim().toUpperCase(Locale.ROOT);
        if (userAnswer.isBlank()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "请选择答案");
        }
        boolean correct = answerEvaluator.isCorrect(
                variantQuestion.getQuestionType(), userAnswer, variantQuestion.getCorrectAnswer());
        LocalDateTime now = LocalDateTime.now();
        training.setUserAnswer(userAnswer);
        training.setIsCorrect(correct ? 1 : 0);
        training.setAnsweredTime(now);
        training.setStatus("COMPLETED");
        training.setCompletedTime(now);
        aiVariantTrainingMapper.updateById(training);
        if (courseLearningEventService != null) {
            com.learnplatform.entity.Question sourceQuestion = new com.learnplatform.entity.Question();
            sourceQuestion.setId(questionId);
            // 变式题的课程归属仍由原题决定，避免客户端传入或复制课程范围。
            sourceQuestion.setCourseId(questionAiAssetMapper.findCourseIdByQuestionId(questionId));
            courseLearningEventService.recordQuestionAnswer(userId, sourceQuestion, "AI_VARIANT_ANSWERED", "AI_TUTOR",
                    training.getId(), correct, now);
        }
        return enrichTrainingVO(training, newTrainingVO(training));
    }

    /** 只有完成首次判分后才补充正确答案和解析。 */
    public AiVariantTrainingVO enrichTrainingVO(AiVariantTraining training, AiVariantTrainingVO vo) {
        boolean answered = training != null && training.getAnsweredTime() != null;
        vo.setAnswered(answered);
        if (!answered) return vo;

        vo.setCorrect(Integer.valueOf(1).equals(training.getIsCorrect()));
        vo.setUserAnswer(training.getUserAnswer());
        vo.setAnsweredTime(training.getAnsweredTime());
        AiVariantQuestion question = findByAssetId(training.getAssetId());
        if (question != null) {
            vo.setCorrectAnswer(question.getCorrectAnswer());
            vo.setAnalysis(question.getAnalysis());
        }
        return vo;
    }

    private AiVariantTrainingVO newTrainingVO(AiVariantTraining training) {
        AiVariantTrainingVO vo = new AiVariantTrainingVO();
        vo.setQuestionId(training.getQuestionId());
        vo.setAssetId(training.getAssetId());
        vo.setStatus(training.getStatus());
        vo.setCompleted("COMPLETED".equals(training.getStatus()));
        vo.setStartedTime(training.getStartedTime());
        vo.setCompletedTime(training.getCompletedTime());
        return vo;
    }

    private AiVariantQuestion findByAssetId(Long assetId) {
        return aiVariantQuestionMapper.selectOne(new LambdaQueryWrapper<AiVariantQuestion>()
                .eq(AiVariantQuestion::getAssetId, assetId));
    }

    private ParsedVariant parseAndValidate(String rawContent) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(rawContent));
            String questionType = requiredText(root, "questionType", "题型");
            if (!"SINGLE_CHOICE".equals(questionType)) {
                throw invalidFormat("首版只支持 SINGLE_CHOICE");
            }
            String questionContent = requiredText(root, "questionContent", "题干");
            String correctAnswer = requiredText(root, "correctAnswer", "正确答案")
                    .toUpperCase(Locale.ROOT);
            String analysis = requiredText(root, "analysis", "解析");
            int difficulty = root.path("difficulty").asInt(3);
            if (difficulty < 1 || difficulty > 5) {
                throw invalidFormat("难度必须在1到5之间");
            }

            JsonNode optionNodes = root.path("options");
            if (!optionNodes.isArray() || optionNodes.size() < 2 || optionNodes.size() > 6) {
                throw invalidFormat("选项数量必须在2到6之间");
            }
            List<AiVariantQuestionVO.Option> options = new ArrayList<>();
            Set<String> labels = new HashSet<>();
            for (JsonNode optionNode : optionNodes) {
                String label = requiredText(optionNode, "label", "选项标签").toUpperCase(Locale.ROOT);
                String content = requiredText(optionNode, "content", "选项内容");
                if (!labels.add(label)) throw invalidFormat("选项标签不能重复");
                options.add(new AiVariantQuestionVO.Option(label, content));
            }
            if (!labels.contains(correctAnswer)) {
                throw invalidFormat("正确答案必须对应一个选项标签");
            }
            return new ParsedVariant(questionContent, options, correctAnswer, analysis, difficulty);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("AI 变式题未返回合法结构化 JSON: {}", ex.getMessage());
            throw invalidFormat("无法解析 AI 返回内容");
        }
    }

    private String requiredText(JsonNode node, String field, String label) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) throw invalidFormat(label + "不能为空");
        return value;
    }

    private String writeOptions(List<AiVariantQuestionVO.Option> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException ex) {
            throw invalidFormat("选项序列化失败");
        }
    }

    private String stripCodeFence(String content) {
        if (content == null) return "";
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int firstLineEnd = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstLineEnd < 0 || lastFence <= firstLineEnd) return trimmed;
        return trimmed.substring(firstLineEnd + 1, lastFence).trim();
    }

    private BusinessException invalidFormat(String detail) {
        return new BusinessException(ResultCode.BUSINESS_ERROR, "AI 变式题格式无效：" + detail);
    }

    private record ParsedVariant(String questionContent,
                                 List<AiVariantQuestionVO.Option> options,
                                 String correctAnswer,
                                 String analysis,
                                 int difficulty) {}
}
