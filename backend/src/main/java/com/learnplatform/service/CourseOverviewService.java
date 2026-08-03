package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.CourseLearningEvent;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.entity.UserCourse;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.CourseLearningEventMapper;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionReviewScheduleMapper;
import com.learnplatform.mapper.UserCourseMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
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
    private final KnowledgePointMapper knowledgePointMapper;

    public CourseOverviewService(UserCourseMapper userCourseMapper, CourseMapper courseMapper,
                                 CourseLearningEventMapper eventMapper, WrongQuestionMapper wrongQuestionMapper,
                                 QuestionReviewScheduleMapper reviewScheduleMapper, QuestionMapper questionMapper,
                                 KnowledgePointMapper knowledgePointMapper) {
        this.userCourseMapper = userCourseMapper;
        this.courseMapper = courseMapper;
        this.eventMapper = eventMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.reviewScheduleMapper = reviewScheduleMapper;
        this.questionMapper = questionMapper;
        this.knowledgePointMapper = knowledgePointMapper;
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
                .eq(Question::getCourseId, courseId));
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
        overview.setRecommendedTargets(buildTargets(courseId, dueSchedules, unresolvedWrongQuestions));
        return overview;
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
            Long courseId, List<QuestionReviewSchedule> dueSchedules, List<WrongQuestion> wrongQuestions) {
        List<CourseOverviewVO.LearningTargetVO> targets = new ArrayList<>();
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
                .isNull(KnowledgePoint::getParentId)
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

    private CourseOverviewVO.LearningTargetVO questionTarget(String type, String title, String reason,
                                                               Long questionId) {
        CourseOverviewVO.LearningTargetVO target = new CourseOverviewVO.LearningTargetVO();
        target.setType(type);
        target.setTitle(title);
        target.setReason(reason);
        target.setQuestionId(questionId);
        return target;
    }
}
