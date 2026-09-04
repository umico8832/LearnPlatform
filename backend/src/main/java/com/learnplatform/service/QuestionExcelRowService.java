package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.dto.QuestionExcelDTO;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QuestionExcelRowService {
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;

    public QuestionExcelRowService(QuestionOptionMapper questionOptionMapper,
                                   QuestionKnowledgePointMapper questionKnowledgePointMapper) {
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
    }

    public String normalizeQuestionType(String input) {
        if (input == null) { return null; }
        return switch (input.trim()) {
            case "单选", "单选题", "SINGLE_CHOICE" -> "SINGLE_CHOICE";
            case "多选", "多选题", "MULTIPLE_CHOICE" -> "MULTIPLE_CHOICE";
            case "判断", "判断题", "TRUE_FALSE", "JUDGMENT" -> "TRUE_FALSE";
            case "填空", "填空题", "FILL_BLANK" -> "FILL_BLANK";
            case "简答", "简答题", "SHORT_ANSWER" -> "SHORT_ANSWER";
            default -> null;
        };
    }

    public List<QuestionCreateRequest.OptionItem> parseOptions(
            String optionsText, String answer, String questionType) {
        List<QuestionCreateRequest.OptionItem> result = new ArrayList<>();
        if (optionsText == null || optionsText.trim().isEmpty()) {
            if ("TRUE_FALSE".equals(questionType)) {
                result.add(createOption("对", "A", answer != null && "对".equals(answer.trim()), 1));
                result.add(createOption("错", "B", answer != null && "错".equals(answer.trim()), 2));
            }
            return result;
        }

        String[] parts = optionsText.split("[|｜]");
        Set<String> correctAnswers = parseCorrectAnswers(answer, questionType);
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index].trim();
            String label;
            String content;
            if (part.length() >= 2 && (part.charAt(1) == '.' || part.charAt(1) == '、')) {
                label = String.valueOf(part.charAt(0)).toUpperCase();
                content = part.substring(2).trim();
            } else {
                label = String.valueOf((char) ('A' + index));
                content = part;
            }
            boolean correct = correctAnswers.contains(label) || correctAnswers.contains(content);
            result.add(createOption(content, label, correct, index + 1));
        }
        return result;
    }

    public QuestionExcelDTO toExcelRow(Question question, Map<Long, String> courseNames,
                                       Map<Long, String> knowledgePointNames) {
        QuestionExcelDTO row = new QuestionExcelDTO();
        row.setContent(question.getContent());
        row.setQuestionType(displayQuestionType(question.getQuestionType()));
        row.setCourseName(courseNames.getOrDefault(question.getCourseId(), ""));
        row.setDifficulty(question.getDifficulty());
        row.setAnalysis(question.getAnalysis());
        row.setScore(question.getScore());
        row.setTags(question.getTags());

        List<QuestionOption> options = questionOptionMapper.selectList(
                new LambdaQueryWrapper<QuestionOption>()
                        .eq(QuestionOption::getQuestionId, question.getId())
                        .orderByAsc(QuestionOption::getSortOrder));
        if (!options.isEmpty()) {
            StringBuilder optionText = new StringBuilder();
            List<String> correctLabels = new ArrayList<>();
            for (int index = 0; index < options.size(); index++) {
                QuestionOption option = options.get(index);
                if (index > 0) { optionText.append("|"); }
                String label = option.getOptionLabel() != null ? option.getOptionLabel()
                        : String.valueOf((char) ('A' + index));
                optionText.append(label).append(".").append(option.getContent());
                if (option.getIsCorrect() != null && option.getIsCorrect() == 1) {
                    correctLabels.add(label);
                }
            }
            row.setOptions(optionText.toString());
            row.setAnswer(String.join(",", correctLabels));
        }

        List<QuestionKnowledgePoint> relations = questionKnowledgePointMapper.selectList(
                new LambdaQueryWrapper<QuestionKnowledgePoint>()
                        .eq(QuestionKnowledgePoint::getQuestionId, question.getId()));
        if (!relations.isEmpty()) {
            row.setKnowledgePoints(relations.stream()
                    .map(relation -> knowledgePointNames.getOrDefault(relation.getKnowledgePointId(), ""))
                    .filter(name -> !name.isEmpty())
                    .collect(Collectors.joining(",")));
        }
        return row;
    }

    private Set<String> parseCorrectAnswers(String answer, String questionType) {
        Set<String> result = new HashSet<>();
        if (answer == null || answer.trim().isEmpty()) { return result; }
        if ("TRUE_FALSE".equals(questionType)) {
            result.add(answer.trim());
            return result;
        }
        for (String part : answer.split("[,，]")) { result.add(part.trim().toUpperCase()); }
        return result;
    }

    private QuestionCreateRequest.OptionItem createOption(
            String content, String label, boolean correct, int sortOrder) {
        QuestionCreateRequest.OptionItem item = new QuestionCreateRequest.OptionItem();
        item.setContent(content);
        item.setOptionLabel(label);
        item.setIsCorrect(correct ? 1 : 0);
        item.setSortOrder(sortOrder);
        return item;
    }

    private String displayQuestionType(String type) {
        if (type == null) { return ""; }
        return switch (type) {
            case "SINGLE_CHOICE" -> "单选";
            case "MULTIPLE_CHOICE" -> "多选";
            case "TRUE_FALSE" -> "判断";
            case "FILL_BLANK" -> "填空";
            case "SHORT_ANSWER" -> "简答";
            default -> type;
        };
    }
}
