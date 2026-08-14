package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.PrivateExamImportConfirmRequest;
import com.learnplatform.dto.PrivateExamImportPreviewVO;
import com.learnplatform.dto.PrivateExamImportRequest;
import com.learnplatform.dto.PrivateExamSourceVO;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamQuestion;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.entity.UserExamSource;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.mapper.UserExamSourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PrivateExamImportService {
    private static final int MAX_QUESTIONS = 100;
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE");
    private static final Pattern TEXT_FIELD = Pattern.compile(
            "^(题型|题干|题目|选项|答案|解析|分值|type|question|options|answer|analysis|score)[:：]\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXT_OPTION = Pattern.compile("^([A-Fa-f])[.、．]\\s*(.+)$");

    private final MarkdownQuestionParser markdownQuestionParser;
    private final CourseMapper courseMapper;
    private final UserExamSourceMapper sourceMapper;
    private final PrivateExamSourceStorageService sourceStorageService;
    private final ExamPaperMapper paperMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper optionMapper;
    private final ExamPaperService examPaperService;

    public PrivateExamImportService(MarkdownQuestionParser markdownQuestionParser,
                                    CourseMapper courseMapper,
                                    UserExamSourceMapper sourceMapper,
                                    PrivateExamSourceStorageService sourceStorageService,
                                    ExamPaperMapper paperMapper,
                                    ExamQuestionMapper examQuestionMapper,
                                    QuestionMapper questionMapper,
                                    QuestionOptionMapper optionMapper,
                                    ExamPaperService examPaperService) {
        this.markdownQuestionParser = markdownQuestionParser;
        this.courseMapper = courseMapper;
        this.sourceMapper = sourceMapper;
        this.sourceStorageService = sourceStorageService;
        this.paperMapper = paperMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.questionMapper = questionMapper;
        this.optionMapper = optionMapper;
        this.examPaperService = examPaperService;
    }

    public PrivateExamImportPreviewVO preview(PrivateExamImportRequest request) {
        return previewWithSourceHash(request, sha256(request.getContent()));
    }

    public PrivateExamImportPreviewVO previewWithSourceHash(PrivateExamImportRequest request, String sourceHash) {
        ensureCourseExists(request.getCourseId());
        List<ParsedQuestion> questions = parseAndValidate(request, false);
        return toPreview(request, questions, sourceHash);
    }

    @Transactional
    public ExamPaperVO confirm(PrivateExamImportConfirmRequest request, Long userId) {
        return confirmWithSourceHash(request, userId, sha256(request.getContent()));
    }

    @Transactional
    public ExamPaperVO confirmWithSourceHash(PrivateExamImportConfirmRequest request, Long userId,
                                             String sourceHash) {
        return confirmWithSourceFile(request, userId, sourceHash, null, null);
    }

    @Transactional
    public ExamPaperVO confirmWithSourceFile(PrivateExamImportConfirmRequest request, Long userId,
                                             String sourceHash, byte[] sourceFile, String sourceMediaType) {
        ensureCourseExists(request.getCourseId());
        if (!sourceHash.equalsIgnoreCase(request.getExpectedContentHash())) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "原始资料已变化，请重新预览确认");
        }
        List<ParsedQuestion> questions = parseAndValidate(request, true);

        UserExamSource source = new UserExamSource();
        source.setOwnerUserId(userId);
        source.setSourceName(request.getSourceName().trim());
        source.setSourceFormat(request.getSourceFormat());
        source.setContentSha256(sourceHash);
        source.setOriginalContent(request.getContent());
        sourceStorageService.attachFileWithinQuota(source, userId, sourceFile, sourceMediaType);
        sourceMapper.insert(source);
        PrivateExamImportPreviewVO preview = toPreview(request, questions, sourceHash);
        return createConfirmedPaper(preview.getTitle(), preview.getCourseId(), preview.getDuration(),
                source, preview.getQuestions(), userId);
    }

    public PrivateExamSourceVO getSource(Long paperId, Long userId) {
        ExamPaper paper = paperMapper.selectById(paperId);
        if (!isOwnedPrivatePaper(paper, userId) || paper.getSourceRecordId() == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "私有试卷不存在");
        }
        UserExamSource source = sourceMapper.selectById(paper.getSourceRecordId());
        if (source == null || !userId.equals(source.getOwnerUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "原始资料不存在");
        }
        PrivateExamSourceVO vo = new PrivateExamSourceVO();
        vo.setPaperId(paperId);
        vo.setSourceName(source.getSourceName());
        vo.setSourceFormat(source.getSourceFormat());
        vo.setContentHash(source.getContentSha256());
        vo.setOriginalContent(source.getOriginalContent());
        vo.setOriginalFileAvailable(source.getSourceSize() != null && source.getSourceSize() > 0);
        vo.setCreateTime(source.getCreateTime());
        return vo;
    }

    ExamPaperVO createConfirmedPaper(String title, Long courseId, Integer duration,
                                     UserExamSource source,
                                     List<PrivateExamImportPreviewVO.QuestionPreview> questions,
                                     Long userId) {
        if (source == null || source.getId() == null || !userId.equals(source.getOwnerUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "原始资料不存在");
        }
        for (int index = 0; index < questions.size(); index++) {
            validateConfirmedQuestion(questions.get(index), index + 1);
        }

        ExamPaper paper = new ExamPaper();
        paper.setTitle(title.trim());
        paper.setDescription("由用户复核确认的结构化" + formatLabel(source.getSourceFormat()) + "资料导入");
        paper.setCourseId(courseId);
        paper.setTotalScore(questions.stream().mapToInt(PrivateExamImportPreviewVO.QuestionPreview::getScore).sum());
        paper.setDuration(duration != null ? duration : 60);
        paper.setQuestionCount(questions.size());
        paper.setStatus(1);
        paper.setCreateBy(userId);
        paper.setOwnerUserId(userId);
        paper.setVisibility("PRIVATE");
        paper.setPaperType("USER_PRIVATE");
        paper.setSourceReference("user-source:" + source.getContentSha256());
        paper.setSourceVerified(false);
        paper.setSourceRecordId(source.getId());
        paper.setImportStatus("CONFIRMED");
        paper.setDeleted(0);
        paperMapper.insert(paper);

        for (int index = 0; index < questions.size(); index++) {
            PrivateExamImportPreviewVO.QuestionPreview item = questions.get(index);
            Question question = new Question();
            question.setContent(item.getContent());
            question.setQuestionType(item.getQuestionType());
            question.setCourseId(courseId);
            question.setDifficulty(3);
            question.setAnalysis(item.getAnalysis());
            question.setScore(item.getScore());
            question.setStatus(1);
            question.setCreateBy(userId);
            question.setOwnerUserId(userId);
            question.setVisibility("PRIVATE");
            question.setSourceType("USER_PRIVATE_IMPORT");
            question.setSourceReference("user-source:" + source.getId());
            question.setReviewRounds(0);
            question.setDeleted(0);
            questionMapper.insert(question);

            for (int optionIndex = 0; optionIndex < item.getOptions().size(); optionIndex++) {
                PrivateExamImportPreviewVO.OptionPreview itemOption = item.getOptions().get(optionIndex);
                QuestionOption option = new QuestionOption();
                option.setQuestionId(question.getId());
                option.setContent(itemOption.getContent());
                option.setOptionLabel(itemOption.getLabel());
                option.setIsCorrect(Boolean.TRUE.equals(itemOption.getCorrect()) ? 1 : 0);
                option.setSortOrder(optionIndex + 1);
                option.setDeleted(0);
                optionMapper.insert(option);
            }

            ExamQuestion relation = new ExamQuestion();
            relation.setExamPaperId(paper.getId());
            relation.setQuestionId(question.getId());
            relation.setSortOrder(index + 1);
            relation.setScore(item.getScore());
            relation.setDisplayNumber(String.valueOf(index + 1));
            examQuestionMapper.insert(relation);
        }
        return examPaperService.getAccessiblePublishedExamPaperById(paper.getId(), userId);
    }

    ExamPaperVO getConfirmedPaper(Long paperId, Long userId) {
        return examPaperService.getAccessiblePublishedExamPaperById(paperId, userId);
    }

    private void validateConfirmedQuestion(PrivateExamImportPreviewVO.QuestionPreview question, int number) {
        if (question.getContent() == null || question.getContent().isBlank()
                || !SUPPORTED_TYPES.contains(question.getQuestionType())
                || question.getScore() == null || question.getScore() < 1 || question.getScore() > 100
                || question.getOptions() == null || question.getOptions().size() < 2
                || question.getOptions().size() > 6) {
            throw invalid(number, "题目结构不完整");
        }
        long correctCount = question.getOptions().stream()
                .filter(option -> Boolean.TRUE.equals(option.getCorrect())).count();
        if (correctCount == 0) throw invalid(number, "答案必须匹配现有选项");
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
            if (line.isEmpty()) continue;
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
                current.options.add(new RawOption(option.group(1).toUpperCase(Locale.ROOT), option.group(2).trim()));
            }
        }
        addTextQuestion(result, current);
        return result;
    }

    private void addTextQuestion(List<ParsedQuestion> result, TextQuestion current) {
        if (current.content == null && current.options.isEmpty()) return;
        String type = current.type != null ? current.type
                : inferType(current.answer, current.options.size(), current.options.stream().map(RawOption::content).toList());
        result.add(buildParsed(type, current.content, current.answer, current.analysis, current.score, current.options));
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
        return new ParsedQuestion(content != null ? content.trim() : null, type, answer != null ? answer.trim() : null,
                analysis != null ? analysis.trim() : null, score != null ? score : 1, options);
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
        if (answer == null) return result;
        for (String value : answer.split("[,，、]")) {
            if (!value.isBlank()) result.add(value.trim().toUpperCase(Locale.ROOT));
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

    private void ensureCourseExists(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在");
        }
    }

    private PrivateExamImportPreviewVO toPreview(PrivateExamImportRequest request,
                                                 List<ParsedQuestion> questions, String hash) {
        PrivateExamImportPreviewVO preview = new PrivateExamImportPreviewVO();
        preview.setTitle(request.getTitle().trim());
        preview.setCourseId(request.getCourseId());
        preview.setDuration(request.getDuration() != null ? request.getDuration() : 60);
        preview.setSourceName(request.getSourceName().trim());
        preview.setSourceFormat(request.getSourceFormat());
        preview.setContentHash(hash);
        preview.setQuestionCount(questions.size());
        preview.setTotalScore(questions.stream().mapToInt(ParsedQuestion::score).sum());
        preview.setRequiresAnswerReview(questions.stream().anyMatch(question ->
                question.options().stream().noneMatch(ParsedOption::correct)));
        preview.setQuestions(questions.stream().map(question -> {
            PrivateExamImportPreviewVO.QuestionPreview item = new PrivateExamImportPreviewVO.QuestionPreview();
            item.setContent(question.content());
            item.setQuestionType(question.type());
            item.setAnswer(question.answer());
            item.setAnalysis(question.analysis());
            item.setScore(question.score());
            item.setAnswerComplete(question.options().stream().anyMatch(ParsedOption::correct));
            item.setOptions(question.options().stream().map(option ->
                    new PrivateExamImportPreviewVO.OptionPreview(
                            option.label(), option.content(), option.correct())).toList());
            return item;
        }).toList());
        return preview;
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算内容哈希", exception);
        }
    }

    private boolean isOwnedPrivatePaper(ExamPaper paper, Long userId) {
        return paper != null && "PRIVATE".equals(paper.getVisibility()) && userId.equals(paper.getOwnerUserId());
    }

    private String formatLabel(String format) {
        if ("MARKDOWN".equals(format)) return "Markdown";
        if ("PDF".equals(format)) return "PDF";
        return "DOCX".equals(format) ? "DOCX" : "文本";
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
