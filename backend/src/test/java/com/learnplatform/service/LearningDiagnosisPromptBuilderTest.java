package com.learnplatform.service;

import com.learnplatform.dto.LearningDiagnosisVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LearningDiagnosisPromptBuilderTest {

    private final LearningDiagnosisPromptBuilder builder = new LearningDiagnosisPromptBuilder();

    @Test
    void systemPromptContainsCoreInstructions() {
        String prompt = builder.systemPrompt();

        assertTrue(prompt.contains("AI 学习顾问"));
        assertTrue(prompt.contains("Markdown"));
        assertTrue(prompt.contains("中文回复"));
    }

    @Test
    void userPromptIncludesBasicLearningData() {
        LearningDiagnosisVO diagnosis = new LearningDiagnosisVO();
        diagnosis.setTotalPractice(42);
        diagnosis.setOverallCorrectRate(85.5);
        diagnosis.setStreakDays(7);
        diagnosis.setActiveDaysLast30(12);

        String prompt = builder.userPrompt(diagnosis);

        assertTrue(prompt.contains("42 道"));
        assertTrue(prompt.contains("85.5%"));
        assertTrue(prompt.contains("7 天"));
        assertTrue(prompt.contains("12 天"));
    }

    @Test
    void userPromptHandlesEmptyDiagnosisWithoutThrowing() {
        LearningDiagnosisVO diagnosis = new LearningDiagnosisVO();

        String prompt = builder.userPrompt(diagnosis);

        assertNotNull(prompt);
        assertTrue(prompt.contains("请基于以上数据，给出个性化的学习建议。"));
    }
}
