package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.dto.QuestionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class AdaptivePracticeServiceIntegrationTest extends IntegrationTestBase {
    private static final long COURSE = 971000;
    private static final long NEW_USER = 971001;
    private static final long SUCCESSFUL_USER = 971002;
    private static final long STRUGGLING_USER = 971003;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private AdaptivePracticeService adaptivePracticeService;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO course (id,name) VALUES (?, '自适应难度测试课程')", COURSE);
        for (int difficulty = 1; difficulty <= 5; difficulty++) {
            long questionId = COURSE + 10 + difficulty;
            jdbc.update("""
                    INSERT INTO question (id,content,question_type,course_id,difficulty,visibility)
                    VALUES (?,'测试题','SINGLE_CHOICE',?,?,'PUBLIC')
                    """, questionId, COURSE, difficulty);
            jdbc.update("""
                    INSERT INTO question_option (question_id,content,option_label,is_correct)
                    VALUES (?,'正确答案','A',1)
                    """, questionId);
        }
    }

    @Test
    void firstQuestionStartsEasyAndAvailableQuestionsStayInPriorityOrder() {
        List<QuestionVO> one = adaptivePracticeService.getAdaptiveQuestions(NEW_USER, COURSE, null, null, 1);
        List<QuestionVO> group = adaptivePracticeService.getAdaptiveQuestions(NEW_USER, COURSE, null, null, 5);

        assertEquals(2, one.getFirst().getDifficulty());
        assertEquals(2, group.getFirst().getDifficulty());
        assertEquals(5, group.size());
        assertEquals(List.of(2, 1, 3), group.subList(0, 3).stream().map(QuestionVO::getDifficulty).toList());
    }

    @Test
    void aFewGradedAnswersMoveTheStartingDifficultyWithoutChangingGrading() {
        long mediumQuestion = COURSE + 12;
        for (int index = 0; index < 3; index++) {
            jdbc.update("""
                    INSERT INTO practice_record (user_id,question_id,user_answer,is_correct)
                    VALUES (?,?,?,1)
                    """, SUCCESSFUL_USER, mediumQuestion, "A");
            jdbc.update("""
                    INSERT INTO practice_record (user_id,question_id,user_answer,is_correct)
                    VALUES (?,?,?,0)
                    """, STRUGGLING_USER, mediumQuestion, "B");
        }
        jdbc.update("""
                INSERT INTO practice_record (user_id,question_id,user_answer,is_correct)
                VALUES (?,?,'   ',0)
                """, STRUGGLING_USER, mediumQuestion);

        assertEquals(3, adaptivePracticeService.getAdaptiveQuestions(
                SUCCESSFUL_USER, COURSE, null, null, 1).getFirst().getDifficulty());
        assertEquals(1, adaptivePracticeService.getAdaptiveQuestions(
                STRUGGLING_USER, COURSE, null, null, 1).getFirst().getDifficulty());
        Map<String, Object> summary = adaptivePracticeService.getAdaptiveSummary(STRUGGLING_USER);
        assertEquals(3, summary.get("totalAnswered"));
        assertTrue((double) summary.get("recommendedDifficulty") < 2.2);
    }
}
