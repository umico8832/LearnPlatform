package com.learnplatform.service.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/** 固定提示夹具只验证服务端动作和工具契约，教学质量仍需人工验收真实模型输出。 */
final class AiTutorHintEvaluation {
    private static final ObjectMapper JSON = new ObjectMapper();

    private AiTutorHintEvaluation() { }

    static String toolOutput(String scenario) {
        return "TUTOR_HINT_ANSWERED".equals(scenario)
                ? "{\"status\":\"ANSWERED\",\"result\":{\"correct\":true,\"explanation\":\"服务端已判分。\"}}"
                : "{\"status\":\"AVAILABLE\"}";
    }

    static void check(String scenario, String publicOutput,
                      List<AiEvaluationFixture.ToolObservation> traces, List<String> failures) {
        if (!scenario.startsWith("TUTOR_HINT_")) { return; }
        var hints = traces.stream().filter(trace -> "request_tutor_hint".equals(trace.name())).toList();
        if (hints.isEmpty()) { failures.add("agent-request-tutor-hint"); }
        try {
            var actions = JSON.readTree(publicOutput).path("actions");
            int hintCount = 0;
            int level = 0;
            for (var action : actions) {
                if ("HINT".equals(action.path("type").asText())) {
                    hintCount++;
                    level = action.path("level").asInt();
                }
            }
            if ("TUTOR_HINT_FIRST".equals(scenario)) {
                if (hintCount != 1 || level != 1) { failures.add("agent-hint-level"); }
                if (hints.stream().noneMatch(trace -> trace.output().contains("\"level\":1"))) {
                    failures.add("agent-hint-server-level-context");
                }
            } else {
                if (hintCount != 0) { failures.add("agent-no-hint-after-answer"); }
                if (hints.stream().noneMatch(trace -> trace.output().contains("\"status\":\"ANSWERED\""))) {
                    failures.add("agent-hint-server-answer-context");
                }
            }
        } catch (Exception exception) {
            failures.add("agent-hint-public-contract");
        }
    }
}
