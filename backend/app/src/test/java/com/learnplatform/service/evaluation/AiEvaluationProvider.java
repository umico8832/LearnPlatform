package com.learnplatform.service.evaluation;

import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.ModelEvent;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.config.AiConfig;
import com.learnplatform.service.ai.AiProvider;
import com.learnplatform.service.ai.AiTokenUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class AiEvaluationProvider implements AiProvider {
    private final AiProvider delegate;
    private final AiEvaluationCorpus.Case sample;
    private final AiConfig config;
    private ModelResult lastResult;
    final ArrayList<ModelRequest> requests = new ArrayList<>();
    String systemPrompt;
    String userPrompt;
    String response = "";
    int calls;

    AiEvaluationProvider(AiProvider delegate, AiEvaluationCorpus.Case sample, AiConfig config) {
        this.delegate = delegate;
        this.sample = sample;
        this.config = config;
    }
    @Override public ModelRequest.Options defaultOptions() {
        return new ModelRequest.Options(config.getModel(), config.getMaxTokens(), config.getTemperature());
    }
    @Override public void validate(ModelRequest request) {
        if (delegate != null) { delegate.validate(request); }
    }
    @Override public ModelResult complete(ModelRequest request, Cancellation cancellation) {
        capture(request);
        if (delegate != null) {
            lastResult = delegate.complete(request, cancellation);
        } else {
            failIfRequested();
            lastResult = scripted();
        }
        response = lastResult.text();
        return lastResult;
    }
    @Override public ModelResult stream(ModelRequest request, Consumer<ModelEvent> events, Cancellation cancellation) {
        capture(request);
        Consumer<ModelEvent> capture = event -> {
            if (event instanceof ModelEvent.TextDelta delta) { response += delta.text(); }
            events.accept(event);
        };
        if (delegate != null) {
            lastResult = delegate.stream(request, capture, cancellation);
        } else {
            capture.accept(new ModelEvent.TextDelta(sample.response()));
            failIfRequested();
            lastResult = scripted();
        }
        return lastResult;
    }
    AiTokenUsage usage() {
        if (lastResult == null || lastResult.usage() == null) { return null; }
        var usage = lastResult.usage();
        return new AiTokenUsage(usage.inputTokens(), usage.outputTokens(), usage.totalTokens());
    }
    private ModelResult scripted() {
        if ("AGENT".equals(sample.route()) && calls == 1) {
            var toolCalls = new ArrayList<ModelRequest.ToolCall>();
            toolCalls.add(new ModelRequest.ToolCall("lesson-1", "read_tutor_lesson", "{}"));
            if ("LEARNING_EVIDENCE".equals(sample.scenario())) {
                toolCalls.add(new ModelRequest.ToolCall("evidence-1", "read_learning_evidence", "{}"));
            }
            return new ModelResult("", toolCalls, config.getModel(), null,
                    ModelResult.Finish.TOOL_CALLS, null);
        }
        return new ModelResult(sample.response(), List.of(), config.getModel(), null,
                ModelResult.Finish.STOP, null);
    }
    private void capture(ModelRequest request) {
        calls++;
        requests.add(request);
        lastResult = null;
        response = "";
        systemPrompt = request.messages().get(0).content();
        userPrompt = request.messages().get(1).content();
    }
    private void failIfRequested() {
        if ("UPSTREAM_ERROR".equals(sample.scenario())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "evaluation upstream unavailable");
        }
    }
}
