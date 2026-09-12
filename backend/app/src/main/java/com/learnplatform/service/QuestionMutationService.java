package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class QuestionMutationService {
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final CourseMapper courseMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final QuestionVersionService questionVersionService;

    public QuestionMutationService(
            QuestionMapper questionMapper,
            QuestionOptionMapper questionOptionMapper,
            QuestionKnowledgePointMapper questionKnowledgePointMapper,
            CourseMapper courseMapper,
            KnowledgePointMapper knowledgePointMapper,
            ExamQuestionMapper examQuestionMapper,
            QuestionVersionService questionVersionService) {
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.courseMapper = courseMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.questionVersionService = questionVersionService;
    }

    public Long create(QuestionCreateRequest request, Long createBy,
                       String sourceType, String sourceReference, Long originQuestionId) {
        Course course = courseMapper.selectById(request.getCourseId());
        if (course == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在");
        }
        Question question = new Question();
        question.setContent(request.getContent());
        question.setQuestionType(request.getQuestionType());
        question.setCourseId(request.getCourseId());
        question.setDifficulty(request.getDifficulty() != null ? request.getDifficulty() : 3);
        question.setAnalysis(request.getAnalysis());
        question.setTags(request.getTags());
        question.setScore(request.getScore() != null ? request.getScore() : 1);
        question.setStatus(1);
        question.setCreateBy(createBy);
        question.setVisibility("PUBLIC");
        question.setSourceType(sourceType);
        question.setSourceReference(sourceReference);
        question.setOriginQuestionId(originQuestionId);
        question.setReviewRounds(0);
        question.setNextReviewTime(LocalDateTime.now().plusDays(90));
        question.setDeleted(0);
        questionMapper.insert(question);
        replaceOptions(question.getId(), request, false);
        replaceKnowledgePoints(question.getId(), request, false);
        questionVersionService.recordChange(question.getId(), "CREATE", createBy,
                "创建题目", null, questionMapper.selectById(question.getId()));
        return question.getId();
    }

    public void update(Long id, QuestionCreateRequest request, Long operatorId) {
        Question question = findPublicQuestion(id);
        ensureNotUsedByPublishedPaper(id);
        String snapshotBefore = questionVersionService.buildSnapshotJson(question);
        if (request.getContent() != null) {
            question.setContent(request.getContent());
        }
        if (request.getQuestionType() != null) {
            question.setQuestionType(request.getQuestionType());
        }
        if (request.getCourseId() != null) {
            question.setCourseId(request.getCourseId());
        }
        if (request.getDifficulty() != null) {
            question.setDifficulty(request.getDifficulty());
        }
        if (request.getAnalysis() != null) {
            question.setAnalysis(request.getAnalysis());
        }
        if (request.getTags() != null) {
            question.setTags(request.getTags());
        }
        if (request.getScore() != null) {
            question.setScore(request.getScore());
        }
        questionMapper.updateById(question);
        replaceOptions(id, request, true);
        replaceKnowledgePoints(id, request, true);
        questionVersionService.recordChangeSnapshots(id, "UPDATE", operatorId,
                "更新题目内容、选项或知识点", snapshotBefore,
                questionVersionService.buildSnapshotJson(questionMapper.selectById(id)));
    }

    public void delete(Long id, Long operatorId) {
        Question question = findPublicQuestion(id);
        ensureNotUsedByPublishedPaper(id);
        String snapshotBefore = questionVersionService.buildSnapshotJson(question);
        questionMapper.deleteById(id);
        questionOptionMapper.delete(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, id));
        questionKnowledgePointMapper.delete(new LambdaQueryWrapper<QuestionKnowledgePoint>()
                .eq(QuestionKnowledgePoint::getQuestionId, id));
        questionVersionService.recordChangeSnapshots(id, "DELETE", operatorId,
                "删除题目", snapshotBefore, null);
    }

    private void replaceOptions(Long questionId, QuestionCreateRequest request, boolean deleteExisting) {
        if (request.getOptions() == null) {
            return;
        }
        if (deleteExisting) {
            questionOptionMapper.delete(new LambdaQueryWrapper<QuestionOption>()
                    .eq(QuestionOption::getQuestionId, questionId));
        }
        for (QuestionCreateRequest.OptionItem item : request.getOptions()) {
            QuestionOption option = new QuestionOption();
            option.setQuestionId(questionId);
            option.setContent(item.getContent());
            option.setOptionLabel(item.getOptionLabel());
            option.setIsCorrect(item.getIsCorrect() != null ? item.getIsCorrect() : 0);
            option.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
            option.setDeleted(0);
            questionOptionMapper.insert(option);
        }
    }

    private void replaceKnowledgePoints(
            Long questionId, QuestionCreateRequest request, boolean deleteExisting) {
        if (request.getKnowledgePointIds() == null) {
            return;
        }
        if (deleteExisting) {
            questionKnowledgePointMapper.delete(new LambdaQueryWrapper<QuestionKnowledgePoint>()
                    .eq(QuestionKnowledgePoint::getQuestionId, questionId));
        }
        for (Long knowledgePointId : request.getKnowledgePointIds()) {
            KnowledgePoint knowledgePoint = knowledgePointMapper.selectById(knowledgePointId);
            if (knowledgePoint == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "知识点不存在: " + knowledgePointId);
            }
            QuestionKnowledgePoint relation = new QuestionKnowledgePoint();
            relation.setQuestionId(questionId);
            relation.setKnowledgePointId(knowledgePointId);
            questionKnowledgePointMapper.insert(relation);
        }
    }

    private Question findPublicQuestion(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null || "PRIVATE".equals(question.getVisibility())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }
        return question;
    }

    private void ensureNotUsedByPublishedPaper(Long questionId) {
        if (examQuestionMapper.countPublishedPapersByQuestionId(questionId) > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "题目已用于已发布试卷，不能修改或删除");
        }
    }
}
