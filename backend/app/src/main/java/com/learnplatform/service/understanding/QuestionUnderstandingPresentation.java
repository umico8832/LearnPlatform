package com.learnplatform.service.understanding;

import tools.jackson.databind.ObjectMapper;
import com.learnplatform.entity.Question;
import com.learnplatform.mapper.QuestionUnderstandingMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class QuestionUnderstandingPresentation {
    private final QuestionUnderstandingMapper mapper;
    private final ObjectMapper json;

    public QuestionUnderstandingPresentation(QuestionUnderstandingMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    public Map<String, Object> source(Question question, Map<String, Object> content) {
        Map<String, Object> source = new LinkedHashMap<>();
        boolean official = content.get("source_exam_question_id") != null
                && "VERIFIED".equals(content.get("source_verification"))
                && content.get("source_verified_by") != null && content.get("source_verified_time") != null;
        String kind = official ? "OFFICIAL_EXAM"
                : "AI_GENERATED".equals(question.getSourceType()) ? "AI_GENERATED" : "MANUAL";
        source.put("kind", kind);
        if (official) {
            Map<String, Object> paper = mapper.officialSource(
                    id(content, "source_exam_question_id"), question.getId());
            source.put("title", content.get("source_title") != null ? content.get("source_title")
                    : paper.get("exam_name") + " " + paper.get("exam_year") + " " + paper.get("display_number"));
            String reference = (String) paper.get("source_reference");
            source.put("url", reference != null && reference.matches("https?://.+") ? reference : null);
        } else {
            source.put("title", content.get("source_title"));
            source.put("url", content.get("source_url"));
        }
        source.put("verification", content.get("source_verification"));
        return source;
    }

    public Map<String, Object> sourceVariant() { return sourceVariant("REVIEWED"); }

    public Map<String, Object> sourceVariant(String status) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kind", "AI_GENERATED");
        source.put("title", null);
        source.put("url", null);
        source.put("verification", status);
        return source;
    }

    public Map<String, Object> viz(Map<String, Object> row, String titleKey, String specKey) {
        if (row.get(titleKey) == null || row.get(specKey) == null) {
            return null;
        }
        try {
            Map<String, Object> viz = new LinkedHashMap<>();
            viz.put("title", row.get(titleKey));
            viz.put("spec", json.readTree(row.get(specKey).toString()));
            String kind = row.containsKey("content_source") ? (String) row.get("content_source")
                    : row.containsKey("solution_content_source") ? (String) row.get("solution_content_source")
                    : "AI_GENERATED";
            String status = row.getOrDefault("review_status", "REVIEWED").toString();
            viz.put("source", contentSource(kind, "APPROVED".equals(status) ? "REVIEWED" : status));
            return viz;
        } catch (Exception exception) {
            throw new IllegalStateException("理解可视化内容无效", exception);
        }
    }

    public Map<String, Object> contentSource(String kind) {
        return contentSource(kind, "REVIEWED");
    }

    public Map<String, Object> contentSource(String kind, String status) {
        return Map.of("kind", kind, "reviewStatus", status);
    }

    private long id(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
