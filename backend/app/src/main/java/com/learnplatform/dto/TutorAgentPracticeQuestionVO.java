package com.learnplatform.dto;

import java.util.List;

public record TutorAgentPracticeQuestionVO(Long id, String content, String questionType, List<Option> options) {
    public record Option(String label, String content) { }
}
