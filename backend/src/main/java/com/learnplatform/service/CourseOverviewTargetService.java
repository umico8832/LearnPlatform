package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.CourseOverviewVO;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.QuestionReviewSchedule;
import com.learnplatform.entity.TutorContent;
import com.learnplatform.entity.TutorSession;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.TutorContentMapper;
import com.learnplatform.mapper.TutorSessionMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CourseOverviewTargetService {

    private final KnowledgePointMapper knowledgePointMapper;
    private final TutorContentMapper tutorContentMapper;
    private final TutorSessionMapper tutorSessionMapper;

    public CourseOverviewTargetService(KnowledgePointMapper knowledgePointMapper,
                                       TutorContentMapper tutorContentMapper,
                                       TutorSessionMapper tutorSessionMapper) {
        this.knowledgePointMapper = knowledgePointMapper;
        this.tutorContentMapper = tutorContentMapper;
        this.tutorSessionMapper = tutorSessionMapper;
    }

    TargetSnapshot build(Long userId, Long courseId,
                         List<QuestionReviewSchedule> dueSchedules,
                         List<WrongQuestion> wrongQuestions) {
        List<TutorProgress> tutorProgress = findTutorProgress(userId, courseId);
        List<CourseOverviewVO.TutorProgressVO> progressViews = tutorProgress.stream()
                .map(this::toTutorProgressView)
                .toList();
        return new TargetSnapshot(progressViews,
                buildTargets(courseId, dueSchedules, wrongQuestions, tutorProgress));
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
        if (points.isEmpty()) {
            return List.of();
        }
        List<Long> pointIds = points.stream().map(KnowledgePoint::getId).toList();
        List<TutorContent> contents = tutorContentMapper.selectList(new LambdaQueryWrapper<TutorContent>()
                .in(TutorContent::getKnowledgePointId, pointIds)
                .eq(TutorContent::getReviewStatus, "REVIEWED"));
        if (contents.isEmpty()) {
            return List.of();
        }
        Set<Long> contentIds = contents.stream().map(TutorContent::getId).collect(Collectors.toSet());
        List<TutorSession> sessions = tutorSessionMapper.selectList(new LambdaQueryWrapper<TutorSession>()
                .eq(TutorSession::getUserId, userId)
                .eq(TutorSession::getCourseId, courseId)
                .in(TutorSession::getTutorContentId, contentIds));
        Set<Long> completedContentIds = sessions.stream()
                .filter(session -> Boolean.TRUE.equals(session.getCheckCorrect()))
                .map(TutorSession::getTutorContentId).collect(Collectors.toSet());
        Set<Long> attemptedContentIds = sessions.stream().map(TutorSession::getTutorContentId)
                .collect(Collectors.toSet());
        Map<Long, Integer> pointOrder = points.stream().collect(Collectors.toMap(KnowledgePoint::getId,
                point -> point.getSortOrder() == null ? Integer.MAX_VALUE : point.getSortOrder(),
                (left, right) -> left));
        return contents.stream()
                .sorted(Comparator.comparing(content -> pointOrder
                        .getOrDefault(content.getKnowledgePointId(), Integer.MAX_VALUE)))
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

    record TargetSnapshot(List<CourseOverviewVO.TutorProgressVO> tutorProgress,
                          List<CourseOverviewVO.LearningTargetVO> recommendedTargets) {
    }

    private record TutorProgress(TutorContent content, String status) {
    }
}
