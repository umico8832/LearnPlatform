package com.learnplatform.dto;

public record ReviewAnswerOutcome(boolean correct, Long recordId, GamificationRewardFeedback reward) { }
