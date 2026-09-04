package com.learnplatform.service;

import com.learnplatform.dto.QuestionCreateRequest;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MarkdownQuestionContentParser {
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,3}\\s+(?:\\d+\\.\\s*)?(.+)$");
    private static final Pattern FIELD_PATTERN = Pattern.compile("^\\*\\*(.+?)\\*\\*[:：]\\s*(.*)$");
    private static final Pattern OPTION_WITH_LABEL = Pattern.compile("^-\\s+([A-Fa-f])[.、．]\\s*(.+)$");
    private static final Pattern OPTION_PLAIN = Pattern.compile("^-\\s+(.+)$");
    private static final Pattern DIVIDER_PATTERN = Pattern.compile("^-{3,}$|^\\*{3,}$");

    public List<MarkdownQuestionParser.RawQuestion> parse(InputStream inputStream) throws Exception {
        List<MarkdownQuestionParser.RawQuestion> questions = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) { lines.add(line); }
        }

        MarkdownQuestionParser.RawQuestion current = null;
        for (String sourceLine : lines) {
            String line = sourceLine.trim();
            if (line.isEmpty()) { continue; }

            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.matches()) {
                String headingText = headingMatcher.group(1).trim();
                String typeGuess = normalizeQuestionType(headingText);
                boolean newQuestion = typeGuess != null;
                if (!newQuestion && headingText.matches("^\\d+[.、．].*")) {
                    String afterNumber = headingText.replaceFirst("^\\d+[.、．]\\s*", "").trim();
                    typeGuess = normalizeQuestionType(afterNumber);
                    newQuestion = true;
                } else if (!newQuestion
                        && (headingText.matches("^题目.*") || headingText.matches("^第.*题.*"))) {
                    newQuestion = true;
                }
                if (newQuestion) {
                    current = new MarkdownQuestionParser.RawQuestion();
                    current.questionType = typeGuess;
                    questions.add(current);
                    continue;
                }
                if (current == null) { continue; }
            }

            if (current == null || DIVIDER_PATTERN.matcher(line).matches()) { continue; }
            Matcher fieldMatcher = FIELD_PATTERN.matcher(line);
            if (fieldMatcher.matches()) {
                applyField(current, fieldMatcher.group(1).trim(), fieldMatcher.group(2).trim());
                continue;
            }

            Matcher labeledOption = OPTION_WITH_LABEL.matcher(line);
            if (labeledOption.matches() || current.inOptionsBlock) {
                if (labeledOption.matches()) {
                    current.options.add(new MarkdownQuestionParser.RawOption(
                            labeledOption.group(1).toUpperCase(), labeledOption.group(2).trim()));
                    continue;
                }
                Matcher plainOption = OPTION_PLAIN.matcher(line);
                if (plainOption.matches()) {
                    String label = String.valueOf((char) ('A' + current.options.size()));
                    current.options.add(new MarkdownQuestionParser.RawOption(label, plainOption.group(1).trim()));
                    continue;
                }
            }
            if (current.inOptionsBlock && !line.startsWith("-")) { current.inOptionsBlock = false; }
            if (!current.inOptionsBlock && (current.content == null || current.content.isEmpty())
                    && !line.startsWith("#") && !line.startsWith(">") && !line.startsWith("```")) {
                current.content = line;
            }
        }
        return questions;
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

    public String inferQuestionType(MarkdownQuestionParser.RawQuestion raw) {
        if (raw.options.size() == 2) {
            Set<String> contents = new HashSet<>();
            raw.options.forEach(option -> contents.add(option.content));
            if ((contents.contains("对") && contents.contains("错"))
                    || (contents.contains("正确") && contents.contains("错误"))
                    || (contents.contains("True") && contents.contains("False"))) {
                return "TRUE_FALSE";
            }
        }
        if (raw.answer != null
                && (raw.answer.contains(",") || raw.answer.contains("，") || raw.answer.contains("、"))) {
            return "MULTIPLE_CHOICE";
        }
        return raw.options.isEmpty() ? "SHORT_ANSWER" : "SINGLE_CHOICE";
    }

    public List<QuestionCreateRequest.OptionItem> parseOptions(
            List<MarkdownQuestionParser.RawOption> options, String answer, String questionType) {
        List<QuestionCreateRequest.OptionItem> result = new ArrayList<>();
        Set<String> correctAnswers = parseCorrectAnswers(answer, questionType);
        for (int index = 0; index < options.size(); index++) {
            MarkdownQuestionParser.RawOption option = options.get(index);
            boolean correct = correctAnswers.contains(option.label)
                    || correctAnswers.contains(option.content)
                    || correctAnswers.contains(String.valueOf(index + 1));
            if ("TRUE_FALSE".equals(questionType)) {
                if ("A".equals(option.label) || "对".equals(option.content) || "正确".equals(option.content)) {
                    correct = answer != null && ("对".equals(answer.trim()) || "正确".equals(answer.trim())
                            || "A".equals(answer.trim().toUpperCase()));
                } else if (answer != null) {
                    correct = "错".equals(answer.trim()) || "错误".equals(answer.trim())
                            || "B".equals(answer.trim().toUpperCase());
                } else {
                    correct = !correct;
                }
            }
            result.add(createOption(option.content, option.label, correct, index + 1));
        }
        return result;
    }

    public QuestionCreateRequest.OptionItem createOption(
            String content, String label, boolean correct, int sortOrder) {
        QuestionCreateRequest.OptionItem item = new QuestionCreateRequest.OptionItem();
        item.setContent(content);
        item.setOptionLabel(label);
        item.setIsCorrect(correct ? 1 : 0);
        item.setSortOrder(sortOrder);
        return item;
    }

    public int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void applyField(MarkdownQuestionParser.RawQuestion current, String name, String value) {
        switch (name) {
            case "题干", "题目", "题干内容", "content" -> {
                current.content = value;
                current.inOptionsBlock = false;
            }
            case "选项", "options" -> {
                current.inOptionsBlock = true;
                if (!value.isEmpty()) { parseInlineOptions(current, value); }
            }
            case "答案", "answer" -> { current.answer = value; current.inOptionsBlock = false; }
            case "解析", "analysis" -> { current.analysis = value; current.inOptionsBlock = false; }
            case "课程", "course" -> { current.course = value; current.inOptionsBlock = false; }
            case "难度", "difficulty" -> {
                current.difficulty = parseInteger(value);
                current.inOptionsBlock = false;
            }
            case "知识点", "knowledgePoints", "知识点标签" -> {
                current.knowledgePoints = value;
                current.inOptionsBlock = false;
            }
            case "标签", "tags" -> { current.tags = value; current.inOptionsBlock = false; }
            case "分值", "score" -> { current.score = parseInteger(value); current.inOptionsBlock = false; }
            case "题型", "type" -> {
                current.questionType = normalizeQuestionType(value);
                current.inOptionsBlock = false;
            }
            default -> { }
        }
    }

    private void parseInlineOptions(MarkdownQuestionParser.RawQuestion raw, String value) {
        for (String candidate : value.split("[;；|｜]")) {
            String part = candidate.trim();
            Matcher matcher = OPTION_WITH_LABEL.matcher("- " + part);
            if (matcher.matches()) {
                raw.options.add(new MarkdownQuestionParser.RawOption(
                        matcher.group(1).toUpperCase(), matcher.group(2).trim()));
            } else {
                String label = String.valueOf((char) ('A' + raw.options.size()));
                raw.options.add(new MarkdownQuestionParser.RawOption(label, part));
            }
        }
    }

    private Set<String> parseCorrectAnswers(String answer, String questionType) {
        Set<String> result = new HashSet<>();
        if (answer == null || answer.trim().isEmpty()) { return result; }
        if ("TRUE_FALSE".equals(questionType)) {
            result.add(answer.trim());
            return result;
        }
        for (String part : answer.split("[,，、]")) { result.add(part.trim().toUpperCase()); }
        return result;
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
