package com.learnplatform.controller;

import com.learnplatform.common.result.R;
import com.learnplatform.dto.KnowledgePointVO;
import com.learnplatform.service.KnowledgePointService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/knowledge-points")
public class AdminKnowledgePointController {

    private final KnowledgePointService knowledgePointService;

    public AdminKnowledgePointController(KnowledgePointService knowledgePointService) {
        this.knowledgePointService = knowledgePointService;
    }

    @PostMapping
    public R<KnowledgePointVO> createKnowledgePoint(@RequestBody CreateKPRequest request) {
        return R.ok(knowledgePointService.createKnowledgePoint(
                request.getCourseId(), request.getParentId(), request.getName(),
                request.getDescription(), request.getSortOrder()));
    }

    @PutMapping("/{id}")
    public R<KnowledgePointVO> updateKnowledgePoint(@PathVariable Long id, @RequestBody CreateKPRequest request) {
        return R.ok(knowledgePointService.updateKnowledgePoint(
                id, request.getName(), request.getDescription(), request.getSortOrder()));
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteKnowledgePoint(@PathVariable Long id) {
        knowledgePointService.deleteKnowledgePoint(id);
        return R.ok();
    }

    public static class CreateKPRequest {
        private Long courseId;
        private Long parentId;
        private String name;
        private String description;
        private Integer sortOrder;

        public Long getCourseId() { return courseId; }
        public void setCourseId(Long courseId) { this.courseId = courseId; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }
}
