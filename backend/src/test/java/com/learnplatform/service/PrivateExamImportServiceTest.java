package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.PrivateExamImportConfirmRequest;
import com.learnplatform.dto.PrivateExamImportPreviewVO;
import com.learnplatform.dto.PrivateExamImportRequest;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.UserExamSource;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.mapper.UserExamSourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivateExamImportServiceTest {
    @Mock private CourseMapper courseMapper;
    @Mock private UserExamSourceMapper sourceMapper;
    @Mock private PrivateExamSourceStorageService sourceStorageService;
    @Mock private ExamPaperMapper paperMapper;
    @Mock private ExamQuestionMapper examQuestionMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionOptionMapper optionMapper;
    @Mock private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    @Mock private KnowledgePointMapper knowledgePointMapper;
    @Mock private ExamPaperService examPaperService;
    private PrivateExamImportService service;

    @BeforeEach
    void setUp() {
        MarkdownQuestionParser parser = new MarkdownQuestionParser(
                questionMapper, optionMapper, questionKnowledgePointMapper, courseMapper, knowledgePointMapper);
        service = new PrivateExamImportService(parser, courseMapper, sourceMapper, sourceStorageService, paperMapper,
                examQuestionMapper, questionMapper, optionMapper, examPaperService);
        Course course = new Course();
        course.setId(10L);
        when(courseMapper.selectById(10L)).thenReturn(course);
    }

    @Test
    void previewsLimitedStructuredTextWithoutWriting() {
        PrivateExamImportPreviewVO preview = service.preview(request());

        assertEquals(2, preview.getQuestionCount());
        assertEquals(3, preview.getTotalScore());
        assertEquals("SINGLE_CHOICE", preview.getQuestions().get(0).getQuestionType());
        assertEquals(true, preview.getQuestions().get(0).getOptions().get(0).getCorrect());
        verify(paperMapper, never()).insert(any());
        verify(questionMapper, never()).insert(any());
    }

    @Test
    void previewsAnswerlessObjectiveQuestionsForDraftImport() {
        PrivateExamImportRequest request = request();
        request.setContent("""
                题型：单选题
                题干：先进后出的数据结构是？
                选项：
                A. 栈
                B. 队列
                分值：1
                """);

        PrivateExamImportPreviewVO preview = service.preview(request);

        assertEquals(1, preview.getQuestionCount());
        assertEquals(null, preview.getQuestions().get(0).getAnswer());
    }

    @Test
    void previewsEnglishStructuredTextExtractedFromDocx() {
        PrivateExamImportRequest request = request();
        request.setSourceName("paper.docx");
        request.setSourceFormat("DOCX");
        request.setContent("""
                Type: SINGLE_CHOICE
                Question: Which access order does a queue follow?
                Options:
                A. First in, first out
                B. Last in, first out
                Answer: A
                Analysis: A queue follows FIFO.
                Score: 2
                """);

        PrivateExamImportPreviewVO preview = service.previewWithSourceHash(request, "a".repeat(64));

        assertEquals(1, preview.getQuestionCount());
        assertEquals("Which access order does a queue follow?", preview.getQuestions().get(0).getContent());
        assertEquals(true, preview.getQuestions().get(0).getOptions().get(0).getCorrect());
    }

    @Test
    void rejectsConfirmationWhenSourceChangedAfterPreview() {
        PrivateExamImportConfirmRequest request = confirmRequest();
        request.setExpectedContentHash("0".repeat(64));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.confirm(request, 7L));

        assertEquals("原始资料已变化，请重新预览确认", exception.getMessage());
        verify(sourceMapper, never()).insert(any());
    }

    @Test
    void persistsConfirmedPaperQuestionsAndQuotaCheckedSourceFileAsOwnerPrivateContent() {
        PrivateExamImportRequest previewRequest = request();
        String hash = service.preview(previewRequest).getContentHash();
        PrivateExamImportConfirmRequest request = confirmRequest();
        request.setExpectedContentHash(hash);
        when(sourceMapper.insert(any())).thenAnswer(invocation -> {
            UserExamSource source = invocation.getArgument(0);
            source.setId(31L);
            return 1;
        });
        when(paperMapper.insert(any())).thenAnswer(invocation -> {
            ExamPaper paper = invocation.getArgument(0);
            paper.setId(41L);
            return 1;
        });
        when(questionMapper.insert(any())).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(100L + question.getScore());
            return 1;
        });
        ExamPaperVO created = new ExamPaperVO();
        created.setId(41L);
        when(examPaperService.getAccessiblePublishedExamPaperById(41L, 7L)).thenReturn(created);

        byte[] sourceFile = "%PDF-file".getBytes();
        request.setSourceFormat("PDF");
        service.confirmWithSourceFile(request, 7L, hash, sourceFile, "application/pdf");

        verify(sourceStorageService).attachFileWithinQuota(
                org.mockito.ArgumentMatchers.any(UserExamSource.class),
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(sourceFile),
                org.mockito.ArgumentMatchers.eq("application/pdf"));
        ArgumentCaptor<ExamPaper> paperCaptor = ArgumentCaptor.forClass(ExamPaper.class);
        verify(paperMapper).insert(paperCaptor.capture());
        assertEquals("PRIVATE", paperCaptor.getValue().getVisibility());
        assertEquals("USER_PRIVATE", paperCaptor.getValue().getPaperType());
        assertEquals(7L, paperCaptor.getValue().getOwnerUserId());
        assertEquals(31L, paperCaptor.getValue().getSourceRecordId());
        ArgumentCaptor<Question> questionCaptor = ArgumentCaptor.forClass(Question.class);
        verify(questionMapper, org.mockito.Mockito.times(2)).insert(questionCaptor.capture());
        questionCaptor.getAllValues().forEach(question -> {
            assertEquals("PRIVATE", question.getVisibility());
            assertEquals(7L, question.getOwnerUserId());
        });
    }

    private PrivateExamImportRequest request() {
        PrivateExamImportRequest request = new PrivateExamImportRequest();
        request.setTitle("我的练习卷");
        request.setCourseId(10L);
        request.setDuration(30);
        request.setSourceName("notes.txt");
        request.setSourceFormat("TEXT");
        request.setContent(content());
        return request;
    }

    private PrivateExamImportConfirmRequest confirmRequest() {
        PrivateExamImportConfirmRequest request = new PrivateExamImportConfirmRequest();
        request.setTitle("我的练习卷");
        request.setCourseId(10L);
        request.setDuration(30);
        request.setSourceName("notes.txt");
        request.setSourceFormat("TEXT");
        request.setContent(content());
        request.setConfirmed(true);
        return request;
    }

    private String content() {
        return """
                题型：单选题
                题干：先进后出的数据结构是？
                选项：
                A. 栈
                B. 队列
                答案：A
                解析：栈遵循 LIFO。
                分值：1
                ---
                题型：多选题
                题干：以下哪些属于线性结构？
                选项：
                A. 栈
                B. 队列
                C. 图
                答案：A,B
                分值：2
                """;
    }
}
