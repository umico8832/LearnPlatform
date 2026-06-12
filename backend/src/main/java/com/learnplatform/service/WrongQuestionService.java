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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public WrongQuestionService(WrongQuestionMapper wrongQuestionMapper,
                                QuestionMapper questionMapper,
                                CourseMapper courseMapper) {
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
    }

    /**
     * 答错时自动加入错题本（同一用户+题目不重复）
     */
    @Transactional
    public void addWrongQuestion(Long userId, Long questionId, String userAnswer) {
        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
               .eq(WrongQuestion::getQuestionId, questionId);
        WrongQuestion existing = wrongQuestionMapper.selectOne(wrapper);

        if (existing != null) {
            existing.setWrongCount(existing.getWrongCount() + 1);
            existing.setLastWrongAnswer(userAnswer);
            if (existing.getMasteryLevel() != null && existing.getMasteryLevel() == 2) {
                existing.setMasteryLevel(0);
            }
            wrongQuestionMapper.updateById(existing);
        } else {
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
     * 获取用户错题本（分页）
     */
    public Page<WrongQuestionVO> getWrongQuestions(Long userId, int pageNum, int pageSize,
                                                    Long courseId, Integer masteryLevel) {
        Page<WrongQuestion> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId);
        if (masteryLevel != null) {
            wrapper.eq(WrongQuestion::getMasteryLevel, masteryLevel);
        }
        wrapper.orderByDesc(WrongQuestion::getUpdateTime);

        Page<WrongQuestion> result = wrongQuestionMapper.selectPage(page, wrapper);

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

                    Question question = questionMapper.selectById(wq.getQuestionId());
                    if (question != null) {
                        vo.setQuestionContent(question.getContent());
                        vo.setQuestionType(question.getQuestionType());
                        vo.setCourseId(question.getCourseId());
                        vo.setDifficulty(question.getDifficulty());
                        Course course = courseMapper.selectById(question.getCourseId());
                        if (course != null) {
                            vo.setCourseName(course.getName());
                        }
                    }
                    return vo;
                })
                .filter(vo -> {
                    if (courseId != null) {
                        return courseId.equals(vo.getCourseId());
                    }
                    return true;
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
    }

    /**
     * 移出错题本（逻辑删除）
     */
    @Transactional
    public void removeWrongQuestion(Long id, Long userId) {
        WrongQuestion wq = wrongQuestionMapper.selectById(id);
        if (wq == null || !wq.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "错题记录不存在");
        }
        wrongQuestionMapper.deleteById(id);
    }

    /**
     * 答对时自动从错题本移出
     */
    @Transactional
    public void removeOnCorrect(Long userId, Long questionId) {
        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId)
               .eq(WrongQuestion::getQuestionId, questionId);
        WrongQuestion existing = wrongQuestionMapper.selectOne(wrapper);
        if (existing != null) {
            wrongQuestionMapper.deleteById(existing.getId());
        }
    }

    /**
     * 获取错题本统计
     */
    public Map<String, Object> getWrongQuestionStats(Long userId) {
        LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WrongQuestion::getUserId, userId);
        List<WrongQuestion> list = wrongQuestionMapper.selectList(wrapper);

        int total = list.size();
        int unmastered = (int) list.stream().filter(w -> w.getMasteryLevel() != null && w.getMasteryLevel() == 0).count();
        int partial = (int) list.stream().filter(w -> w.getMasteryLevel() != null && w.getMasteryLevel() == 1).count();
        int mastered = (int) list.stream().filter(w -> w.getMasteryLevel() != null && w.getMasteryLevel() == 2).count();

        Map<String, Integer> courseWrongCount = new HashMap<>();
        for (WrongQuestion wq : list) {
            Question q = questionMapper.selectById(wq.getQuestionId());
            if (q != null) {
                Course c = courseMapper.selectById(q.getCourseId());
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