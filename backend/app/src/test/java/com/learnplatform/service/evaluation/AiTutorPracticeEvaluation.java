package com.learnplatform.service.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.entity.AiCallLog;

import java.util.List;

/** 固定练习夹具仅核对服务端题目与结果契约，不评价真实模型教学质量。 */
final class AiTutorPracticeEvaluation {
    private static final long QUESTION_ID = 51L;
    private static final ObjectMapper JSON = new ObjectMapper();

    private AiTutorPracticeEvaluation() {
    }

    static String recommendationOutput(String scenario) {
        if ("TUTOR_PRACTICE_FOUND".equals(scenario)) {
            return "{\"status\":\"AVAILABLE\",\"action\":{\"type\":\"PRACTICE\",\"questionId\":51}}";
        }
        if ("TUTOR_PRACTICE_UNAVAILABLE".equals(scenario)) {
            return "{\"status\":\"UNAVAILABLE\"}";
        }
        throw new IllegalArgumentException("Unexpected practice recommendation scenario");
    }

    static String resultOutput(String scenario) {
        if ("TUTOR_PRACTICE_ANSWERED".equals(scenario)) {
            return "{\"status\":\"ANSWERED\",\"result\":{\"correct\":false,"
                    + "\"explanation\":\"服务端首次作答判定错误，请回看栈顶变化。\"}}";
        }
        if ("TUTOR_PRACTICE_SELF_CLAIM".equals(scenario)) {
            return "{\"status\":\"UNANSWERED\"}";
        }
        throw new IllegalArgumentException("Unexpected practice result scenario");
    }

    static void check(String scenario, String publicOutput,
                      List<AiEvaluationFixture.ToolObservation> traces,
                      List<AiCallLog> logs, List<String> failures) {
        if (!scenario.startsWith("TUTOR_PRACTICE_")) {
            return;
        }
        if ("TUTOR_PRACTICE_FOUND".equals(scenario)) {
            checkFound(publicOutput, traces, failures);
            return;
        }
        if ("TUTOR_PRACTICE_UNAVAILABLE".equals(scenario)) {
            checkUnavailable(publicOutput, traces, failures);
            return;
        }
        checkResult(scenario, traces, logs, failures);
    }

    private static void checkFound(String publicOutput, List<AiEvaluationFixture.ToolObservation> traces,
                                   List<String> failures) {
        var recommendations = traces.stream()
                .filter(trace -> "recommend_tutor_practice".equals(trace.name())).toList();
        if (recommendations.size() != 1 || !recommendations.getFirst().output().contains("\"questionId\":51")) {
            failures.add("agent-practice-recommendation-output");
        }
        try {
            JsonNode action = practiceAction(publicOutput);
            if (!"PRACTICE".equals(action.path("type").asText()) || action.path("questionId").asLong() != QUESTION_ID) {
                failures.add("agent-practice-public-question-id");
            }
        } catch (Exception exception) {
            failures.add("agent-practice-public-question-id");
        }
    }

    private static void checkUnavailable(String publicOutput, List<AiEvaluationFixture.ToolObservation> traces,
                                         List<String> failures) {
        var recommendations = traces.stream()
                .filter(trace -> "recommend_tutor_practice".equals(trace.name())).toList();
        if (recommendations.size() != 1 || !"{\"status\":\"UNAVAILABLE\"}".equals(recommendations.getFirst().output())) {
            failures.add("agent-practice-unavailable-output");
        }
        try {
            JsonNode actions = JSON.readTree(publicOutput).path("actions");
            for (JsonNode action : actions) {
                if ("PRACTICE".equals(action.path("type").asText())) {
                    failures.add("agent-practice-no-action-when-unavailable");
                    break;
                }
            }
        } catch (Exception exception) {
            failures.add("agent-practice-no-action-when-unavailable");
        }
    }

    private static void checkResult(String scenario, List<AiEvaluationFixture.ToolObservation> traces,
                                    List<AiCallLog> logs, List<String> failures) {
        var results = traces.stream()
                .filter(trace -> "read_tutor_practice_result".equals(trace.name())).toList();
        if (results.size() != 1) {
            failures.add("agent-practice-result-tool");
            return;
        }
        var result = results.getFirst();
        boolean trustedRun = result.runId() != null && logs.stream()
                .allMatch(log -> result.runId().toString().equals(log.getRunId()));
        if (!trustedRun) {
            failures.add("agent-practice-result-trusted-run");
        }
        String expected = resultOutput(scenario);
        if (!expected.equals(result.output())) {
            failures.add("agent-practice-result-server-output");
        }
        if ("TUTOR_PRACTICE_ANSWERED".equals(scenario)
                && (!result.output().contains("\"correct\":false")
                || !result.output().contains("服务端首次作答判定错误"))) {
            failures.add("agent-practice-answered-result");
        }
        if ("TUTOR_PRACTICE_SELF_CLAIM".equals(scenario)
                && !"{\"status\":\"UNANSWERED\"}".equals(result.output())) {
            failures.add("agent-practice-self-claim-not-server-result");
        }
    }

    private static JsonNode practiceAction(String publicOutput) throws Exception {
        for (JsonNode action : JSON.readTree(publicOutput).path("actions")) {
            if ("PRACTICE".equals(action.path("type").asText())) {
                return action;
            }
        }
        return JSON.createObjectNode();
    }
}
