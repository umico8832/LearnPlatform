package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.dto.QuestionImportResult;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 格式题目解析与导入服务
 *
 * 支持的 Markdown 格式：
 * <pre>
 * # 题目导入（标题可选，仅起装饰作用）
 *
 * ## 1. 单选题
 * **题干**: 以下哪个是 Java 的基本数据类型？
 *
 * **选项**:
 * - A. int
 * - B. String
 * - C. ArrayList
 * - D. HashMap
 *
 * **答案**: A
 *
 * **解析**: int 是 Java 的 8 种基本数据类型之一。
 *
 * **课程**: Java 基础
 * **难度**: 2
 * **知识点**: Java 语言基础, 面向对象
 * **标签**: 基础
 * **分值**: 2
 *
 * ---
 *
 * ## 2. 判断题
 * **题干**: Java 是一种编译型语言。
 *
 * **选项**:
 * - 对
 * - 错
 *
 * **答案**: 错
 *
 * **解析**: Java 既是编译型语言也是解释型语言。
 *
 * **课程**: Java 基础
 * **难度**: 1
 * </pre>
 *
 * 各字段说明：
 * - **题干**（必填）：题目内容，支持多行（后续非标签行视为题干续行）
 * - **选项**（选填）：以 `- A. ` 或 `- ` 开头，判断题可省略
 * - **答案**（必填）：单选用字母/文字，多选用 `A,B` 或 `A、B`
 * - **解析**（选填）：答案解析
 * - **课程**（必填）：课程名称，必须与数据库已有课程精确匹配
 * - **难度**（选填）：1-5，默认 3
 * - **知识点**（选填）：逗号分隔，不存在的知识点自动跳过
 * - **标签**（选填）：逗号分隔
 * - **分值**（选填）：默认 1
 * - **题型**（选填）：可通过 `## N. 题型名` 标题识别，也可在字段中显式指定
 */
@Service
public class MarkdownQuestionParser {

    private static final Logger log = LoggerFactory.getLogger(MarkdownQuestionParser.class);

