package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.ExamPaperCreateRequest;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamQuestion;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.mapper.SubjectiveGradingPointMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Year;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamPaperServiceTest {

    @Mock private ExamPaperMapper examPaperMapper;
    @Mock private ExamQuestionMapper examQuestionMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionOptionMapper questionOptionMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private SubjectiveGradingPointMapper subjectiveGradingPointMapper;
    private ExamPaperService examPaperService;

    @BeforeEach
    void setUp() {
        ExamPaperViewService viewService = new ExamPaperViewService(examPaperMapper, examQuestionMapper,
                questionMapper, questionOptionMapper, courseMapper);
        ExamPaperValidationService validationService = new ExamPaperValidationService(
                examQuestionMapper, questionMapper, subjectiveGradingPointMapper);
        examPaperService = new ExamPaperService(
                examPaperMapper, examQuestionMapper, viewService, validationService);
    }

    @Test
    void rejectsUpdatingPublishedPaper() {
        ExamPaper paper = paper(1, 3);
        when(examPaperMapper.selectByIdForUpdate(1L)).thenReturn(paper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examPaperService.updateExamPaper(1L, new ExamPaperCreateRequest()));

        assertEquals("已发布试卷不能修改", exception.getMessage());
        verify(examPaperMapper, never()).updateById(paper);
    }

    @Test
    void rejectsPublishingEmptyPaper() {
        ExamPaper paper = paper(0, 0);
        when(examPaperMapper.selectByIdForUpdate(1L)).thenReturn(paper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examPaperService.publishExamPaper(1L));

        assertEquals("空试卷不能发布", exception.getMessage());
        verify(examPaperMapper, never()).updateById(paper);
    }

    @Test
    void rejectsPublishingShortAnswerWithoutCompleteRubric() {
        ExamPaper paper = paper(0, 1);
        ExamQuestion relation = new ExamQuestion();
        relation.setQuestionId(10L);
        relation.setScore(13);
        when(examPaperMapper.selectByIdForUpdate(1L)).thenReturn(paper);
        when(examQuestionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(relation));
        com.learnplatform.entity.Question question = new com.learnplatform.entity.Question();
        question.setId(10L);
        question.setQuestionType("SHORT_ANSWER");
        when(questionMapper.selectById(10L)).thenReturn(question);
        when(subjectiveGradingPointMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examPaperService.publishExamPaper(1L));

        assertEquals("主观题发布前必须配置与题目分值一致的评分点", exception.getMessage());
    }

    @Test
    void rejectsCreatingPublishedPaperWithoutQuestions() {
        ExamPaperCreateRequest request = new ExamPaperCreateRequest();
        request.setStatus(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examPaperService.createExamPaper(request, 1L));

        assertEquals("空试卷不能发布", exception.getMessage());
        verify(examPaperMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsPublishingDraftThroughUpdateWithoutQuestions() {
        ExamPaper paper = paper(0, 0);
        ExamPaperCreateRequest request = new ExamPaperCreateRequest();
        request.setStatus(1);
        when(examPaperMapper.selectByIdForUpdate(1L)).thenReturn(paper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examPaperService.updateExamPaper(1L, request));

        assertEquals("空试卷不能发布", exception.getMessage());
        verify(examPaperMapper, never()).updateById(paper);
    }

    @Test
    void rejectsPrivatePaperUntilOwnerVisibilityRulesExist() {
        ExamPaperCreateRequest request = new ExamPaperCreateRequest();
        request.setPaperType("USER_PRIVATE");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examPaperService.createExamPaper(request, 1L));

        assertEquals("不支持的试卷类型", exception.getMessage());
        verify(examPaperMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsPublishingOfficialPaperBeforeSourceVerification() {
        ExamPaper paper = officialPaper();
        paper.setSourceVerified(false);
        when(examPaperMapper.selectByIdForUpdate(1L)).thenReturn(paper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examPaperService.publishExamPaper(1L));

        assertEquals("官方试卷发布前必须确认来源已核验", exception.getMessage());
        verify(examPaperMapper, never()).updateById(paper);
    }

    @Test
    void rejectsPublishingOfficialPaperWithoutDisplayNumbers() {
        ExamPaper paper = officialPaper();
        when(examPaperMapper.selectByIdForUpdate(1L)).thenReturn(paper);
        when(examQuestionMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new ExamQuestion()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examPaperService.publishExamPaper(1L));

        assertEquals("官方试卷每道题必须填写展示题号", exception.getMessage());
        verify(examPaperMapper, never()).updateById(paper);
    }

    @Test
    void publishesOfficialPaperAfterProvenanceAndNumberingAreComplete() {
        ExamPaper paper = officialPaper();
        ExamQuestion question = new ExamQuestion();
        question.setDisplayNumber("1");
        when(examPaperMapper.selectByIdForUpdate(1L)).thenReturn(paper);
        when(examQuestionMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(question));

        examPaperService.publishExamPaper(1L);

        assertEquals(1, paper.getStatus());
        verify(examPaperMapper).updateById(paper);
    }

    @Test
    void persistsOfficialPaperProvenanceAndQuestionStructure() {
        ExamPaperCreateRequest request = officialDraftRequest();
        AtomicReference<ExamPaper> savedPaper = new AtomicReference<>();
        when(examPaperMapper.insert(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            ExamPaper paper = invocation.getArgument(0);
            paper.setId(1L);
            savedPaper.set(paper);
            return 1;
        });
        when(examPaperMapper.selectById(1L)).thenAnswer(invocation -> savedPaper.get());
        when(examQuestionMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        examPaperService.createExamPaper(request, 7L);

        ExamPaper paper = savedPaper.get();
        assertEquals("OFFICIAL_EXAM", paper.getPaperType());
        assertEquals("全国硕士研究生招生考试", paper.getExamName());
        assertEquals(Year.now().getValue(), paper.getExamYear());
        assertEquals("可核验来源", paper.getSourceReference());
        assertEquals(true, paper.getSourceVerified());
        ArgumentCaptor<ExamQuestion> captor = ArgumentCaptor.forClass(ExamQuestion.class);
        verify(examQuestionMapper).insert(captor.capture());
        assertEquals("第一部分", captor.getValue().getSectionTitle());
        assertEquals("1", captor.getValue().getMajorQuestionNumber());
        assertEquals("1", captor.getValue().getMinorQuestionNumber());
        assertEquals("a", captor.getValue().getSubquestionNumber());
        assertEquals("1(1)(a)", captor.getValue().getDisplayNumber());
    }

    private ExamPaper paper(int status, int questionCount) {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        paper.setStatus(status);
        paper.setQuestionCount(questionCount);
        return paper;
    }

    private ExamPaper officialPaper() {
        ExamPaper paper = paper(0, 1);
        paper.setPaperType("OFFICIAL_EXAM");
        paper.setExamName("全国硕士研究生招生考试");
        paper.setExamYear(Year.now().getValue());
        paper.setSourceReference("可核验来源");
        paper.setSourceVerified(true);
        return paper;
    }

    private ExamPaperCreateRequest officialDraftRequest() {
        ExamPaperCreateRequest request = new ExamPaperCreateRequest();
        request.setTitle("来源结构测试试卷");
        request.setCourseId(10L);
        request.setDuration(60);
        request.setStatus(0);
        request.setPaperType("OFFICIAL_EXAM");
        request.setExamName("全国硕士研究生招生考试");
        request.setExamYear(Year.now().getValue());
        request.setSourceReference("可核验来源");
        request.setSourceVerified(true);
        ExamPaperCreateRequest.QuestionItem item = new ExamPaperCreateRequest.QuestionItem();
        item.setQuestionId(21L);
        item.setScore(2);
        item.setSectionTitle("第一部分");
        item.setMajorQuestionNumber("1");
        item.setMinorQuestionNumber("1");
        item.setSubquestionNumber("a");
        item.setDisplayNumber("1(1)(a)");
        request.setQuestions(List.of(item));
        return request;
    }
}
