package com.learnplatform.service.exam;

import com.learnplatform.entity.Question;
import com.learnplatform.service.AiExamGenerationService.SmartExamRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiExamQuestionSelectionServiceTest {

    private final AiExamQuestionSelectionService service = new AiExamQuestionSelectionService();

    @Test
    void selectCoversEachKnowledgePointBeforeFillingRemainingQuestions() {
        Question first = question(1L, 1);
        Question second = question(2L, 3);
        Question third = question(3L, 5);
        SmartExamRequest request = new SmartExamRequest();
        request.setDifficultyMode("BALANCED");

        List<Long> selected = service.select(List.of(first, second, third),
                Map.of(1L, List.of(10L), 2L, List.of(20L)), Set.of(), Map.of(), request, 2);

        assertEquals(2, selected.size());
        assertTrue(selected.containsAll(List.of(1L, 2L)));
    }

    @Test
    void hardModeKeepsTheOriginalDifficultyTargetBeforeFallbackSelection() {
        SmartExamRequest request = new SmartExamRequest();
        request.setDifficultyMode("HARD");
        List<Question> questions = List.of(question(1L, 1), question(2L, 1), question(3L, 1),
                question(4L, 5), question(5L, 5), question(6L, 5));

        List<Long> selected = service.select(questions, Map.of(), Set.of(), Map.of(1, 0.6), request, 3);

        assertEquals(3, selected.size());
        assertTrue(selected.containsAll(List.of(4L, 5L, 6L)));
    }

    private Question question(Long id, int difficulty) {
        Question question = new Question();
        question.setId(id);
        question.setDifficulty(difficulty);
        return question;
    }
}
