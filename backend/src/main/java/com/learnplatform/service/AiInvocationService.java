package com.learnplatform.service;

import com.learnplatform.dto.AiResponse;
import com.learnplatform.service.ai.AiProvider;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
public class AiInvocationService {
    private final AiProvider aiProvider;
    private final AiCallGovernanceService callGovernanceService;

    public AiInvocationService(AiProvider aiProvider, AiCallGovernanceService callGovernanceService) {
        this.aiProvider = aiProvider;
        this.callGovernanceService = callGovernanceService;
    }

    public AiResponse callUnlogged(AiService.AiPrompt prompt) {
        return new AiResponse(aiProvider.chat(prompt.systemPrompt(), prompt.userPrompt()), "ai");
    }

    public void streamUnlogged(AiService.AiPrompt prompt, Consumer<String> onContent) {
        aiProvider.chatStream(prompt.systemPrompt(), prompt.userPrompt(), onContent);
    }

    public AiResponse call(String functionType, Long userId, AiService.AiPrompt prompt) {
        callGovernanceService.checkDailyQuota(userId);
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        try {
            String content = aiProvider.chat(prompt.systemPrompt(), prompt.userPrompt());
            success = true;
            return new AiResponse(content, "ai");
        } catch (RuntimeException exception) {
            errorMessage = errorMessage(exception);
            throw exception;
        } finally {
            log(userId, functionType, success, errorMessage, start, prompt);
        }
    }

    public void stream(String functionType, Long userId, AiService.AiPrompt prompt,
                       Consumer<String> onContent) {
        callGovernanceService.checkDailyQuota(userId);
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;
        try {
            aiProvider.chatStream(prompt.systemPrompt(), prompt.userPrompt(), onContent);
            success = true;
        } catch (RuntimeException exception) {
            errorMessage = errorMessage(exception);
            throw exception;
        } finally {
            log(userId, functionType, success, errorMessage, start, prompt);
        }
    }

    private void log(Long userId, String functionType, boolean success, String errorMessage,
                     long start, AiService.AiPrompt prompt) {
        int duration = (int) (System.currentTimeMillis() - start);
        callGovernanceService.logCallWithPrompt(userId, functionType, success, errorMessage, duration,
                prompt.systemPrompt(), prompt.userPrompt());
    }

    private String errorMessage(RuntimeException exception) {
        return exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
    }
}
