package com.learnplatform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class QuestionAskResponse {
    private QuestionAskResponse() { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Status(boolean available, String reason, int questionRemaining, Integer dailyRemaining,
                         List<Entry> records) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Entry(String requestId, long attemptId, String message, String content, String status,
                        Integer errorCode, LocalDateTime createdAt, Object suggestion) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Done(String requestId, int questionRemaining, Integer dailyRemaining,
                       Map<String, Object> suggestion) { }
}
