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
        Course course = courseMapper.selectById(view.getCourseId());
        if (course != null) {
            view.setCourseName(course.getName());
        }
        List<QuestionOption> options = questionOptionMapper.selectList(
                new LambdaQueryWrapper<QuestionOption>()
                        .eq(QuestionOption::getQuestionId, view.getId())
                        .orderByAsc(QuestionOption::getSortOrder));
        view.setOptions(options.stream().map(QuestionOptionVO::fromEntity).toList());

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
        enrich(view);
        if (view.getOptions() != null) {
            view.getOptions().forEach(option -> option.setIsCorrect(null));
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
