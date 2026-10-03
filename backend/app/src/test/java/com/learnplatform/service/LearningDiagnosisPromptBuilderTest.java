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

        assertTrue(prompt.contains("42 次"));
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

    @Test
    void userPromptExplicitlyLimitsAiJudgmentForInsufficientEvidence() {
        LearningDiagnosisVO diagnosis = new LearningDiagnosisVO();
        diagnosis.setTotalPractice(1);
        diagnosis.setOverallCorrectRate(0);
        diagnosis.setActiveDaysLast30(1);
        LearningDiagnosisVO.WeakPoint point = new LearningDiagnosisVO.WeakPoint();
        point.setKnowledgePointName("栈");
        point.setCourseName("数据结构");
        point.setCorrectRate(0);
        point.setTotalAttempts(1);
        point.setMasteryStatus("INSUFFICIENT_DATA");
        diagnosis.setWeakPoints(java.util.List.of(point));
        LearningDiagnosisVO.LearningHabit habit = new LearningDiagnosisVO.LearningHabit();
        habit.setFrequencyLevel("INSUFFICIENT_DATA");
        habit.setFrequencyDescription("近30天有1天留下练习记录，样本不足，继续积累记录后再评估学习节奏。");
        diagnosis.setLearningHabit(habit);

        String prompt = builder.userPrompt(diagnosis);

        assertTrue(prompt.contains("样本不足"));
        assertTrue(prompt.contains("不得据此评定能力、基础或长期学习习惯"));
    }


    @Test
    void userPromptUsesNeutralEvidenceTitleAndIncludesEachDiagnosisDetail() {
        LearningDiagnosisVO diagnosis = new LearningDiagnosisVO();
        LearningDiagnosisVO.WeakPoint point = new LearningDiagnosisVO.WeakPoint();
        point.setKnowledgePointName("栈");
        point.setCourseName("数据结构");
        point.setCorrectRate(0);
        point.setTotalAttempts(1);
        point.setWrongCount(1);
        point.setMasteryStatus("INSUFFICIENT_DATA");
        point.setDiagnosis("仅有 1 条已判分记录，本次未答对，暂不足以判断掌握程度。");
        diagnosis.setWeakPoints(java.util.List.of(point));

        String prompt = builder.userPrompt(diagnosis);

        assertTrue(prompt.contains("需要继续处理的知识点"));
        assertFalse(prompt.contains("## 薄弱知识点"));
        assertTrue(prompt.contains("样本=1次作答"));
        assertTrue(prompt.contains("状态=INSUFFICIENT_DATA"));
        assertTrue(prompt.contains(point.getDiagnosis()));
    }


    @Test
    void userPromptUsesNoRecordTextInsteadOfSyntheticRates() {
        LearningDiagnosisVO diagnosis = new LearningDiagnosisVO();
        diagnosis.setTotalPractice(0);
        diagnosis.setOverallCorrectRate(0);
        LearningDiagnosisVO.WeakPoint point = new LearningDiagnosisVO.WeakPoint();
        point.setKnowledgePointName("栈");
        point.setCourseName("数据结构");
        point.setCorrectRate(-1);
        point.setTotalAttempts(0);
        point.setMasteryStatus("NOT_STARTED");
        diagnosis.setWeakPoints(java.util.List.of(point));
        LearningDiagnosisVO.CourseMastery course = new LearningDiagnosisVO.CourseMastery();
        course.setCourseName("数据结构");
        course.setTotalAttempts(0);
        course.setCorrectRate(0);
        diagnosis.setCourseMasteries(java.util.List.of(course));

        String prompt = builder.userPrompt(diagnosis);

        assertTrue(prompt.contains("总正确率：暂无记录"));
        assertTrue(prompt.contains("正确率 暂无记录"));
        assertFalse(prompt.contains("-1.0%"));
        assertFalse(prompt.contains("总正确率：0.0%"));
    }

    @Test
    void userPromptKeepsObservedZeroRate() {
        LearningDiagnosisVO diagnosis = new LearningDiagnosisVO();
        diagnosis.setTotalPractice(1);
        diagnosis.setOverallCorrectRate(0);

        String prompt = builder.userPrompt(diagnosis);

        assertTrue(prompt.contains("总正确率：0.0%"));
    }

}
