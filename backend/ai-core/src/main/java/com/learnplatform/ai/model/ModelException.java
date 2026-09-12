package com.learnplatform.ai.model;

public final class ModelException extends RuntimeException {
    public enum Code { CONFIGURATION, UNSUPPORTED, UPSTREAM, TIMEOUT, CANCELLED, PROTOCOL, REFUSAL, TRUNCATED, SCHEMA }
    private final Code code;
    private final ModelResult partialResult;

    public ModelException(Code code) { this(code, null); }
    public ModelException(Code code, ModelResult partialResult) {
        super("AI_" + code.name());
        this.code = code;
        this.partialResult = partialResult;
    }
    public Code code() { return code; }
    public ModelResult partialResult() { return partialResult; }
}
