package com.learnplatform.service;

import com.learnplatform.common.time.BusinessTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.WrongQuestionVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.WrongQuestion;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import com.learnplatform.mapper.WrongQuestionInsightMapper;
import com.learnplatform.service.understanding.QuestionUnderstandingContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.learnplatform.service.question.QuestionAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 错题本服务
 */
@Service
public class WrongQuestionService {

    private static final String NEXT_REVIEW_DATE_SQL = """
            (SELECT s.next_review_date FROM question_review_schedule s
             WHERE s.user_id=wrong_question.user_id AND s.question_id=wrong_question.question_id AND s.deleted=0)
            """.strip();

    private static final Logger log = LoggerFactory.getLogger(WrongQuestionService.class);

    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionMapper questionMapper;
    private final CourseMapper courseMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final CacheEvictService cacheEvictService;
    private final WrongQuestionInsightMapper insightMapper;
    private final QuestionUnderstandingContent understanding;

    public WrongQuestionService(WrongQuestionMapper wrongQuestionMapper,
                                QuestionMapper questionMapper,
                                CourseMapper courseMapper,
                                KnowledgePointMapper knowledgePointMapper,
                                CacheEvictService cacheEvictService,
                                WrongQuestionInsightMapper insightMapper,
                                QuestionUnderstandingContent understanding) {
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.cacheEvictService = cacheEvictService;
        this.insightMapper = insightMapper;
        this.understanding = understanding;
    }

    /**
     * 答错时自动加入错题本（同一用户+题目不重复）
     */
    @Transactional
    public void addWrongQuestion(Long userId, Long questionId, String userAnswer) {
        log.info("加入错题本: userId={}, questionId={}", userId, questionId);
        int updated = wrongQuestionMapper.reviveOrIncrement(userId, questionId, userAnswer);
        if (updated == 0) {
            WrongQuestion wq = new WrongQuestion();
            wq.setUserId(userId);
            wq.setQuestionId(questionId);
            wq.setWrongCount(1);
            wq.setMasteryLevel(0);
            wq.setLastWrongAnswer(userAnswer);
            wq.setDeleted(0);
            wrongQuestionMapper.insert(wq);
        }
    }

    /**
     * 获取用户错题本（分页）- 使用批量查询优化 N+1
     */
    public Page<WrongQuestionVO> getWrongQuestions(Long userId, int pageNum, int pageSize,
                                                    Long courseId, Integer masteryLevel) {
        return getWrongQuestions(userId, pageNum, pageSize, courseId, null, null, masteryLevel);
    }

    /** 分页查询当前用户错题，可在数据库分页前限定课程、目标题目和知识点。 */
    public Page<WrongQuestionVO> getWrongQuestions(Long userId, int pageNum, int pageSize,
                                                    Long courseId, Long questionId, Integer masteryLevel) {
        return getWrongQuestions(userId, pageNum, pageSize, courseId, questionId, null, masteryLevel);
    }

    /** 分页查询当前用户错题，可在数据库分页前限定课程、目标题目和知识点。 */
    public Page<WrongQuestionVO> getWrongQuestions(Long userId, int pageNum, int pageSize,
                                                    Long courseId, Long questionId,
                                                    Long knowledgePointId, Integer masteryLevel) {
        return getWrongQuestions(userId, pageNum, pageSize, courseId, questionId, knowledgePointId,
                masteryLevel, false, "updateTime");
    }

    public Page<WrongQuestionVO> getWrongQuestions(Long userId, int pageNum, int pageSize,
                                                    Long courseId, Long questionId, Long knowledgePointId,
                                                    Integer masteryLevel, boolean dueOnly, String sortBy) {
        if (!"updateTime".equals(sortBy) && !"nextReviewDate".equals(sortBy)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "不支持的错题排序方式");
        }
        Page<WrongQuestion> page = new Page<>(pageNum, pageSize);
        QueryWrapper<WrongQuestion> wrapper = scope(userId, courseId, questionId, knowledgePointId);
        wrapper.eq(masteryLevel != null, "mastery_level", masteryLevel);
        if (dueOnly) {
            wrapper.apply(NEXT_REVIEW_DATE_SQL + " <= {0}", BusinessTime.today());
        }
        if ("nextReviewDate".equals(sortBy)) {
            wrapper.orderByAsc(NEXT_REVIEW_DATE_SQL + " IS NULL", NEXT_REVIEW_DATE_SQL);
        }
        wrapper.orderByDesc("update_time", "id");

