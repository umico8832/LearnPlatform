package com.learnplatform.service;

import com.learnplatform.dto.QuestionVO;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamQuestion;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.PracticeRecordMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.mapper.UserFavoriteQuestionMapper;
import com.learnplatform.mapper.WrongQuestionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionAnswerExposureTest {
    private final QuestionMapper questions = mock(QuestionMapper.class);
    private final QuestionOptionMapper options = mock(QuestionOptionMapper.class);
    private final CourseMapper courses = mock(CourseMapper.class);
    private final KnowledgePointMapper points = mock(KnowledgePointMapper.class);
    private final QuestionKnowledgePointMapper links = mock(QuestionKnowledgePointMapper.class);
    private Question question;
    private QuestionOption answer;

    @BeforeEach
    void setUp() {
        question = new Question();
        question.setId(10L);
        question.setCourseId(1L);
        question.setStatus(1);
        question.setVisibility("PUBLIC");
        question.setDifficulty(3);
        answer = new QuestionOption();
        answer.setContent("private reference answer");
        answer.setIsCorrect(1);
        answer.setOptionLabel("A");
        when(questions.selectById(10L)).thenReturn(question);
        when(questions.selectList(any())).thenReturn(List.of(question));
        when(options.selectList(any())).thenReturn(List.of(answer));
        when(links.selectList(any())).thenReturn(List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FILL_BLANK", "SHORT_ANSWER"})
    void examPreviewOmitsAnswerBearingOptionsButAdminKeepsThem(String type) {
        question.setQuestionType(type);
        var papers = mock(ExamPaperMapper.class);
        var paperQuestions = mock(ExamQuestionMapper.class);
        var paper = new ExamPaper();
        paper.setId(1L);
        paper.setStatus(1);
        paper.setVisibility("PUBLIC");
        var relation = new ExamQuestion();
        relation.setQuestionId(10L);
        when(papers.selectById(1L)).thenReturn(paper);
        when(paperQuestions.selectList(any())).thenReturn(List.of(relation));
        var service = new ExamPaperViewService(papers, paperQuestions, questions, options, courses);

        assertTrue(service.getAccessiblePublishedById(1L, 7L).getQuestions().getFirst().getOptions().isEmpty());
        assertEquals(answer.getContent(), service.getPublicById(1L).getQuestions()
                .getFirst().getOptions().getFirst().getContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FILL_BLANK", "SHORT_ANSWER", "SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE"})
    void learnerQueriesExposeOnlyDisplayOptions(String type) {
        question.setQuestionType(type);
        var viewService = new QuestionViewService(options, links, courses, points);
        QuestionVO detail = QuestionVO.fromEntity(question);
        viewService.enrichForUser(detail);
        var practice = new PracticeQuestionQueryService(questions, options, links, courses, points,
                mock(WrongQuestionMapper.class), mock(UserFavoriteQuestionMapper.class));
        var adaptive = new AdaptivePracticeService(questions, options, links,
                mock(PracticeRecordMapper.class), courses, points);

        for (QuestionVO view : List.of(detail,
                practice.getPracticeQuestions(1L, null, type, null, 1).getFirst(),
                adaptive.getAdaptiveQuestions(7L, 1L, null, type, 1).getFirst())) {
            if ("FILL_BLANK".equals(type) || "SHORT_ANSWER".equals(type)) {
                assertTrue(view.getOptions().isEmpty(), type + " must not expose reference answer options");
            } else {
                assertEquals(answer.getContent(), view.getOptions().getFirst().getContent());
                assertTrue(view.getOptions().stream().noneMatch(option -> Integer.valueOf(1).equals(option.getIsCorrect())));
            }
            assertNull(view.getAnalysis());
        }
    }
}
