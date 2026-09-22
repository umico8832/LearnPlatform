package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.KnowledgeImportVO;
import com.learnplatform.dto.KnowledgeIndexVO;
import com.learnplatform.dto.KnowledgeBundleReviewRequest;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.KnowledgeImportService;
import com.learnplatform.service.KnowledgeIndexService;
import com.learnplatform.service.KnowledgeIndexStateService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/knowledge")
public class AdminKnowledgeController {
    private final KnowledgeImportService imports;

    private final KnowledgeIndexService indexing;
    private final KnowledgeIndexStateService states;

    public AdminKnowledgeController(KnowledgeImportService imports, KnowledgeIndexService indexing,
                                    KnowledgeIndexStateService states) {
        this.imports = imports;
        this.indexing = indexing;
        this.states = states;
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
