package com.learnplatform.service.understanding;

import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.service.AiInvocationService;
import com.learnplatform.service.ai.AiCallContext;
import org.springframework.stereotype.Service;

@Service
public class QuestionAskAnswerService {
    private final AiInvocationService ai;

    public QuestionAskAnswerService(AiInvocationService ai) { this.ai = ai; }

    public String answer(AiCallContext context, QuestionAskMaterials.Snapshot materials, String message,
                          Cancellation cancellation, Runnable check, Runnable progress) {
        for (int round = 0; round < 2; round++) {
            check.run();
            var request = QuestionAskPolicy.request(materials.generation(), message, materials.unlocked(),
                    round > 0, ai.defaultOptions());
            String answer = ai.generateStream(context, request, event -> progress.run(), cancellation,
                    result -> result.requireCompleteText());
            check.run();
            if (!materials.unlocked()
                    && !QuestionAskPolicy.rulesPass(answer, materials.correctLabel(), materials.correctContent())) {
                continue;
            }
            var judge = QuestionAskPolicy.judgment(materials.judgment(), message, answer,
                    materials.unlocked(), ai.defaultOptions());
            boolean safe = judge(context, judge, cancellation, progress);
            check.run();
            if (safe) { return answer; }
        }
        return QuestionAskPolicy.FALLBACK;
    }

    private boolean judge(AiCallContext context, ModelRequest request, Cancellation cancellation, Runnable progress) {
        try {
            return ai.generateStream(context, request, event -> progress.run(), cancellation,
                    result -> QuestionAskPolicy.judgmentPass(result.requireCompleteText()));
        } catch (ModelException exception) {
            ModelResult partial = exception.partialResult();
            if (exception.code() == ModelException.Code.SCHEMA || exception.code() == ModelException.Code.TRUNCATED
                    || exception.code() == ModelException.Code.REFUSAL
                    || exception.code() == ModelException.Code.PROTOCOL && partial != null
                    && partial.finish() == ModelResult.Finish.STOP
                    && (partial.text() == null || partial.text().isBlank())) {
                return false;
            }
            throw exception;
        }
    }
}
