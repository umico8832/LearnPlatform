package com.learnplatform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.QuestionSubmissionRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuestionSubmissionOptionService {

    private final ObjectMapper objectMapper;

    public QuestionSubmissionOptionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void normalizeAndValidateRequest(QuestionSubmissionRequest request, String questionType) {
        if ("SINGLE_CHOICE".equals(questionType) || "MULTIPLE_CHOICE".equals(questionType)) {
            List<OptionItem> options = parseOptionsJson(request.getOptionsJson());
            if (options.size() < 2) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "选择题至少需要 2 个选项");
            }
            int correctCount = 0;
            for (int i = 0; i < options.size(); i++) {
                OptionItem item = options.get(i);
                if (item.content == null || item.content.trim().isEmpty()) {
                    throw new BusinessException(ResultCode.VALIDATION_ERROR, "选项内容不能为空");
                }
                item.content = item.content.trim();
                item.label = normalizeOptionLabel(item, i);
                if (Boolean.TRUE.equals(item.isCorrect)) {
                    correctCount++;
                }
            }
            if ("SINGLE_CHOICE".equals(questionType) && correctCount != 1) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "单选题必须且只能有 1 个正确答案");
            }
            if ("MULTIPLE_CHOICE".equals(questionType) && correctCount < 1) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "多选题至少需要 1 个正确答案");
            }
            request.setOptionsJson(writeOptionsJson(options));
            return;
        }

        if ("TRUE_FALSE".equals(questionType)) {
            String normalizedAnswer = normalizeTrueFalseAnswer(request.getCorrectAnswer());
            request.setCorrectAnswer(normalizedAnswer);
            request.setOptionsJson(writeOptionsJson(List.of(
                    optionItem("正确", "A", "TRUE".equals(normalizedAnswer)),
                    optionItem("错误", "B", "FALSE".equals(normalizedAnswer))
            )));
            return;
        }

        if (("FILL_BLANK".equals(questionType) || "SHORT_ANSWER".equals(questionType))
                && (request.getCorrectAnswer() == null || request.getCorrectAnswer().trim().isEmpty())) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "填空题和简答题必须提供参考答案");
        }
        if ("FILL_BLANK".equals(questionType) || "SHORT_ANSWER".equals(questionType)) {
            request.setCorrectAnswer(request.getCorrectAnswer().trim());
            request.setOptionsJson(null);
        }
    }

    List<OptionItem> parseOptionsJson(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "选择题必须提供选项");
        }
        try {
            return objectMapper.readValue(optionsJson, new TypeReference<List<OptionItem>>() { });
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "选项JSON格式不正确");
        }
    }

    String normalizeOptionLabel(OptionItem item, int index) {
        String label = item.label != null ? item.label : item.optionLabel;
        if (label == null || label.trim().isEmpty()) {
            return String.valueOf((char) ('A' + index));
        }
        return label.trim().toUpperCase();
    }

    private String writeOptionsJson(List<OptionItem> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "选项JSON序列化失败");
        }
    }

    private String normalizeTrueFalseAnswer(String answer) {
        if (answer == null || answer.trim().isEmpty()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "判断题必须提供正确答案");
        }
        String normalized = answer.trim();
        if ("TRUE".equalsIgnoreCase(normalized) || "正确".equals(normalized)
                || "对".equals(normalized) || "A".equalsIgnoreCase(normalized)) {
            return "TRUE";
        }
        if ("FALSE".equalsIgnoreCase(normalized) || "错误".equals(normalized)
                || "错".equals(normalized) || "B".equalsIgnoreCase(normalized)) {
            return "FALSE";
        }
        throw new BusinessException(ResultCode.VALIDATION_ERROR, "判断题答案只能是正确/错误");
    }

    private OptionItem optionItem(String content, String label, boolean isCorrect) {
        OptionItem item = new OptionItem();
        item.content = content;
        item.label = label;
        item.optionLabel = label;
        item.isCorrect = isCorrect;
        return item;
    }

    static class OptionItem {
        public String content;
        public String label;
        public String optionLabel;
        public Boolean isCorrect;
    }
}
