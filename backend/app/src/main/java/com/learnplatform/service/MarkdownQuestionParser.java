package com.learnplatform.service;

import com.learnplatform.common.time.BusinessTime;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.dto.QuestionImportResult;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private final CourseMapper courseMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final MarkdownQuestionContentParser contentParser;
    private final QuestionImportRowWriter rowWriter;

    public MarkdownQuestionParser(CourseMapper courseMapper,
                                   KnowledgePointMapper knowledgePointMapper,
                                   MarkdownQuestionContentParser contentParser, QuestionImportRowWriter rowWriter) {
        this.courseMapper = courseMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.contentParser = contentParser;
        this.rowWriter = rowWriter;
    }

    /**
     * 从 Markdown 输入流解析并导入题目
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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

            try {
                // 验证题干
                if (raw.content == null || raw.content.trim().isEmpty()) {
                    result.addError("第 " + rowNum + " 题：题干不能为空");
                    result.setFailCount(result.getFailCount() + 1);
                    continue;
                }

                // 验证题型
                String questionType = contentParser.normalizeQuestionType(raw.questionType);
                if (questionType == null) {
                    // 尝试从选项数推断
                    questionType = contentParser.inferQuestionType(raw);
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
                question.setDifficulty(raw.difficulty != null ? contentParser.clamp(raw.difficulty, 1, 5) : 3);
                question.setAnalysis(raw.analysis);
                question.setTags(raw.tags);
                question.setScore(raw.score != null ? raw.score : 1);
                question.setStatus(1);
                question.setCreateBy(createBy);
                question.setSourceType("MARKDOWN_IMPORT");
                question.setReviewRounds(0);
                question.setNextReviewTime(BusinessTime.now().plusDays(90));
                question.setDeleted(0);

                List<QuestionCreateRequest.OptionItem> optionItems = List.of();
                if ("SINGLE_CHOICE".equals(questionType) || "MULTIPLE_CHOICE".equals(questionType)
                        || "TRUE_FALSE".equals(questionType)) {
                    if (raw.options.isEmpty() && "TRUE_FALSE".equals(questionType)) {
                        // 判断题自动生成选项
                        optionItems = new ArrayList<>();
                        boolean answerIsTrue = raw.answer != null && "对".equals(raw.answer.trim());
                        optionItems.add(contentParser.createOption("对", "A", answerIsTrue, 1));
                        optionItems.add(contentParser.createOption("错", "B", !answerIsTrue, 2));
                    } else {
                        optionItems = contentParser.parseOptions(raw.options, raw.answer, questionType);
                    }
                }

                List<Long> pointIds = new ArrayList<>();
                if (raw.knowledgePoints != null && !raw.knowledgePoints.trim().isEmpty()) {
                    String[] kpNames = raw.knowledgePoints.split("[,，]");
                    for (String kpName : kpNames) {
                        String trimmedName = kpName.trim();
                        if (!trimmedName.isEmpty()) {
                            Long kpId = kpNameToId.get(courseId + ":" + trimmedName);
                            if (kpId != null) {
                                pointIds.add(kpId);
                            }
                            // 知识点不存在时跳过，不阻断导入
                        }
                    }
                }

                rowWriter.write(question, optionItems, pointIds);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                log.error("Markdown 导入题目失败，第 {} 题: {}", rowNum, e.getMessage(), e);
                result.addError("第 " + rowNum + " 题：导入失败 - " + e.getMessage());
                result.setFailCount(result.getFailCount() + 1);
            }
        }

        log.info("Markdown 题目导入完成：总题数={}，成功={}，失败={}", result.getTotalRows(),
                result.getSuccessCount(), result.getFailCount());
        return result;
    }
    List<RawQuestion> parseMarkdown(InputStream inputStream) throws Exception {
        return contentParser.parse(inputStream);
    }

    String normalizeQuestionType(String input) {
        return contentParser.normalizeQuestionType(input);
    }
    private Map<String, Long> buildCourseNameToIdMap() {
        List<Course> courses = courseMapper.selectList(null);
        return courses.stream().collect(java.util.stream.Collectors.toMap(Course::getName, Course::getId, (a, b) -> a));
    }

    private Map<String, Long> buildKnowledgePointNameToIdMap() {
        List<KnowledgePoint> kps = knowledgePointMapper.selectList(null);
        return kps.stream().collect(java.util.stream.Collectors.toMap(kp -> kp.getCourseId() + ":" + kp.getName(),
                KnowledgePoint::getId, (a, b) -> a));
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
