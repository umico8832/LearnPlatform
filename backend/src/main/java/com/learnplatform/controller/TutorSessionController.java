package com.learnplatform.controller;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.TutorCheckAnswerRequest;
import com.learnplatform.dto.TutorCheckResultVO;
import com.learnplatform.dto.TutorSessionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.TutorSessionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/my-courses/{courseId}/tutor-sessions")
public class TutorSessionController {
    private final TutorSessionService service;

    public TutorSessionController(TutorSessionService value) {
        service = value;
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
}
