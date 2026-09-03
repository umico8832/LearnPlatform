package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.CourseLearningEvent;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.entity.UserCourse;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.CourseLearningEventMapper;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionReviewScheduleMapper;
import com.learnplatform.mapper.UserCourseMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** 从已有的课程学习事实、错题与复习计划构建课程页概览，不另存进度副本。 */
@Service
public class CourseOverviewService {

    private final UserCourseMapper userCourseMapper;
    private final CourseMapper courseMapper;
    private final CourseLearningEventMapper eventMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionReviewScheduleMapper reviewScheduleMapper;
    private final QuestionMapper questionMapper;
    private final CourseOverviewTargetService targetService;
    private final CourseOverviewAssessmentService assessmentService;

    public CourseOverviewService(UserCourseMapper userCourseMapper, CourseMapper courseMapper,
                                 CourseLearningEventMapper eventMapper, WrongQuestionMapper wrongQuestionMapper,
                                 QuestionReviewScheduleMapper reviewScheduleMapper, QuestionMapper questionMapper,
                                 CourseOverviewTargetService targetService,
                                 CourseOverviewAssessmentService assessmentService) {
        this.userCourseMapper = userCourseMapper;
        this.courseMapper = courseMapper;
        this.eventMapper = eventMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.reviewScheduleMapper = reviewScheduleMapper;
        this.questionMapper = questionMapper;
        this.targetService = targetService;
        this.assessmentService = assessmentService;
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
        CourseOverviewTargetService.TargetSnapshot targets =
                targetService.build(userId, courseId, dueSchedules, unresolvedWrongQuestions);
        overview.setTutorProgress(targets.tutorProgress());
        overview.setRecommendedTargets(targets.recommendedTargets());
        overview.setLatestStageAssessment(assessmentService.getLatest(userId, courseId));
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

}
