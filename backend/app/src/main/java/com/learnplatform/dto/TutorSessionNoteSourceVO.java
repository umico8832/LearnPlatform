package com.learnplatform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TutorSessionNoteSourceVO(boolean available, Long knowledgePointId, String title,
                                       LocalDateTime sessionStartedAt, String checkStatus,
                                       LocalDateTime checkAnsweredAt) { }
