package com.learnplatform.service.tutor;

import com.learnplatform.dto.TutorAgentActionVO;

import java.util.List;

public record TutorAgentReply(String content, List<TutorAgentActionVO> actions) {
    public TutorAgentReply {
        actions = List.copyOf(actions);
    }
}
