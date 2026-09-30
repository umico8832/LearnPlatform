package com.learnplatform.service.gamification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GamificationRulesTest {

    @Test
    void awardsMeaningfulButBoundedXpForFirstGradedAnswers() {
        assertEquals(10, GamificationRules.answerXp(true, "PRACTICE_ANSWERED"));
        assertEquals(2, GamificationRules.answerXp(false, "PRACTICE_ANSWERED"));
        assertEquals(14, GamificationRules.answerXp(true, "REVIEW_ANSWERED"));
    }

    @Test
    void levelCurveGivesEarlyFeedbackWithoutClaimingMastery() {
        assertEquals(1, GamificationRules.levelFor(0));
        assertEquals(2, GamificationRules.levelFor(100));
        assertEquals(3, GamificationRules.levelFor(250));
    }
}
