package com.learnplatform.service.evaluation;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.service.ai.AiProvider;
import com.learnplatform.service.ai.AiTokenUsage;

import java.util.function.Consumer;

final class AiEvaluationProvider implements AiProvider {
    private final AiProvider delegate;
    private final AiEvaluationCorpus.Case sample;
    String systemPrompt;
    String userPrompt;
    String response = "";
    int calls;

    AiEvaluationProvider(AiProvider delegate, AiEvaluationCorpus.Case sample) {
        this.delegate = delegate;
        this.sample = sample;
    }

    @Override
    public String chat(String system, String user) {
        capture(system, user);
        if (delegate != null) {
            response = delegate.chat(system, user);
        } else {
            failIfRequested();
            response = sample.response();
        }
        return response;
    }

    @Override
    public void chatStream(String system, String user, Consumer<String> onContent) {
        capture(system, user);
        Consumer<String> capture = chunk -> {
            response += chunk;
            onContent.accept(chunk);
        };
        if (delegate != null) {
            delegate.chatStream(system, user, capture);
        } else {
            capture.accept(sample.response());
            failIfRequested();
        }
    }

    @Override
    public AiTokenUsage getLastTokenUsage() {
        return delegate == null ? null : delegate.getLastTokenUsage();
    }

    private void capture(String system, String user) {
        calls++;
        systemPrompt = system;
        userPrompt = user;
    }

    private void failIfRequested() {
        if ("UPSTREAM_ERROR".equals(sample.scenario())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "evaluation upstream unavailable");
        }
    }
}