    // 匹配题目标题: ## 1. 单选题 或 ## 单选题 或 ### 题目1
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,3}\\s+(?:\\d+\\.\\s*)?(.+)$");
    // 匹配字段: **字段名**: 值
    private static final Pattern FIELD_PATTERN = Pattern.compile("^\\*\\*(.+?)\\*\\*[:：]\\s*(.*)$");
    // 匹配选项: - A. 内容 或 - A、内容 或 - 内容
    private static final Pattern OPTION_WITH_LABEL = Pattern.compile("^-\\s+([A-Fa-f])[.、．]\\s*(.+)$");
    private static final Pattern OPTION_PLAIN = Pattern.compile("^-\\s+(.+)$");
    // 分隔线
    private static final Pattern DIVIDER_PATTERN = Pattern.compile("^-{3,}$|^\\*{3,}$");

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final CourseMapper courseMapper;
    private final KnowledgePointMapper knowledgePointMapper;

    public MarkdownQuestionParser(QuestionMapper questionMapper,
                                   QuestionOptionMapper questionOptionMapper,
                                   QuestionKnowledgePointMapper questionKnowledgePointMapper,
                                   CourseMapper courseMapper,
                                   KnowledgePointMapper knowledgePointMapper) {
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.courseMapper = courseMapper;
        this.knowledgePointMapper = knowledgePointMapper;
    }

    /**
     * 从 Markdown 输入流解析并导入题目
     */
    @Transactional
    public QuestionImportResult importFromMarkdown(InputStream inputStream, Long createBy) {
        QuestionImportResult result = new QuestionImportResult();

        // 读取并解析 Markdown
        List<RawQuestion> rawQuestions;
        try {
            rawQuestions = parseMarkdown(inputStream);
        } catch (Exception e) {
            log.error("Markdown 解析失败: {}", e.getMessage(), e);
            result.addError("Markdown 文件格式错误: " + e.getMessage());
            return result;
        }

        result.setTotalRows(rawQuestions.size());

        if (rawQuestions.isEmpty()) {
            result.addError("未在 Markdown 文件中识别到任何题目");
            return result;
        }

        // 预加载映射
        Map<String, Long> courseNameToId = buildCourseNameToIdMap();
        Map<String, Long> kpNameToId = buildKnowledgePointNameToIdMap();

        for (int i = 0; i < rawQuestions.size(); i++) {
            RawQuestion raw = rawQuestions.get(i);
            int rowNum = i + 1;
            Long insertedQuestionId = null;

            try {
                // 验证题干
                if (raw.content == null || raw.content.trim().isEmpty()) {
                    result.addError("第 " + rowNum + " 题：题干不能为空");
                    result.setFailCount(result.getFailCount() + 1);
                    continue;
                }

                // 验证题型
                String questionType = normalizeQuestionType(raw.questionType);
                if (questionType == null) {
                    // 尝试从选项数推断
                    questionType = inferQuestionType(raw);
                }
                if (questionType == null) {
                    result.addError("第 " + rowNum + " 题：无法识别题型，请在标题或字段中指定（单选/多选/判断/填空/简答）");
                    result.setFailCount(result.getFailCount() + 1);
                    continue;
                }

                // 查找课程
                Long courseId = null;
                if (raw.course != null && !raw.course.trim().isEmpty()) {
                    courseId = courseNameToId.get(raw.course.trim());
                    if (courseId == null) {
                        result.addError("第 " + rowNum + " 题：课程 '" + raw.course + "' 不存在");
                        result.setFailCount(result.getFailCount() + 1);
                        continue;
                    }
                } else {
                    result.addError("第 " + rowNum + " 题：课程名称不能为空");
                    result.setFailCount(result.getFailCount() + 1);
                    continue;
                }

                // 创建题目
                Question question = new Question();
                question.setContent(raw.content.trim());
                question.setQuestionType(questionType);
                question.setCourseId(courseId);
                question.setDifficulty(raw.difficulty != null ? clamp(raw.difficulty, 1, 5) : 3);
                question.setAnalysis(raw.analysis);
                question.setTags(raw.tags);
                question.setScore(raw.score != null ? raw.score : 1);
                question.setStatus(1);
                question.setCreateBy(createBy);
                question.setDeleted(0);
                questionMapper.insert(question);
                insertedQuestionId = question.getId();

                // 处理选项
                if ("SINGLE_CHOICE".equals(questionType) || "MULTIPLE_CHOICE".equals(questionType)
                        || "TRUE_FALSE".equals(questionType)) {
                    List<QuestionCreateRequest.OptionItem> optionItems;
                    if (raw.options.isEmpty() && "TRUE_FALSE".equals(questionType)) {
                        // 判断题自动生成选项
                        optionItems = new ArrayList<>();
                        boolean answerIsTrue = raw.answer != null && "对".equals(raw.answer.trim());
                        optionItems.add(createOption("对", "A", answerIsTrue, 1));
                        optionItems.add(createOption("错", "B", !answerIsTrue, 2));
                    } else {
                        optionItems = parseOptions(raw.options, raw.answer, questionType);
                    }
                    for (QuestionCreateRequest.OptionItem item : optionItems) {
                        QuestionOption option = new QuestionOption();
                        option.setQuestionId(question.getId());
                        option.setContent(item.getContent());
                        option.setOptionLabel(item.getOptionLabel());
                        option.setIsCorrect(item.getIsCorrect());
                        option.setSortOrder(item.getSortOrder());
                        option.setDeleted(0);
                        questionOptionMapper.insert(option);
                    }
                }

                // 处理知识点关联
                if (raw.knowledgePoints != null && !raw.knowledgePoints.trim().isEmpty()) {
                    String[] kpNames = raw.knowledgePoints.split("[,，]");
                    for (String kpName : kpNames) {
                        String trimmedName = kpName.trim();
                        if (!trimmedName.isEmpty()) {
                            Long kpId = kpNameToId.get(trimmedName);
                            if (kpId != null) {
                                QuestionKnowledgePoint qkp = new QuestionKnowledgePoint();
                                qkp.setQuestionId(question.getId());
                                qkp.setKnowledgePointId(kpId);
                                questionKnowledgePointMapper.insert(qkp);
                            }
                            // 知识点不存在时跳过，不阻断导入
                        }
                    }
                }

                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                cleanupFailedImport(insertedQuestionId);
                log.error("Markdown 导入题目失败，第 {} 题: {}", rowNum, e.getMessage(), e);
                result.addError("第 " + rowNum + " 题：导入失败 - " + e.getMessage());
                result.setFailCount(result.getFailCount() + 1);
            }
        }

        log.info("Markdown 题目导入完成：总题数={}，成功={}，失败={}", result.getTotalRows(),
                result.getSuccessCount(), result.getFailCount());
        return result;
    }

    /**
     * 解析 Markdown 内容为原始题目列表
     */
    List<RawQuestion> parseMarkdown(InputStream inputStream) throws Exception {
        List<RawQuestion> questions = new ArrayList<>();
        List<String> lines = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        RawQuestion current = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // 跳过空行和标题级的总标题
            if (line.isEmpty()) {
                continue;
            }

            // 检查是否是题目标题 (## )
            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.matches()) {
                String headingText = headingMatcher.group(1).trim();

                // 判断是否是题目标题（包含题型关键字，或者以数字开头）
                String typeGuess = normalizeQuestionType(headingText);
                boolean isNewQuestion = false;

                if (typeGuess != null) {
                    isNewQuestion = true;
                } else if (headingText.matches("^\\d+[.、．].*")) {
                    // "## 1. 题目标题" 格式 — 去掉序号
                    String afterNum = headingText.replaceFirst("^\\d+[.、．]\\s*", "").trim();
                    typeGuess = normalizeQuestionType(afterNum);
                    isNewQuestion = true;
                } else if (headingText.matches("^题目.*") || headingText.matches("^第.*题.*")) {
                    isNewQuestion = true;
                }

                if (isNewQuestion) {
                    current = new RawQuestion();
                    current.questionType = typeGuess;
                    questions.add(current);
                    continue;
                }

                // 非题目标题，跳过
                if (current == null) continue;
            }

            // 如果还没有题目，跳过
            if (current == null) continue;

            // 分隔线
            if (DIVIDER_PATTERN.matcher(line).matches()) {
                continue;
            }

            // 字段匹配
            Matcher fieldMatcher = FIELD_PATTERN.matcher(line);
            if (fieldMatcher.matches()) {
                String fieldName = fieldMatcher.group(1).trim();
                String fieldValue = fieldMatcher.group(2).trim();

                switch (fieldName) {
                    case "题干", "题目", "题干内容", "content" -> {
                        current.content = fieldValue;
                        current.inOptionsBlock = false;
                    }
                    case "选项", "options" -> {
                        current.inOptionsBlock = true;
                        // 如果值非空（同行写了选项），解析它
                        if (!fieldValue.isEmpty()) {
                            parseInlineOptions(current, fieldValue);
                        }
                    }
                    case "答案", "answer" -> {
                        current.answer = fieldValue;
                        current.inOptionsBlock = false;
                    }
                    case "解析", "analysis" -> {
                        current.analysis = fieldValue;
                        current.inOptionsBlock = false;
                    }
                    case "课程", "course" -> {
                        current.course = fieldValue;
                        current.inOptionsBlock = false;
                    }
                    case "难度", "difficulty" -> {
                        try {
                            current.difficulty = Integer.parseInt(fieldValue);
                        } catch (NumberFormatException e) {
                            // 忽略无效难度
                        }
                        current.inOptionsBlock = false;
                    }
                    case "知识点", "knowledgePoints", "知识点标签" -> {
                        current.knowledgePoints = fieldValue;
                        current.inOptionsBlock = false;
                    }
                    case "标签", "tags" -> {
                        current.tags = fieldValue;
                        current.inOptionsBlock = false;
                    }
                    case "分值", "score" -> {
                        try {
                            current.score = Integer.parseInt(fieldValue);
                        } catch (NumberFormatException e) {
                            // 忽略无效分值
                        }
                        current.inOptionsBlock = false;
                    }
                    case "题型", "type" -> {
                        current.questionType = normalizeQuestionType(fieldValue);
                        current.inOptionsBlock = false;
                    }
                    default -> {
                        // 未知字段，如果在选项块中可能仍需尝试解析
                        if (!current.inOptionsBlock) {
                            current.inOptionsBlock = false;
                        }
                    }
                }
                continue;
            }

            // 选项行匹配（- A. xxx）
            Matcher optionWithLabel = OPTION_WITH_LABEL.matcher(line);
            if (optionWithLabel.matches() || current.inOptionsBlock) {
                if (optionWithLabel.matches()) {
                    String label = optionWithLabel.group(1).toUpperCase();
                    String content = optionWithLabel.group(2).trim();
                    current.options.add(new RawOption(label, content));
                    continue;
                }

                // 无标签选项行
                Matcher plainOption = OPTION_PLAIN.matcher(line);
                if (plainOption.matches()) {
                    String content = plainOption.group(1).trim();
                    // 自动分配标签
                    String label = String.valueOf((char) ('A' + current.options.size()));
                    current.options.add(new RawOption(label, content));
                    continue;
                }
            }

            // 普通文本行 — 如果在选项块中但不匹配选项格式，结束选项块
            if (current.inOptionsBlock && !line.startsWith("-")) {
                current.inOptionsBlock = false;
            }

            // 如果不是字段也不是选项，且当前题目题干为空，当作题干续行
            if (!current.inOptionsBlock && (current.content == null || current.content.isEmpty())) {
                // 跳过纯 Markdown 格式标记
                if (!line.startsWith("#") && !line.startsWith(">") && !line.startsWith("```")) {
                    current.content = line;
                }
            }
        }

        return questions;
    }

    /**
     * 解析内联选项（同一行用分号或逗号分隔）
     */
    private void parseInlineOptions(RawQuestion raw, String value) {
        String[] parts = value.split("[;；|｜]");
        for (String part : parts) {
            part = part.trim();
            Matcher m = OPTION_WITH_LABEL.matcher("- " + part);
            if (m.matches()) {
                raw.options.add(new RawOption(m.group(1).toUpperCase(), m.group(2).trim()));
            } else {
                String label = String.valueOf((char) ('A' + raw.options.size()));
                raw.options.add(new RawOption(label, part));
            }
        }
    }

    /**
     * 标准化题型
     */
    String normalizeQuestionType(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        return switch (trimmed) {
            case "单选", "单选题", "SINGLE_CHOICE" -> "SINGLE_CHOICE";
            case "多选", "多选题", "MULTIPLE_CHOICE" -> "MULTIPLE_CHOICE";
            case "判断", "判断题", "TRUE_FALSE", "JUDGMENT" -> "TRUE_FALSE";
            case "填空", "填空题", "FILL_BLANK" -> "FILL_BLANK";
            case "简答", "简答题", "SHORT_ANSWER" -> "SHORT_ANSWER";
            default -> null;
        };
    }

    /**
     * 从原始数据推断题型
     */
    private String inferQuestionType(RawQuestion raw) {
        // 判断题：选项只有"对/错"或"正确/错误"
        if (raw.options.size() == 2) {
            Set<String> contents = new HashSet<>();
            for (RawOption opt : raw.options) {
                contents.add(opt.content);
            }
            if ((contents.contains("对") && contents.contains("错"))
                    || (contents.contains("正确") && contents.contains("错误"))
                    || (contents.contains("True") && contents.contains("False"))) {
                return "TRUE_FALSE";
            }
        }

        // 多选：答案包含多个
        if (raw.answer != null && (raw.answer.contains(",") || raw.answer.contains("，")
                || raw.answer.contains("、"))) {
            return "MULTIPLE_CHOICE";
        }

        // 有选项则默认单选
        if (!raw.options.isEmpty()) {
            return "SINGLE_CHOICE";
        }

        // 无选项，无答案类型线索 → 默认简答
        return "SHORT_ANSWER";
    }

    /**
     * 解析选项为 OptionItem 列表
     */
    private List<QuestionCreateRequest.OptionItem> parseOptions(List<RawOption> options, String answer,
                                                                   String questionType) {
        List<QuestionCreateRequest.OptionItem> result = new ArrayList<>();
        Set<String> correctAnswers = parseCorrectAnswers(answer, questionType);

        for (int i = 0; i < options.size(); i++) {
            RawOption opt = options.get(i);
            boolean isCorrect = correctAnswers.contains(opt.label)
                    || correctAnswers.contains(opt.content)
                    || correctAnswers.contains(String.valueOf(i + 1));

            // 判断题特殊处理
            if ("TRUE_FALSE".equals(questionType)) {
                if ("A".equals(opt.label) || "对".equals(opt.content) || "正确".equals(opt.content)) {
                    isCorrect = answer != null && ("对".equals(answer.trim()) || "正确".equals(answer.trim())
                            || "A".equals(answer.trim().toUpperCase()));
                } else {
                    isCorrect = !isCorrect; // 另一个选项取反
                    if (answer != null) {
                        isCorrect = "错".equals(answer.trim()) || "错误".equals(answer.trim())
                                || "B".equals(answer.trim().toUpperCase());
                    }
                }
            }

            result.add(createOption(opt.content, opt.label, isCorrect, i + 1));
        }

        return result;
    }

    /**
     * 解析正确答案为 Set
     */
    private Set<String> parseCorrectAnswers(String answer, String questionType) {
        Set<String> result = new HashSet<>();
        if (answer == null || answer.trim().isEmpty()) return result;

        if ("TRUE_FALSE".equals(questionType)) {
            result.add(answer.trim());
            return result;
        }

        // 多选答案用逗号/顿号分隔
        String[] parts = answer.split("[,，、]");
        for (String part : parts) {
            result.add(part.trim().toUpperCase());
        }
        return result;
    }

    private QuestionCreateRequest.OptionItem createOption(String content, String label,
                                                            boolean isCorrect, int sortOrder) {
        QuestionCreateRequest.OptionItem item = new QuestionCreateRequest.OptionItem();
        item.setContent(content);
        item.setOptionLabel(label);
        item.setIsCorrect(isCorrect ? 1 : 0);
        item.setSortOrder(sortOrder);
        return item;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private Map<String, Long> buildCourseNameToIdMap() {
        List<Course> courses = courseMapper.selectList(null);
        return courses.stream().collect(java.util.stream.Collectors.toMap(Course::getName, Course::getId, (a, b) -> a));
    }

    private Map<String, Long> buildKnowledgePointNameToIdMap() {
        List<KnowledgePoint> kps = knowledgePointMapper.selectList(null);
        return kps.stream().collect(java.util.stream.Collectors.toMap(KnowledgePoint::getName, KnowledgePoint::getId, (a, b) -> a));
    }

    private void cleanupFailedImport(Long questionId) {
        if (questionId == null) return;
        LambdaQueryWrapper<QuestionOption> optionWrapper = new LambdaQueryWrapper<>();
        optionWrapper.eq(QuestionOption::getQuestionId, questionId);
        questionOptionMapper.delete(optionWrapper);

        LambdaQueryWrapper<QuestionKnowledgePoint> kpWrapper = new LambdaQueryWrapper<>();
        kpWrapper.eq(QuestionKnowledgePoint::getQuestionId, questionId);
        questionKnowledgePointMapper.delete(kpWrapper);
        questionMapper.deleteById(questionId);
    }

    // ===== 内部数据结构 =====

    /** 解析过程中的原始题目数据 */
    static class RawQuestion {
        String questionType;
        String content;
        String answer;
        String analysis;
        String course;
        Integer difficulty;
        String knowledgePoints;
        String tags;
        Integer score;
        List<RawOption> options = new ArrayList<>();
        boolean inOptionsBlock = false;
    }

    /** 解析过程中的原始选项数据 */
    static class RawOption {
        String label;
        String content;

        RawOption(String label, String content) {
            this.label = label;
            this.content = content;
        }
    }
}