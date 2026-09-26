package com.learnplatform.service.tutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.dto.TutorAgentActionVO;
import com.learnplatform.dto.TutorAgentPlanStepVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TutorAgentPlanActionParserTest {
    @Test void keepsServerTargetsAndRestoresTheSamePlanAlongsideLegacyActions() throws Exception {
        var action = TutorAgentPlanActionParser.parse("""
                {"status":"AVAILABLE","action":{"type":"PLAN","steps":[
                {"type":"TUTOR","title":"学习栈","reason":"检查尚未完成","knowledgePointId":31},
                {"type":"DUE_REVIEW","title":"复习","reason":"已有到期记录","questionId":51}]}}
                """);
        assertEquals(List.of(new TutorAgentPlanStepVO("TUTOR", "学习栈", "检查尚未完成", 31L, null),
                new TutorAgentPlanStepVO("DUE_REVIEW", "复习", "已有到期记录", null, 51L)), action.steps());
        var json = new ObjectMapper();
        assertEquals(action, json.readValue(json.writeValueAsString(action), TutorAgentActionVO.class));
        for (String legacy : List.of("{\"type\":\"CHECK\"}", "{\"type\":\"HINT\",\"level\":2}",
                "{\"type\":\"PRACTICE\",\"questionId\":51}")) {
            assertEquals(legacy, json.writeValueAsString(json.readValue(legacy, TutorAgentActionVO.class)));
        }
        assertNull(TutorAgentPlanActionParser.parse("{\"status\":\"UNAVAILABLE\"}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "[]", "{\"status\":\"UNAVAILABLE\",\"action\":{}}",
            "{\"status\":\"AVAILABLE\",\"action\":{\"type\":\"PLAN\",\"steps\":[]}}",
            "{\"status\":\"AVAILABLE\",\"action\":{\"type\":\"PLAN\",\"steps\":[null]}}"
    })
    void rejectsMalformedOrAmbiguousOutputs(String output) {
        assertEquals(ModelException.Code.PROTOCOL, assertThrows(ModelException.class,
                () -> TutorAgentPlanActionParser.parse(output)).code());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\":\"TUTOR\",\"title\":\"学习\",\"reason\":\"依据\",\"knowledgePointId\":\"31\"}",
            "{\"type\":\"TUTOR\",\"title\":\"学习\",\"reason\":\"依据\",\"knowledgePointId\":1.5}",
            "{\"type\":\"TUTOR\",\"title\":\"学习\",\"reason\":\"依据\",\"questionId\":31}",
            "{\"type\":\"TUTOR\",\"title\":\"\",\"reason\":\"依据\",\"knowledgePointId\":31}",
            "{\"type\":\"TUTOR\",\"title\":\"学习\",\"reason\":\"依据\",\"knowledgePointId\":0}",
            "{\"type\":\"DELETE\",\"title\":\"删除\",\"reason\":\"依据\",\"questionId\":31}",
            "{\"type\":\"TUTOR\",\"title\":\"学习\",\"reason\":\"依据\",\"knowledgePointId\":31,\"url\":\"/\"}"
    })
    void rejectsFabricatedTargetsAndExtraFields(String step) {
        String output = "{\"status\":\"AVAILABLE\",\"action\":{\"type\":\"PLAN\",\"steps\":[" + step + "]}}";
        assertEquals(ModelException.Code.PROTOCOL, assertThrows(ModelException.class,
                () -> TutorAgentPlanActionParser.parse(output)).code());
    }

    @Test void rejectsDuplicatesAndPlansBeyondThreeSteps() {
        String step = "{\"type\":\"TUTOR\",\"title\":\"学习\",\"reason\":\"依据\",\"knowledgePointId\":31}";
        for (int count : List.of(2, 4)) {
            String output = "{\"status\":\"AVAILABLE\",\"action\":{\"type\":\"PLAN\",\"steps\":["
                    + String.join(",", java.util.Collections.nCopies(count, step)) + "]}}";
            assertEquals(ModelException.Code.PROTOCOL, assertThrows(ModelException.class,
                    () -> TutorAgentPlanActionParser.parse(output)).code());
        }
    }
}
