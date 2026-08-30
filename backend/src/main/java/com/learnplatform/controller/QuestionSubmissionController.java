package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.result.R;
import com.learnplatform.dto.QuestionSubmissionRequest;
import com.learnplatform.dto.QuestionSubmissionVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.QuestionSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题目投稿控制器（用户端）
 */
@Tag(name = "题目投稿", description = "用户提交题目投稿、查看投稿状态")
@RestController
@RequestMapping("/api/submission")
public class QuestionSubmissionController {

    private final QuestionSubmissionService submissionService;

    public QuestionSubmissionController(QuestionSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @Operation(summary = "提交题目投稿", description = "用户提交一道新题目，待管理员审核")
    @PostMapping
    public R<QuestionSubmissionVO> submit(@Valid @RequestBody QuestionSubmissionRequest request) {
        Long userId = getCurrentUserId();
        QuestionSubmissionVO vo = submissionService.submitQuestion(request, userId);
        return R.ok(vo);
    }

    @Operation(summary = "我的投稿列表", description = "分页查看当前用户的投稿记录")
    @GetMapping("/my")
    public R<Page<QuestionSubmissionVO>> mySubmissions(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer status) {
        Long userId = getCurrentUserId();
        Page<QuestionSubmissionVO> page = submissionService.getMySubmissions(userId, pageNum, pageSize, status);
        return R.ok(page);
    }

    @Operation(summary = "投稿详情", description = "查看指定投稿的详细信息（仅限本人）")
    @GetMapping("/{id}")
    public R<QuestionSubmissionVO> detail(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        QuestionSubmissionVO vo = submissionService.getSubmissionById(id);
        // 非本人不能查看（管理员通过admin接口查看）
        if (!vo.getUserId().equals(userId)) {
            return R.fail(com.learnplatform.common.result.ResultCode.FORBIDDEN, "无权查看他人投稿");
        }
        return R.ok(vo);
    }

    private Long getCurrentUserId() {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return userDetails.getUserId();
    }
}
