package com.learnplatform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TutorSessionNoteVO(String sessionKey, long revision, String note, LocalDateTime updatedAt,
                                 TutorSessionNoteSourceVO source) { }
