package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.PrivateExamImportPreviewVO;
import com.learnplatform.dto.PrivateExamImportRequest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PrivateExamImportParserService {
    private static final int MAX_QUESTIONS = 100;
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE");
    private static final Pattern TEXT_FIELD = Pattern.compile(
            "^(题型|题干|题目|选项|答案|解析|分值|type|question|options|answer|analysis|score)[:：]\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXT_OPTION = Pattern.compile("^([A-Fa-f])[.、．]\\s*(.+)$");

    private final MarkdownQuestionParser markdownQuestionParser;

    public PrivateExamImportParserService(MarkdownQuestionParser markdownQuestionParser) {
        this.markdownQuestionParser = markdownQuestionParser;
    }

    public PrivateExamImportPreviewVO parse(
            PrivateExamImportRequest request, String sourceHash, boolean requireAnswers) {
        List<ParsedQuestion> questions = parseAndValidate(request, requireAnswers);
        PrivateExamImportPreviewVO preview = new PrivateExamImportPreviewVO();
        preview.setTitle(request.getTitle().trim());
        preview.setCourseId(request.getCourseId());
        preview.setDuration(request.getDuration() != null ? request.getDuration() : 60);
        preview.setSourceName(request.getSourceName().trim());
        preview.setSourceFormat(request.getSourceFormat());
        preview.setContentHash(sourceHash);
        preview.setQuestionCount(questions.size());
        preview.setTotalScore(questions.stream().mapToInt(ParsedQuestion::score).sum());
        preview.setRequiresAnswerReview(questions.stream().anyMatch(question ->
                question.options().stream().noneMatch(ParsedOption::correct)));
        preview.setQuestions(questions.stream().map(this::toQuestionPreview).toList());
        return preview;
    }

    public void validateConfirmedQuestion(
            PrivateExamImportPreviewVO.QuestionPreview question, int number) {
        if (question.getContent() == null || question.getContent().isBlank()
                || !SUPPORTED_TYPES.contains(question.getQuestionType())
                || question.getScore() == null || question.getScore() < 1 || question.getScore() > 100
                || question.getOptions() == null || question.getOptions().size() < 2
                || question.getOptions().size() > 6) {
            throw invalid(number, "题目结构不完整");
        }
        long correctCount = question.getOptions().stream()
                .filter(option -> Boolean.TRUE.equals(option.getCorrect())).count();
        if (correctCount == 0) {
            throw invalid(number, "答案必须匹配现有选项");
        }
        if (("SINGLE_CHOICE".equals(question.getQuestionType())
                || "TRUE_FALSE".equals(question.getQuestionType())) && correctCount != 1) {
            throw invalid(number, "单选或判断题只能有一个正确答案");
        }
        if ("MULTIPLE_CHOICE".equals(question.getQuestionType()) && correctCount < 2) {
            throw invalid(number, "多选题至少需要两个正确答案");
        }
    }

    private List<ParsedQuestion> parseAndValidate(PrivateExamImportRequest request, boolean requireAnswers) {
        List<ParsedQuestion> questions = "TEXT".equals(request.getSourceFormat())
                || "PDF".equals(request.getSourceFormat()) || "DOCX".equals(request.getSourceFormat())
                ? parseText(request.getContent()) : parseMarkdown(request.getContent());
        if (questions.isEmpty()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "未识别到题目");
        }
        if (questions.size() > MAX_QUESTIONS) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "一次最多导入100道题");
        }
        for (int index = 0; index < questions.size(); index++) {
            validateQuestion(questions.get(index), index + 1, requireAnswers);
        }
        return questions;
    }

    private List<ParsedQuestion> parseMarkdown(String content) {
        try {
            List<MarkdownQuestionParser.RawQuestion> rawQuestions = markdownQuestionParser.parseMarkdown(
                    new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            return rawQuestions.stream().map(this::fromMarkdown).toList();
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "Markdown 解析失败");
        }
    }

    private ParsedQuestion fromMarkdown(MarkdownQuestionParser.RawQuestion raw) {
        String type = raw.questionType != null ? raw.questionType : inferType(raw.answer, raw.options.size(),
                raw.options.stream().map(option -> option.content).toList());
        return buildParsed(type, raw.content, raw.answer, raw.analysis, raw.score,
                raw.options.stream().map(option -> new RawOption(option.label, option.content)).toList());
    }

    private List<ParsedQuestion> parseText(String content) {
        List<ParsedQuestion> result = new ArrayList<>();
        TextQuestion current = new TextQuestion();
        boolean optionsBlock = false;
        for (String rawLine : content.split("\\R", -1)) {
            String line = rawLine.trim();
            if (line.matches("^-{3,}$")) {
                addTextQuestion(result, current);
                current = new TextQuestion();
                optionsBlock = false;
                continue;
            }
            if (line.isEmpty()) {
                continue;
            }
            Matcher field = TEXT_FIELD.matcher(line);
            if (field.matches()) {
                String name = field.group(1).toLowerCase(Locale.ROOT);
                String value = field.group(2).trim();
                optionsBlock = "选项".equals(name) || "options".equals(name);
                switch (name) {
                    case "题型", "type" -> current.type = markdownQuestionParser.normalizeQuestionType(value);
                    case "题干", "题目", "question" -> current.content = value;
                    case "答案", "answer" -> current.answer = value;
                    case "解析", "analysis" -> current.analysis = value;
                    case "分值", "score" -> current.score = parseScore(value);
                    default -> { }
                }
                continue;
            }
            Matcher option = TEXT_OPTION.matcher(line);
            if (optionsBlock && option.matches()) {
                current.options.add(new RawOption(
                        option.group(1).toUpperCase(Locale.ROOT), option.group(2).trim()));
            }
        }
        addTextQuestion(result, current);
        return result;
    }

    private void addTextQuestion(List<ParsedQuestion> result, TextQuestion current) {
        if (current.content == null && current.options.isEmpty()) {
            return;
        }
        String type = current.type != null ? current.type
                : inferType(current.answer, current.options.size(),
                        current.options.stream().map(RawOption::content).toList());
        result.add(buildParsed(type, current.content, current.answer, current.analysis, current.score,
                current.options));
    }

    private ParsedQuestion buildParsed(String type, String content, String answer, String analysis,
                                       Integer score, List<RawOption> rawOptions) {
        Set<String> answers = answerSet(answer);
        List<ParsedOption> options = new ArrayList<>();
        for (RawOption option : rawOptions) {
            boolean correct = answers.contains(option.label().toUpperCase(Locale.ROOT))
                    || answers.contains(option.content().toUpperCase(Locale.ROOT));
            options.add(new ParsedOption(option.label().toUpperCase(Locale.ROOT), option.content(), correct));
        }
        return new ParsedQuestion(content != null ? content.trim() : null, type,
                answer != null ? answer.trim() : null, analysis != null ? analysis.trim() : null,
                score != null ? score : 1, options);
    }

    private void validateQuestion(ParsedQuestion question, int number, boolean requireAnswers) {
        if (question.content() == null || question.content().isBlank()) {
            throw invalid(number, "题干不能为空");
        }
        if (!SUPPORTED_TYPES.contains(question.type())) {
            throw invalid(number, "首期仅支持单选、多选和判断题");
        }
        if (question.score() < 1 || question.score() > 100) {
            throw invalid(number, "分值必须在1到100之间");
        }
        if (question.options().size() < 2 || question.options().size() > 6) {
            throw invalid(number, "选项数量必须在2到6之间");
        }
        long correctCount = question.options().stream().filter(ParsedOption::correct).count();
        if (correctCount == 0) {
            if (requireAnswers) {
                throw invalid(number, "答案必须匹配现有选项");
            }
            return;
        }
        if (("SINGLE_CHOICE".equals(question.type()) || "TRUE_FALSE".equals(question.type()))
                && correctCount != 1) {
            throw invalid(number, "单选或判断题只能有一个正确答案");
        }
        if ("MULTIPLE_CHOICE".equals(question.type()) && correctCount < 2) {
            throw invalid(number, "多选题至少需要两个正确答案");
        }
    }

    private PrivateExamImportPreviewVO.QuestionPreview toQuestionPreview(ParsedQuestion question) {
        PrivateExamImportPreviewVO.QuestionPreview preview = new PrivateExamImportPreviewVO.QuestionPreview();
        preview.setContent(question.content());
        preview.setQuestionType(question.type());
        preview.setAnswer(question.answer());
        preview.setAnalysis(question.analysis());
        preview.setScore(question.score());
        preview.setAnswerComplete(question.options().stream().anyMatch(ParsedOption::correct));
        preview.setOptions(question.options().stream().map(option ->
                new PrivateExamImportPreviewVO.OptionPreview(
                        option.label(), option.content(), option.correct())).toList());
        return preview;
    }

    private BusinessException invalid(int number, String message) {
        return new BusinessException(ResultCode.VALIDATION_ERROR, "第" + number + "题：" + message);
    }

    private String inferType(String answer, int optionCount, List<String> optionContents) {
        Set<String> contents = new HashSet<>(optionContents);
        if (optionCount == 2 && ((contents.contains("对") && contents.contains("错"))
                || (contents.contains("正确") && contents.contains("错误")))) {
            return "TRUE_FALSE";
        }
        return answerSet(answer).size() > 1 ? "MULTIPLE_CHOICE" : "SINGLE_CHOICE";
    }

    private Set<String> answerSet(String answer) {
        Set<String> result = new HashSet<>();
        if (answer == null) {
            return result;
        }
        for (String value : answer.split("[,，、]")) {
            if (!value.isBlank()) {
                result.add(value.trim().toUpperCase(Locale.ROOT));
            }
        }
        return result;
    }

    private Integer parseScore(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record RawOption(String label, String content) { }
    private record ParsedOption(String label, String content, boolean correct) { }
    private record ParsedQuestion(String content, String type, String answer, String analysis,
                                  int score, List<ParsedOption> options) { }

    private static class TextQuestion {
        private String type;
        private String content;
        private String answer;
        private String analysis;
        private Integer score;
        private final List<RawOption> options = new ArrayList<>();
    }
}
