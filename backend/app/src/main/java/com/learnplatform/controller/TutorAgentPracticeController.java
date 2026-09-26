package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.TutorAgentPracticeAnswerRequest;
import com.learnplatform.dto.TutorAgentPracticeVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.tutor.TutorAgentPracticeService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/my-courses/{courseId}/tutor-sessions/{sessionKey}/agent-runs/{runKey}/messages/{sequence}/practice")
public class TutorAgentPracticeController {
    private final TutorAgentPracticeService service;
    public TutorAgentPracticeController(TutorAgentPracticeService service) {
        this.service = service;
    }

    @GetMapping
    public R<TutorAgentPracticeVO> get(@PathVariable Long courseId, @PathVariable String sessionKey,
                                       @PathVariable String runKey, @PathVariable Integer sequence,
                                       @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.get(user.getUserId(), courseId, sessionKey, runKey, sequence));
    }

    @PostMapping("/answer")
    public R<TutorAgentPracticeVO> answer(@PathVariable Long courseId, @PathVariable String sessionKey,
                                          @PathVariable String runKey, @PathVariable Integer sequence,
                                          @Valid @RequestBody TutorAgentPracticeAnswerRequest request,
                                          @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.answer(user.getUserId(), courseId, sessionKey, runKey, sequence, request));
    }
}
