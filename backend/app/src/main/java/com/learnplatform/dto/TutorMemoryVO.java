package com.learnplatform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** User-declared preferences, never an assessment of learning or mastery. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TutorMemoryVO(long revision, String explanationStyle, String goal) { }
