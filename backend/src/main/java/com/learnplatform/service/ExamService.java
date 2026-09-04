package com.learnplatform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.ExamTimedOutException;
import com.learnplatform.dto.ExamRecordVO;
import com.learnplatform.dto.ExamSubmitRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户端考试兼容门面，保留既有 API 与事务边界。
 */
@Service
public class ExamService {

    private final ExamSessionService examSessionService;
    private final ExamSubmissionService examSubmissionService;
    private final ExamRecordViewService examRecordViewService;

    public ExamService(ExamSessionService examSessionService,
                       ExamSubmissionService examSubmissionService,
                       ExamRecordViewService examRecordViewService) {
        this.examSessionService = examSessionService;
        this.examSubmissionService = examSubmissionService;
        this.examRecordViewService = examRecordViewService;
    }

    public Page<ExamRecordVO> getExamList(Long userId, int pageNum, int pageSize) {
        return examSessionService.getExamList(userId, pageNum, pageSize);
    }

    @Transactional
    public ExamRecordVO startExam(Long examPaperId, Long userId) {
        return examSessionService.startExam(examPaperId, userId);
    }

    @Transactional
    public ExamRecordVO getExamSession(Long examRecordId, Long userId) {
        return examSessionService.getExamSession(examRecordId, userId);
    }

    @Transactional(noRollbackFor = ExamTimedOutException.class)
    public ExamRecordVO submitExam(ExamSubmitRequest request, Long userId) {
        Long examRecordId = examSubmissionService.submitExam(request, userId);
        return examRecordViewService.getExamResult(examRecordId, userId);
    }

    public ExamRecordVO getExamResult(Long examRecordId, Long userId) {
        return examRecordViewService.getExamResult(examRecordId, userId);
    }

}
