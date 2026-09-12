package com.learnplatform.service.question;

import com.learnplatform.entity.Question;

public final class QuestionAccessPolicy {
    private QuestionAccessPolicy() { }

    public static boolean canAccess(Question question, Long userId) {
        if (question == null) { return false; }
        String visibility = question.getVisibility();
        return visibility == null || "PUBLIC".equals(visibility)
                || ("PRIVATE".equals(visibility) && userId != null && userId.equals(question.getOwnerUserId()));
    }

    public static boolean isPublic(Question question) {
        return question != null && (question.getVisibility() == null || "PUBLIC".equals(question.getVisibility()));
    }
}
