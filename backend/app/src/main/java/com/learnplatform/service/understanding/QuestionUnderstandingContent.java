package com.learnplatform.service.understanding;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.entity.Question;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionUnderstandingMapper;
import com.learnplatform.service.question.QuestionAccessPolicy;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class QuestionUnderstandingContent {
    private final QuestionMapper questions;
    private final QuestionUnderstandingMapper mapper;
    private final ObjectMapper json;

    public QuestionUnderstandingContent(QuestionMapper questions, QuestionUnderstandingMapper mapper,
                                        ObjectMapper json) {
        this.questions = questions;
        this.mapper = mapper;
        this.json = json;
    }

    public boolean available(Long userId, long questionId) {
        try {
            ready(userId, questionId, null, false);
            return true;
        } catch (BusinessException exception) {
            if (isUnavailable(exception)) { return false; }
            throw exception;
        }
    }

    public Set<Long> available(Long userId, List<Question> candidates) {
        if (candidates.isEmpty()) { return Set.of(); }
        List<Long> ids = candidates.stream().map(Question::getId).toList();
        Map<Long, Map<String, Object>> contents = mapper.latestContents(ids).stream()
                .collect(Collectors.toMap(row -> id(row, "question_id"), row -> row));
        Map<Long, Long> points = new HashMap<>();
        contents.forEach((questionId, row) -> points.put(questionId, id(row, "knowledge_point_id")));
        Map<Long, Map<String, Object>> positions = mapper.positions(points);
        Map<Long, List<Map<String, Object>>> options = mapper.options(ids).stream()
                .collect(Collectors.groupingBy(row -> id(row, "question_id")));
        Map<Long, Map<String, Object>> sources = mapper.officialSources(ids);
        Set<Long> result = new HashSet<>();
        for (Question question : candidates) {
            Map<String, Object> content = contents.get(question.getId());
            if (!usableQuestion(question, userId, false) || content == null
                    || !visible(content, false) || !positions.containsKey(question.getId())) { continue; }
            if (content.get("source_exam_question_id") != null) {
                Map<String, Object> source = sources.get(id(content, "source_exam_question_id"));
                if (source == null || id(source, "question_id") != question.getId()) { continue; }
            }
            try {
                validateQuestion(question, content, options.getOrDefault(question.getId(), List.of()));
                result.add(question.getId());
            } catch (BusinessException exception) {
                if (!isUnavailable(exception)) { throw exception; }
            }
        }
        return result;
    }

    private boolean isUnavailable(BusinessException exception) {
        return exception.getCode() == ResultCode.NOT_FOUND.getCode()
                || exception.getCode() == ResultCode.BUSINESS_ERROR.getCode();
    }

    Ready ready(Long userId, long questionId, Long contentId, boolean preview) {
        Question question = questions.selectById(questionId);
        if (!usableQuestion(question, userId, preview)) { throw unavailable(); }
        Map<String, Object> latest = mapper.latestContent(questionId);
        if (latest == null || !visible(latest, preview)) {
            throw unavailable();
        }
        Map<String, Object> content = contentId == null ? latest : mapper.content(contentId);
        if (content == null || !Objects.equals(id(content, "question_id"), questionId)
                || !visible(content, preview)) {
            throw unavailable();
        }
        Map<String, Object> position = mapper.position(questionId, id(content, "knowledge_point_id"));
        if (position == null) {
            throw unavailable();
        }
        List<Map<String, Object>> options = mapper.options(questionId);
        validateQuestion(question, content, options);
        if (content.get("source_exam_question_id") != null
                && mapper.officialSource(id(content, "source_exam_question_id"), questionId) == null) {
            throw unavailable();
        }
        return new Ready(question, content, options, position);
    }

    private void validateQuestion(Question question, Map<String, Object> content, List<Map<String, Object>> options) {
        correctOption(options);
        if (!fingerprint(question, options).equals(content.get("question_fingerprint"))) { throw unavailable(); }
    }

    private boolean usableQuestion(Question question, Long userId, boolean preview) {
        return (QuestionAccessPolicy.canAccess(question, userId) || preview && QuestionAccessPolicy.isDraft(question))
                && Integer.valueOf(1).equals(question.getStatus())
                && "SINGLE_CHOICE".equals(question.getQuestionType());
    }

    Answer answer(JsonNode body, List<Map<String, Object>> options, boolean variant) {
        if (body == null || !body.isObject() || !body.path("answer").isTextual()) {
            throw validation("答案无效");
        }
        String label = body.get("answer").asText().trim().toUpperCase(Locale.ROOT);
        Integer time = null;
        if (body.has("answerTime")) {
            JsonNode node = body.get("answerTime");
            if (!node.isInt() || node.intValue() < 0) {
                throw validation("作答时间无效");
            }
            time = node.intValue();
        }
        if (!variant && body.has("assistance")) {
            throw validation("请求字段无效");
        }
        for (Map<String, Object> option : options) {
            Object optionLabel = variant ? option.get("label") : option.get("option_label");
            if (label.equals(optionLabel)) {
                return new Answer(label, time, variant ? null : id(option, "id"));
            }
        }
        throw validation("答案不是有效选项");
    }

    Map<String, Object> correctOption(List<Map<String, Object>> options) {
        List<Map<String, Object>> correct = options.stream()
                .filter(row -> ((Number) row.get("is_correct")).intValue() == 1).toList();
        if (correct.size() != 1 || options.stream()
                .map(row -> row.get("option_label")).distinct().count() != options.size()) {
            throw business("题目缺少有效判分依据");
        }
        return correct.getFirst();
    }

    private String fingerprint(Question question, List<Map<String, Object>> options) {
        StringBuilder value = new StringBuilder(question.getContent())
                .append('\n').append(question.getQuestionType()).append('\n');
        for (Map<String, Object> option : options) {
            value.append(option.get("option_label")).append('\t')
                .append(option.get("content")).append('\t')
                    .append(((Number) option.get("is_correct")).intValue()).append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    List<Map<String, Object>> publicOptions(List<Map<String, Object>> rows) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            options.add(Map.of("label", row.get("option_label"), "content", row.get("content")));
        }
        return options;
    }

    List<Map<String, Object>> variantOptions(Map<String, Object> variant) {
        try {
            JsonNode nodes = json.readTree((String) variant.get("options_json"));
            if (!nodes.isArray() || nodes.size() == 0) {
                throw unavailable();
            }
            List<Map<String, Object>> options = new ArrayList<>();
            for (JsonNode node : nodes) {
                if (!node.path("label").isTextual() || !node.path("content").isTextual()) {
                    throw unavailable();
                }
                options.add(Map.of("label", node.get("label").asText(), "content", node.get("content").asText()));
            }
            if (options.stream().map(row -> row.get("label")).distinct().count() != options.size()) {
                throw unavailable();
            }
            return options;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    Map<String, Object> position(Ready ready) {
        Map<String, Object> row = ready.position();
        return Map.of("courseId", id(row, "course_id"), "courseName", row.get("course_name"),
                "chapterName", row.get("chapter_name"), "knowledgePointId", id(row, "knowledge_point_id"),
                "knowledgePointName", row.get("knowledge_point_name"));
    }

    private boolean visible(Map<String, Object> row, boolean preview) {
        return "REVIEWED".equals(row.get("review_status")) || preview && "PENDING".equals(row.get("review_status"));
    }

    private long id(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private BusinessException unavailable() { return new BusinessException(ResultCode.NOT_FOUND, "题目理解内容不可用"); }
    private BusinessException validation(String message) {
        return new BusinessException(ResultCode.VALIDATION_ERROR, message);
    }
    private BusinessException business(String message) {
        return new BusinessException(ResultCode.BUSINESS_ERROR, message);
    }

    record Ready(Question question, Map<String, Object> content,
                 List<Map<String, Object>> options, Map<String, Object> position) { }
    record Answer(String label, Integer time, Long optionId) { }
}
