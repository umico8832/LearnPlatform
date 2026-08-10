package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.WrongQuestionVO;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 错题本服务
 */
@Service
public class WrongQuestionService {

    private static final Logger log = LoggerFactory.getLogger(WrongQuestionService.class);

    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionMapper questionMapper;
    private final CourseMapper courseMapper;
    private final CacheEvictService cacheEvictService;

    public WrongQuestionService(WrongQuestionMapper wrongQuestionMapper,
                                QuestionMapper questionMapper,
                                CourseMapper courseMapper,
                                CacheEvictService cacheEvictService) {
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
        this.cacheEvictService = cacheEvictService;
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
        return getWrongQuestions(userId, pageNum, pageSize, courseId, null, masteryLevel);
    }

    /** 分页查询当前用户错题，可在数据库分页前限定课程和目标题目。 */
    public Page<WrongQuestionVO> getWrongQuestions(Long userId, int pageNum, int pageSize,
                                                    Long courseId, Long questionId, Integer masteryLevel) {
        Page<WrongQuestion> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId);
        if (courseId != null) {
            Set<Long> courseQuestionIds = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                            .eq(Question::getCourseId, courseId)).stream()
                    .map(Question::getId)
                    .collect(Collectors.toSet());
            if (courseQuestionIds.isEmpty()) {
                Page<WrongQuestionVO> emptyPage = new Page<>(pageNum, pageSize, 0);
                emptyPage.setRecords(List.of());
                return emptyPage;
            }
            wrapper.in(WrongQuestion::getQuestionId, courseQuestionIds);
        }
        if (questionId != null) {
            wrapper.eq(WrongQuestion::getQuestionId, questionId);
        }
        if (masteryLevel != null) {
            wrapper.eq(WrongQuestion::getMasteryLevel, masteryLevel);
        }
        wrapper.orderByDesc(WrongQuestion::getUpdateTime);

        Page<WrongQuestion> result = wrongQuestionMapper.selectPage(page, wrapper);

        // 批量加载关联的 Question（避免 N+1）
        List<Long> questionIds = result.getRecords().stream()
                .map(WrongQuestion::getQuestionId).distinct().collect(Collectors.toList());
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
                    vo.setWrongCount(wq.getWrongCount());
                    vo.setMasteryLevel(wq.getMasteryLevel());
                    vo.setLastWrongAnswer(wq.getLastWrongAnswer());
                    vo.setCreateTime(wq.getCreateTime());
                    vo.setUpdateTime(wq.getUpdateTime());

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
        cacheEvictService.evictUserStatistics(userId);
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
        cacheEvictService.evictUserStatistics(userId);
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
        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId);
        List<WrongQuestion> list = wrongQuestionMapper.selectList(wrapper);

        int total = list.size();
        int unmastered = (int) list.stream().filter(w -> w.getMasteryLevel() != null && w.getMasteryLevel() == 0).count();
        int partial = (int) list.stream().filter(w -> w.getMasteryLevel() != null && w.getMasteryLevel() == 1).count();
        int mastered = (int) list.stream().filter(w -> w.getMasteryLevel() != null && w.getMasteryLevel() == 2).count();

        // 批量加载 Question（避免 N+1）
        List<Long> questionIds = list.stream().map(WrongQuestion::getQuestionId).distinct().collect(Collectors.toList());
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
        stats.put("unmastered", unmastered);
        stats.put("partial", partial);
        stats.put("mastered", mastered);
        stats.put("courseWrongCount", courseWrongCount);

        return stats;
    }
}
