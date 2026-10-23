package com.learnplatform.service.tutor;

import tools.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.ObjectMapper;
import com.learnplatform.dto.TutorAgentPracticeQuestionVO;
import com.learnplatform.dto.TutorAgentPracticeVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TutorAgentPracticeContractTest {
    @Test void explicitlyReturnsNoResultBeforeAnswerEvenWhenGlobalNullFieldsAreOmitted() throws Exception {
        ObjectMapper json = JsonMapper.builderWithJackson2Defaults()
                .changeDefaultPropertyInclusion(value -> value.withValueInclusion(JsonInclude.Include.NON_NULL)).build();
        var response = new TutorAgentPracticeVO(
                new TutorAgentPracticeQuestionVO(51L, "问题", "SINGLE_CHOICE", List.of()), null);
        var value = json.readTree(json.writeValueAsString(response));
        assertTrue(value.has("result"));
        assertTrue(value.path("result").isNull());
    }
}
