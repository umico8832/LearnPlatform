package com.learnplatform.service;

import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.EmbeddingRequest;
import com.learnplatform.ai.model.EmbeddingResult;
import com.learnplatform.ai.model.ModelEvent;
import com.learnplatform.ai.model.ModelException;
import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.ai.model.ModelResult;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.config.AiConfig;
import com.learnplatform.dto.AiResponse;
import com.learnplatform.service.ai.AiCallContext;
import com.learnplatform.service.ai.AiCallTicket;
import com.learnplatform.service.ai.AiProvider;
import com.learnplatform.service.ai.EmbeddingProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
public class AiInvocationService {
    private final AiProvider provider;
    private final EmbeddingProvider embeddingProvider;
    private final AiCallGovernanceService governance;
    private final AiConfig config;

    @Autowired
    public AiInvocationService(AiProvider provider, EmbeddingProvider embeddingProvider,
                               AiCallGovernanceService governance, AiConfig config) {
        this.provider = provider;
        this.embeddingProvider = embeddingProvider;
        this.governance = governance;
        this.config = config;
    }

    public AiInvocationService(AiProvider provider, EmbeddingProvider embeddingProvider,
                               AiCallGovernanceService governance) {
        this(provider, embeddingProvider, governance, null);
    }

    public AiInvocationService(AiProvider provider, AiCallGovernanceService governance) {
        this(provider, null, governance, null);
    }

    public AiResponse call(String function, Long userId, AiService.AiPrompt prompt) {
        return new AiResponse(text(function, userId, prompt), "ai");
    }

    public String text(String function, Long userId, AiService.AiPrompt prompt) {
        return text(function, userId, prompt, Function.identity());
    }

    public <T> T text(String function, Long userId, AiService.AiPrompt prompt, Function<String, T> consume) {
        ModelRequest request = ModelRequest.text(prompt.systemPrompt(), prompt.userPrompt(), provider.defaultOptions());
        return business(() -> invoke(new AiCallContext(userId, function, null), request, null, new Cancellation(),
                result -> consume.apply(result.requireCompleteText())));
    }

    public void stream(String function, Long userId, AiService.AiPrompt prompt, Consumer<String> consumer) {
        ModelRequest request = ModelRequest.text(prompt.systemPrompt(), prompt.userPrompt(), provider.defaultOptions());
        business(() -> invoke(new AiCallContext(userId, function, null), request, event -> {
            if (event instanceof ModelEvent.TextDelta delta) {
                consumer.accept(delta.text());
            }
        }, new Cancellation(), ModelResult::requireCompleteText));
    }

    public ModelResult generate(AiCallContext context, ModelRequest request, Cancellation cancellation) {
        return invoke(context, request, null, cancellation, Function.identity());
    }

    public <T> T generate(AiCallContext context, ModelRequest request, Cancellation cancellation,
                          Function<ModelResult, T> consume) {
        return invoke(context, request, null, cancellation, consume);
    }

    public ModelRequest.Options defaultOptions() {
        return provider.defaultOptions();
    }

    public ModelResult generateStream(AiCallContext context, ModelRequest request,
                                      Consumer<ModelEvent> consumer, Cancellation cancellation) {
        return invoke(context, request, consumer, cancellation, Function.identity());
    }

    public EmbeddingRequest embeddingRequest(java.util.List<String> inputs) {
        if (config == null || !config.getEmbedding().isEnabled()) {
            throw new ModelException(ModelException.Code.CONFIGURATION);
        }
        AiConfig.EmbeddingConfig embedding = config.getEmbedding();
        if (embedding.getModel() == null || embedding.getModel().isBlank()
                || embedding.getDimensions() != null && embedding.getDimensions() <= 0) {
            throw new ModelException(ModelException.Code.CONFIGURATION);
        }
        return new EmbeddingRequest(embedding.getModel(), inputs, embedding.getDimensions());
    }

    public EmbeddingResult embed(AiCallContext context, EmbeddingRequest request, Cancellation cancellation) {
        if (embeddingProvider == null) {
            throw new ModelException(ModelException.Code.CONFIGURATION);
        }
        ModelResult auditResult = null;
        AiCallTicket ticket = null;
        String outcome = "FAILED";
        long start = System.nanoTime();
        try {
            cancellation.check();
            embeddingProvider.validate(request);
            ticket = governance.beginEmbedding(context, request);
            EmbeddingResult result = embeddingProvider.embed(request, cancellation);
            auditResult = result.auditResult();
            outcome = "SUCCEEDED";
            return result;
        } catch (ModelException exception) {
            outcome = exception.code().name();
            auditResult = exception.partialResult();
            throw exception;
        } finally {
            if (ticket != null) {
                governance.finish(ticket, auditResult, outcome,
                        (System.nanoTime() - start) / 1_000_000);
            }
        }
    }

    private <T> T invoke(AiCallContext context, ModelRequest request, Consumer<ModelEvent> events,
                          Cancellation cancellation, Function<ModelResult, T> consume) {
        ModelResult result = null;
        AiCallTicket ticket = null;
        String outcome = "FAILED";
        long start = System.nanoTime();
        try {
            cancellation.check();
            provider.validate(request);
            ticket = governance.begin(context, request);
            result = events == null ? provider.complete(request, cancellation) : provider.stream(request, event -> {
                if (!(event instanceof ModelEvent.Completed)) {
                    events.accept(event);
                }
            }, cancellation);
            T value = consume.apply(result);
            if (events != null) {
                events.accept(new ModelEvent.Completed(result));
            }
            outcome = switch (result.finish()) {
                case REFUSAL -> "REFUSAL";
                case LENGTH -> "TRUNCATED";
                default -> "SUCCEEDED";
            };
            return value;
        } catch (ModelException exception) {
            outcome = exception.code().name();
            if (exception.partialResult() != null) {
                result = exception.partialResult();
            }
            throw exception;
        } finally {
            if (ticket != null) {
                governance.finish(ticket, result, outcome, (System.nanoTime() - start) / 1_000_000);
            }
        }
    }

    private <T> T business(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ModelException exception) {
            String message = switch (exception.code()) {
                case CONFIGURATION -> "AI 服务尚未配置完成";
                case UNSUPPORTED -> "当前 AI 模型不支持此功能";
                case TIMEOUT -> "AI 响应超时，请稍后重试";
                case CANCELLED -> "AI 请求已取消";
                case REFUSAL -> "AI 未能提供此内容，请调整问题后重试";
                case TRUNCATED -> "AI 回答未完整生成，请重试";
                case SCHEMA -> "AI 生成内容未通过校验，请重试";
                case PROTOCOL -> "AI 服务返回不完整，请重试";
                default -> "AI 服务暂时不可用，请稍后重试";
            };
            throw new BusinessException(ResultCode.BUSINESS_ERROR, message);
        }
    }

    public AiResponse callUnlogged(AiService.AiPrompt prompt) {
        throw new BusinessException(ResultCode.UNAUTHORIZED, "AI 调用需要用户身份");
    }

    public void streamUnlogged(AiService.AiPrompt prompt, Consumer<String> onContent) {
        throw new BusinessException(ResultCode.UNAUTHORIZED, "AI 调用需要用户身份");
    }
}
