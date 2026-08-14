package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.dto.CourseStageAssessmentSummaryVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.CourseLearningEvent;
import com.learnplatform.entity.CourseStageAssessment;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.entity.TutorContent;
import com.learnplatform.entity.TutorSession;
import com.learnplatform.entity.UserCourse;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.CourseLearningEventMapper;
import com.learnplatform.mapper.CourseStageAssessmentMapper;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionReviewScheduleMapper;
import com.learnplatform.mapper.UserCourseMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import com.learnplatform.mapper.TutorContentMapper;
import com.learnplatform.mapper.TutorSessionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 从已有的课程学习事实、错题与复习计划构建课程页概览，不另存进度副本。 */
@Service
public class CourseOverviewService {

    private final UserCourseMapper userCourseMapper;
    private final CourseMapper courseMapper;
    private final CourseLearningEventMapper eventMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionReviewScheduleMapper reviewScheduleMapper;
    private final QuestionMapper questionMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final TutorContentMapper tutorContentMapper;
    private final TutorSessionMapper tutorSessionMapper;
    private final CourseStageAssessmentMapper stageAssessmentMapper;

    public CourseOverviewService(UserCourseMapper userCourseMapper, CourseMapper courseMapper,
                                 CourseLearningEventMapper eventMapper, WrongQuestionMapper wrongQuestionMapper,
                                 QuestionReviewScheduleMapper reviewScheduleMapper, QuestionMapper questionMapper,
                                 KnowledgePointMapper knowledgePointMapper, TutorContentMapper tutorContentMapper,
                                 TutorSessionMapper tutorSessionMapper,
                                 CourseStageAssessmentMapper stageAssessmentMapper) {
        this.userCourseMapper = userCourseMapper;
        this.courseMapper = courseMapper;
        this.eventMapper = eventMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.reviewScheduleMapper = reviewScheduleMapper;
        this.questionMapper = questionMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.tutorContentMapper = tutorContentMapper;
        this.tutorSessionMapper = tutorSessionMapper;
        this.stageAssessmentMapper = stageAssessmentMapper;
    }

    public CourseOverviewVO getOverview(Long userId, Long courseId) {
        requireInLibrary(userId, courseId);
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在");
        }

