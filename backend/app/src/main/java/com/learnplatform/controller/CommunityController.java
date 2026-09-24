package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.community.CommunityContracts.Category;
import com.learnplatform.dto.community.CommunityContracts.Comment;
import com.learnplatform.dto.community.CommunityContracts.Draft;
import com.learnplatform.dto.community.CommunityContracts.Page;
import com.learnplatform.dto.community.CommunityContracts.Post;
import com.learnplatform.dto.community.CommunityContracts.Query;
import com.learnplatform.dto.community.CommunityContracts.Reply;
import com.learnplatform.dto.community.CommunityContracts.ReviewEvent;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.CommunityService;

import jakarta.validation.Valid;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/community")
public class CommunityController {
    private final CommunityService service;

    public CommunityController(CommunityService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public R<List<Category>> categories(@AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(service.categories(user.getUserId()));
    }

    @GetMapping("/posts")
    public R<Page<Post>> posts(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) String examId,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(required = false) String status) {
        return R.ok(
                service.posts(
                        user.getUserId(),
                        new Query(
                                pageNum,
                                pageSize,
                                keyword,
                                contentType,
                                examId,
                                subjectId,
                                schoolId,
                                courseId,
                                mine,
                                status),
                        false));
    }

    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Long> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestPart("post") Draft draft,
            @RequestPart(value = "files", required = false) List<MultipartFile> files)
            throws IOException {
        return R.ok(service.create(user.getUserId(), draft, files == null ? List.of() : files));
    }

    @GetMapping("/posts/{id}")
    public R<Post> detail(@AuthenticationPrincipal CustomUserDetails user, @PathVariable long id) {
        return R.ok(service.detail(user.getUserId(), id));
    }

    @DeleteMapping("/posts/{id}")
    public R<Void> delete(@AuthenticationPrincipal CustomUserDetails user, @PathVariable long id) {
        service.delete(user.getUserId(), id);
        return R.ok();
    }

    @GetMapping("/posts/{id}/reviews")
    public R<List<ReviewEvent>> reviews(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable long id) {
        return R.ok(service.reviews(user.getUserId(), id));
    }

    @GetMapping("/posts/{id}/comments")
    public R<Page<Comment>> comments(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(service.comments(user.getUserId(), id, pageNum, pageSize));
    }

    @PostMapping("/posts/{id}/comments")
    public R<Long> comment(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable long id,
            @Valid @RequestBody Reply reply) {
        return R.ok(service.comment(user.getUserId(), id, reply));
    }

    @DeleteMapping("/comments/{id}")
    public R<Void> deleteComment(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable long id) {
        service.deleteComment(user.getUserId(), id);
        return R.ok();
    }

    @PutMapping({"/posts/{id}/like", "/posts/{id}/comments/{commentId}/like"})
    public R<Void> like(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable long id,
            @PathVariable(required = false) Long commentId) {
        service.like(user.getUserId(), id, commentId == null ? 0 : commentId, true);
        return R.ok();
    }

    @DeleteMapping({"/posts/{id}/like", "/posts/{id}/comments/{commentId}/like"})
    public R<Void> unlike(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable long id,
            @PathVariable(required = false) Long commentId) {
        service.like(user.getUserId(), id, commentId == null ? 0 : commentId, false);
        return R.ok();
    }

    @GetMapping("/attachments/{id}")
    public ResponseEntity<ByteArrayResource> download(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable long id) {
        var file = service.attachment(user.getUserId(), id);
        byte[] bytes = service.download(user.getUserId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.name(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new ByteArrayResource(bytes));
    }
}
