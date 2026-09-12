package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.QuestionOptionVO;
import com.learnplatform.dto.QuestionVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.entity.UserFavoriteQuestion;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.mapper.UserFavoriteQuestionMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** 查询并映射练习、错题重练和收藏题目，统一隐藏答案与解析。 */
@Service
public class PracticeQuestionQueryService {

    private static final Logger log = LoggerFactory.getLogger(PracticeQuestionQueryService.class);

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final CourseMapper courseMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final UserFavoriteQuestionMapper userFavoriteQuestionMapper;

    public PracticeQuestionQueryService(
            QuestionMapper questionMapper,
            QuestionOptionMapper questionOptionMapper,
            QuestionKnowledgePointMapper questionKnowledgePointMapper,
            CourseMapper courseMapper,
            KnowledgePointMapper knowledgePointMapper,
            WrongQuestionMapper wrongQuestionMapper,
            UserFavoriteQuestionMapper userFavoriteQuestionMapper) {
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.courseMapper = courseMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.userFavoriteQuestionMapper = userFavoriteQuestionMapper;
    }

    public List<QuestionVO> getPracticeQuestions(Long courseId, Long knowledgePointId,
                                                  String questionType, Integer difficulty,
                                                  Integer requestedCount) {
        int count = normalizeCount(requestedCount);
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getStatus, 1);
        wrapper.eq(Question::getVisibility, "PUBLIC");
        if (courseId != null) {
            wrapper.eq(Question::getCourseId, courseId);
        }
        if (questionType != null && !questionType.isEmpty()) {
            wrapper.eq(Question::getQuestionType, questionType);
        }
        if (difficulty != null) {
            wrapper.eq(Question::getDifficulty, difficulty);
        }
        if (knowledgePointId != null) {
            LambdaQueryWrapper<QuestionKnowledgePoint> knowledgePointWrapper = new LambdaQueryWrapper<>();
            knowledgePointWrapper.eq(QuestionKnowledgePoint::getKnowledgePointId, knowledgePointId);
            List<Long> questionIds = questionKnowledgePointMapper.selectList(knowledgePointWrapper).stream()
                    .map(QuestionKnowledgePoint::getQuestionId)
                    .collect(Collectors.toList());
            if (questionIds.isEmpty()) {
                return new ArrayList<>();
            }
            wrapper.in(Question::getId, questionIds);
        }

