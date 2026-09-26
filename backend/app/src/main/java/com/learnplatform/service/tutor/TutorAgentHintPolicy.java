package com.learnplatform.service.tutor;

import com.learnplatform.ai.model.ModelRequest;
import com.learnplatform.dto.TutorAgentActionVO;

import java.util.List;
import java.util.OptionalInt;

/** 提示层级由服务端控制；展示提示不构成学习事实。 */
public final class TutorAgentHintPolicy {
    private static final int MAX_LEVEL = 3;

    private TutorAgentHintPolicy() { }

    public static OptionalInt nextLevel(List<TutorAgentHistoryMessage> history) {
        int maximum = history.stream()
                .filter(message -> message.role() == ModelRequest.Role.ASSISTANT)
                .flatMap(message -> message.actions().stream())
                .filter(action -> "HINT".equals(action.type()))
                .map(TutorAgentActionVO::level)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max().orElse(0);
        return maximum >= MAX_LEVEL ? OptionalInt.empty() : OptionalInt.of(maximum + 1);
    }

    public static String guidance(int level) {
        return switch (level) {
            case 1 -> "从本节核心概念与教学步骤中找方向，不要判断或排除选项。";
            case 2 -> "按本节教学步骤逐步推理，检查其中的不变量或条件，不要判断或排除选项。";
            case 3 -> "使用本节材料中的相似情境说明推理过程，不要给出答案或判断、排除选项。";
            default -> throw new IllegalArgumentException("Unsupported Tutor hint level");
        };
    }
}
