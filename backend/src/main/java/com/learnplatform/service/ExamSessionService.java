package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamRecordVO;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamRecord;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Collectors;

/** Handles exam session creation, resumption, and timeout transitions. */
@Service
public class ExamSessionService {

    private static final Logger log = LoggerFactory.getLogger(ExamSessionService.class);
    static final int ACTIVE = 0;
    static final int TIMED_OUT = 2;
    static final ZoneId EXAM_ZONE = ZoneId.of("Asia/Shanghai");

    private final ExamRecordMapper examRecordMapper;
    private final ExamPaperMapper examPaperMapper;
    private final ExamRecordViewService examRecordViewService;
    private final Clock clock;

    public ExamSessionService(ExamRecordMapper examRecordMapper,
                              ExamPaperMapper examPaperMapper,
                              ExamRecordViewService examRecordViewService) {
        this(examRecordMapper, examPaperMapper, examRecordViewService, Clock.system(EXAM_ZONE));
    }

    ExamSessionService(ExamRecordMapper examRecordMapper,
                       ExamPaperMapper examPaperMapper,
                       ExamRecordViewService examRecordViewService,
                       Clock clock) {
        this.examRecordMapper = examRecordMapper;
        this.examPaperMapper = examPaperMapper;
        this.examRecordViewService = examRecordViewService;
        this.clock = clock;
    }

    public Page<ExamRecordVO> getExamList(Long userId, int pageNum, int pageSize) {
        Page<ExamRecord> page = new Page<>(pageNum, pageSize);
        Page<ExamRecord> result = examRecordMapper.selectPage(page, new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getUserId, userId)
                .orderByDesc(ExamRecord::getCreateTime));
        LocalDateTime now = currentExamTime();
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

        String activeKey = activeExamKey(userId, examPaperId);
        LocalDateTime now = currentExamTime();
        ExamRecord existing = lockActiveRecord(activeKey);
        if (existing != null) {
            if (!isExpired(existing, paper, now)) {
                return examRecordViewService.toRecordVO(existing, now);
            }
            markTimedOut(existing, now);
        }

        ExamRecord record = new ExamRecord();
        record.setUserId(userId);
        record.setExamPaperId(examPaperId);
        record.setStartTime(now);
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
            return examRecordViewService.toRecordVO(concurrent, currentExamTime());
        }
        return examRecordViewService.toRecordVO(record, now);
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
        if (record.getStatus() == ACTIVE && isExpired(record, paper, now)) {
            markTimedOut(record, now);
        }
        return examRecordViewService.toRecordVO(record, now);
    }

    static boolean isExpired(ExamRecord record, ExamPaper paper, LocalDateTime now) {
        int duration = paper.getDuration() != null ? paper.getDuration() : 60;
        return record.getStartTime() == null || !now.isBefore(record.getStartTime().plusMinutes(duration));
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
        return LocalDateTime.now(clock);
    }

    private void markTimedOut(ExamRecord record, LocalDateTime now) {
        record.setEndTime(now);
        record.setScore(0);
        record.setStatus(TIMED_OUT);
        record.setActiveExamKey(null);
        examRecordMapper.updateById(record);
    }
}
