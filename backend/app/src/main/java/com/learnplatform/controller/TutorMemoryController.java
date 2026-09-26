package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.TutorMemoryUpdateRequest;
import com.learnplatform.dto.TutorMemoryVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.tutor.TutorMemoryService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/my-courses/{courseId}/tutor-memory")
public class TutorMemoryController {
    private final TutorMemoryService memories;

    public TutorMemoryController(TutorMemoryService memories) { this.memories = memories; }

    @GetMapping
    public R<TutorMemoryVO> get(@PathVariable Long courseId, @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(memories.get(user.getUserId(), courseId));
    }

    @PutMapping
    public R<TutorMemoryVO> save(@PathVariable Long courseId, @Valid @RequestBody TutorMemoryUpdateRequest request,
                                @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(memories.save(user.getUserId(), courseId, request));
    }

    @DeleteMapping
    public R<TutorMemoryVO> delete(@PathVariable Long courseId, @RequestParam Long revision,
                                  @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(memories.delete(user.getUserId(), courseId, revision));
    }
}
