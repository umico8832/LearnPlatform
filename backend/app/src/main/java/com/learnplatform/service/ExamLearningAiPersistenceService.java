package com.learnplatform.service;

import java.util.List;

import com.learnplatform.entity.ExamLearningAiInteraction;
import com.learnplatform.mapper.ExamLearningAiInteractionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short database writes around streaming; never hold a parent lock during model generation. */
@Service
public class ExamLearningAiPersistenceService {
    private final ExamLearningAiInteractionMapper interactions;
    private final ContentReferenceLockService locks;
    private final CourseLearningEventService events;
    public ExamLearningAiPersistenceService(ExamLearningAiInteractionMapper interactions,
                                            ContentReferenceLockService locks, CourseLearningEventService events) {
        this.interactions = interactions;
        this.locks = locks;
        this.events = events;
    }
    @Transactional public void start(ExamLearningAiInteraction interaction) {
        locks.lockForReference(List.of(interaction.getCourseId()), List.of());
        interactions.insert(interaction);
    }
    @Transactional public void complete(ExamLearningAiInteraction interaction, Long sessionId, Long paperId,
                                        Long answerId, String type) {
        locks.lockForReference(List.of(interaction.getCourseId()), List.of());
        interactions.updateById(interaction);
        events.recordPaperLearningAiAssistance(interaction.getUserId(), interaction.getCourseId(),
                interaction.getQuestionId(), interaction.getId(), sessionId, paperId, type, answerId,
                interaction.getCompleteTime());
    }
    @Transactional public void fail(ExamLearningAiInteraction interaction) {
        locks.lockForReference(List.of(interaction.getCourseId()), List.of());
        interactions.updateById(interaction);
    }
}
