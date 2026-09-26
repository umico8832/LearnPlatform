package com.learnplatform.dto;

/** Minimal persisted question projection used by Tutor practice query mappers. */
public record TutorAgentPracticeQuestionRow(Long id, String content, String questionType) {
}