        List<Question> questions = questionMapper.selectList(wrapper);
        if (questions.size() > count) {
            Collections.shuffle(questions);
            questions = questions.subList(0, count);
        }
        log.info("获取练习题目: courseId={}, questionType={}, difficulty={}, count={}",
                courseId, questionType, difficulty, questions.size());
        return toPracticeQuestions(questions);
    }

    public List<QuestionVO> getWrongQuestionPractice(
            Long userId, Integer masteryLevel, Integer requestedCount) {
        int count = normalizeCount(requestedCount);
        log.info("获取错题重练题目: userId={}, masteryLevel={}, count={}", userId, masteryLevel, count);

        LambdaQueryWrapper<WrongQuestion> wrongWrapper = new LambdaQueryWrapper<>();
        wrongWrapper.eq(WrongQuestion::getUserId, userId);
        if (masteryLevel != null) {
            wrongWrapper.eq(WrongQuestion::getMasteryLevel, masteryLevel);
        }
        wrongWrapper.orderByDesc(WrongQuestion::getUpdateTime);
        List<WrongQuestion> wrongQuestions = wrongQuestionMapper.selectList(wrongWrapper);
        if (wrongQuestions.isEmpty()) {
            return new ArrayList<>();
        }
        if (wrongQuestions.size() > count) {
            Collections.shuffle(wrongQuestions);
            wrongQuestions = wrongQuestions.subList(0, count);
        }

        List<Long> questionIds = wrongQuestions.stream()
                .map(WrongQuestion::getQuestionId)
                .distinct()
                .collect(Collectors.toList());
        return findAccessibleQuestions(userId, questionIds);
    }

    public List<QuestionVO> getFavoritePractice(Long userId, Integer requestedCount, Long questionId) {
        int count = normalizeCount(requestedCount);
        log.info("获取收藏题练习题目: userId={}, count={}", userId, count);

        LambdaQueryWrapper<UserFavoriteQuestion> favoriteWrapper = new LambdaQueryWrapper<>();
        favoriteWrapper.eq(UserFavoriteQuestion::getUserId, userId)
                .orderByDesc(UserFavoriteQuestion::getCreateTime);
        if (questionId != null) {
            favoriteWrapper.eq(UserFavoriteQuestion::getQuestionId, questionId);
        }
        List<UserFavoriteQuestion> favorites = userFavoriteQuestionMapper.selectList(favoriteWrapper);
        if (favorites.isEmpty()) {
            return new ArrayList<>();
        }
        if (questionId == null && favorites.size() > count) {
            Collections.shuffle(favorites);
            favorites = favorites.subList(0, count);
        }
        List<Long> questionIds = favorites.stream()
                .map(UserFavoriteQuestion::getQuestionId)
                .distinct()
                .collect(Collectors.toList());
        return findAccessibleQuestions(userId, questionIds);
    }

    private List<QuestionVO> findAccessibleQuestions(Long userId, List<Long> questionIds) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Question::getId, questionIds);
        wrapper.eq(Question::getStatus, 1);
        wrapper.and(scope -> scope.eq(Question::getVisibility, "PUBLIC")
                .or(privateScope -> privateScope.eq(Question::getVisibility, "PRIVATE")
                        .eq(Question::getOwnerUserId, userId)));
        return toPracticeQuestions(questionMapper.selectList(wrapper));
    }

    private List<QuestionVO> toPracticeQuestions(List<Question> questions) {
        return questions.stream()
                .map(question -> {
                    QuestionVO view = QuestionVO.fromEntity(question);
                    view.setAnalysis(null);
                    fillQuestionView(view);
                    return view;
                })
                .collect(Collectors.toList());
    }

    private void fillQuestionView(QuestionVO view) {
        Course course = courseMapper.selectById(view.getCourseId());
        if (course != null) {
            view.setCourseName(course.getName());
        }

        LambdaQueryWrapper<QuestionOption> optionWrapper = new LambdaQueryWrapper<>();
        optionWrapper.eq(QuestionOption::getQuestionId, view.getId())
                .orderByAsc(QuestionOption::getSortOrder);
        List<QuestionOptionVO> options = questionOptionMapper.selectList(optionWrapper).stream()
                .map(option -> {
                    QuestionOptionVO optionView = QuestionOptionVO.fromEntity(option);
                    optionView.setIsCorrect(0);
                    return optionView;
                })
                .collect(Collectors.toList());
        view.setOptions(options);

        LambdaQueryWrapper<QuestionKnowledgePoint> knowledgePointWrapper = new LambdaQueryWrapper<>();
        knowledgePointWrapper.eq(QuestionKnowledgePoint::getQuestionId, view.getId());
        List<Long> knowledgePointIds = questionKnowledgePointMapper.selectList(knowledgePointWrapper).stream()
                .map(QuestionKnowledgePoint::getKnowledgePointId)
                .collect(Collectors.toList());
        view.setKnowledgePointIds(knowledgePointIds);

        List<String> knowledgePointNames = new ArrayList<>();
        for (Long knowledgePointId : knowledgePointIds) {
            KnowledgePoint point = knowledgePointMapper.selectById(knowledgePointId);
            if (point != null) {
                knowledgePointNames.add(point.getName());
            }
        }
        view.setKnowledgePointNames(knowledgePointNames);
    }

    private int normalizeCount(Integer count) {
        if (count == null || count <= 0) {
            return 10;
        }
        return Math.min(count, 50);
    }
}
