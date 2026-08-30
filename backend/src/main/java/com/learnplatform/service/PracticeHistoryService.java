package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.dto.PracticeRecordVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.Question;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.service.question.QuestionAccessPolicy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 查询用户练习记录及其汇总事实。 */
@Service
public class PracticeHistoryService {

    private final PracticeRecordMapper practiceRecordMapper;
    private final QuestionMapper questionMapper;
    private final CourseMapper courseMapper;

    public PracticeHistoryService(PracticeRecordMapper practiceRecordMapper,
                                  QuestionMapper questionMapper,
                                  CourseMapper courseMapper) {
        this.practiceRecordMapper = practiceRecordMapper;
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
    }

    public Page<PracticeRecordVO> getUserPracticeRecords(
            Long userId, int pageNum, int pageSize,
            String questionType, Long courseId, Integer isCorrect) {
        Page<PracticeRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PracticeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeRecord::getUserId, userId);
        if (isCorrect != null) {
            wrapper.eq(PracticeRecord::getIsCorrect, isCorrect);
        }
        if ((questionType != null && !questionType.isBlank()) || courseId != null) {
            List<Long> questionIds = findAccessibleQuestionIds(userId, questionType, courseId);
            if (questionIds.isEmpty()) {
                return new Page<>(pageNum, pageSize, 0);
            }
            wrapper.in(PracticeRecord::getQuestionId, questionIds);
        }
        wrapper.orderByDesc(PracticeRecord::getCreateTime);

        Page<PracticeRecord> result = practiceRecordMapper.selectPage(page, wrapper);
        Page<PracticeRecordVO> viewPage = new Page<>(
                result.getCurrent(), result.getSize(), result.getTotal());
        viewPage.setRecords(result.getRecords().stream()
                .map(record -> toView(record, userId))
                .collect(Collectors.toList()));
        return viewPage;
    }

    public Map<String, Object> getUserPracticeStats(Long userId) {
        LambdaQueryWrapper<PracticeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeRecord::getUserId, userId);
        List<PracticeRecord> records = practiceRecordMapper.selectList(wrapper);
        int totalAnswered = records.size();
        int correctCount = (int) records.stream()
                .filter(record -> record.getIsCorrect() != null && record.getIsCorrect() == 1)
                .count();
        int wrongCount = totalAnswered - correctCount;
        double correctRate = totalAnswered > 0
                ? (double) correctCount / totalAnswered * 100
                : 0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAnswered", totalAnswered);
        stats.put("correctCount", correctCount);
        stats.put("wrongCount", wrongCount);
        stats.put("correctRate", Math.round(correctRate * 10.0) / 10.0);
        return stats;
    }

    private List<Long> findAccessibleQuestionIds(Long userId, String questionType, Long courseId) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(scope -> scope.eq(Question::getVisibility, "PUBLIC")
                .or(privateScope -> privateScope.eq(Question::getVisibility, "PRIVATE")
                        .eq(Question::getOwnerUserId, userId)));
        if (questionType != null && !questionType.isBlank()) {
            wrapper.eq(Question::getQuestionType, questionType);
        }
        if (courseId != null) {
            wrapper.eq(Question::getCourseId, courseId);
        }
        return questionMapper.selectList(wrapper).stream()
                .map(Question::getId)
                .collect(Collectors.toList());
    }

    private PracticeRecordVO toView(PracticeRecord record, Long userId) {
        PracticeRecordVO view = new PracticeRecordVO();
        view.setId(record.getId());
        view.setQuestionId(record.getQuestionId());
        view.setUserAnswer(record.getUserAnswer());
        view.setIsCorrect(record.getIsCorrect());
        view.setAnswerTime(record.getAnswerTime());
        view.setCreateTime(record.getCreateTime());

        Question question = questionMapper.selectById(record.getQuestionId());
        if (QuestionAccessPolicy.canAccess(question, userId)) {
            view.setQuestionContent(question.getContent());
            view.setQuestionType(question.getQuestionType());
            view.setDifficulty(question.getDifficulty());
            Course course = courseMapper.selectById(question.getCourseId());
            if (course != null) {
                view.setCourseName(course.getName());
            }
        }
        return view;
    }
}
