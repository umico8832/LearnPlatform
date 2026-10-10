package com.learnplatform.entity;

import java.time.LocalDateTime;

public record QuestionUnderstandingAttempt(
        Long id,
        Long userId,
        Long questionId,
        Long contentId,
        Long variantId,
        Long practiceRecordId,
        boolean hintChainEnabled,
        LocalDateTime solutionRevealedTime) { }
