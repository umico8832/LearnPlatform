package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.exception.ExamTimedOutException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamSubmitRequest;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamRecord;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/** Coordinates a submitted paper's locked record, grading, and completion state. */
@Service
public class ExamSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(ExamSubmissionService.class);
    private static final int COMPLETED = 1;
    private static final int TIMED_OUT = 2;
    private static final int PENDING_REVIEW = 3;

    private final ExamRecordMapper examRecordMapper;
    private final ExamPaperMapper examPaperMapper;
    private final ExamAnswerSubmissionService examAnswerSubmissionService;
    private final CacheEvictService cacheEvictService;
    private final Clock clock;
    private final ContentReferenceLockService referenceLocks;

    @Autowired
    public ExamSubmissionService(ExamRecordMapper examRecordMapper,
                                 ExamPaperMapper examPaperMapper,
                                 ExamAnswerSubmissionService examAnswerSubmissionService,
                                 CacheEvictService cacheEvictService, ContentReferenceLockService referenceLocks) {
        this(examRecordMapper, examPaperMapper, examAnswerSubmissionService, cacheEvictService,
                Clock.system(ExamSessionService.EXAM_ZONE), referenceLocks);
    }

    ExamSubmissionService(ExamRecordMapper examRecordMapper,
                          ExamPaperMapper examPaperMapper,
                          ExamAnswerSubmissionService examAnswerSubmissionService,
                          CacheEvictService cacheEvictService,
                          Clock clock, ContentReferenceLockService referenceLocks) {
        this.examRecordMapper = examRecordMapper;
        this.examPaperMapper = examPaperMapper;
        this.examAnswerSubmissionService = examAnswerSubmissionService;
        this.cacheEvictService = cacheEvictService;
        this.clock = clock;
        this.referenceLocks = referenceLocks;
    }

    public Long submitExam(ExamSubmitRequest request, Long userId) {
        log.info("提交考试: userId={}, examRecordId={}", userId, request.getExamRecordId());
        ExamRecord preview = examRecordMapper.selectById(request.getExamRecordId());
        if (preview == null) { throw new BusinessException(ResultCode.NOT_FOUND, "考试记录不存在"); }
        ExamPaper previewPaper = examPaperMapper.selectById(preview.getExamPaperId());
        if (previewPaper == null) { throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在"); }
        referenceLocks.lockPaperQuestions(previewPaper.getId());
        ExamRecord record = examRecordMapper.selectByIdForUpdate(request.getExamRecordId());
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "考试记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作");
        }
        if (record.getStatus() != ExamSessionService.ACTIVE) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "考试已结束");
        }
        ExamPaper paper = examPaperMapper.selectById(record.getExamPaperId());
        if (paper == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        }

        LocalDateTime now = currentExamTime();
        if (ExamSessionService.isExpired(record, paper, clock.instant())) {
            markTimedOut(record, now);
            throw new ExamTimedOutException();
        }

        ExamAnswerSubmissionService.GradingSummary summary = examAnswerSubmissionService.gradeAndSave(
                request, record, paper, userId);
        log.info("考试判分完成: userId={}, examRecordId={}, score={}/{}", userId, record.getId(),
                summary.earnedScore(), summary.totalScore());
        record.setEndTime(currentExamTime());
        record.setScore(summary.earnedScore());
        record.setTotalScore(summary.totalScore());
        record.setStatus(summary.hasPendingReview() ? PENDING_REVIEW : COMPLETED);
        record.setActiveExamKey(null);
        examRecordMapper.updateById(record);
        cacheEvictService.evictUserStatistics(userId);
        return record.getId();
    }

    private LocalDateTime currentExamTime() {
        return LocalDateTime.ofInstant(clock.instant(), ExamSessionService.EXAM_ZONE);
    }

    private void markTimedOut(ExamRecord record, LocalDateTime now) {
        record.setEndTime(now);
        record.setScore(0);
        record.setStatus(TIMED_OUT);
        record.setActiveExamKey(null);
        examRecordMapper.updateById(record);
    }
}
