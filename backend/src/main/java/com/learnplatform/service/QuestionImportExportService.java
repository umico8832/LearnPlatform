package com.learnplatform.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.dto.QuestionExcelDTO;
import com.learnplatform.dto.QuestionImportResult;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 题目导入/导出服务
 */
@Service
public class QuestionImportExportService {

    private static final Logger log = LoggerFactory.getLogger(QuestionImportExportService.class);

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final CourseMapper courseMapper;
    private final KnowledgePointMapper knowledgePointMapper;

    public QuestionImportExportService(QuestionMapper questionMapper,
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
     * 导出题目到 Excel
     */
    public void exportQuestions(HttpServletResponse response, String questionType,
                                Long courseId, Integer difficulty) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("题目导出", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        // 查询题目
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        if (questionType != null && !questionType.isEmpty()) {
            wrapper.eq(Question::getQuestionType, questionType);
        }
        if (courseId != null) {
            wrapper.eq(Question::getCourseId, courseId);
        }
        if (difficulty != null) {
            wrapper.eq(Question::getDifficulty, difficulty);
        }
        wrapper.orderByAsc(Question::getId);
        List<Question> questions = questionMapper.selectList(wrapper);

        // 构建课程和知识点映射
        Map<Long, String> courseMap = buildCourseMap();
        Map<Long, String> kpMap = buildKnowledgePointMap();

        // 转换为 DTO
        List<QuestionExcelDTO> dataList = questions.stream()
                .map(q -> toExcelDTO(q, courseMap, kpMap))
                .collect(Collectors.toList());

        EasyExcel.write(response.getOutputStream(), QuestionExcelDTO.class)
                .sheet("题目")
                .doWrite(dataList);
    }

    /**
     * 下载导入模板
     */
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("题目导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        // 写示例数据
        List<QuestionExcelDTO> template = new ArrayList<>();
        QuestionExcelDTO example = new QuestionExcelDTO();
        example.setContent("以下哪个是 Java 的基本数据类型？");
        example.setQuestionType("SINGLE_CHOICE");
        example.setCourseName("Java 基础");
        example.setDifficulty(2);
        example.setOptions("A.int|B.String|C.ArrayList|D.HashMap");
        example.setAnswer("A");
        example.setAnalysis("int 是 Java 的 8 种基本数据类型之一，String、ArrayList、HashMap 是引用类型。");
        example.setScore(2);
        example.setTags("基础");
        example.setKnowledgePoints("Java 语言基础");
        template.add(example);

        QuestionExcelDTO example2 = new QuestionExcelDTO();
        example2.setContent("Java 是一种编译型语言。");
        example2.setQuestionType("TRUE_FALSE");
        example2.setCourseName("Java 基础");
        example2.setDifficulty(1);
        example2.setOptions("对|错");
        example2.setAnswer("错");
        example2.setAnalysis("Java 既是编译型语言（编译为字节码），也是解释型语言（JVM 解释执行字节码）。");
        example2.setScore(1);
        example2.setTags("基础");
        example2.setKnowledgePoints("Java 语言基础");
        template.add(example2);

        EasyExcel.write(response.getOutputStream(), QuestionExcelDTO.class)
                .sheet("题目导入模板")
                .doWrite(template);
    }

    /**
     * 从 Excel 导入题目
     */
    @Transactional
    public QuestionImportResult importQuestions(java.io.InputStream inputStream, Long createBy) {
        QuestionImportResult result = new QuestionImportResult();
        List<QuestionExcelDTO> rows = new ArrayList<>();

        // 读取 Excel
        EasyExcel.read(inputStream, QuestionExcelDTO.class, new ReadListener<QuestionExcelDTO>() {
            @Override
            public void invoke(QuestionExcelDTO data, AnalysisContext context) {
                rows.add(data);
            }
            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                // 读取完成
            }
        }).sheet().doRead();

        result.setTotalRows(rows.size());

        // 预加载课程映射
        Map<String, Long> courseNameToId = buildCourseNameToIdMap();
        // 预加载知识点映射
        Map<String, Long> kpNameToId = buildKnowledgePointNameToIdMap();

        for (int i = 0; i < rows.size(); i++) {
            QuestionExcelDTO row = rows.get(i);
            int rowNum = i + 2; // Excel 行号（第 1 行是表头）
            Long insertedQuestionId = null;

            try {
                // 验证必填字段
                if (row.getContent() == null || row.getContent().trim().isEmpty()) {
                    result.addError("第 " + rowNum + " 行：题干不能为空");
                    result.setFailCount(result.getFailCount() + 1);
                    continue;
                }
                if (row.getQuestionType() == null || row.getQuestionType().trim().isEmpty()) {
                    result.addError("第 " + rowNum + " 行：题型不能为空");
                    result.setFailCount(result.getFailCount() + 1);
                    continue;
                }

                // 验证题型
                String questionType = normalizeQuestionType(row.getQuestionType());
                if (questionType == null) {
                    result.addError("第 " + rowNum + " 行：不支持的题型 '" + row.getQuestionType()
                            + "'，支持：单选/多选/判断/填空/简答 或 SINGLE_CHOICE/MULTIPLE_CHOICE/TRUE_FALSE/FILL_BLANK/SHORT_ANSWER");
                    result.setFailCount(result.getFailCount() + 1);
                    continue;
                }

                // 查找课程
                Long courseId = null;
                if (row.getCourseName() != null && !row.getCourseName().trim().isEmpty()) {
                    courseId = courseNameToId.get(row.getCourseName().trim());
                    if (courseId == null) {
                        result.addError("第 " + rowNum + " 行：课程 '" + row.getCourseName() + "' 不存在");
                        result.setFailCount(result.getFailCount() + 1);
                        continue;
                    }
                } else {
                    result.addError("第 " + rowNum + " 行：课程名称不能为空");
                    result.setFailCount(result.getFailCount() + 1);
                    continue;
                }

                // 创建题目
                Question question = new Question();
                question.setContent(row.getContent().trim());
                question.setQuestionType(questionType);
                question.setCourseId(courseId);
                question.setDifficulty(row.getDifficulty() != null ? row.getDifficulty() : 3);
                question.setAnalysis(row.getAnalysis());
                question.setTags(row.getTags());
                question.setScore(row.getScore() != null ? row.getScore() : 1);
                question.setStatus(1);
                question.setCreateBy(createBy);
                question.setDeleted(0);
                questionMapper.insert(question);
                insertedQuestionId = question.getId();

                // 处理选项
                if ("SINGLE_CHOICE".equals(questionType) || "MULTIPLE_CHOICE".equals(questionType)
                        || "TRUE_FALSE".equals(questionType)) {
                    List<QuestionCreateRequest.OptionItem> optionItems = parseOptions(row.getOptions(),
                            row.getAnswer(), questionType);
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
                if (row.getKnowledgePoints() != null && !row.getKnowledgePoints().trim().isEmpty()) {
                    String[] kpNames = row.getKnowledgePoints().split(",");
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
                            // 知识点不存在时不阻断导入，只跳过
                        }
                    }
                }

                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                cleanupFailedImport(insertedQuestionId);
                log.error("导入题目失败，第 {} 行: {}", rowNum, e.getMessage(), e);
                result.addError("第 " + rowNum + " 行：导入失败 - " + e.getMessage());
                result.setFailCount(result.getFailCount() + 1);
            }
        }

