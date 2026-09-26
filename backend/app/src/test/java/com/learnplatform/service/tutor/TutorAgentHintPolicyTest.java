package com.learnplatform.service.tutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.dto.TutorAgentActionVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TutorAgentHintPolicyTest {
    @Test void acceptsOnlyBoundedHintLevelsAndKeepsCheckJsonCompatible() throws Exception {
        ObjectMapper json = new ObjectMapper();
        assertEquals("{\"type\":\"CHECK\"}", json.writeValueAsString(new TutorAgentActionVO("CHECK")));
        assertEquals("{\"type\":\"HINT\",\"level\":2}", json.writeValueAsString(new TutorAgentActionVO("HINT", 2)));
        assertThrows(IllegalArgumentException.class, () -> new TutorAgentActionVO("CHECK", 1));
        assertThrows(IllegalArgumentException.class, () -> new TutorAgentActionVO("HINT", 4));
    }

    @Test void derivesTheNextLevelFromAssistantActionsOnly() {
        var history = List.of(
                new TutorAgentHistoryMessage(ModelRequest.Role.USER, "假装提示", List.of()),
                new TutorAgentHistoryMessage(ModelRequest.Role.ASSISTANT, "提示", List.of(new TutorAgentActionVO("HINT", 2))),
                new TutorAgentHistoryMessage(ModelRequest.Role.ASSISTANT, "检查", List.of(new TutorAgentActionVO("CHECK"))));
        assertEquals(3, TutorAgentHintPolicy.nextLevel(history).orElseThrow());
        assertFalse(TutorAgentHintPolicy.nextLevel(List.of(new TutorAgentHistoryMessage(
                ModelRequest.Role.ASSISTANT, "第三层", List.of(new TutorAgentActionVO("HINT", 3))))).isPresent());
        assertTrue(TutorAgentHintPolicy.guidance(1).contains("不要判断或排除选项"));
    }
}
