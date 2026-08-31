package com.learnplatform.service;

import com.learnplatform.dto.LearningDiagnosisVO;
import com.learnplatform.service.ai.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/** 负责学习诊断提示词调用、流式输出和调用审计。 */
@Service
public class LearningDiagnosisAiAdviceService {

    private static final Logger log = LoggerFactory.getLogger(LearningDiagnosisAiAdviceService.class);

    private final AiProvider aiProvider;
    private final AiCallGovernanceService callGovernanceService;
    private final LearningDiagnosisPromptBuilder promptBuilder;

    public LearningDiagnosisAiAdviceService(AiProvider aiProvider,
                                            AiCallGovernanceService callGovernanceService,
                                            LearningDiagnosisPromptBuilder promptBuilder) {
        this.aiProvider = aiProvider;
        this.callGovernanceService = callGovernanceService;
        this.promptBuilder = promptBuilder;
    }

    public String generate(Long userId, LearningDiagnosisVO diagnosis) {
        String systemPrompt = promptBuilder.systemPrompt();
        String userPrompt = promptBuilder.userPrompt(diagnosis);
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            String content = aiProvider.chat(systemPrompt, userPrompt);
            success = true;
            return content;
        } catch (Exception exception) {
            log.error("AI 学习建议生成失败: userId={}", userId, exception);
            throw exception;
        } finally {
            int duration = (int) (System.currentTimeMillis() - start);
            callGovernanceService.logCall(
                    userId, "learning_advice", success,
                    success ? null : "AI 学习建议生成失败", duration);
        }
    }

    public void generateStream(Long userId, LearningDiagnosisVO diagnosis, Consumer<String> onContent) {
        String systemPrompt = promptBuilder.systemPrompt();
        String userPrompt = promptBuilder.userPrompt(diagnosis);
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            aiProvider.chatStream(systemPrompt, userPrompt, onContent);
            success = true;
        } catch (Exception exception) {
            log.error("AI 学习建议流式生成失败: userId={}", userId, exception);
            throw exception;
        } finally {
            int duration = (int) (System.currentTimeMillis() - start);
            callGovernanceService.logCall(
                    userId, "learning_advice_stream", success,
                    success ? null : "AI 学习建议流式生成失败", duration);
        }
    }
}
