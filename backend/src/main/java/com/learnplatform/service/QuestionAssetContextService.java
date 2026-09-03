package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class QuestionAssetContextService {

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final CourseMapper courseMapper;

    public QuestionAssetContextService(QuestionMapper questionMapper,
                                       QuestionOptionMapper questionOptionMapper,
                                       QuestionKnowledgePointMapper questionKnowledgePointMapper,
                                       KnowledgePointMapper knowledgePointMapper,
                                       CourseMapper courseMapper) {
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.courseMapper = courseMapper;
    }

    public String load(Long questionId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }

        StringBuilder context = new StringBuilder();
        context.append("题型：").append(getTypeLabel(question.getQuestionType())).append("\n");
        context.append("难度：").append(question.getDifficulty()).append("/5\n");
        context.append("题目：").append(question.getContent()).append("\n");

        appendOptions(context, question);
        if (question.getAnalysis() != null && !question.getAnalysis().isBlank()) {
            context.append("原始解析：").append(question.getAnalysis()).append("\n");
        }
        appendKnowledgePoints(context, question);
        appendCourse(context, question);
        return context.toString();
    }

    private void appendOptions(StringBuilder context, Question question) {
        LambdaQueryWrapper<QuestionOption> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionOption::getQuestionId, question.getId())
                .orderByAsc(QuestionOption::getSortOrder);
        List<QuestionOption> options = questionOptionMapper.selectList(wrapper);
        if (options.isEmpty()) {
            return;
        }

        context.append("选项：\n");
        for (QuestionOption option : options) {
            context.append("  ").append(option.getOptionLabel()).append(". ").append(option.getContent());
            if (option.getIsCorrect() != null && option.getIsCorrect() == 1) {
                context.append(" [正确答案]");
            }
            context.append("\n");
        }
    }

    private void appendKnowledgePoints(StringBuilder context, Question question) {
        LambdaQueryWrapper<QuestionKnowledgePoint> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionKnowledgePoint::getQuestionId, question.getId());
        List<QuestionKnowledgePoint> relations = questionKnowledgePointMapper.selectList(wrapper);
        if (relations.isEmpty()) {
            return;
        }

        List<Long> knowledgePointIds = relations.stream()
                .map(QuestionKnowledgePoint::getKnowledgePointId)
                .collect(Collectors.toList());
        List<KnowledgePoint> knowledgePoints = knowledgePointMapper.selectBatchIds(knowledgePointIds);
        if (!knowledgePoints.isEmpty()) {
            context.append("知识点：")
                    .append(knowledgePoints.stream().map(KnowledgePoint::getName)
                            .collect(Collectors.joining("、")))
                    .append("\n");
        }
    }

    private void appendCourse(StringBuilder context, Question question) {
        if (question.getCourseId() == null) {
            return;
        }
        Course course = courseMapper.selectById(question.getCourseId());
        if (course != null) {
            context.append("所属课程：").append(course.getName()).append("\n");
        }
    }

    private String getTypeLabel(String type) {
        if (type == null) { return "未知"; }
        switch (type) {
            case "SINGLE_CHOICE": return "单选题";
            case "MULTIPLE_CHOICE": return "多选题";
            case "TRUE_FALSE": return "判断题";
            case "FILL_BLANK": return "填空题";
            case "SHORT_ANSWER": return "简答题";
            default: return type;
        }
    }
}
