package com.learnplatform.service.exam;

import com.learnplatform.entity.Question;
import com.learnplatform.service.AiExamGenerationService.SmartExamPreview;
import com.learnplatform.service.AiExamGenerationService.SmartExamRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiExamPreviewPresentationServiceTest {

    private final AiExamPreviewPresentationService service = new AiExamPreviewPresentationService();

    @Test
    void createPreservesExistingPreviewValuesAndRecommendationText() {
        SmartExamRequest request = new SmartExamRequest();
        request.setCourseId(6L);
        request.setDifficultyMode("ADAPTIVE");
        request.setDuration(null);
        Question question = new Question();
        question.setId(1L);
        question.setDifficulty(3);

        SmartExamPreview preview = service.create(request, List.of(question), List.of(1L),
                Map.of(1L, List.of(10L)), Map.of(10L, "集合"), Set.of(1L), Map.of(3, 0.4));

        assertEquals("智能模拟试卷（ADAPTIVE）", preview.getTitle());
        assertEquals("由 AI 智能组卷系统自动生成。题目数量：1 道。覆盖知识点：1 个。"
                + "已包含用户易错题目。难度模式：自适应。", preview.getDescription());
        assertEquals(Map.of("集合", 1), preview.getKnowledgePointDistribution());
        assertEquals(Map.of("★★★", 1), preview.getDifficultyDistribution());
        assertEquals("本次试卷包含 1 道您的易错题，建议重点复习。基于您的历史答题表现，"
                + "★★★难度正确率较低，已适当增加练习。", preview.getRecommendation());
        assertEquals(60, preview.getDuration());
        assertNull(preview.getCourseName());
    }
}