        List<CourseLearningEvent> events = eventMapper.selectList(new LambdaQueryWrapper<CourseLearningEvent>()
                .eq(CourseLearningEvent::getUserId, userId)
                .eq(CourseLearningEvent::getCourseId, courseId)
                .orderByDesc(CourseLearningEvent::getOccurredTime));
        List<Question> courseQuestions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getCourseId, courseId)
                .and(scope -> scope.eq(Question::getVisibility, "PUBLIC")
                        .or(privateScope -> privateScope.eq(Question::getVisibility, "PRIVATE")
                                .eq(Question::getOwnerUserId, userId))));
        List<Long> questionIds = courseQuestions.stream().map(Question::getId).toList();
        List<WrongQuestion> unresolvedWrongQuestions = questionIds.isEmpty() ? List.of()
                : wrongQuestionMapper.selectList(new LambdaQueryWrapper<WrongQuestion>()
                        .eq(WrongQuestion::getUserId, userId)
                        .in(WrongQuestion::getQuestionId, questionIds)
                        .ne(WrongQuestion::getMasteryLevel, 2)).stream()
                .sorted(Comparator.comparing(WrongQuestion::getWrongCount,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<QuestionReviewSchedule> dueSchedules = questionIds.isEmpty() ? List.of()
                : reviewScheduleMapper.selectList(new LambdaQueryWrapper<QuestionReviewSchedule>()
                        .eq(QuestionReviewSchedule::getUserId, userId)
                        .in(QuestionReviewSchedule::getQuestionId, questionIds)
                        .le(QuestionReviewSchedule::getNextReviewDate, LocalDate.now())).stream()
                .sorted(Comparator.comparing(QuestionReviewSchedule::getNextReviewDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        CourseOverviewVO overview = new CourseOverviewVO();
        overview.setCourseId(courseId);
        overview.setCourseName(course.getName());
        overview.setAnsweredCount(events.size());
        overview.setCorrectCount((int) events.stream()
                .filter(event -> "{\"isCorrect\":true}".equals(event.getPayloadJson())).count());
        overview.setDueReviewCount(dueSchedules.size());
        overview.setUnresolvedWrongCount(unresolvedWrongQuestions.size());
        overview.setLastLearningTime(events.isEmpty() ? null : events.get(0).getOccurredTime());
        List<TutorProgress> tutorProgress = findTutorProgress(userId, courseId);
        overview.setTutorProgress(tutorProgress.stream().map(this::toTutorProgressView).toList());
        overview.setRecommendedTargets(buildTargets(courseId, dueSchedules, unresolvedWrongQuestions, tutorProgress));
        overview.setLatestStageAssessment(toAssessmentSummary(
                stageAssessmentMapper.selectLatestCompleted(userId, courseId)));
        return overview;
    }

    /** 复用课程总览的统一排序，为未显式指定目标的“开始学习”选择首个可解释目标。 */
    public CourseOverviewVO.LearningTargetVO selectStartTarget(Long userId, Long courseId) {
        List<CourseOverviewVO.LearningTargetVO> targets = getOverview(userId, courseId).getRecommendedTargets();
        if (targets == null || targets.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程暂无可学习内容");
        }
        return targets.get(0);
    }

    private void requireInLibrary(Long userId, Long courseId) {
        long count = userCourseMapper.selectCount(new LambdaQueryWrapper<UserCourse>()
                .eq(UserCourse::getUserId, userId)
                .eq(UserCourse::getCourseId, courseId));
        if (count == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "请先将课程加入个人课程库");
        }
    }

    private List<CourseOverviewVO.LearningTargetVO> buildTargets(
            Long courseId, List<QuestionReviewSchedule> dueSchedules, List<WrongQuestion> wrongQuestions,
            List<TutorProgress> tutorProgress) {
        List<CourseOverviewVO.LearningTargetVO> targets = new ArrayList<>();
        tutorProgress.stream().filter(progress -> !"COMPLETED".equals(progress.status()))
                .findFirst().map(progress -> tutorTarget(progress.content())).ifPresent(targets::add);
        if (!dueSchedules.isEmpty()) {
            targets.add(questionTarget("DUE_REVIEW", "优先复习到期题目", "有 " + dueSchedules.size()
                    + " 道题已到复习时间", dueSchedules.get(0).getQuestionId()));
        }
        if (!wrongQuestions.isEmpty()) {
            targets.add(questionTarget("WRONG_QUESTION", "处理未掌握错题", "有 " + wrongQuestions.size()
                    + " 道错题仍待巩固", wrongQuestions.get(0).getQuestionId()));
        }
        KnowledgePoint firstRoot = knowledgePointMapper.selectOne(new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId)
                .eq(KnowledgePoint::getParentId, 0L)
                .orderByAsc(KnowledgePoint::getSortOrder)
                .last("LIMIT 1"));
        CourseOverviewVO.LearningTargetVO defaultTarget = new CourseOverviewVO.LearningTargetVO();
        defaultTarget.setType("COURSE_SEQUENCE");
        defaultTarget.setTitle(firstRoot == null ? "从课程题目开始" : "从“" + firstRoot.getName() + "”开始");
        defaultTarget.setReason("按课程目录建立学习起点");
        defaultTarget.setKnowledgePointId(firstRoot == null ? null : firstRoot.getId());
        targets.add(defaultTarget);
        return targets;
    }

    private List<TutorProgress> findTutorProgress(Long userId, Long courseId) {
        List<KnowledgePoint> points = knowledgePointMapper.selectList(new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId)
                .orderByAsc(KnowledgePoint::getSortOrder));
        if (points.isEmpty()) return List.of();
        List<Long> pointIds = points.stream().map(KnowledgePoint::getId).toList();
        List<TutorContent> contents = tutorContentMapper.selectList(new LambdaQueryWrapper<TutorContent>()
                .in(TutorContent::getKnowledgePointId, pointIds)
                .eq(TutorContent::getReviewStatus, "REVIEWED"));
        if (contents.isEmpty()) return List.of();
        Set<Long> contentIds = contents.stream().map(TutorContent::getId).collect(Collectors.toSet());
        List<TutorSession> sessions = tutorSessionMapper.selectList(new LambdaQueryWrapper<TutorSession>()
                        .eq(TutorSession::getUserId, userId)
                        .eq(TutorSession::getCourseId, courseId)
                        .in(TutorSession::getTutorContentId, contentIds));
        Set<Long> completedContentIds = sessions.stream().filter(session -> Boolean.TRUE.equals(session.getCheckCorrect()))
                .map(TutorSession::getTutorContentId).collect(Collectors.toSet());
        Set<Long> attemptedContentIds = sessions.stream().map(TutorSession::getTutorContentId).collect(Collectors.toSet());
        Map<Long, Integer> pointOrder = points.stream().collect(Collectors.toMap(KnowledgePoint::getId,
                point -> point.getSortOrder() == null ? Integer.MAX_VALUE : point.getSortOrder(), (left, right) -> left));
        return contents.stream()
                .sorted(Comparator.comparing(content -> pointOrder.getOrDefault(content.getKnowledgePointId(), Integer.MAX_VALUE)))
                .map(content -> new TutorProgress(content, completedContentIds.contains(content.getId()) ? "COMPLETED"
                        : attemptedContentIds.contains(content.getId()) ? "IN_PROGRESS" : "NOT_STARTED"))
                .toList();
    }

    private CourseOverviewVO.LearningTargetVO questionTarget(String type, String title, String reason,
                                                               Long questionId) {
        CourseOverviewVO.LearningTargetVO target = new CourseOverviewVO.LearningTargetVO();
        target.setType(type);
        target.setTitle(title);
        target.setReason(reason);
        target.setQuestionId(questionId);
        return target;
    }

    private CourseOverviewVO.LearningTargetVO tutorTarget(TutorContent content) {
        CourseOverviewVO.LearningTargetVO target = new CourseOverviewVO.LearningTargetVO();
        target.setType("TUTOR");
        target.setTitle("继续 AI 教学：“" + content.getTitle() + "”");
        target.setReason("这是当前课程中已审查、尚未完成理解检查的教学内容");
        target.setKnowledgePointId(content.getKnowledgePointId());
        return target;
    }

    private CourseOverviewVO.TutorProgressVO toTutorProgressView(TutorProgress progress) {
        CourseOverviewVO.TutorProgressVO view = new CourseOverviewVO.TutorProgressVO();
        view.setKnowledgePointId(progress.content().getKnowledgePointId());
        view.setTitle(progress.content().getTitle());
        view.setStatus(progress.status());
        return view;
    }

    private CourseStageAssessmentSummaryVO toAssessmentSummary(CourseStageAssessment assessment) {
        if (assessment == null) return null;
        CourseStageAssessmentSummaryVO view = new CourseStageAssessmentSummaryVO();
        view.setId(assessment.getId());
        view.setSelectionStrategy(assessment.getSelectionStrategy());
        view.setQuestionCount(assessment.getQuestionCount());
        view.setCorrectCount(assessment.getCorrectCount());
        view.setStartTime(assessment.getStartTime());
        view.setCompleteTime(assessment.getCompleteTime());
        return view;
    }

    private record TutorProgress(TutorContent content, String status) { }
}
