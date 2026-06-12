package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.*;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 刷题与判分服务
 */
@Service
public class PracticeService {

    private static final Logger log = LoggerFactory.getLogger(PracticeService.class);

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final CourseMapper courseMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final WrongQuestionService wrongQuestionService;

    public PracticeService(QuestionMapper questionMapper,
                           QuestionOptionMapper questionOptionMapper,
                           QuestionKnowledgePointMapper questionKnowledgePointMapper,
                           PracticeRecordMapper practiceRecordMapper,
                           CourseMapper courseMapper,
                           KnowledgePointMapper knowledgePointMapper,
                           WrongQuestionService wrongQuestionService) {
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.courseMapper = courseMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.wrongQuestionService = wrongQuestionService;
    }

    /**
     * 获取练习题目列表
     * @param courseId 课程ID（可选）
     * @param knowledgePointId 知识点ID（可选）
     * @param questionType 题型（可选）
     * @param difficulty 难度（可选）
     * @param count 题目数量（默认10）
     * @return 题目列表（不含答案）
     */
    public List<QuestionVO> getPracticeQuestions(Long courseId, Long knowledgePointId,
                                                  String questionType, Integer difficulty,
                                                  Integer count) {
        if (count == null || count <= 0) {
            count = 10;
        }
        if (count > 50) {
            count = 50;
        }

        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getStatus, 1);

        if (courseId != null) {
            wrapper.eq(Question::getCourseId, courseId);
        }
        if (questionType != null && !questionType.isEmpty()) {
            wrapper.eq(Question::getQuestionType, questionType);
        }
        if (difficulty != null) {
            wrapper.eq(Question::getDifficulty, difficulty);
        }

        // 如果指定了知识点ID，先查询关联的题目ID
        if (knowledgePointId != null) {
            LambdaQueryWrapper<QuestionKnowledgePoint> kpWrapper = new LambdaQueryWrapper<>();
            kpWrapper.eq(QuestionKnowledgePoint::getKnowledgePointId, knowledgePointId);
            List<QuestionKnowledgePoint> qkps = questionKnowledgePointMapper.selectList(kpWrapper);
            List<Long> questionIds = qkps.stream()
                    .map(QuestionKnowledgePoint::getQuestionId)
                    .collect(Collectors.toList());
            if (questionIds.isEmpty()) {
                return new ArrayList<>();
            }
            wrapper.in(Question::getId, questionIds);
        }

        List<Question> questions = questionMapper.selectList(wrapper);

        // 随机选取指定数量
        if (questions.size() > count) {
            Collections.shuffle(questions);
            questions = questions.subList(0, count);
        }

