package com.learnplatform.service.tutor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.PracticeResultVO;
import com.learnplatform.dto.PracticeSubmitRequest;
import com.learnplatform.dto.TutorAgentActionVO;
import com.learnplatform.dto.TutorAgentPracticeAnswerRequest;
import com.learnplatform.dto.TutorAgentPracticeQuestionRow;
import com.learnplatform.dto.TutorAgentPracticeQuestionVO;
import com.learnplatform.dto.TutorAgentPracticeVO;
import com.learnplatform.entity.TutorAgentMessage;
import com.learnplatform.entity.TutorAgentPracticeAttempt;
import com.learnplatform.entity.TutorSession;
import com.learnplatform.mapper.TutorAgentPracticeAttemptMapper;
import com.learnplatform.mapper.TutorAgentPracticeQueryMapper;
import com.learnplatform.service.PracticeAnswerService;
import com.learnplatform.service.TutorSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Exposes only approved, currently-public Tutor practice questions and serializes first answers. */
@Service
public class TutorAgentPracticeService {
    private final TutorSessionService sessionService;
    private final TutorAgentPracticeQueryMapper queries;
    private final TutorAgentPracticeAttemptMapper attempts;
    private final PracticeAnswerService practiceAnswers;
    private final ObjectMapper json;

    public TutorAgentPracticeService(TutorSessionService sessionService, TutorAgentPracticeQueryMapper queries,
                                     TutorAgentPracticeAttemptMapper attempts, PracticeAnswerService practiceAnswers,
                                     ObjectMapper json) {
        this.sessionService = sessionService;
        this.queries = queries;
        this.attempts = attempts;
        this.practiceAnswers = practiceAnswers;
        this.json = json;
    }

    /** Returns one deterministic, unattempted approved variant without creating learning facts. */
    public Long recommend(Long userId, Long courseId, String sessionKey) {
        TutorSession session = session(userId, courseId, sessionKey);
        TutorAgentPracticeQuestionRow question = queries.findRecommendedQuestion(session.getId());
        return question == null ? null : question.id();
    }

    /** Returns the result for the latest practice action, never an arbitrary assistant reply. */
    public PracticeResultVO latestResult(Long userId, Long courseId, String sessionKey, String runKey) {
        TutorSession session = session(userId, courseId, sessionKey);
        if (!queries.ownsRun(userId, session.getId(), runKey)) {
            throw unavailable();
        }
        TutorAgentMessage message = queries.findLatestPracticeMessage(userId, session.getId(), runKey);
        if (message == null) {
            return null;
        }
        eligibleQuestion(session, message);
        TutorAgentPracticeAttempt attempt = attempts.selectByMessageId(message.getId());
        return attempt == null ? null : result(attempt);
    }

    public TutorAgentPracticeVO get(Long userId, Long courseId, String sessionKey, String runKey, Integer sequence) {
        TutorSession session = session(userId, courseId, sessionKey);
        TutorAgentMessage message = requiredMessage(userId, session.getId(), runKey, sequence, false);
        TutorAgentPracticeQuestionRow question = eligibleQuestion(session, message);
        TutorAgentPracticeAttempt attempt = attempts.selectByMessageId(message.getId());
        return attempt == null ? view(question, null) : snapshotView(attempt);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TutorAgentPracticeVO answer(Long userId, Long courseId, String sessionKey, String runKey, Integer sequence,
                                       TutorAgentPracticeAnswerRequest request) {
        TutorSession session = session(userId, courseId, sessionKey);
        TutorAgentMessage message = requiredMessage(userId, session.getId(), runKey, sequence, true);
        TutorAgentPracticeQuestionRow question = eligibleQuestion(session, message);
        TutorAgentPracticeAttempt existing = attempts.selectForUpdate(message.getId());
        if (existing != null) {
            return snapshotView(existing);
        }
        String answer = request.getUserAnswer().trim();
        if (!queries.hasOption(question.id(), answer)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "练习选项无效");
        }
        PracticeResultVO result = practiceAnswers.submitAnswer(
                submission(question.id(), answer, request.getAnswerTime()), userId);
        TutorAgentPracticeAttempt attempt = new TutorAgentPracticeAttempt();
        attempt.setMessageId(message.getId());
        attempt.setQuestionId(question.id());
        attempt.setPracticeRecordId(result.getRecordId());
        TutorAgentPracticeQuestionVO questionSnapshot = questionView(question);
        attempt.setQuestionJson(write(questionSnapshot));
        attempt.setResultJson(write(result));
        attempts.insert(attempt);
        return new TutorAgentPracticeVO(questionSnapshot, result);
    }

