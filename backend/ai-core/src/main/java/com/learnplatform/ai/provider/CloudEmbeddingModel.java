package com.learnplatform.ai.provider;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.learnplatform.ai.model.Cancellation;
import com.learnplatform.ai.model.EmbeddingRequest;
import com.learnplatform.ai.model.EmbeddingResult;
import com.learnplatform.ai.model.ModelException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class CloudEmbeddingModel {
    private final String baseUrl;
    private final String apiKey;
    private final Duration timeout;

    public CloudEmbeddingModel(String baseUrl, String apiKey, Duration timeout) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.timeout = timeout;
    }

    public void validate(EmbeddingRequest request) {
        try {
            URI endpoint = URI.create(baseUrl);
            if (request == null || apiKey == null || apiKey.isBlank()
                    || timeout == null || timeout.isNegative() || timeout.isZero()
                    || !List.of("http", "https").contains(endpoint.getScheme()) || endpoint.getHost() == null
                    || endpoint.getUserInfo() != null || endpoint.getQuery() != null
                    || endpoint.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            HttpRequest.newBuilder(endpoint).header("Authorization", "Bearer " + apiKey).timeout(timeout);
        } catch (RuntimeException exception) {
            throw new ModelException(ModelException.Code.CONFIGURATION);
        }
    }

    public EmbeddingResult embed(EmbeddingRequest request, Cancellation cancellation) {
        validate(request);
        cancellation.check();
        var body = JsonNodeFactory.instance.objectNode().put("model", request.model())
                .put("encoding_format", "float");
        var inputs = body.putArray("input");
        request.inputs().forEach(inputs::add);
        if (request.dimensions() != null) {
            body.put("dimensions", request.dimensions());
        }
        var endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/embeddings");
        var httpRequest = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var pending = client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString());
        long started = System.nanoTime();
        try {
            while (System.nanoTime() - started < timeout.toNanos()) {
                cancellation.check();
                try {
                    var response = pending.get(50, TimeUnit.MILLISECONDS);
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new ModelException(ModelException.Code.UPSTREAM);
                    }
                    var result = EmbeddingResponseParser.parse(response.body(), request);
                    if (cancellation.isCancelled()) {
                        throw new ModelException(ModelException.Code.CANCELLED, result.auditResult());
                    }
                    return result;
                } catch (TimeoutException exception) {
                    continue;
                }
            }
            throw new ModelException(ModelException.Code.TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelException(ModelException.Code.CANCELLED);
        } catch (ExecutionException exception) {
            throw new ModelException(exception.getCause() instanceof HttpTimeoutException
                    ? ModelException.Code.TIMEOUT : ModelException.Code.UPSTREAM);
        } finally {
            pending.cancel(true);
            client.shutdownNow();
        }
    }
}
