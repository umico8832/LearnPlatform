package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.AiResponse;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import org.springframework.stereotype.Service;

@Service
public class AiKnowledgeSummaryService {
    private final KnowledgePointMapper knowledgePointMapper;
    private final CourseMapper courseMapper;
    private final AiInvocationService invocationService;

    public AiKnowledgeSummaryService(
            KnowledgePointMapper knowledgePointMapper,
            CourseMapper courseMapper,
            AiInvocationService invocationService) {
        this.knowledgePointMapper = knowledgePointMapper;
        this.courseMapper = courseMapper;
        this.invocationService = invocationService;
    }

    public AiResponse generateSummary(Long knowledgePointId, Long userId) {
        KnowledgePoint knowledgePoint = knowledgePointMapper.selectById(knowledgePointId);
        if (knowledgePoint == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "知识点不存在");
        }
        String courseName = "";
        if (knowledgePoint.getCourseId() != null) {
            Course course = courseMapper.selectById(knowledgePoint.getCourseId());
            if (course != null) {
                courseName = course.getName();
            }
        }
        String systemPrompt = "你是一位专业的教育内容创作者。请为以下知识点生成一份简洁的知识总结。"
                + "要求：\n1. 清晰解释知识点的定义和概念\n2. 列出核心要点\n3. 提供实际例子\n"
                + "4. 如果有相关公式或规则请列出\n5. 使用 Markdown 格式输出";
        AiService.AiPrompt prompt = new AiService.AiPrompt(systemPrompt,
                String.format("请总结以下知识点：\n课程：%s\n知识点：%s", courseName, knowledgePoint.getName()));
        return invocationService.call("summary", userId, prompt);
    }
}
