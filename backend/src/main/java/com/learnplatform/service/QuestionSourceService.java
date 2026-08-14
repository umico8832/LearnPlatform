package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.*;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionReviewRecord;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 题目来源追踪与复审服务
 */
@Service
public class QuestionSourceService {

    private static final Logger log = LoggerFactory.getLogger(QuestionSourceService.class);

    /** 默认复审周期（天） */
    private static final int DEFAULT_REVIEW_DAYS = 90;

    private final QuestionMapper questionMapper;
    private final QuestionReviewRecordMapper reviewRecordMapper;
    private final UserMapper userMapper;
    private final QuestionVersionService questionVersionService;

    public QuestionSourceService(QuestionMapper questionMapper,
                                  QuestionReviewRecordMapper reviewRecordMapper,
                                  UserMapper userMapper,
                                  QuestionVersionService questionVersionService) {
        this.questionMapper = questionMapper;
        this.reviewRecordMapper = reviewRecordMapper;
        this.userMapper = userMapper;
        this.questionVersionService = questionVersionService;
    }

    /**
     * 设置题目的来源信息（创建题目时调用）
     */
    public void setSource(Long questionId, String sourceType, String sourceReference) {
        Question question = questionMapper.selectById(questionId);
        if (question == null) return;
        question.setSourceType(sourceType);
        question.setSourceReference(sourceReference);
        // 新入库的题目默认 90 天后需要复审
        question.setNextReviewTime(LocalDateTime.now().plusDays(DEFAULT_REVIEW_DAYS));
        question.setReviewRounds(0);
        questionMapper.updateById(question);
        log.info("题目 {} 来源设置为 {} (reference={})", questionId, sourceType, sourceReference);
    }

    /**
     * 记录入库初审（投稿入库时自动记录）
     */
    public void recordInitialReview(Long questionId, Long reviewerId, String comment) {
        QuestionReviewRecord record = new QuestionReviewRecord();
        record.setQuestionId(questionId);
        record.setReviewerId(reviewerId);
        record.setReviewType("INITIAL");
        record.setAction("APPROVE");
        record.setComment(comment != null ? comment : "入库初审通过");
        reviewRecordMapper.insert(record);

        Question question = questionMapper.selectById(questionId);
        if (question != null) {
            question.setLastReviewTime(LocalDateTime.now());
            question.setReviewRounds(1);
            questionMapper.updateById(question);
        }
        log.info("题目 {} 入库初审完成，审核人 {}", questionId, reviewerId);
    }

    /**
     * 题目来源统计
     */
    public List<QuestionSourceStatsVO> getSourceStats() {
        // 查询所有未删除的题目来源类型分布
        List<Question> questions = questionMapper.selectList(
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getVisibility, "PUBLIC")
                        .select(Question::getSourceType));

        Map<String, Long> grouped = questions.stream()
                .collect(Collectors.groupingBy(
                        q -> q.getSourceType() != null ? q.getSourceType() : "MANUAL",
                        Collectors.counting()));

