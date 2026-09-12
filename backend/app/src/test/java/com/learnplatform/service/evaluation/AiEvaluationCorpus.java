package com.learnplatform.service.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

record AiEvaluationCorpus(int version, String provenance, Map<String, QuestionData> questions,
                          List<Case> cases) {
    static AiEvaluationCorpus load() throws IOException {
        try (InputStream input = AiEvaluationCorpus.class.getResourceAsStream("/ai-evaluation/cases.json")) {
            if (input == null) { throw new IOException("Missing AI evaluation corpus"); }
            return new ObjectMapper().readValue(input, AiEvaluationCorpus.class);
        }
    }

    record QuestionData(String content, List<String> options, String answer, String analysis,
                        String knowledge, String course, String questionType) { }

    record Case(String id, String route, String type, String question, String category,
                String scenario, boolean online, String response, int expectedCode,
                List<String> promptContains, List<String> outputContains, List<String> outputAbsent,
                List<String> manualCriteria) { }
}
