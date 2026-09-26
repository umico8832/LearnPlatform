package com.learnplatform.dto;

import java.time.LocalDateTime;

public record TutorSessionNoteRow(Long sessionId, String sessionKey, long revision, String note,
                                  LocalDateTime updatedAt, Long knowledgePointId, String title,
                                  LocalDateTime sessionStartedAt, Boolean checkCorrect,
                                  LocalDateTime checkAnsweredAt, boolean available) { }
