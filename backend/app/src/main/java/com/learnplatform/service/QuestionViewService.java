package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.QuestionDuplicateGroupVO;
import com.learnplatform.dto.QuestionOptionVO;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.service.question.QuestionDuplicateDetector;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuestionViewService {
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final CourseMapper courseMapper;
    private final KnowledgePointMapper knowledgePointMapper;

    public QuestionViewService(
            QuestionOptionMapper questionOptionMapper,
            QuestionKnowledgePointMapper questionKnowledgePointMapper,
            CourseMapper courseMapper,
            KnowledgePointMapper knowledgePointMapper) {
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.courseMapper = courseMapper;
        this.knowledgePointMapper = knowledgePointMapper;
    }

    public void enrich(QuestionVO view) {
        enrich(view, false);
    }

    private void enrich(QuestionVO view, boolean learner) {
        Course course = courseMapper.selectById(view.getCourseId());
        if (course != null) {
            view.setCourseName(course.getName());
        }
        List<QuestionOption> options = questionOptionMapper.selectList(
                new LambdaQueryWrapper<QuestionOption>()
                        .eq(QuestionOption::getQuestionId, view.getId())
                        .orderByAsc(QuestionOption::getSortOrder));
        view.setOptions(learner ? QuestionOptionVO.forLearner(view.getQuestionType(), options)
                : options.stream().map(QuestionOptionVO::fromEntity).toList());

        List<Long> knowledgePointIds = questionKnowledgePointMapper.selectList(
                        new LambdaQueryWrapper<QuestionKnowledgePoint>()
                                .eq(QuestionKnowledgePoint::getQuestionId, view.getId()))
                .stream().map(QuestionKnowledgePoint::getKnowledgePointId).toList();
        view.setKnowledgePointIds(knowledgePointIds);
        view.setKnowledgePointNames(knowledgePointIds.stream()
                .map(knowledgePointMapper::selectById)
                .filter(point -> point != null)
                .map(KnowledgePoint::getName)
                .toList());
    }

    public void enrichForUser(QuestionVO view) {
        enrich(view, true);
    }

    public void enrichForUsers(List<QuestionVO> views) {
        if (views.isEmpty()) { return; }
        List<Long> ids = views.stream().map(QuestionVO::getId).toList();
        List<Long> courseIds = views.stream().map(QuestionVO::getCourseId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Course> courses = courseIds.isEmpty() ? Map.of() : courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        Map<Long, List<QuestionOption>> options = questionOptionMapper.selectList(
                new LambdaQueryWrapper<QuestionOption>().in(QuestionOption::getQuestionId, ids)
                        .orderByAsc(QuestionOption::getSortOrder, QuestionOption::getId)).stream()
                .collect(Collectors.groupingBy(QuestionOption::getQuestionId));
        Map<Long, List<QuestionKnowledgePoint>> links = questionKnowledgePointMapper.selectList(
                new LambdaQueryWrapper<QuestionKnowledgePoint>()
                        .in(QuestionKnowledgePoint::getQuestionId, ids)).stream()
                .collect(Collectors.groupingBy(QuestionKnowledgePoint::getQuestionId));
        List<Long> pointIds = links.values().stream().flatMap(List::stream)
                .map(QuestionKnowledgePoint::getKnowledgePointId).distinct().toList();
        Map<Long, KnowledgePoint> points = pointIds.isEmpty() ? Map.of()
                : knowledgePointMapper.selectBatchIds(pointIds).stream()
                    .collect(Collectors.toMap(KnowledgePoint::getId, Function.identity()));
        for (QuestionVO view : views) {
            Course course = courses.get(view.getCourseId());
            if (course != null) { view.setCourseName(course.getName()); }
            view.setOptions(QuestionOptionVO.forLearner(view.getQuestionType(),
                    options.getOrDefault(view.getId(), List.of())));
            List<Long> related = links.getOrDefault(view.getId(), List.of()).stream()
                    .map(QuestionKnowledgePoint::getKnowledgePointId).toList();
            view.setKnowledgePointIds(related);
            view.setKnowledgePointNames(related.stream().map(points::get).filter(Objects::nonNull)
                    .map(KnowledgePoint::getName).toList());
        }
    }

    public QuestionDuplicateGroupVO toDuplicateGroup(QuestionDuplicateDetector.DuplicateGroup group) {
        QuestionDuplicateGroupVO view = new QuestionDuplicateGroupVO();
        view.setMatchType(group.matchType());
        view.setSimilarityScore(group.similarityScore());
        view.setRepresentativeContent(group.questions().get(0).getContent());
        view.setQuestions(group.questions().stream().map(question -> {
            QuestionVO item = QuestionVO.fromEntity(question);
            enrich(item);
            return item;
        }).toList());
        return view;
    }
}
