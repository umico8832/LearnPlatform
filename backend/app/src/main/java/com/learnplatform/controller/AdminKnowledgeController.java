package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.dto.KnowledgeBundleVO;
import com.learnplatform.dto.KnowledgeChunkVO;
import com.learnplatform.dto.KnowledgeIndexStatusVO;
import com.learnplatform.dto.KnowledgeImportVO;
import com.learnplatform.dto.KnowledgeIndexVO;
import com.learnplatform.dto.KnowledgeBundleReviewRequest;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.KnowledgeImportService;
import com.learnplatform.service.KnowledgeIndexService;
import com.learnplatform.service.KnowledgeIndexStateService;
import com.learnplatform.service.KnowledgeQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/knowledge")
public class AdminKnowledgeController {
    private final KnowledgeImportService imports;

    private final KnowledgeIndexService indexing;
    private final KnowledgeIndexStateService states;
    private final KnowledgeQueryService queries;

    public AdminKnowledgeController(KnowledgeImportService imports, KnowledgeIndexService indexing,
                                    KnowledgeIndexStateService states, KnowledgeQueryService queries) {
        this.imports = imports;
        this.indexing = indexing;
        this.states = states;
        this.queries = queries;
    }

    @GetMapping
    public R<Page<KnowledgeBundleVO>> bundles(@AuthenticationPrincipal CustomUserDetails user,
                                             @RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "20") int pageSize,
                                             @RequestParam(required = false) String courseKey,
                                             @RequestParam(required = false) String reviewStatus) {
        return R.ok(queries.bundles(user == null ? null : user.getUserId(),
                pageNum, pageSize, courseKey, reviewStatus));
    }

    @GetMapping("/{bundleId}")
    public R<KnowledgeBundleVO> bundle(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long bundleId) {
        return R.ok(queries.bundle(user == null ? null : user.getUserId(), bundleId));
    }

    @GetMapping("/{bundleId}/chunks")
    public R<Page<KnowledgeChunkVO>> chunks(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long bundleId,
                                           @RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "20") int pageSize,
                                           @RequestParam(required = false) String conceptId) {
        return R.ok(queries.chunks(user == null ? null : user.getUserId(), bundleId, pageNum, pageSize, conceptId));
    }

    @GetMapping("/{bundleId}/indexes")
    public R<Page<KnowledgeIndexStatusVO>> indexes(@AuthenticationPrincipal CustomUserDetails user,
                                                  @PathVariable Long bundleId,
                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(queries.indexes(user == null ? null : user.getUserId(), bundleId, pageNum, pageSize));
    }

    @PostMapping("/import")
    public R<KnowledgeImportVO> importSnapshot(@AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(imports.importConfigured(user == null ? null : user.getUserId()));
    }

    @PostMapping("/{bundleId}/review")
    public R<Void> review(@PathVariable Long bundleId, @RequestBody KnowledgeBundleReviewRequest request,
                          @AuthenticationPrincipal CustomUserDetails user) {
        states.review(user == null ? null : user.getUserId(), bundleId, request.decision(), request.note());
        return R.ok();
    }

    @PostMapping("/{bundleId}/index")
    public R<KnowledgeIndexVO> index(@PathVariable Long bundleId, @AuthenticationPrincipal CustomUserDetails user) {
        return R.ok(indexing.index(user == null ? null : user.getUserId(), bundleId));
    }

    @PostMapping("/{bundleId}/withdraw")
    public R<Void> withdraw(@PathVariable Long bundleId, @AuthenticationPrincipal CustomUserDetails user) {
        indexing.withdraw(user == null ? null : user.getUserId(), bundleId);
        return R.ok();
    }
}