    private TutorSession session(Long userId, Long courseId, String sessionKey) {
        sessionService.get(userId, courseId, sessionKey);
        TutorSession session = queries.findSession(userId, courseId, sessionKey);
        if (session == null) {
            throw unavailable();
        }
        return session;
    }

    private TutorAgentMessage requiredMessage(Long userId, Long sessionId, String runKey,
                                              Integer sequence, boolean lock) {
        TutorAgentMessage message = lock
                ? queries.findPracticeMessageForUpdate(userId, sessionId, runKey, sequence)
                : queries.findPracticeMessage(userId, sessionId, runKey, sequence);
        if (message == null) {
            throw unavailable();
        }
        return message;
    }

    private TutorAgentPracticeQuestionRow eligibleQuestion(TutorSession session, TutorAgentMessage message) {
        Long questionId = practiceActionQuestionId(message);
        TutorAgentPracticeQuestionRow question = questionId == null ? null
                : queries.findEligibleQuestion(session.getId(), questionId);
        if (question == null) {
            throw unavailable();
        }
        return question;
    }

    private Long practiceActionQuestionId(TutorAgentMessage message) {
        if (message.getActionsJson() == null) {
            return null;
        }
        try {
            List<TutorAgentActionVO> actions = json.readValue(message.getActionsJson(), new TypeReference<>() { });
            return actions.stream().filter(action -> "PRACTICE".equals(action.type()))
                    .map(TutorAgentActionVO::questionId).findFirst().orElse(null);
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private TutorAgentPracticeVO view(TutorAgentPracticeQuestionRow question, PracticeResultVO result) {
        return new TutorAgentPracticeVO(questionView(question), result);
    }

    private TutorAgentPracticeQuestionVO questionView(TutorAgentPracticeQuestionRow question) {
        return new TutorAgentPracticeQuestionVO(
                question.id(), question.content(), question.questionType(),
                queries.findPublicOptions(question.id()));
    }

    private TutorAgentPracticeVO snapshotView(TutorAgentPracticeAttempt attempt) {
        return new TutorAgentPracticeVO(question(attempt), result(attempt));
    }

    private PracticeSubmitRequest submission(Long questionId, String answer, Integer answerTime) {
        PracticeSubmitRequest request = new PracticeSubmitRequest();
        request.setQuestionId(questionId);
        request.setUserAnswer(answer);
        request.setAnswerTime(answerTime);
        return request;
    }

    private PracticeResultVO result(TutorAgentPracticeAttempt attempt) {
        try {
            return json.readValue(attempt.getResultJson(), PracticeResultVO.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Tutor 练习结果格式无效", exception);
        }
    }

    private TutorAgentPracticeQuestionVO question(TutorAgentPracticeAttempt attempt) {
        try {
            return json.readValue(attempt.getQuestionJson(), TutorAgentPracticeQuestionVO.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Tutor 练习题面快照格式无效", exception);
        }
    }

    private String write(PracticeResultVO result) {
        try {
            return json.writeValueAsString(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Tutor 练习结果无法保存", exception);
        }
    }

    private String write(TutorAgentPracticeQuestionVO question) {
        try {
            return json.writeValueAsString(question);
        } catch (Exception exception) {
            throw new IllegalStateException("Tutor 练习题面无法保存", exception);
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(ResultCode.NOT_FOUND, "Tutor 推荐练习不存在或已不可用");
    }
}