        Page<WrongQuestion> result = wrongQuestionMapper.selectPage(page, wrapper);

        // 批量加载关联的 Question（避免 N+1）
        List<Long> questionIds = result.getRecords().stream()
                .map(WrongQuestion::getQuestionId).distinct().collect(Collectors.toList());
        Map<Long, WrongQuestionInsightMapper.Insight> insights = insightMapper.forQuestions(userId, questionIds);
        Map<Long, Question> questionMap = new HashMap<>();
        if (!questionIds.isEmpty()) {
            LambdaQueryWrapper<Question> qWrapper = new LambdaQueryWrapper<>();
            qWrapper.in(Question::getId, questionIds);
            questionMapper.selectList(qWrapper).forEach(q -> questionMap.put(q.getId(), q));
        }

        // 批量加载关联的 Course（避免 N+1）
        Set<Long> courseIds = questionMap.values().stream()
                .map(Question::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Course> courseMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            LambdaQueryWrapper<Course> cWrapper = new LambdaQueryWrapper<>();
            cWrapper.in(Course::getId, courseIds);
            courseMapper.selectList(cWrapper).forEach(c -> courseMap.put(c.getId(), c));
        }

        Page<WrongQuestionVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(wq -> {
                    WrongQuestionVO vo = new WrongQuestionVO();
                    vo.setId(wq.getId());
                    vo.setQuestionId(wq.getQuestionId());
                    vo.setHasUnderstanding(understanding.available(userId, wq.getQuestionId()));
                    vo.setWrongCount(wq.getWrongCount());
                    vo.setMasteryLevel(wq.getMasteryLevel());
                    vo.setLastWrongAnswer(wq.getLastWrongAnswer());
                    vo.setCreateTime(wq.getCreateTime());
                    vo.setUpdateTime(wq.getUpdateTime());
                    WrongQuestionInsightMapper.Insight insight = insights.get(wq.getQuestionId());
                    if (insight != null) {
                        vo.setNextReviewDate(insight.nextReviewDate());
                        if (insight.title() != null && insight.explanation() != null) {
                            vo.setMisconception(Map.of("title", insight.title(),
                                    "explanation", insight.explanation(),
                                    "source", Map.of("kind", insight.contentSource(), "reviewStatus", "REVIEWED")));
                        }
                    }

                    Question question = questionMap.get(wq.getQuestionId());
                    if (question != null) {
                        vo.setQuestionContent(question.getContent());
                        vo.setQuestionType(question.getQuestionType());
                        vo.setCourseId(question.getCourseId());
                        vo.setDifficulty(question.getDifficulty());
                        Course course = courseMap.get(question.getCourseId());
                        if (course != null) {
                            vo.setCourseName(course.getName());
                        }
                    }
                    return vo;
                })
                .collect(Collectors.toList()));

        return voPage;
    }

    /**
     * 更新掌握程度
     */
    @Transactional
    public void updateMasteryLevel(Long id, Long userId, Integer masteryLevel) {
        WrongQuestion wq = wrongQuestionMapper.selectById(id);
        if (wq == null || !wq.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "错题记录不存在");
        }
        wq.setMasteryLevel(masteryLevel);
        wrongQuestionMapper.updateById(wq);
        Question question = questionMapper.selectById(wq.getQuestionId());
        cacheEvictService.evictUserStatistics(userId, question == null ? null : question.getCourseId());
    }

    /**
     * 移出错题本（逻辑删除）
     */
    @Transactional
    public void removeWrongQuestion(Long id, Long userId) {
        log.info("移出错题本: userId={}, id={}", userId, id);
        WrongQuestion wq = wrongQuestionMapper.selectById(id);
        if (wq == null || !wq.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "错题记录不存在");
        }
        wrongQuestionMapper.deleteById(id);
        Question question = questionMapper.selectById(wq.getQuestionId());
        cacheEvictService.evictUserStatistics(userId, question == null ? null : question.getCourseId());
    }

    /**
     * 答对时自动从错题本移出
     */
    @Transactional
    public void removeOnCorrect(Long userId, Long questionId) {
        log.info("答对自动移出错题本: userId={}, questionId={}", userId, questionId);
        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
               .eq(WrongQuestion::getQuestionId, questionId);
        WrongQuestion existing = wrongQuestionMapper.selectOne(wrapper);
        if (existing != null) {
            wrongQuestionMapper.deleteById(existing.getId());
        }
    }

    /**
     * 获取错题本统计 - 使用批量查询优化 N+1
     */
    public Map<String, Object> getWrongQuestionStats(Long userId) {
        return getWrongQuestionStats(userId, null, null, null);
    }

    public Map<String, Object> getWrongQuestionStats(Long userId, Long courseId, Long questionId,
                                                     Long knowledgePointId) {
        QueryWrapper<WrongQuestion> wrapper = scope(userId, courseId, questionId, knowledgePointId);
        LocalDate today = BusinessTime.today();
        List<WrongQuestion> list = wrongQuestionMapper.selectList(wrapper);
        long dueCount = wrongQuestionMapper.selectCount(
                wrapper.clone().apply(NEXT_REVIEW_DATE_SQL + " <= {0}", today));

        int total = list.size();
        int unmastered = (int) list.stream()
                .filter(w -> w.getMasteryLevel() != null && w.getMasteryLevel() == 0).count();
        int partial = (int) list.stream().filter(w -> w.getMasteryLevel() != null && w.getMasteryLevel() == 1).count();
        int mastered = (int) list.stream().filter(w -> w.getMasteryLevel() != null && w.getMasteryLevel() == 2).count();

        // 批量加载 Question（避免 N+1）
        List<Long> questionIds = list.stream().map(WrongQuestion::getQuestionId).distinct()
                .collect(Collectors.toList());
        Map<Long, Question> questionMap = new HashMap<>();
        if (!questionIds.isEmpty()) {
            LambdaQueryWrapper<Question> qWrapper = new LambdaQueryWrapper<>();
            qWrapper.in(Question::getId, questionIds);
            questionMapper.selectList(qWrapper).forEach(q -> questionMap.put(q.getId(), q));
        }

        // 批量加载 Course（避免 N+1）
        Set<Long> courseIds = questionMap.values().stream()
                .map(Question::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Course> courseMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            LambdaQueryWrapper<Course> cWrapper = new LambdaQueryWrapper<>();
            cWrapper.in(Course::getId, courseIds);
            courseMapper.selectList(cWrapper).forEach(c -> courseMap.put(c.getId(), c));
        }

        Map<String, Integer> courseWrongCount = new HashMap<>();
        for (WrongQuestion wq : list) {
            Question q = questionMap.get(wq.getQuestionId());
            if (q != null) {
                Course c = courseMap.get(q.getCourseId());
                if (c != null) {
                    courseWrongCount.merge(c.getName(), 1, Integer::sum);
                }
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("dueCount", Math.toIntExact(dueCount));
        stats.put("unmastered", unmastered);
        stats.put("partial", partial);
        stats.put("mastered", mastered);
        stats.put("courseWrongCount", courseWrongCount);

        return stats;
    }

    private QueryWrapper<WrongQuestion> scope(Long userId, Long courseId, Long questionId, Long knowledgePointId) {
        QueryWrapper<WrongQuestion> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq(questionId != null, "question_id", questionId);
        wrapper.notInSql("question_id", QuestionAccessPolicy.DRAFT_IDS_SQL);
        if (courseId != null) {
            Set<Long> ids = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                            .eq(Question::getCourseId, courseId)).stream()
                    .map(Question::getId).collect(Collectors.toSet());
            restrictQuestions(wrapper, ids);
        }
        if (knowledgePointId != null) {
            restrictQuestions(wrapper, new HashSet<>(knowledgePointMapper
                    .selectQuestionIdsByKnowledgePointId(knowledgePointId)));
        }
        return wrapper;
    }

    private void restrictQuestions(QueryWrapper<WrongQuestion> wrapper, Set<Long> ids) {
        if (ids.isEmpty()) {
            wrapper.apply("1=0");
        } else {
            wrapper.in("question_id", ids);
        }
    }
}
