package com.learnplatform.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.dto.QuestionExcelDTO;
import com.learnplatform.dto.QuestionImportResult;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final QuestionExcelRowService rowService;

    public QuestionImportExportService(QuestionMapper questionMapper,
                                        QuestionOptionMapper questionOptionMapper,
                                        QuestionKnowledgePointMapper questionKnowledgePointMapper,
                                        CourseMapper courseMapper,
                                        KnowledgePointMapper knowledgePointMapper,
                                        QuestionExcelRowService rowService) {
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.courseMapper = courseMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.rowService = rowService;
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
        wrapper.eq(Question::getVisibility, "PUBLIC");
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
                .map(q -> rowService.toExcelRow(q, courseMap, kpMap))
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
                String questionType = rowService.normalizeQuestionType(row.getQuestionType());
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
                question.setSourceType("EXCEL_IMPORT");
                question.setReviewRounds(0);
                question.setNextReviewTime(java.time.LocalDateTime.now().plusDays(90));
                question.setDeleted(0);
                questionMapper.insert(question);
                insertedQuestionId = question.getId();

                // 处理选项
                if ("SINGLE_CHOICE".equals(questionType) || "MULTIPLE_CHOICE".equals(questionType)
                        || "TRUE_FALSE".equals(questionType)) {
                    List<QuestionCreateRequest.OptionItem> optionItems = rowService.parseOptions(row.getOptions(),
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
