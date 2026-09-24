package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.QuestionSubmissionRequest;
import com.learnplatform.dto.QuestionSubmissionVO;
import com.learnplatform.dto.community.CommunityContracts.Page;
import com.learnplatform.dto.community.CommunityContracts.Post;
import com.learnplatform.dto.community.CommunityContracts.Query;
import com.learnplatform.dto.community.CommunityContracts.Review;
import com.learnplatform.dto.community.CommunityContracts.School;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.CommunityService;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/community")
public class AdminCommunityController {
    private final CommunityService service;

    public AdminCommunityController(CommunityService service) {
        this.service = service;
    }

    @GetMapping("/posts")
    public R<Page<Post>> posts(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return R.ok(
                service.posts(
                        user.getUserId(),
                        new Query(
                                pageNum, pageSize, keyword, null, null, null, null, null, false,
                                status),
                        true));
    }

    @PostMapping("/posts/{id}/review")
    public R<Void> review(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable long id,
            @Valid @RequestBody Review review) {
        service.review(user.getUserId(), id, review);
        return R.ok();
    }

    @PostMapping("/schools")
    public R<String> school(
            @AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody School school) {
        return R.ok(service.createSchool(user.getUserId(), school));
    }

    @PostMapping("/posts/{id}/questions")
    public R<QuestionSubmissionVO> question(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable long id,
            @Valid @RequestBody QuestionSubmissionRequest request,
            @RequestHeader("Idempotency-Key") String requestKey) {
        return R.ok(service.prepareQuestion(user.getUserId(), id, request, requestKey));
    }
}
