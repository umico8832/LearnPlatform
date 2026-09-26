package com.learnplatform.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TutorMemoryUpdateRequest(
        @NotNull @Min(0) Long revision,
        @Pattern(regexp = "STEP_BY_STEP|CONCISE|EXAMPLES") String explanationStyle,
        @Size(max = 500) String goal) { }
