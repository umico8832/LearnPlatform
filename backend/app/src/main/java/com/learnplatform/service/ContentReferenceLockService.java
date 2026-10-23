package com.learnplatform.service;

import com.learnplatform.service.content.LockedContentParents;
import java.util.Collections;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ContentReferenceIntegrityMapper;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Serializes logical parent deletion with all reference writes. Caller owns the transaction.
 * Parents are always acquired course-id ascending, then knowledge-point-id ascending.
 */
@Service
public class ContentReferenceLockService {
    private final CourseMapper courses;
    private final KnowledgePointMapper knowledgePoints;
    private final ContentReferenceIntegrityMapper references;
    private final QuestionMapper questions;
    private final ExamPaperMapper papers;

    public ContentReferenceLockService(CourseMapper courses, KnowledgePointMapper knowledgePoints,
                                       ContentReferenceIntegrityMapper references,
                                       QuestionMapper questions, ExamPaperMapper papers) {
        this.courses = courses;
        this.knowledgePoints = knowledgePoints;
        this.references = references;
        this.questions = questions;
        this.papers = papers;
    }

    public Map<Long, Question> lockQuestions(Collection<Long> ids, Collection<Long> extraCourses) {
        var snapshots = discoverQuestions(ids);
        lockQuestionCourses(snapshots, extraCourses);
        return lockQuestionRows(snapshots);
    }

    public void lockPaperQuestions(Long paperId) {
        requireTransaction();
        var snapshot = papers.selectById(paperId);
        if (snapshot == null) { throw notFound("试卷不存在"); }
        var ids = references.paperQuestionIds(paperId, false);
        var snapshots = discoverQuestions(ids);
        lockQuestionCourses(snapshots, Collections.singletonList(snapshot.getCourseId()));
        var current = papers.selectActiveForShare(paperId);
        if (current == null || !Objects.equals(snapshot.getCourseId(), current.getCourseId())
                || !ids.equals(references.paperQuestionIds(paperId, true))) { throw changedReference(); }
        lockQuestionRows(snapshots);
    }

    public void lockAssessmentQuestions(Long assessmentId, Long courseId, Long targetId) {
        // Assessment question identities are immutable once their creating transaction commits.
        var snapshots = discoverQuestions(references.assessmentQuestionIds(assessmentId));
        var ids = new ArrayList<Long>();
        ids.add(courseId);
        snapshots.values().forEach(question -> ids.add(question.getCourseId()));
        var parents = lockForReference(ids, targetId == null ? List.of() : List.of(targetId));
        if (targetId != null && !Objects.equals(courseId, parents.knowledgePoints().get(targetId).getCourseId())) {
            throw changedReference();
        }
        lockQuestionRows(snapshots);
    }

    private Map<Long, Question> discoverQuestions(Collection<Long> ids) {
        requireTransaction();
        var snapshots = new LinkedHashMap<Long, Question>();
        for (Long id : sortedIds(ids)) {
            var question = questions.selectById(id);
            if (question == null) { throw notFound("题目不存在"); }
            snapshots.put(id, question);
        }
        return snapshots;
    }

    private void lockQuestionCourses(Map<Long, Question> snapshots, Collection<Long> extraCourses) {
        var coursesToLock = new ArrayList<Long>();
        if (extraCourses != null) { coursesToLock.addAll(extraCourses); }
        snapshots.values().forEach(question -> coursesToLock.add(question.getCourseId()));
        lockForReference(coursesToLock, List.of());
    }

    private Map<Long, Question> lockQuestionRows(Map<Long, Question> snapshots) {
        var locked = new LinkedHashMap<Long, Question>();
        for (var entry : snapshots.entrySet()) {
            Question question = questions.selectActiveForShare(entry.getKey());
            if (question == null || !Objects.equals(entry.getValue().getCourseId(), question.getCourseId())) {
                throw changedReference();
            }
            locked.put(entry.getKey(), question);
        }
        return locked;
    }

    private BusinessException changedReference() {
        return new BusinessException(ResultCode.BUSINESS_ERROR, "题目或试卷归属已变更，请重试");
    }

    public LockedContentParents lockForReference(Collection<Long> courseIds, Collection<Long> knowledgePointIds) {
        requireTransaction();
        Map<Long, Course> lockedCourses = new LinkedHashMap<>();
        for (Long id : sortedIds(courseIds)) {
            Course course = courses.selectActiveForShare(id);
            if (course == null) { throw notFound("课程不存在"); }
            lockedCourses.put(id, course);
        }
        Map<Long, KnowledgePoint> lockedPoints = new LinkedHashMap<>();
        for (Long id : sortedIds(knowledgePointIds)) {
            KnowledgePoint point = knowledgePoints.selectActiveForShare(id);
            if (point == null) { throw validation("知识点不存在"); }
            if (!lockedCourses.containsKey(point.getCourseId())) {
                throw validation("知识点不属于当前课程");
            }
            lockedPoints.put(id, point);
        }
        return new LockedContentParents(lockedCourses, lockedPoints);
    }

    public Course lockCourseForDelete(Long courseId) {
        requireTransaction();
        Course course = courses.selectActiveForUpdate(courseId);
        if (course == null) { throw notFound("课程不存在"); }
        return course;
    }

    public KnowledgePoint lockKnowledgePointForDelete(Long knowledgePointId) {
        requireTransaction();
        KnowledgePoint candidate = knowledgePoints.selectById(knowledgePointId);
        if (candidate == null) { throw notFound("知识点不存在"); }
        lockForReference(List.of(candidate.getCourseId()), List.of());
        KnowledgePoint point = knowledgePoints.selectActiveForUpdate(knowledgePointId);
        if (point == null) { throw notFound("知识点不存在"); }
        if (!Objects.equals(candidate.getCourseId(), point.getCourseId())) { throw changedReference(); }
        return point;
    }

    public void rejectCourseDeleteWithReferences(Long courseId) {
        if (references.courseKnowledgePoint(courseId) != null || references.courseQuestion(courseId) != null
                || references.courseExamPaper(courseId) != null || references.courseUserCourse(courseId) != null
                || references.courseLearningEvent(courseId) != null || references.courseTutorSession(courseId) != null
                || references.courseStageAssessment(courseId) != null
                || references.courseQuestionSubmission(courseId) != null
                || references.courseCommunityPost(courseId) != null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "课程仍被下游内容引用，无法删除");
        }
    }

    public void rejectKnowledgePointDeleteWithReferences(Long knowledgePointId) {
        if (references.pointChild(knowledgePointId) != null
                || references.pointQuestionRelation(knowledgePointId) != null
                || references.pointTutorContent(knowledgePointId) != null
                || references.pointTutorSession(knowledgePointId) != null
                || references.pointStageAssessment(knowledgePointId) != null
                || references.pointQuestionSubmission(knowledgePointId) != null
                || references.pointCommunityPost(knowledgePointId) != null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "知识点仍被下游内容引用，无法删除");
        }
    }

    private static List<Long> sortedIds(Collection<Long> ids) {
        if (ids == null) { return List.of(); }
        return ids.stream().filter(Objects::nonNull).distinct().sorted(Comparator.naturalOrder()).toList();
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("父引用锁必须在业务事务内获取");
        }
    }

    private static BusinessException notFound(String message) {
        return new BusinessException(ResultCode.NOT_FOUND, message);
    }

    private static BusinessException validation(String message) {
        return new BusinessException(ResultCode.VALIDATION_ERROR, message);
    }

}