        log.info("题目导入完成：总行数={}，成功={}，失败={}", result.getTotalRows(),
                result.getSuccessCount(), result.getFailCount());
        return result;
    }

    /**
     * 标准化题型
     */
    private String normalizeQuestionType(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        // 支持中文题型名
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
     * 解析选项字符串为 OptionItem 列表
     * 格式: "A.选项1|B.选项2|C.选项3" 或 "A.选项1,B.选项2" 或 "对|错"
     * answer: "A" 或 "A,B" 或 "对"/"错"
     */
    private List<QuestionCreateRequest.OptionItem> parseOptions(String optionsStr, String answer,
                                                                  String questionType) {
        List<QuestionCreateRequest.OptionItem> result = new ArrayList<>();
        if (optionsStr == null || optionsStr.trim().isEmpty()) {
            // 判断题自动生成选项
            if ("TRUE_FALSE".equals(questionType)) {
                result.add(createOption("对", "A", answer != null && "对".equals(answer.trim()), 1));
                result.add(createOption("错", "B", answer != null && "错".equals(answer.trim()), 2));
            }
            return result;
        }

        // 拆分选项
        String[] parts = optionsStr.split("[|｜]");
        Set<String> correctAnswers = parseCorrectAnswers(answer, questionType);

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            String label;
            String content;

            // 尝试提取选项标号 "A.xxx" → label="A", content="xxx"
            if (part.length() >= 2 && part.charAt(1) == '.') {
                label = String.valueOf(part.charAt(0)).toUpperCase();
                content = part.substring(2).trim();
            } else if (part.length() >= 2 && part.charAt(1) == '、') {
                label = String.valueOf(part.charAt(0)).toUpperCase();
                content = part.substring(2).trim();
            } else {
                label = String.valueOf((char) ('A' + i));
                content = part;
            }

            boolean isCorrect = correctAnswers.contains(label) || correctAnswers.contains(content);
            result.add(createOption(content, label, isCorrect, i + 1));
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

        // 多选答案用逗号分隔: "A,B,C"
        String[] parts = answer.split("[,，]");
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

    /**
     * Question → QuestionExcelDTO
     */
    private QuestionExcelDTO toExcelDTO(Question q, Map<Long, String> courseMap,
                                          Map<Long, String> kpMap) {
        QuestionExcelDTO dto = new QuestionExcelDTO();
        dto.setContent(q.getContent());
        dto.setQuestionType(displayQuestionType(q.getQuestionType()));
        dto.setCourseName(courseMap.getOrDefault(q.getCourseId(), ""));
        dto.setDifficulty(q.getDifficulty());
        dto.setAnalysis(q.getAnalysis());
        dto.setScore(q.getScore());
        dto.setTags(q.getTags());

        // 获取选项
        LambdaQueryWrapper<QuestionOption> optWrapper = new LambdaQueryWrapper<>();
        optWrapper.eq(QuestionOption::getQuestionId, q.getId())
                  .orderByAsc(QuestionOption::getSortOrder);
        List<QuestionOption> options = questionOptionMapper.selectList(optWrapper);

        if (!options.isEmpty()) {
            StringBuilder optStr = new StringBuilder();
            String answerStr = "";
            List<String> correctLabels = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                QuestionOption opt = options.get(i);
                if (i > 0) optStr.append("|");
                String label = opt.getOptionLabel() != null ? opt.getOptionLabel()
                        : String.valueOf((char) ('A' + i));
                optStr.append(label).append(".").append(opt.getContent());
                if (opt.getIsCorrect() != null && opt.getIsCorrect() == 1) {
                    correctLabels.add(label);
                }
            }
            dto.setOptions(optStr.toString());
            dto.setAnswer(String.join(",", correctLabels));
        }

        // 获取知识点
        LambdaQueryWrapper<QuestionKnowledgePoint> kpWrapper = new LambdaQueryWrapper<>();
        kpWrapper.eq(QuestionKnowledgePoint::getQuestionId, q.getId());
        List<QuestionKnowledgePoint> qkps = questionKnowledgePointMapper.selectList(kpWrapper);
        if (!qkps.isEmpty()) {
            String kpNames = qkps.stream()
                    .map(qkp -> kpMap.getOrDefault(qkp.getKnowledgePointId(), ""))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(","));
            dto.setKnowledgePoints(kpNames);
        }

        return dto;
    }

    private String displayQuestionType(String type) {
        if (type == null) return "";
        return switch (type) {
            case "SINGLE_CHOICE" -> "单选";
            case "MULTIPLE_CHOICE" -> "多选";
            case "TRUE_FALSE" -> "判断";
            case "FILL_BLANK" -> "填空";
            case "SHORT_ANSWER" -> "简答";
            default -> type;
        };
    }

    private Map<Long, String> buildCourseMap() {
        List<Course> courses = courseMapper.selectList(null);
        return courses.stream().collect(Collectors.toMap(Course::getId, Course::getName, (a, b) -> a));
    }

    private Map<Long, String> buildKnowledgePointMap() {
        List<KnowledgePoint> kps = knowledgePointMapper.selectList(null);
        return kps.stream().collect(Collectors.toMap(KnowledgePoint::getId, KnowledgePoint::getName, (a, b) -> a));
    }

    private Map<String, Long> buildCourseNameToIdMap() {
        List<Course> courses = courseMapper.selectList(null);
        return courses.stream().collect(Collectors.toMap(Course::getName, Course::getId, (a, b) -> a));
    }

    private Map<String, Long> buildKnowledgePointNameToIdMap() {
        List<KnowledgePoint> kps = knowledgePointMapper.selectList(null);
        return kps.stream().collect(Collectors.toMap(KnowledgePoint::getName, KnowledgePoint::getId, (a, b) -> a));
    }

    private void cleanupFailedImport(Long questionId) {
        if (questionId == null) {
            return;
        }
        LambdaQueryWrapper<QuestionOption> optionWrapper = new LambdaQueryWrapper<>();
        optionWrapper.eq(QuestionOption::getQuestionId, questionId);
        questionOptionMapper.delete(optionWrapper);

        LambdaQueryWrapper<QuestionKnowledgePoint> kpWrapper = new LambdaQueryWrapper<>();
        kpWrapper.eq(QuestionKnowledgePoint::getQuestionId, questionId);
        questionKnowledgePointMapper.delete(kpWrapper);
        questionMapper.deleteById(questionId);
    }
}
