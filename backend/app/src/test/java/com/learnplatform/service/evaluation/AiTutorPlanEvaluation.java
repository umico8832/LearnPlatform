package com.learnplatform.service.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.entity.AiCallLog;

import java.util.List;

/** 固定计划夹具核对服务端动作和确认状态，不评价真实模型的计划质量。 */
final class AiTutorPlanEvaluation {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String STEPS = "[{\"type\":\"TUTOR\",\"title\":\"回看栈顶\","
            + "\"reason\":\"先巩固当前知识点\",\"knowledgePointId\":41}]";

    private AiTutorPlanEvaluation() {
    }

    static String proposalOutput(String scenario) {
        if ("TUTOR_PLAN_PROPOSED".equals(scenario)) {
            return "{\"status\":\"AVAILABLE\",\"action\":{\"type\":\"PLAN\",\"steps\":" + STEPS + "}}";
        }
        if ("TUTOR_PLAN_UNAVAILABLE".equals(scenario)) {
            return "{\"status\":\"UNAVAILABLE\"}";
        }
        throw new IllegalArgumentException("Unexpected plan proposal scenario");
    }

    static String stateOutput(String scenario) {
        if ("TUTOR_PLAN_CONFIRMED".equals(scenario)) {
            return "{\"status\":\"CONFIRMED\",\"plan\":{\"steps\":" + STEPS
                    + ",\"confirmed\":true,\"confirmedAt\":\"2026-09-26T10:00:00\",\"available\":true}}";
        }
        if ("TUTOR_PLAN_SELF_CLAIM".equals(scenario)) {
            return "{\"status\":\"PROPOSED\",\"plan\":{\"steps\":" + STEPS
                    + ",\"confirmed\":false,\"confirmedAt\":null,\"available\":true}}";
        }
        if ("TUTOR_PLAN_NONE".equals(scenario)) {
            return "{\"status\":\"NONE\"}";
        }
        throw new IllegalArgumentException("Unexpected plan state scenario");
    }

    static void check(String scenario, String publicOutput,
                      List<AiEvaluationFixture.ToolObservation> traces,
                      List<AiCallLog> logs, List<String> failures) {
        if (!scenario.startsWith("TUTOR_PLAN_")) {
            return;
        }
        if ("TUTOR_PLAN_PROPOSED".equals(scenario)) {
            checkProposed(publicOutput, traces, failures);
            return;
        }
        if ("TUTOR_PLAN_UNAVAILABLE".equals(scenario)) {
            checkUnavailable(publicOutput, traces, failures);
            return;
        }
        checkState(scenario, traces, logs, failures);
    }

    private static void checkProposed(String publicOutput, List<AiEvaluationFixture.ToolObservation> traces,
                                      List<String> failures) {
        var proposals = named(traces, "propose_tutor_plan");
        if (proposals.size() != 1 || !proposalOutput("TUTOR_PLAN_PROPOSED").equals(proposals.getFirst().output())) {
            failures.add("agent-plan-proposal-output");
        }
        try {
            JsonNode action = planAction(publicOutput);
            if (!action.path("steps").equals(JSON.readTree(STEPS))) {
                failures.add("agent-plan-public-steps");
            }
        } catch (Exception exception) {
            failures.add("agent-plan-public-steps");
        }
    }

    private static void checkUnavailable(String publicOutput, List<AiEvaluationFixture.ToolObservation> traces,
                                         List<String> failures) {
        var proposals = named(traces, "propose_tutor_plan");
        if (proposals.size() != 1 || !proposalOutput("TUTOR_PLAN_UNAVAILABLE").equals(proposals.getFirst().output())) {
            failures.add("agent-plan-unavailable-output");
        }
        try {
            if (!planAction(publicOutput).isEmpty()) {
                failures.add("agent-plan-no-action-when-unavailable");
            }
        } catch (Exception exception) {
            failures.add("agent-plan-no-action-when-unavailable");
        }
    }

    private static void checkState(String scenario, List<AiEvaluationFixture.ToolObservation> traces,
                                   List<AiCallLog> logs, List<String> failures) {
        var states = named(traces, "read_tutor_plan_state");
        if (states.size() != 1) {
            failures.add("agent-plan-state-tool");
            return;
        }
        var state = states.getFirst();
        if (state.runId() == null || logs.isEmpty()
                || logs.stream().anyMatch(log -> !state.runId().toString().equals(log.getRunId()))) {
            failures.add("agent-plan-state-trusted-run");
        }
        if (!stateOutput(scenario).equals(state.output())) {
            failures.add("agent-plan-state-server-output");
        }
        if ("TUTOR_PLAN_CONFIRMED".equals(scenario)
                && (!state.output().contains("\"confirmed\":true") || !state.output().contains("\"available\":true"))) {
            failures.add("agent-plan-confirmed-state");
        }
        if ("TUTOR_PLAN_SELF_CLAIM".equals(scenario)
                && (!state.output().contains("\"status\":\"PROPOSED\"")
                || !state.output().contains("\"confirmed\":false")
                || !state.output().contains("\"confirmedAt\":null"))) {
            failures.add("agent-plan-no-server-confirmation");
        }
        if ("TUTOR_PLAN_NONE".equals(scenario) && !"{\"status\":\"NONE\"}".equals(state.output())) {
            failures.add("agent-plan-none-state");
        }
    }

    private static List<AiEvaluationFixture.ToolObservation> named(
            List<AiEvaluationFixture.ToolObservation> traces, String name) {
        return traces.stream().filter(trace -> name.equals(trace.name())).toList();
    }

    private static JsonNode planAction(String publicOutput) throws Exception {
        for (JsonNode action : JSON.readTree(publicOutput).path("actions")) {
            if ("PLAN".equals(action.path("type").asText())) {
                return action;
            }
        }
        return JSON.createObjectNode();
    }
}
