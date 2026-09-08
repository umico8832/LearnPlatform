package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.CourseKnowledgePointFactVO;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.UserCourse;
import com.learnplatform.mapper.CourseLearningFactMapper;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.UserCourseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** 从已有的课程学习事实、错题与复习计划构建课程页概览，不另存进度副本。 */
@Service
@Transactional(readOnly = true)
public class CourseOverviewService {

    private final UserCourseMapper userCourseMapper;
    private final CourseMapper courseMapper;
    private final CourseLearningFactMapper factMapper;
    private final CourseOverviewTargetService targetService;
    private final CourseOverviewAssessmentService assessmentService;

    public CourseOverviewService(UserCourseMapper userCourseMapper, CourseMapper courseMapper,
                                 CourseLearningFactMapper factMapper, CourseOverviewTargetService targetService,
                                 CourseOverviewAssessmentService assessmentService) {
        this.userCourseMapper = userCourseMapper;
        this.courseMapper = courseMapper;
        this.factMapper = factMapper;
        this.targetService = targetService;
        this.assessmentService = assessmentService;
    }

    public CourseOverviewVO getOverview(Long userId, Long courseId) {
        requireInLibrary(userId, courseId);
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在");
        }

        LocalDate today = LocalDate.now();
        CourseOverviewVO overview = factMapper.selectTotals(userId, courseId, today);
        overview.setCourseId(courseId);
        overview.setCourseName(course.getName());
        Long dueQuestionId = overview.getDueReviewCount() == 0 ? null
                : factMapper.selectFirstDueQuestion(userId, courseId, today);
        Long wrongQuestionId = overview.getUnresolvedWrongCount() == 0 ? null
                : factMapper.selectFirstWrongQuestion(userId, courseId);
        CourseOverviewTargetService.TargetSnapshot targets =
                targetService.build(userId, courseId, overview, dueQuestionId, wrongQuestionId);
        overview.setTutorProgress(targets.tutorProgress());
        overview.setRecommendedTargets(targets.recommendedTargets());
        overview.setLatestStageAssessment(assessmentService.getLatest(userId, courseId));
        return overview;
    }

    public Page<CourseKnowledgePointFactVO> getKnowledgePointFacts(
            Long userId, Long courseId, int pageNum, int pageSize) {
        requireInLibrary(userId, courseId);
        if (courseMapper.selectById(courseId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在");
        }
        if (pageNum < 1 || pageSize < 1 || pageSize > 50) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "页码必须大于 0，每页数量须为 1–50");
        }
        LocalDate today = LocalDate.now();
        long total = factMapper.countKnowledgePoints(userId, courseId, today);
        Page<CourseKnowledgePointFactVO> page = new Page<>(pageNum, pageSize, total);
        long offset = ((long) pageNum - 1) * pageSize;
        page.setRecords(offset >= total ? List.of()
                : factMapper.selectKnowledgePoints(userId, courseId, today, offset, pageSize));
        return page;
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
