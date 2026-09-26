package com.learnplatform.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TutorSessionNoteUpdateRequest(@NotNull @Min(0) Long revision, @NotBlank @Size(max = 500) String note) { }
