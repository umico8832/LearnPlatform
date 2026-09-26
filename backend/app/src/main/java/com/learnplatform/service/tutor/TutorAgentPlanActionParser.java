package com.learnplatform.service.tutor;

import com.learnplatform.ai.model.JsonContract;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.dto.TutorAgentActionVO;
import com.learnplatform.dto.TutorAgentPlanStepVO;

import java.util.ArrayList;

/** Accepts only the server tool's bounded target projection, never a model-written plan. */
final class TutorAgentPlanActionParser {
    private TutorAgentPlanActionParser() { }

    static TutorAgentActionVO parse(String output) {
        var result = JsonContract.parse(output);
        if (result.isObject() && result.size() == 1
                && "UNAVAILABLE".equals(result.path("status").asText())) {
            return null;
        }
        var action = result.path("action");
        var steps = action.path("steps");
        if (!result.isObject() || result.size() != 2 || !"AVAILABLE".equals(result.path("status").asText())
                || !action.isObject() || action.size() != 2 || !"PLAN".equals(action.path("type").asText())
                || !steps.isArray() || steps.isEmpty() || steps.size() > 3) {
            throw invalid();
        }
        var selected = new ArrayList<TutorAgentPlanStepVO>();
        for (var step : steps) {
            String type = step.path("type").asText();
            boolean knowledgePoint = "TUTOR".equals(type) || "COURSE_SEQUENCE".equals(type);
            var target = step.path(knowledgePoint ? "knowledgePointId" : "questionId");
            if (!step.isObject() || step.size() != 4 || !step.path("type").isTextual()
                    || !step.path("title").isTextual() || !step.path("reason").isTextual()
                    || !target.isIntegralNumber() || !target.canConvertToLong() || target.asLong() <= 0) {
                throw invalid();
            }
            try {
                selected.add(new TutorAgentPlanStepVO(type, step.path("title").asText(), step.path("reason").asText(),
                        knowledgePoint ? target.asLong() : null, knowledgePoint ? null : target.asLong()));
            } catch (IllegalArgumentException exception) {
                throw invalid();
            }
        }
        try {
            return new TutorAgentActionVO("PLAN", null, null, selected);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static ModelException invalid() {
        return new ModelException(ModelException.Code.PROTOCOL);
    }
}
