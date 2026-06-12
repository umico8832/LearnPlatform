package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.KnowledgePointVO;
import com.learnplatform.service.KnowledgePointService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-points")
public class KnowledgePointController {

    private final KnowledgePointService knowledgePointService;

    public KnowledgePointController(KnowledgePointService knowledgePointService) {
        this.knowledgePointService = knowledgePointService;
    }

    @GetMapping("/tree/{courseId}")
    public R<List<KnowledgePointVO>> getKnowledgeTree(@PathVariable Long courseId) {
        return R.ok(knowledgePointService.getKnowledgeTree(courseId));
    }
}