        // 保证所有来源类型都有统计
        List<String> allTypes = Arrays.asList("MANUAL", "SUBMISSION", "EXCEL_IMPORT", "MARKDOWN_IMPORT", "AI_GENERATED");
        return allTypes.stream()
                .map(type -> new QuestionSourceStatsVO(type, grouped.getOrDefault(type, 0L)))
                .collect(Collectors.toList());
    }

    /**
     * 获取待复审题目列表（next_review_time <= now，或从未设置过 next_review_time 的老题目）
     */
    public Page<Question> getOverdueReviews(int pageNum, int pageSize) {
        Page<Question> page = new Page<>(pageNum, pageSize);
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getVisibility, "PUBLIC");
        wrapper.and(w -> w
                .le(Question::getNextReviewTime, now)
                .or()
                .isNull(Question::getNextReviewTime));
        wrapper.eq(Question::getStatus, 1);
        wrapper.orderByAsc(Question::getNextReviewTime);
        return questionMapper.selectPage(page, wrapper);
    }

    /**
     * 获取指定题目的复审记录列表
     */
    public List<QuestionReviewRecordVO> getReviewRecords(Long questionId) {
        List<QuestionReviewRecord> records = reviewRecordMapper.selectList(
                new LambdaQueryWrapper<QuestionReviewRecord>()
                        .eq(QuestionReviewRecord::getQuestionId, questionId)
                        .orderByDesc(QuestionReviewRecord::getCreateTime));

        return records.stream().map(this::convertRecordToVO).collect(Collectors.toList());
    }

    /**
     * 执行复审（APPROVE/REVISE/REJECT）
     */
    @CacheEvict(value = "questionReviewSuggestion", key = "#questionId")
    @Transactional
    public QuestionReviewRecordVO performReReview(Long questionId, QuestionReReviewRequest request, Long reviewerId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null || "PRIVATE".equals(question.getVisibility())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }
        String snapshotBefore = questionVersionService.buildSnapshotJson(question);

        String action = request.getAction().toUpperCase();
        if (!Arrays.asList("APPROVE", "REVISE", "REJECT").contains(action)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "复审动作只能为 APPROVE/REVISE/REJECT");
        }

        // 创建复审记录（快照旧内容）
        QuestionReviewRecord record = new QuestionReviewRecord();
        record.setQuestionId(questionId);
        record.setReviewerId(reviewerId);
        record.setReviewType("REGULAR");
        record.setAction(action);
        record.setOldContent(question.getContent());
        record.setOldDifficulty(question.getDifficulty());
        record.setComment(request.getComment());

        if ("REVISE".equals(action)) {
            if (request.getNewContent() == null || request.getNewContent().isBlank()) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "修订时新题干不能为空");
            }
            record.setNewContent(request.getNewContent());
            record.setNewDifficulty(request.getNewDifficulty());

            // 更新题目内容
            question.setContent(request.getNewContent());
            if (request.getNewDifficulty() != null) {
                question.setDifficulty(request.getNewDifficulty());
            }
            log.info("题目 {} 复审修订，审核人 {}", questionId, reviewerId);
        } else if ("REJECT".equals(action)) {
            // 标记废弃
            question.setStatus(0);
            log.info("题目 {} 复审废弃，审核人 {}", questionId, reviewerId);
        } else {
            log.info("题目 {} 复审通过，审核人 {}", questionId, reviewerId);
        }

        // 更新复审信息
        question.setLastReviewTime(LocalDateTime.now());
        question.setNextReviewTime(LocalDateTime.now().plusDays(DEFAULT_REVIEW_DAYS));
        question.setReviewRounds(question.getReviewRounds() != null ? question.getReviewRounds() + 1 : 1);
        questionMapper.updateById(question);

        reviewRecordMapper.insert(record);
        questionVersionService.recordChangeSnapshots(questionId, "REVIEW_" + action, reviewerId,
                "题目复审：" + action, snapshotBefore,
                questionVersionService.buildSnapshotJson(questionMapper.selectById(questionId)));
        return convertRecordToVO(record);
    }

    /**
     * 查询来源类型列表（用于前端筛选下拉）
     */
    public List<String> getSourceTypes() {
        return Arrays.asList("MANUAL", "SUBMISSION", "EXCEL_IMPORT", "MARKDOWN_IMPORT", "AI_GENERATED");
    }

    // ========== private ==========

    private QuestionReviewRecordVO convertRecordToVO(QuestionReviewRecord r) {
        QuestionReviewRecordVO vo = new QuestionReviewRecordVO();
        vo.setId(r.getId());
        vo.setQuestionId(r.getQuestionId());
        vo.setReviewerId(r.getReviewerId());
        vo.setReviewType(r.getReviewType());
        vo.setAction(r.getAction());
        vo.setOldContent(r.getOldContent());
        vo.setNewContent(r.getNewContent());
        vo.setOldDifficulty(r.getOldDifficulty());
        vo.setNewDifficulty(r.getNewDifficulty());
        vo.setComment(r.getComment());
        vo.setCreateTime(r.getCreateTime());

        // 填充审核人名
        if (r.getReviewerId() != null) {
            User reviewer = userMapper.selectById(r.getReviewerId());
            if (reviewer != null) {
                vo.setReviewerName(reviewer.getNickname() != null ? reviewer.getNickname() : reviewer.getUsername());
            }
        }
        return vo;
    }
}
