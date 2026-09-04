package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.PrivateExamImportPreviewVO;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamQuestion;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.entity.UserExamSource;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PrivateExamConfirmedPaperService {
    private final ExamPaperMapper paperMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper optionMapper;
    private final ExamPaperService examPaperService;
    private final PrivateExamImportParserService parserService;

    public PrivateExamConfirmedPaperService(
            ExamPaperMapper paperMapper,
            ExamQuestionMapper examQuestionMapper,
            QuestionMapper questionMapper,
            QuestionOptionMapper optionMapper,
            ExamPaperService examPaperService,
            PrivateExamImportParserService parserService) {
        this.paperMapper = paperMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.questionMapper = questionMapper;
        this.optionMapper = optionMapper;
        this.examPaperService = examPaperService;
        this.parserService = parserService;
    }

    public ExamPaperVO create(String title, Long courseId, Integer duration,
                              UserExamSource source,
                              List<PrivateExamImportPreviewVO.QuestionPreview> questions,
                              Long userId) {
        if (source == null || source.getId() == null || !userId.equals(source.getOwnerUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "原始资料不存在");
        }
        for (int index = 0; index < questions.size(); index++) {
            parserService.validateConfirmedQuestion(questions.get(index), index + 1);
        }

        ExamPaper paper = buildPaper(title, courseId, duration, source, questions, userId);
        paperMapper.insert(paper);
        for (int index = 0; index < questions.size(); index++) {
            createQuestion(paper, courseId, source, questions.get(index), index, userId);
        }
        return get(paper.getId(), userId);
    }

    public ExamPaperVO get(Long paperId, Long userId) {
        return examPaperService.getAccessiblePublishedExamPaperById(paperId, userId);
    }

    private ExamPaper buildPaper(String title, Long courseId, Integer duration,
                                 UserExamSource source,
                                 List<PrivateExamImportPreviewVO.QuestionPreview> questions,
                                 Long userId) {
        ExamPaper paper = new ExamPaper();
        paper.setTitle(title.trim());
        paper.setDescription("由用户复核确认的结构化" + formatLabel(source.getSourceFormat()) + "资料导入");
        paper.setCourseId(courseId);
        paper.setTotalScore(questions.stream()
                .mapToInt(PrivateExamImportPreviewVO.QuestionPreview::getScore).sum());
        paper.setDuration(duration != null ? duration : 60);
        paper.setQuestionCount(questions.size());
        paper.setStatus(1);
        paper.setCreateBy(userId);
        paper.setOwnerUserId(userId);
        paper.setVisibility("PRIVATE");
        paper.setPaperType("USER_PRIVATE");
        paper.setSourceReference("user-source:" + source.getContentSha256());
        paper.setSourceVerified(false);
        paper.setSourceRecordId(source.getId());
        paper.setImportStatus("CONFIRMED");
        paper.setDeleted(0);
        return paper;
    }

    private void createQuestion(ExamPaper paper, Long courseId, UserExamSource source,
                                PrivateExamImportPreviewVO.QuestionPreview item,
                                int index, Long userId) {
        Question question = new Question();
        question.setContent(item.getContent());
        question.setQuestionType(item.getQuestionType());
        question.setCourseId(courseId);
        question.setDifficulty(3);
        question.setAnalysis(item.getAnalysis());
        question.setScore(item.getScore());
        question.setStatus(1);
        question.setCreateBy(userId);
        question.setOwnerUserId(userId);
        question.setVisibility("PRIVATE");
        question.setSourceType("USER_PRIVATE_IMPORT");
        question.setSourceReference("user-source:" + source.getId());
        question.setReviewRounds(0);
        question.setDeleted(0);
        questionMapper.insert(question);
        createOptions(question.getId(), item.getOptions());

        ExamQuestion relation = new ExamQuestion();
        relation.setExamPaperId(paper.getId());
        relation.setQuestionId(question.getId());
        relation.setSortOrder(index + 1);
        relation.setScore(item.getScore());
        relation.setDisplayNumber(String.valueOf(index + 1));
        examQuestionMapper.insert(relation);
    }

    private void createOptions(Long questionId, List<PrivateExamImportPreviewVO.OptionPreview> options) {
        for (int index = 0; index < options.size(); index++) {
            PrivateExamImportPreviewVO.OptionPreview item = options.get(index);
            QuestionOption option = new QuestionOption();
            option.setQuestionId(questionId);
            option.setContent(item.getContent());
            option.setOptionLabel(item.getLabel());
            option.setIsCorrect(Boolean.TRUE.equals(item.getCorrect()) ? 1 : 0);
            option.setSortOrder(index + 1);
            option.setDeleted(0);
            optionMapper.insert(option);
        }
    }

    private String formatLabel(String format) {
        if ("MARKDOWN".equals(format)) {
            return "Markdown";
        }
        if ("PDF".equals(format)) {
            return "PDF";
        }
        return "DOCX".equals(format) ? "DOCX" : "文本";
    }
}
