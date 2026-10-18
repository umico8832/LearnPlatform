package com.learnplatform.service;

import com.learnplatform.common.time.BusinessTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamRecordVO;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamRecord;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamRecordMapper;
import com.learnplatform.service.exam.ExamAutomaticGradingGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Collectors;

/** Handles exam session creation, resumption, and timeout transitions. */
@Service
public class ExamSessionService {

    private static final Logger log = LoggerFactory.getLogger(ExamSessionService.class);
    static final int ACTIVE = 0;
    static final int TIMED_OUT = 2;
    static final ZoneId EXAM_ZONE = BusinessTime.ZONE;

    private final ExamRecordMapper examRecordMapper;
    private final ExamPaperMapper examPaperMapper;
    private final ExamRecordViewService examRecordViewService;
    private final Clock clock;
    private final ExamAutomaticGradingGuard gradingGuard;

    @Autowired
    public ExamSessionService(ExamRecordMapper examRecordMapper,
                              ExamPaperMapper examPaperMapper,
                              ExamRecordViewService examRecordViewService,
                              ExamAutomaticGradingGuard gradingGuard) {
        this(examRecordMapper, examPaperMapper, examRecordViewService, Clock.system(EXAM_ZONE), gradingGuard);
    }

    ExamSessionService(ExamRecordMapper examRecordMapper,
                       ExamPaperMapper examPaperMapper,
                       ExamRecordViewService examRecordViewService,
                       Clock clock, ExamAutomaticGradingGuard gradingGuard) {
        this.examRecordMapper = examRecordMapper;
        this.examPaperMapper = examPaperMapper;
        this.examRecordViewService = examRecordViewService;
        this.clock = clock;
        this.gradingGuard = gradingGuard;
    }

    public Page<ExamRecordVO> getExamList(Long userId, int pageNum, int pageSize) {
        Page<ExamRecord> page = new Page<>(pageNum, pageSize);
        Page<ExamRecord> result = examRecordMapper.selectPage(page, new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getUserId, userId)
                .orderByDesc(ExamRecord::getCreateTime));
        Instant now = clock.instant();
        Page<ExamRecordVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(record -> examRecordViewService.toRecordVO(record, now))
                .collect(Collectors.toList()));
        return voPage;
    }

    public ExamRecordVO startExam(Long examPaperId, Long userId) {
        log.info("开始考试: userId={}, examPaperId={}", userId, examPaperId);
        ExamPaper paper = examPaperMapper.selectById(examPaperId);
        if (paper == null || !canAccessPaper(paper, userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        }
        if (paper.getStatus() == null || paper.getStatus() != 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "试卷未发布");
        }
        gradingGuard.requirePaper(examPaperId);

        String activeKey = activeExamKey(userId, examPaperId);
        LocalDateTime now = currentExamTime();
        ExamRecord existing = lockActiveRecord(activeKey);
        if (existing != null) {
            if (!isExpired(existing, paper, clock.instant())) {
                return examRecordViewService.toRecordVO(existing, clock.instant());
            }
            markTimedOut(existing, now);
        }

        ExamRecord record = new ExamRecord();
        record.setUserId(userId);
        record.setExamPaperId(examPaperId);
        record.setStartTime(now);
        record.setStartInstantUtc(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        record.setTotalScore(paper.getTotalScore());
        record.setStatus(ACTIVE);
        record.setActiveExamKey(activeKey);
        try {
            examRecordMapper.insert(record);
        } catch (DuplicateKeyException exception) {
            ExamRecord concurrent = examRecordMapper.selectByActiveExamKeyForUpdate(activeKey);
            if (concurrent == null || concurrent.getStatus() == null || concurrent.getStatus() != ACTIVE) {
                throw exception;
            }
            return examRecordViewService.toRecordVO(concurrent, clock.instant());
        }
        return examRecordViewService.toRecordVO(record, clock.instant());
    }

    public ExamRecordVO getExamSession(Long examRecordId, Long userId) {
        ExamRecord record = examRecordMapper.selectByIdForUpdate(examRecordId);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "考试记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作");
        }
        ExamPaper paper = examPaperMapper.selectById(record.getExamPaperId());
        if (paper == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        }
        LocalDateTime now = currentExamTime();
        if (record.getStatus() == ACTIVE && isExpired(record, paper, clock.instant())) {
            markTimedOut(record, now);
        }
        return examRecordViewService.toRecordVO(record, clock.instant());
    }

    static Instant deadline(ExamRecord record, ExamPaper paper) {
        if (record.getStartTime() == null) {
            return Instant.MIN;
        }
        Instant start = record.getStartInstantUtc() == null
                ? record.getStartTime().atZone(BusinessTime.ZONE).toInstant()
                : record.getStartInstantUtc().toInstant(ZoneOffset.UTC);
        return start.plusSeconds((paper.getDuration() != null ? paper.getDuration() : 60) * 60L);
    }

    static boolean isExpired(ExamRecord record, ExamPaper paper, Instant now) {
        return !now.isBefore(deadline(record, paper));
    }

    private boolean canAccessPaper(ExamPaper paper, Long userId) {
        return paper.getVisibility() == null || "PUBLIC".equals(paper.getVisibility())
                || ("PRIVATE".equals(paper.getVisibility()) && userId.equals(paper.getOwnerUserId()));
    }

    private String activeExamKey(Long userId, Long examPaperId) {
        return "EXAM:" + userId + ":" + examPaperId;
    }

    private ExamRecord lockActiveRecord(String activeKey) {
        ExamRecord candidate = examRecordMapper.selectByActiveExamKey(activeKey);
        if (candidate == null) {
            return null;
        }
        ExamRecord locked = examRecordMapper.selectByIdForUpdate(candidate.getId());
        if (locked == null || locked.getStatus() == null || locked.getStatus() != ACTIVE
                || !activeKey.equals(locked.getActiveExamKey())) {
            return null;
        }
        return locked;
    }

    private LocalDateTime currentExamTime() {
        return LocalDateTime.ofInstant(clock.instant(), BusinessTime.ZONE);
    }

    private void markTimedOut(ExamRecord record, LocalDateTime now) {
        record.setEndTime(now);
        record.setScore(0);
        record.setStatus(TIMED_OUT);
        record.setActiveExamKey(null);
        examRecordMapper.updateById(record);
    }
}
