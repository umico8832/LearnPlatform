package com.learnplatform.controller;

import tools.jackson.databind.JsonNode;
import com.learnplatform.common.result.R;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.understanding.QuestionUnderstandingPreviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/understanding/preview")
public class QuestionUnderstandingPreviewController {
    private final QuestionUnderstandingPreviewService service;

    public QuestionUnderstandingPreviewController(QuestionUnderstandingPreviewService service) {
        this.service = service;
    }

    @PostMapping("/questions/{questionId}")
    public R<Map<String, Object>> preview(@PathVariable long questionId, @RequestBody JsonNode body,
                                           @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.preview(user.getUserId(), user.getRole(), questionId, body));
    }
}
