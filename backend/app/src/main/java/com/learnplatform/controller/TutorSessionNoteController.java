package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.TutorSessionNotePageVO;
import com.learnplatform.dto.TutorSessionNoteUpdateRequest;
import com.learnplatform.dto.TutorSessionNoteVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.tutor.TutorSessionNoteService;
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
@RequestMapping("/api/my-courses/{courseId}")
public class TutorSessionNoteController {
    private final TutorSessionNoteService notes;

    public TutorSessionNoteController(TutorSessionNoteService notes) { this.notes = notes; }

    @GetMapping("/tutor-notes")
    public R<TutorSessionNotePageVO> list(@PathVariable Long courseId, @RequestParam(defaultValue = "1") int page,
                                         @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(notes.list(user.getUserId(), courseId, page));
    }

    @GetMapping("/tutor-sessions/{sessionKey}/note")
    public R<TutorSessionNoteVO> get(@PathVariable Long courseId, @PathVariable String sessionKey,
                                    @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(notes.get(user.getUserId(), courseId, sessionKey));
    }

    @PutMapping("/tutor-sessions/{sessionKey}/note")
    public R<TutorSessionNoteVO> save(@PathVariable Long courseId, @PathVariable String sessionKey,
                                     @Valid @RequestBody TutorSessionNoteUpdateRequest request,
                                     @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(notes.save(user.getUserId(), courseId, sessionKey, request));
    }

    @DeleteMapping("/tutor-sessions/{sessionKey}/note")
    public R<TutorSessionNoteVO> delete(@PathVariable Long courseId, @PathVariable String sessionKey,
                                       @RequestParam Long revision, @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(notes.delete(user.getUserId(), courseId, sessionKey, revision));
    }
}
