package com.learnplatform.controller;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.TutorAgentMessageRequest;
import com.learnplatform.dto.TutorAgentRunVO;
import com.learnplatform.dto.TutorCheckAnswerRequest;
import com.learnplatform.dto.TutorCheckResultVO;
import com.learnplatform.dto.TutorSessionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.TutorAgentService;
import com.learnplatform.service.TutorSessionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/my-courses/{courseId}/tutor-sessions")
public class TutorSessionController {
    private final TutorSessionService service;
    private final TutorAgentService agentService;

    public TutorSessionController(TutorSessionService value, TutorAgentService tutorAgentService) {
        service = value;
        agentService = tutorAgentService;
    }

    @Operation(summary = "开始已审查的 Tutor 教学")
    @PostMapping
    public R<TutorSessionVO> start(@PathVariable Long courseId, @RequestParam Long knowledgePointId,
                                   @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.start(user.getUserId(), courseId, knowledgePointId));
    }

    @Operation(summary = "提交 Tutor 理解检查")
    @PostMapping("/{sessionKey}/check")
    public R<TutorCheckResultVO> answer(@PathVariable String sessionKey,
                                        @RequestBody @Valid TutorCheckAnswerRequest request,
                                        @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.answer(user.getUserId(), sessionKey, request));
    }

    @Operation(summary = "恢复当前用户的 Tutor 会话")
    @GetMapping("/{sessionKey}")
    public R<TutorSessionVO> get(@PathVariable Long courseId, @PathVariable String sessionKey,
                                 @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.get(user.getUserId(), courseId, sessionKey));
    }

    @Operation(summary = "向新的 Tutor Agent 运行提问")
    @PostMapping("/{sessionKey}/agent-runs")
    public R<TutorAgentRunVO> startAgent(@PathVariable Long courseId, @PathVariable String sessionKey,
                                         @RequestBody @Valid TutorAgentMessageRequest request,
                                         @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(agentService.start(user.getUserId(), courseId, sessionKey, request));
    }

    @Operation(summary = "恢复 Tutor Agent 运行并继续提问")
    @PostMapping("/{sessionKey}/agent-runs/{runKey}/messages")
    public R<TutorAgentRunVO> resumeAgent(@PathVariable Long courseId, @PathVariable String sessionKey,
                                          @PathVariable String runKey,
                                          @RequestBody @Valid TutorAgentMessageRequest request,
                                          @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(agentService.resume(user.getUserId(), courseId, sessionKey, runKey, request));
    }

    @Operation(summary = "读取 Tutor Agent 运行与可见消息")
    @GetMapping("/{sessionKey}/agent-runs/{runKey}")
    public R<TutorAgentRunVO> getAgent(@PathVariable Long courseId, @PathVariable String sessionKey,
                                       @PathVariable String runKey,
                                       @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(agentService.get(user.getUserId(), courseId, sessionKey, runKey));
    }

    @Operation(summary = "恢复本人 Tutor 会话最近创建的 Agent 运行")
    @GetMapping("/{sessionKey}/agent-runs/latest")
    public R<TutorAgentRunVO> latestAgent(@PathVariable Long courseId, @PathVariable String sessionKey,
                                          @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(agentService.latest(user.getUserId(), courseId, sessionKey));
    }
}
