package com.learnplatform.service.tutor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.dto.TutorAgentActionVO;
import com.learnplatform.dto.TutorAgentPlanStepVO;
import com.learnplatform.dto.TutorAgentPlanVO;
import com.learnplatform.entity.TutorAgentMessage;
import com.learnplatform.entity.TutorAgentPlanConfirmation;
import com.learnplatform.mapper.TutorAgentPlanConfirmationMapper;
import com.learnplatform.mapper.TutorAgentPlanQueryMapper;
import com.learnplatform.service.CourseOverviewService;
import com.learnplatform.service.TutorSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Presents and explicitly confirms server-selected Tutor plan steps without recording learning facts. */
@Service
public class TutorAgentPlanService {
    private final TutorSessionService sessions;
    private final CourseOverviewService overviews;
    private final TutorAgentPlanQueryMapper plans;
    private final TutorAgentPlanConfirmationMapper confirmations;
    private final ObjectMapper json;

    public TutorAgentPlanService(TutorSessionService sessions, CourseOverviewService overviews,
                                 TutorAgentPlanQueryMapper plans, TutorAgentPlanConfirmationMapper confirmations,
                                 ObjectMapper json) {
        this.sessions = sessions;
        this.overviews = overviews;
        this.plans = plans;
        this.confirmations = confirmations;
        this.json = json;
    }

    /** Computes at most three current overview targets for the Agent tool; it does not persist anything. */
    public List<TutorAgentPlanStepVO> propose(Long userId, Long courseId, String sessionKey) {
        sessions.get(userId, courseId, sessionKey);
        Set<Long> selectedQuestionIds = new HashSet<>();
        return overviews.getOverview(userId, courseId).getRecommendedTargets().stream()
                .map(this::step).flatMap(java.util.Optional::stream)
                .filter(step -> step.questionId() == null || selectedQuestionIds.add(step.questionId()))
                .limit(3).toList();
    }

    public TutorAgentPlanVO get(Long userId, Long courseId, String sessionKey, String runKey, Integer sequence) {
        sessions.get(userId, courseId, sessionKey);
        TutorAgentMessage message = requiredMessage(userId, courseId, sessionKey, runKey, sequence, false);
        List<TutorAgentPlanStepVO> steps = planSteps(message);
        TutorAgentPlanConfirmation confirmation = confirmations.selectByMessageId(message.getId());
        return planView(userId, courseId, sessionKey, steps, confirmation);
    }

    /** Reads the current run's latest persisted plan action, ignoring later ordinary assistant messages. */
    public TutorAgentPlanVO latest(Long userId, Long courseId, String sessionKey, String runKey) {
        sessions.get(userId, courseId, sessionKey);
        if (!plans.ownsRun(userId, courseId, sessionKey, runKey)) {
            throw unavailable();
        }
        TutorAgentMessage message = plans.findLatestPlanMessage(userId, courseId, sessionKey, runKey);
        if (message == null) {
            return null;
        }
        List<TutorAgentPlanStepVO> steps = planSteps(message);
        TutorAgentPlanConfirmation confirmation = confirmations.selectByMessageId(message.getId());
        return planView(userId, courseId, sessionKey, steps, confirmation);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TutorAgentPlanVO confirm(Long userId, Long courseId, String sessionKey, String runKey, Integer sequence) {
        sessions.get(userId, courseId, sessionKey);
        TutorAgentMessage message = requiredMessage(userId, courseId, sessionKey, runKey, sequence, true);
        List<TutorAgentPlanStepVO> steps = planSteps(message);
        TutorAgentPlanConfirmation existing = confirmations.selectForUpdate(message.getId());
        if (existing != null) {
            return planView(userId, courseId, sessionKey, steps, existing);
        }
        if (!available(userId, courseId, sessionKey, steps)) {
            throw unavailable();
        }
        TutorAgentPlanConfirmation confirmation = new TutorAgentPlanConfirmation();
        confirmation.setMessageId(message.getId());
        confirmation.setConfirmedTime(LocalDateTime.now());
        confirmations.insert(confirmation);
        TutorAgentPlanConfirmation persisted = confirmations.selectByMessageId(message.getId());
        return new TutorAgentPlanVO(steps, true, persisted.getConfirmedTime(), true);
    }

    private java.util.Optional<TutorAgentPlanStepVO> step(CourseOverviewVO.LearningTargetVO target) {
        try {
            TutorAgentPlanStepVO step = new TutorAgentPlanStepVO(target.getType(), target.getTitle(),
                    target.getReason(), target.getKnowledgePointId(), target.getQuestionId());
            return java.util.Optional.of(step);
        } catch (IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }

    private TutorAgentMessage requiredMessage(Long userId, Long courseId, String sessionKey, String runKey,
                                              Integer sequence, boolean lock) {
        TutorAgentMessage message = lock
                ? plans.findPlanMessageForUpdate(userId, courseId, sessionKey, runKey, sequence)
                : plans.findPlanMessage(userId, courseId, sessionKey, runKey, sequence);
        if (message == null) {
            throw unavailable();
        }
        return message;
    }

    private List<TutorAgentPlanStepVO> planSteps(TutorAgentMessage message) {
        if (message.getActionsJson() == null) {
            throw unavailable();
        }
        try {
            List<TutorAgentActionVO> actions = json.readValue(message.getActionsJson(), new TypeReference<>() { });
            return actions.stream().filter(action -> "PLAN".equals(action.type())).findFirst()
                    .map(TutorAgentActionVO::steps).orElseThrow(this::unavailable);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private boolean available(Long userId, Long courseId, String sessionKey, List<TutorAgentPlanStepVO> steps) {
        Set<String> current = propose(userId, courseId, sessionKey).stream()
                .map(this::identity).collect(java.util.stream.Collectors.toSet());
        return steps.stream().allMatch(step -> current.contains(identity(step)));
    }

    private TutorAgentPlanVO planView(Long userId, Long courseId, String sessionKey,
                                      List<TutorAgentPlanStepVO> steps,
                                      TutorAgentPlanConfirmation confirmation) {
        boolean confirmed = confirmation != null;
        LocalDateTime confirmedAt = confirmed ? confirmation.getConfirmedTime() : null;
        return new TutorAgentPlanVO(steps, confirmed, confirmedAt, available(userId, courseId, sessionKey, steps));
    }

    private String identity(TutorAgentPlanStepVO step) {
        return step.type() + ":" + (step.knowledgePointId() == null ? step.questionId() : step.knowledgePointId());
    }

    private BusinessException unavailable() {
        return new BusinessException(ResultCode.NOT_FOUND, "Tutor 学习计划不存在或已不可用");
    }
}
