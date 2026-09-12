package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.mapper.CourseLearningFactMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CourseOverviewTargetService {

    private final KnowledgePointMapper knowledgePointMapper;
    private final CourseLearningFactMapper factMapper;

    public CourseOverviewTargetService(KnowledgePointMapper knowledgePointMapper,
                                       CourseLearningFactMapper factMapper) {
        this.knowledgePointMapper = knowledgePointMapper;
        this.factMapper = factMapper;
    }

    TargetSnapshot build(Long userId, Long courseId, CourseOverviewVO overview,
                         Long dueQuestionId, Long wrongQuestionId) {
        List<CourseOverviewVO.TutorProgressVO> progress = factMapper.selectTutorProgress(userId, courseId);
        List<CourseOverviewVO.LearningTargetVO> targets = new ArrayList<>();
        progress.stream().filter(item -> !"COMPLETED".equals(item.getStatus()))
                .findFirst().map(this::tutorTarget).ifPresent(targets::add);
        if (dueQuestionId != null) {
            targets.add(questionTarget("DUE_REVIEW", "优先复习到期题目", "有 " + overview.getDueReviewCount()
                    + " 道题已到复习时间", dueQuestionId));
        }
        if (wrongQuestionId != null) {
            targets.add(questionTarget("WRONG_QUESTION", "处理未掌握错题", "有 " + overview.getUnresolvedWrongCount()
                    + " 道错题仍待巩固", wrongQuestionId));
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
        return new TargetSnapshot(progress, targets);
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

    private CourseOverviewVO.LearningTargetVO tutorTarget(CourseOverviewVO.TutorProgressVO content) {
        CourseOverviewVO.LearningTargetVO target = new CourseOverviewVO.LearningTargetVO();
        target.setType("TUTOR");
        target.setTitle("继续 AI 教学：“" + content.getTitle() + "”");
        target.setReason("这是当前课程中已审查、尚未完成理解检查的教学内容");
        target.setKnowledgePointId(content.getKnowledgePointId());
        return target;
    }

    record TargetSnapshot(List<CourseOverviewVO.TutorProgressVO> tutorProgress,
                          List<CourseOverviewVO.LearningTargetVO> recommendedTargets) {
    }

}
