package com.learnplatform.ai.model;

public sealed interface ModelEvent {
    record TextDelta(String text) implements ModelEvent { }
    record Completed(ModelResult result) implements ModelEvent { }
}
