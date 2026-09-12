package com.learnplatform.service.assessment;

import com.learnplatform.dto.CourseStageAssessmentSourceCompositionVO;
import com.learnplatform.entity.CourseStageAssessmentQuestion;

import java.util.List;

public final class CourseStageAssessmentSourceComposition {
    private CourseStageAssessmentSourceComposition() { }

    public static CourseStageAssessmentSourceCompositionVO from(List<CourseStageAssessmentQuestion> items) {
        CourseStageAssessmentSourceCompositionVO composition = new CourseStageAssessmentSourceCompositionVO();
        for (CourseStageAssessmentQuestion item : items) {
            switch (item.getSourceCategorySnapshot() == null ? "MANUAL" : item.getSourceCategorySnapshot()) {
                case "OFFICIAL_EXAM" -> composition.setOfficialExamCount(composition.getOfficialExamCount() + 1);
                case "USER_PRIVATE" -> composition.setUserPrivateCount(composition.getUserPrivateCount() + 1);
                case "AI_GENERATED" -> composition.setAiGeneratedCount(composition.getAiGeneratedCount() + 1);
                default -> composition.setManualCount(composition.getManualCount() + 1);
            }
        }
        return composition;
    }
}
