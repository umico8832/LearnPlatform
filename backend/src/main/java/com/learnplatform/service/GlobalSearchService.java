package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.GlobalSearchResultVO;
import com.learnplatform.dto.GlobalSearchResultVO.SearchItem;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局搜索服务
 * Phase 18：全局搜索与快捷导航
 *
 * 在题目内容、课程名称、知识点名称中执行 LIKE 模糊搜索，
 * 结果按类型分组返回，每类最多 limit 条。
 */
@Service
public class GlobalSearchService {

    private static final Logger log = LoggerFactory.getLogger(GlobalSearchService.class);

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;
    private static final int MIN_QUERY_LENGTH = 1;

    private final QuestionMapper questionMapper;
    private final CourseMapper courseMapper;
    private final KnowledgePointMapper knowledgePointMapper;

    public GlobalSearchService(QuestionMapper questionMapper,
                               CourseMapper courseMapper,
                               KnowledgePointMapper knowledgePointMapper) {
        this.questionMapper = questionMapper;
        this.courseMapper = courseMapper;
        this.knowledgePointMapper = knowledgePointMapper;
    }

    /**
     * 全局搜索
     *
     * @param keyword 搜索关键词（至少 1 个字符）
     * @param limit   每类结果最大条数（默认 5，最大 20）
     * @return 分组搜索结果
     */
    @Cacheable(value = "globalSearch", key = "#keyword + '_' + #limit", condition = "#keyword != null && #keyword.trim().length() >= 2")
    public GlobalSearchResultVO search(String keyword, Integer limit) {
        if (keyword == null || keyword.trim().length() < MIN_QUERY_LENGTH) {
            return new GlobalSearchResultVO(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        String trimmed = keyword.trim();
        int perTypeLimit = (limit != null && limit > 0)
                ? Math.min(limit, MAX_LIMIT)
                : DEFAULT_LIMIT;

        log.info("全局搜索: keyword={}, limit={}", trimmed, perTypeLimit);

        List<SearchItem> questions = searchQuestions(trimmed, perTypeLimit);
        List<SearchItem> courses = searchCourses(trimmed, perTypeLimit);
        List<SearchItem> knowledgePoints = searchKnowledgePoints(trimmed, perTypeLimit);

        return new GlobalSearchResultVO(questions, courses, knowledgePoints);
    }

    /**
     * 搜索题目：按 content LIKE 模糊匹配
     */
    private List<SearchItem> searchQuestions(String keyword, int limit) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .like(Question::getContent, keyword)
                .eq(Question::getStatus, 1)
                .eq(Question::getVisibility, "PUBLIC")
                .orderByDesc(Question::getCreateTime)
                .last("LIMIT " + limit);

        return questionMapper.selectList(wrapper).stream()
                .map(q -> {
                    String title = truncate(q.getContent(), 80);
                    String subtitle = formatQuestionSubtitle(q);
                    return new SearchItem(q.getId(), title, subtitle, "QUESTION", "/questions");
                })
                .collect(Collectors.toList());
    }

    /**
     * 搜索课程：按 name LIKE 模糊匹配
     */
    private List<SearchItem> searchCourses(String keyword, int limit) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<Course>()
                .like(Course::getName, keyword)
                .eq(Course::getStatus, 1)
                .orderByAsc(Course::getSortOrder)
                .last("LIMIT " + limit);

        return courseMapper.selectList(wrapper).stream()
                .map(c -> {
                    String subtitle = c.getDescription() != null ? truncate(c.getDescription(), 60) : "";
                    return new SearchItem(c.getId(), c.getName(), subtitle, "COURSE", "/courses/" + c.getId());
                })
                .collect(Collectors.toList());
    }

    /**
     * 搜索知识点：按 name LIKE 模糊匹配
     */
    private List<SearchItem> searchKnowledgePoints(String keyword, int limit) {
        LambdaQueryWrapper<KnowledgePoint> wrapper = new LambdaQueryWrapper<KnowledgePoint>()
                .like(KnowledgePoint::getName, keyword)
                .orderByAsc(KnowledgePoint::getSortOrder)
                .last("LIMIT " + limit);

        return knowledgePointMapper.selectList(wrapper).stream()
                .map(kp -> {
                    String subtitle = kp.getDescription() != null ? truncate(kp.getDescription(), 60) : "";
                    return new SearchItem(kp.getId(), kp.getName(), subtitle,
                            "KNOWLEDGE_POINT", "/questions?knowledgePointId=" + kp.getId());
                })
                .collect(Collectors.toList());
    }

    /**
     * 格式化题目副标题：题型 + 难度星
     */
    private String formatQuestionSubtitle(Question q) {
        StringBuilder sb = new StringBuilder();
        if (q.getQuestionType() != null) {
            sb.append(mapQuestionType(q.getQuestionType()));
        }
        if (q.getDifficulty() != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("难度 ");
            sb.append("★".repeat(Math.max(0, q.getDifficulty())));
        }
        return sb.toString();
    }

    private String mapQuestionType(String type) {
        if (type == null) return "";
        return switch (type.toUpperCase()) {
            case "SINGLE_CHOICE" -> "单选题";
            case "MULTIPLE_CHOICE" -> "多选题";
            case "TRUE_FALSE" -> "判断题";
            case "FILL_BLANK" -> "填空题";
            case "SHORT_ANSWER" -> "简答题";
            default -> type;
        };
    }

    /**
     * 截断文本，超过 maxLength 时添加省略号
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        // 移除换行符，用空格替换
        String cleaned = text.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= maxLength) return cleaned;
        return cleaned.substring(0, maxLength) + "…";
    }
}
