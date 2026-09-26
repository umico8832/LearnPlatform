package com.learnplatform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TutorAgentPracticeVO(TutorAgentPracticeQuestionVO question, PracticeResultVO result) { }
