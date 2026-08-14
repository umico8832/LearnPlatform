package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamLearningAnswerResultVO;
import com.learnplatform.dto.ExamLearningSessionVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.ExamLearningAiInteraction;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.ExamLearningAiInteractionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** 将 AI 辅导调用绑定到试卷学习会话和最近一次真实作答。 */
@Service
public class ExamLearningAiService {

    private static final int PROCESSING = 0;
    private static final int SUCCEEDED = 1;
    private static final int FAILED = 2;
    private static final Set<String> ASSISTANCE_TYPES = Set.of("EXPLANATION", "VARIANT");

    private final ExamPaperLearningService learningService;
    private final ExamLearningAiInteractionMapper interactionMapper;
    private final CourseMapper courseMapper;
    private final AiService aiService;
    private final CourseLearningEventService courseLearningEventService;

    public ExamLearningAiService(ExamPaperLearningService learningService,
                                 ExamLearningAiInteractionMapper interactionMapper,
                                 CourseMapper courseMapper,
                                 AiService aiService,
                                 CourseLearningEventService courseLearningEventService) {
        this.learningService = learningService;
        this.interactionMapper = interactionMapper;
        this.courseMapper = courseMapper;
        this.aiService = aiService;
        this.courseLearningEventService = courseLearningEventService;
    }

    public void streamAssistance(Long sessionId, Long questionId, String assistanceType,
                                 Long userId, Consumer<String> onContent) {
        String normalizedType = normalizeType(assistanceType);
        ExamLearningSessionVO session = learningService.getSession(sessionId, userId);
        ExamLearningSessionVO.QuestionItem question = session.getQuestions().stream()
                .filter(item -> questionId != null && questionId.equals(item.getQuestionId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.VALIDATION_ERROR,
                        "AI 辅导题目不属于当前试卷学习会话"));
        ExamLearningAnswerResultVO latestAnswer = question.getLatestAnswer();
        if (latestAnswer == null || latestAnswer.getAnswerId() == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "请先完成本题首次作答再使用 AI 辅导");
        }

        ExamLearningAiInteraction interaction = new ExamLearningAiInteraction();
        interaction.setUserId(userId);
        interaction.setCourseId(session.getCourseId());
        interaction.setExamPaperId(session.getExamPaperId());
        interaction.setLearningSessionId(sessionId);
        interaction.setQuestionId(questionId);
        interaction.setAnswerId(latestAnswer.getAnswerId());
        interaction.setAnswerAttemptNo(latestAnswer.getAttemptNo());
        interaction.setAnswerCorrect(Boolean.TRUE.equals(latestAnswer.getCorrect()) ? 1 : 0);
        interaction.setInteractionType(normalizedType);
        interaction.setStatus(PROCESSING);
        interaction.setStartTime(LocalDateTime.now());
        interactionMapper.insert(interaction);

        try {
            aiService.generatePaperLearningAssistanceStream(
                    questionId, normalizedType, buildContext(session, question, latestAnswer), userId, onContent);
            interaction.setStatus(SUCCEEDED);
            interaction.setCompleteTime(LocalDateTime.now());
            interactionMapper.updateById(interaction);
            courseLearningEventService.recordPaperLearningAiAssistance(
                    userId, session.getCourseId(), questionId, interaction.getId(), sessionId,
                    session.getExamPaperId(), normalizedType, latestAnswer.getAnswerId(),
                    interaction.getCompleteTime());
        } catch (RuntimeException exception) {
            interaction.setStatus(FAILED);
            interaction.setErrorMessage(truncate(exception.getMessage()));
            interaction.setCompleteTime(LocalDateTime.now());
            interactionMapper.updateById(interaction);
            throw exception;
        }
    }

    private String normalizeType(String assistanceType) {
        String normalized = assistanceType == null ? "" : assistanceType.trim().toUpperCase(Locale.ROOT);
        if (!ASSISTANCE_TYPES.contains(normalized)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "不支持的试卷学习 AI 辅导类型");
        }
        return normalized;
    }

    private String buildContext(ExamLearningSessionVO session,
                                ExamLearningSessionVO.QuestionItem question,
                                ExamLearningAnswerResultVO latestAnswer) {
        Course course = courseMapper.selectById(session.getCourseId());
        String courseName = course != null && course.getName() != null
                ? course.getName() : "课程 " + session.getCourseId();
        String result = Boolean.TRUE.equals(latestAnswer.getCorrect()) ? "正确" : "错误";
        return "课程：" + courseName + "\n"
                + "试卷：" + session.getPaperTitle() + "\n"
                + "试卷学习会话：" + session.getId() + "（"
                + (Integer.valueOf(1).equals(session.getStatus()) ? "已完成" : "进行中") + "）\n"
                + "原试卷位置：" + valueOrDash(question.getSectionTitle()) + " / "
                + valueOrDash(question.getDisplayNumber()) + "\n"
                + "用户最近答案：" + latestAnswer.getUserAnswer() + "\n"
                + "最近作答：第 " + latestAnswer.getAttemptNo() + " 次尝试，结果：" + result + "\n"
                + "请只基于以上服务端学习事实提供本次辅导。";
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "未标注" : value;
    }

    private String truncate(String message) {
        if (message == null) {
            return "AI 服务调用失败";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
