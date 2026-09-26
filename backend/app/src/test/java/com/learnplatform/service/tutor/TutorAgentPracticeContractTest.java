package com.learnplatform.service.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.dto.TutorAgentPracticeQuestionVO;
import com.learnplatform.dto.TutorAgentPracticeVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TutorAgentPracticeContractTest {
    @Test void explicitlyReturnsNoResultBeforeAnswerEvenWhenGlobalNullFieldsAreOmitted() throws Exception {
        ObjectMapper json = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        var response = new TutorAgentPracticeVO(
                new TutorAgentPracticeQuestionVO(51L, "问题", "SINGLE_CHOICE", List.of()), null);
        var value = json.readTree(json.writeValueAsString(response));
        assertTrue(value.has("result"));
        assertTrue(value.path("result").isNull());
    }
}
