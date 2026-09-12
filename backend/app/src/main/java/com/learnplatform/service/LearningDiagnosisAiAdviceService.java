package com.learnplatform.service;

import com.learnplatform.dto.LearningDiagnosisVO;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
public class LearningDiagnosisAiAdviceService {
    private final AiInvocationService invocationService;
    private final LearningDiagnosisPromptBuilder promptBuilder;

    public LearningDiagnosisAiAdviceService(AiInvocationService invocationService,
                                            LearningDiagnosisPromptBuilder promptBuilder) {
        this.invocationService = invocationService;
        this.promptBuilder = promptBuilder;
    }

    public String generate(Long userId, LearningDiagnosisVO diagnosis) {
        return invocationService.text("learning_advice", userId, prompt(diagnosis));
    }

    public void generateStream(Long userId, LearningDiagnosisVO diagnosis, Consumer<String> onContent) {
        invocationService.stream("learning_advice_stream", userId, prompt(diagnosis), onContent);
    }

    private AiService.AiPrompt prompt(LearningDiagnosisVO diagnosis) {
        return new AiService.AiPrompt(promptBuilder.systemPrompt(), promptBuilder.userPrompt(diagnosis));
    }
}
