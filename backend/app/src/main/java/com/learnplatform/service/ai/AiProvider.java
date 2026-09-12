package com.learnplatform.service.ai;

import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.ModelEvent;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;

import java.util.function.Consumer;

public interface AiProvider {
    ModelRequest.Options defaultOptions();
    void validate(ModelRequest request);
    ModelResult complete(ModelRequest request, Cancellation cancellation);
    ModelResult stream(ModelRequest request, Consumer<ModelEvent> events, Cancellation cancellation);

    default String chat(String systemPrompt, String userPrompt) {
        return complete(ModelRequest.text(systemPrompt, userPrompt, defaultOptions()), new Cancellation())
                .requireCompleteText();
    }

    default void chatStream(String systemPrompt, String userPrompt, Consumer<String> onContent) {
        stream(ModelRequest.text(systemPrompt, userPrompt, defaultOptions()), event -> {
            if (event instanceof ModelEvent.TextDelta delta) {
                onContent.accept(delta.text());
            }
        }, new Cancellation()).requireCompleteText();
    }
}
