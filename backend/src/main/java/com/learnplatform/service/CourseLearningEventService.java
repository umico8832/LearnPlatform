package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.entity.CourseLearningEvent;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.UserCourse;
import com.learnplatform.mapper.CourseLearningEventMapper;
import com.learnplatform.mapper.UserCourseMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 将已有业务记录投影为课程学习事件。调用方必须在其业务事务中调用本服务；
 * 事件只在用户已将所属课程加入个人课程库后写入，避免把普通题库浏览误作课程学习。
 */
@Service
public class CourseLearningEventService {

    private static final int EVENT_VERSION = 1;
    private static final String SUBJECT_TYPE_QUESTION = "QUESTION";

    private final UserCourseMapper userCourseMapper;
    private final CourseLearningEventMapper courseLearningEventMapper;

    public CourseLearningEventService(UserCourseMapper userCourseMapper,
                                      CourseLearningEventMapper courseLearningEventMapper) {
        this.userCourseMapper = userCourseMapper;
        this.courseLearningEventMapper = courseLearningEventMapper;
    }

    public void recordQuestionAnswer(Long userId, Question question, String eventType,
                                     String eventSource, Long sourceRecordId, boolean correct,
                                     LocalDateTime occurredTime) {
        if (userId == null || question == null || question.getCourseId() == null || sourceRecordId == null) {
            return;
        }
        if (!hasCourseInLibrary(userId, question.getCourseId())) {
            return;
        }

        CourseLearningEvent event = new CourseLearningEvent();
        event.setUserId(userId);
        event.setCourseId(question.getCourseId());
        event.setEventType(eventType);
        event.setEventSource(eventSource);
        event.setSubjectType(SUBJECT_TYPE_QUESTION);
        event.setSubjectId(question.getId());
        event.setSourceRecordId(sourceRecordId);
        event.setIdempotencyKey(eventSource + ":" + sourceRecordId);
        event.setEventVersion(EVENT_VERSION);
        event.setPayloadJson("{\"isCorrect\":" + correct + "}");
        event.setOccurredTime(occurredTime != null ? occurredTime : LocalDateTime.now());
        try {
            courseLearningEventMapper.insert(event);
        } catch (DuplicateKeyException ignored) {
            // 同一来源事实的重放不能制造第二条学习事件。
        }
    }

    private boolean hasCourseInLibrary(Long userId, Long courseId) {
        return userCourseMapper.selectCount(new LambdaQueryWrapper<UserCourse>()
                .eq(UserCourse::getUserId, userId)
                .eq(UserCourse::getCourseId, courseId)) > 0;
    }
}
