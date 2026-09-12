package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.QuestionSubmissionVO;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.entity.QuestionSubmission;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.mapper.QuestionSubmissionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuestionSubmissionImportService {

    private static final Logger log = LoggerFactory.getLogger(QuestionSubmissionImportService.class);

    private final QuestionSubmissionMapper submissionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final QuestionSourceService questionSourceService;
    private final QuestionSubmissionOptionService optionService;
    private final QuestionSubmissionViewService viewService;

    public QuestionSubmissionImportService(QuestionSubmissionMapper submissionMapper,
                                           QuestionMapper questionMapper,
                                           QuestionOptionMapper questionOptionMapper,
                                           QuestionKnowledgePointMapper questionKnowledgePointMapper,
                                           KnowledgePointMapper knowledgePointMapper,
                                           QuestionSourceService questionSourceService,
                                           QuestionSubmissionOptionService optionService,
                                           QuestionSubmissionViewService viewService) {
        this.submissionMapper = submissionMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.questionSourceService = questionSourceService;
        this.optionService = optionService;
        this.viewService = viewService;
    }

    @Transactional
    public QuestionSubmissionVO importSubmission(Long submissionId, Long adminId) {
        QuestionSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "投稿不存在");
        }
        if (submission.getStatus() != 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "只有已通过的投稿才能入库");
        }

        Question question = new Question();
        question.setContent(submission.getContent());
        question.setQuestionType(submission.getQuestionType());
        question.setCourseId(submission.getCourseId());
        question.setDifficulty(submission.getDifficulty());
        question.setAnalysis(submission.getAnalysis());
        question.setTags(submission.getTags());
        question.setScore(1);
        question.setStatus(1);
        question.setCreateBy(submission.getUserId());
        question.setDeleted(0);
        questionMapper.insert(question);

        insertQuestionOptions(question.getId(), submission);
        insertKnowledgePoints(question.getId(), submission.getKnowledgePointIds());
        questionSourceService.setSource(question.getId(), "SUBMISSION", "submission:" + submissionId);
        questionSourceService.recordInitialReview(question.getId(), adminId, "投稿入库初审");

        submission.setStatus(3);
        submission.setImportedQuestionId(question.getId());
        submissionMapper.updateById(submission);
        log.info("管理员 {} 将投稿 {} 入库为题目 {}", adminId, submissionId, question.getId());
        return viewService.toView(submission);
    }

    private void insertQuestionOptions(Long questionId, QuestionSubmission submission) {
        String questionType = submission.getQuestionType();
        if ("FILL_BLANK".equals(questionType) || "SHORT_ANSWER".equals(questionType)) {
            insertOption(questionId, submission.getCorrectAnswer(), "ANSWER", true, 0);
            return;
        }
        if ("TRUE_FALSE".equals(questionType) || "SINGLE_CHOICE".equals(questionType)
                || "MULTIPLE_CHOICE".equals(questionType)) {
            List<QuestionSubmissionOptionService.OptionItem> options =
                    optionService.parseOptionsJson(submission.getOptionsJson());
            for (int i = 0; i < options.size(); i++) {
                QuestionSubmissionOptionService.OptionItem item = options.get(i);
                insertOption(questionId, item.content, optionService.normalizeOptionLabel(item, i),
                        Boolean.TRUE.equals(item.isCorrect), i);
            }
        }
    }

    private void insertKnowledgePoints(Long questionId, String knowledgePointIds) {
        if (knowledgePointIds == null || knowledgePointIds.isBlank()) {
            return;
        }
        for (String rawId : knowledgePointIds.split(",")) {
            try {
                Long knowledgePointId = Long.parseLong(rawId.trim());
                KnowledgePoint knowledgePoint = knowledgePointMapper.selectById(knowledgePointId);
                if (knowledgePoint != null) {
                    QuestionKnowledgePoint relation = new QuestionKnowledgePoint();
                    relation.setQuestionId(questionId);
                    relation.setKnowledgePointId(knowledgePointId);
                    questionKnowledgePointMapper.insert(relation);
                }
            } catch (NumberFormatException exception) {
                log.warn("知识点ID格式错误: {}", rawId);
            }
        }
    }

    private void insertOption(Long questionId, String content, String label, boolean isCorrect, int sortOrder) {
        QuestionOption option = new QuestionOption();
        option.setQuestionId(questionId);
        option.setContent(content);
        option.setOptionLabel(label);
        option.setIsCorrect(isCorrect ? 1 : 0);
        option.setSortOrder(sortOrder);
        option.setDeleted(0);
        questionOptionMapper.insert(option);
    }
}
