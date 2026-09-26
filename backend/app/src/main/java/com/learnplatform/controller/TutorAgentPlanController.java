package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.TutorAgentPlanVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.tutor.TutorAgentPlanService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/my-courses/{courseId}/tutor-sessions/{sessionKey}/agent-runs/{runKey}/messages/{sequence}/plan")
public class TutorAgentPlanController {
    private final TutorAgentPlanService service;

    public TutorAgentPlanController(TutorAgentPlanService service) {
        this.service = service;
    }

    @GetMapping
    public R<TutorAgentPlanVO> get(@PathVariable Long courseId, @PathVariable String sessionKey,
                                   @PathVariable String runKey, @PathVariable Integer sequence,
                                   @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.get(user.getUserId(), courseId, sessionKey, runKey, sequence));
    }

    @PostMapping("/confirm")
    public R<TutorAgentPlanVO> confirm(@PathVariable Long courseId, @PathVariable String sessionKey,
                                       @PathVariable String runKey, @PathVariable Integer sequence,
                                       @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.confirm(user.getUserId(), courseId, sessionKey, runKey, sequence));
    }
}