        // 转换为 VO（练习模式不返回正确答案）
        return questions.stream().map(q -> {
            QuestionVO vo = QuestionVO.fromEntity(q);
            vo.setAnalysis(null); // 练习模式不返回解析
            fillQuestionVOForPractice(vo);
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 提交答案并判分
     */
    @Transactional
    public PracticeResultVO submitAnswer(PracticeSubmitRequest request, Long userId) {
        if (request.getQuestionId() == null) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "题目ID不能为空");
        }
        if (request.getUserAnswer() == null || request.getUserAnswer().trim().isEmpty()) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "答案不能为空");
        }

        Question question = questionMapper.selectById(request.getQuestionId());
        if (question == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }

        // 获取正确答案
        LambdaQueryWrapper<QuestionOption> optionWrapper = new LambdaQueryWrapper<>();
        optionWrapper.eq(QuestionOption::getQuestionId, question.getId())
                     .orderByAsc(QuestionOption::getSortOrder);
        List<QuestionOption> allOptions = questionOptionMapper.selectList(optionWrapper);

        List<QuestionOption> correctOptions = allOptions.stream()
                .filter(o -> o.getIsCorrect() != null && o.getIsCorrect() == 1)
                .collect(Collectors.toList());

        String correctAnswer = buildCorrectAnswer(correctOptions, question.getQuestionType());

        // 判分
        boolean isCorrect = checkAnswer(question.getQuestionType(), request.getUserAnswer().trim(), correctAnswer);

        // 保存答题记录
        PracticeRecord record = new PracticeRecord();
        record.setUserId(userId);
        record.setQuestionId(request.getQuestionId());
        record.setUserAnswer(request.getUserAnswer().trim());
        record.setIsCorrect(isCorrect ? 1 : 0);
        record.setAnswerTime(request.getAnswerTime());
        practiceRecordMapper.insert(record);

        // 自动处理错题本
        if (isCorrect) {
            // 答对了，从错题本移出
            try {
                wrongQuestionService.removeOnCorrect(userId, request.getQuestionId());
            } catch (Exception e) {
                log.warn("移出错题本失败: {}", e.getMessage());
            }
        } else {
            // 答错了，加入错题本
            try {
                wrongQuestionService.addWrongQuestion(userId, request.getQuestionId(), request.getUserAnswer().trim());
            } catch (Exception e) {
                log.warn("加入错题本失败: {}", e.getMessage());
            }
        }

        // 构建结果
        PracticeResultVO result = new PracticeResultVO();
        result.setRecordId(record.getId());
        result.setQuestionId(question.getId());
        result.setUserAnswer(request.getUserAnswer().trim());
        result.setCorrect(isCorrect);
        result.setCorrectAnswer(correctAnswer);
        result.setAnalysis(question.getAnalysis());
        result.setScore(question.getScore());

        return result;
    }

    /**
     * 获取用户的练习记录（分页）
     */
    public Page<PracticeRecordVO> getUserPracticeRecords(Long userId, int pageNum, int pageSize,
                                                          String questionType, Long courseId,
                                                          Integer isCorrect) {
        Page<PracticeRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PracticeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeRecord::getUserId, userId);
        wrapper.orderByDesc(PracticeRecord::getCreateTime);

        Page<PracticeRecord> result = practiceRecordMapper.selectPage(page, wrapper);

        Page<PracticeRecordVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(record -> {
                    PracticeRecordVO vo = new PracticeRecordVO();
                    vo.setId(record.getId());
                    vo.setQuestionId(record.getQuestionId());
                    vo.setUserAnswer(record.getUserAnswer());
                    vo.setIsCorrect(record.getIsCorrect());
                    vo.setAnswerTime(record.getAnswerTime());
                    vo.setCreateTime(record.getCreateTime());

                    // 查询题目信息
                    Question question = questionMapper.selectById(record.getQuestionId());
                    if (question != null) {
                        vo.setQuestionContent(question.getContent());
                        vo.setQuestionType(question.getQuestionType());
                        vo.setDifficulty(question.getDifficulty());
                        Course course = courseMapper.selectById(question.getCourseId());
                        if (course != null) {
                            vo.setCourseName(course.getName());
                        }
                    }
                    return vo;
                })
                .collect(Collectors.toList()));

        return voPage;
    }

    /**
     * 获取用户练习统计
     */
    public Map<String, Object> getUserPracticeStats(Long userId) {
        LambdaQueryWrapper<PracticeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeRecord::getUserId, userId);
        List<PracticeRecord> records = practiceRecordMapper.selectList(wrapper);

        int totalAnswered = records.size();
        int correctCount = (int) records.stream().filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1).count();
        int wrongCount = totalAnswered - correctCount;
        double correctRate = totalAnswered > 0 ? (double) correctCount / totalAnswered * 100 : 0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAnswered", totalAnswered);
        stats.put("correctCount", correctCount);
        stats.put("wrongCount", wrongCount);
        stats.put("correctRate", Math.round(correctRate * 10.0) / 10.0);

        return stats;
    }

    // ======================== 私有方法 ========================

    /**
     * 构建正确答案字符串
     */
    private String buildCorrectAnswer(List<QuestionOption> correctOptions, String questionType) {
        if ("TRUE_FALSE".equals(questionType)) {
            // 判断题：返回 "TRUE" 或 "FALSE"
            if (!correctOptions.isEmpty()) {
                return "TRUE".equals(correctOptions.get(0).getContent()) ||
                       "正确".equals(correctOptions.get(0).getContent()) ||
                       "true".equalsIgnoreCase(correctOptions.get(0).getContent())
                       ? "TRUE" : "FALSE";
            }
            return "";
        }

        // 选择题：返回选项标签，逗号分隔
        List<String> labels = correctOptions.stream()
                .map(QuestionOption::getOptionLabel)
                .sorted()
                .collect(Collectors.toList());
        return String.join(",", labels);
    }

    /**
     * 判分逻辑
     */
    private boolean checkAnswer(String questionType, String userAnswer, String correctAnswer) {
        if ("SINGLE_CHOICE".equals(questionType) || "TRUE_FALSE".equals(questionType)) {
            return userAnswer.equalsIgnoreCase(correctAnswer);
        }
        if ("MULTIPLE_CHOICE".equals(questionType)) {
            // 多选题：排序后比较
            Set<String> userSet = Arrays.stream(userAnswer.split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());
            Set<String> correctSet = Arrays.stream(correctAnswer.split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());
            return userSet.equals(correctSet);
        }
        if ("FILL_BLANK".equals(questionType)) {
            // 填空题：忽略前后空格，不区分大小写
            return userAnswer.trim().equalsIgnoreCase(correctAnswer.trim());
        }
        // 简答题：暂不自动判分，标记为错误
        return false;
    }

    /**
     * 填充 QuestionVO（练习模式，不返回正确答案标记）
     */
    private void fillQuestionVOForPractice(QuestionVO vo) {
        Course course = courseMapper.selectById(vo.getCourseId());
        if (course != null) {
            vo.setCourseName(course.getName());
        }

        // 填充选项（不含正确答案标记）
        LambdaQueryWrapper<QuestionOption> optionWrapper = new LambdaQueryWrapper<>();
        optionWrapper.eq(QuestionOption::getQuestionId, vo.getId())
                     .orderByAsc(QuestionOption::getSortOrder);
        List<QuestionOption> options = questionOptionMapper.selectList(optionWrapper);
        vo.setOptions(options.stream().map(o -> {
            QuestionOptionVO optVo = QuestionOptionVO.fromEntity(o);
            optVo.setIsCorrect(0); // 练习模式隐藏正确答案
            return optVo;
        }).collect(Collectors.toList()));

        // 填充知识点
        LambdaQueryWrapper<QuestionKnowledgePoint> kpWrapper = new LambdaQueryWrapper<>();
        kpWrapper.eq(QuestionKnowledgePoint::getQuestionId, vo.getId());
        List<QuestionKnowledgePoint> qkps = questionKnowledgePointMapper.selectList(kpWrapper);
        List<Long> kpIds = qkps.stream()
                .map(QuestionKnowledgePoint::getKnowledgePointId)
                .collect(Collectors.toList());
        vo.setKnowledgePointIds(kpIds);

        List<String> kpNames = new ArrayList<>();
        for (Long kpId : kpIds) {
            KnowledgePoint kp = knowledgePointMapper.selectById(kpId);
            if (kp != null) {
                kpNames.add(kp.getName());
            }
        }
        vo.setKnowledgePointNames(kpNames);
    }
}