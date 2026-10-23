package com.learnplatform.service;

import com.learnplatform.service.content.LockedContentParents;
import java.util.Collections;

import com.learnplatform.common.time.BusinessTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.service.question.AutomaticGradingPolicy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.HashSet;

@Service
public class QuestionMutationService {
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final QuestionVersionService questionVersionService;
    private final CacheEvictService cacheEvictService;
    private final ContentReferenceLockService referenceLocks;

    public QuestionMutationService(
            QuestionMapper questionMapper,
            QuestionOptionMapper questionOptionMapper,
            QuestionKnowledgePointMapper questionKnowledgePointMapper,
            ExamQuestionMapper examQuestionMapper,
            QuestionVersionService questionVersionService,
            CacheEvictService cacheEvictService,
            ContentReferenceLockService referenceLocks) {
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.questionVersionService = questionVersionService;
        this.cacheEvictService = cacheEvictService;
        this.referenceLocks = referenceLocks;
    }

    public Long create(QuestionCreateRequest request, Long createBy,
                       String sourceType, String sourceReference, Long originQuestionId) {
        normalizeAndValidateOptions(request.getQuestionType(), request.getOptions());
        var parents = referenceLocks.lockForReference(Collections.singletonList(request.getCourseId()),
                request.getKnowledgePointIds());
        validateReferences(request.getCourseId(), request.getKnowledgePointIds(), parents);
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
        question.setNextReviewTime(BusinessTime.now().plusDays(90));
        question.setDeleted(0);
        questionMapper.insert(question);
        replaceOptions(question.getId(), request, false);
        replaceKnowledgePoints(question.getId(), request, false);
        questionVersionService.recordChange(question.getId(), "CREATE", createBy,
                "创建题目", null, questionMapper.selectById(question.getId()));
        cacheEvictService.evictLearningDiagnosisAfterCommit();
        return question.getId();
    }

    public void update(Long id, QuestionCreateRequest request, Long operatorId) {
        Question question = findPublicQuestion(id);
        ensureNotUsedByPublishedPaper(id);
        Long courseId = request.getCourseId() != null ? request.getCourseId() : question.getCourseId();
        List<Long> pointIds = request.getKnowledgePointIds() != null ? request.getKnowledgePointIds()
                : questionKnowledgePointMapper
                .selectList(new LambdaQueryWrapper<QuestionKnowledgePoint>()
                        .eq(QuestionKnowledgePoint::getQuestionId, id)).stream()
                        .map(QuestionKnowledgePoint::getKnowledgePointId).toList();
        List<Long> oldPointIds = questionKnowledgePointMapper
                .selectList(new LambdaQueryWrapper<QuestionKnowledgePoint>()
                        .eq(QuestionKnowledgePoint::getQuestionId, id)).stream()
                .map(QuestionKnowledgePoint::getKnowledgePointId).toList();
        Long previousCourseId = question.getCourseId();
        List<Long> allCourseIds = new ArrayList<>();
        allCourseIds.add(question.getCourseId());
        allCourseIds.add(courseId);
        List<Long> allPointIds = new ArrayList<>(oldPointIds);
        allPointIds.addAll(pointIds == null ? List.of() : pointIds);
        var parents = referenceLocks.lockForReference(allCourseIds, allPointIds);
        question = questionMapper.selectActiveForUpdate(id);
        if (question == null || !"PUBLIC".equals(question.getVisibility())
                || !Objects.equals(previousCourseId, question.getCourseId())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "题目归属已变更，请重试");
        }
        List<Long> currentPoints = questionKnowledgePointMapper
                .selectList(new LambdaQueryWrapper<QuestionKnowledgePoint>()
                        .eq(QuestionKnowledgePoint::getQuestionId, id)
                        .orderByAsc(QuestionKnowledgePoint::getKnowledgePointId)
                        .last("FOR SHARE")).stream().map(QuestionKnowledgePoint::getKnowledgePointId).toList();
        if (!new HashSet<>(currentPoints).equals(new HashSet<>(oldPointIds))) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "题目关联已变更，请重试");
        }
        courseId = request.getCourseId() != null ? request.getCourseId() : question.getCourseId();
        pointIds = request.getKnowledgePointIds() != null ? request.getKnowledgePointIds() : currentPoints;
        String questionType = request.getQuestionType() != null
                ? request.getQuestionType() : question.getQuestionType();
        normalizeAndValidateOptions(questionType, request.getOptions(), id);
        validateReferences(courseId, pointIds, parents);
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
        cacheEvictService.evictLearningDiagnosisAfterCommit();
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
        cacheEvictService.evictLearningDiagnosisAfterCommit();
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

    private void normalizeAndValidateOptions(
            String questionType, List<QuestionCreateRequest.OptionItem> options) {
        normalizeAndValidateOptions(questionType, options, null);
    }

    private void normalizeAndValidateOptions(
            String questionType, List<QuestionCreateRequest.OptionItem> options, Long questionId) {
        if (options == null) {
            if (questionId == null) {
                return;
            }
            List<QuestionOption> correctOptions = questionOptionMapper.selectList(
                    new LambdaQueryWrapper<QuestionOption>().eq(QuestionOption::getQuestionId, questionId)).stream()
                    .filter(item -> Integer.valueOf(1).equals(item.getIsCorrect()))
                    .toList();
            AutomaticGradingPolicy.validateConfiguredStructure(questionType,
                    correctOptions.stream().map(QuestionOption::getOptionLabel).toList(),
                    correctOptions.stream().map(QuestionOption::getContent).toList());
            return;
        }
        List<QuestionCreateRequest.OptionItem> correctOptions = options.stream()
                .filter(item -> Integer.valueOf(1).equals(item.getIsCorrect()))
                .toList();
        AutomaticGradingPolicy.validateConfiguredStructure(questionType,
                correctOptions.stream().map(QuestionCreateRequest.OptionItem::getOptionLabel).toList(),
                correctOptions.stream().map(QuestionCreateRequest.OptionItem::getContent).toList());
        if ("TRUE_FALSE".equals(questionType) && correctOptions.size() == 1) {
            correctOptions.getFirst().setContent(
                    AutomaticGradingPolicy.normalizeTrueFalseAnswer(correctOptions.getFirst().getContent()));
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
            QuestionKnowledgePoint relation = new QuestionKnowledgePoint();
            relation.setQuestionId(questionId);
            relation.setKnowledgePointId(knowledgePointId);
            questionKnowledgePointMapper.insert(relation);
        }
    }

    private void validateReferences(Long courseId, List<Long> pointIds,
                                    LockedContentParents parents) {
        Course course = courseId == null ? null : parents.courses().get(courseId);
        if (course == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在");
        }
        if (pointIds == null) {
            return;
        }
        for (Long pointId : pointIds) {
            KnowledgePoint point = pointId == null ? null : parents.knowledgePoints().get(pointId);
            if (point == null || !courseId.equals(point.getCourseId())) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "知识点不存在或不属于题目课程");
            }
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
