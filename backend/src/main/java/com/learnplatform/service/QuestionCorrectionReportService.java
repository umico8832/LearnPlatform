package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.QuestionCorrectionProcessRequest;
import com.learnplatform.dto.QuestionCorrectionReportRequest;
import com.learnplatform.dto.QuestionCorrectionReportVO;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionCorrectionReport;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.QuestionCorrectionReportMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** 题目纠错反馈服务。 */
@Service
public class QuestionCorrectionReportService {

    private static final Set<String> REPORT_TYPES = Set.of(
            "CONTENT", "ANSWER", "ANALYSIS", "KNOWLEDGE_POINT", "OTHER");
    private static final Set<String> PROCESS_STATUSES = Set.of("OPEN", "RESOLVED", "REJECTED");

    private final QuestionCorrectionReportMapper correctionReportMapper;
    private final QuestionMapper questionMapper;
    private final UserMapper userMapper;

    public QuestionCorrectionReportService(QuestionCorrectionReportMapper correctionReportMapper,
                                           QuestionMapper questionMapper,
                                           UserMapper userMapper) {
        this.correctionReportMapper = correctionReportMapper;
        this.questionMapper = questionMapper;
        this.userMapper = userMapper;
    }

    @Transactional
    public QuestionCorrectionReportVO submitReport(Long questionId,
                                                   QuestionCorrectionReportRequest request,
                                                   Long reporterId) {
        Question question = questionMapper.selectById(questionId);
        if (question == null || question.getStatus() == null || question.getStatus() != 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }

        String reportType = normalizeReportType(request.getReportType());
        QuestionCorrectionReport report = new QuestionCorrectionReport();
        report.setQuestionId(questionId);
        report.setReporterId(reporterId);
        report.setReportType(reportType);
        report.setDescription(request.getDescription().trim());
        report.setStatus("OPEN");
        report.setDeleted(0);
        correctionReportMapper.insert(report);

        QuestionCorrectionReportVO vo = QuestionCorrectionReportVO.fromEntity(report);
        vo.setQuestionContent(question.getContent());
        fillUserNames(vo);
        return vo;
    }

    public Page<QuestionCorrectionReportVO> getMyReports(Long reporterId, int pageNum, int pageSize, String status) {
        Page<QuestionCorrectionReport> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<QuestionCorrectionReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionCorrectionReport::getReporterId, reporterId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(QuestionCorrectionReport::getStatus, normalizeStatus(status));
        }
        wrapper.orderByDesc(QuestionCorrectionReport::getCreateTime);
        return convertPage(correctionReportMapper.selectPage(page, wrapper));
    }

    public Page<QuestionCorrectionReportVO> getAdminReports(int pageNum, int pageSize, String status, Long questionId) {
        Page<QuestionCorrectionReport> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<QuestionCorrectionReport> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(QuestionCorrectionReport::getStatus, normalizeStatus(status));
        }
        if (questionId != null) {
            wrapper.eq(QuestionCorrectionReport::getQuestionId, questionId);
        }
        wrapper.orderByDesc(QuestionCorrectionReport::getCreateTime);
        return convertPage(correctionReportMapper.selectPage(page, wrapper));
    }

    @Transactional
    public QuestionCorrectionReportVO processReport(Long reportId,
                                                    QuestionCorrectionProcessRequest request,
                                                    Long handlerId) {
        QuestionCorrectionReport report = correctionReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "纠错记录不存在");
        }

        String status = normalizeStatus(request.getStatus());
        report.setStatus(status);
        report.setHandlerId(handlerId);
        report.setHandlerComment(request.getHandlerComment().trim());
        report.setHandledTime("OPEN".equals(status) ? null : LocalDateTime.now());
        correctionReportMapper.updateById(report);

        QuestionCorrectionReportVO vo = QuestionCorrectionReportVO.fromEntity(report);
        fillQuestionContent(vo);
        fillUserNames(vo);
        return vo;
    }

    public List<String> getReportTypes() {
        return Arrays.asList("CONTENT", "ANSWER", "ANALYSIS", "KNOWLEDGE_POINT", "OTHER");
    }

    private Page<QuestionCorrectionReportVO> convertPage(Page<QuestionCorrectionReport> page) {
        Page<QuestionCorrectionReportVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream()
                .map(report -> {
                    QuestionCorrectionReportVO vo = QuestionCorrectionReportVO.fromEntity(report);
                    fillQuestionContent(vo);
                    fillUserNames(vo);
                    return vo;
                })
                .collect(Collectors.toList()));
        return voPage;
    }

    private String normalizeReportType(String rawType) {
        String type = rawType == null ? "" : rawType.trim().toUpperCase(Locale.ROOT);
        if (!REPORT_TYPES.contains(type)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR,
                    "纠错类型只能为 CONTENT/ANSWER/ANALYSIS/KNOWLEDGE_POINT/OTHER");
        }
        return type;
    }

    private String normalizeStatus(String rawStatus) {
        String status = rawStatus == null ? "" : rawStatus.trim().toUpperCase(Locale.ROOT);
        if (!PROCESS_STATUSES.contains(status)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "处理状态只能为 OPEN/RESOLVED/REJECTED");
        }
        return status;
    }

    private void fillQuestionContent(QuestionCorrectionReportVO vo) {
        if (vo.getQuestionId() == null) {
            return;
        }
        Question question = questionMapper.selectById(vo.getQuestionId());
        if (question != null) {
            vo.setQuestionContent(question.getContent());
        }
    }

    private void fillUserNames(QuestionCorrectionReportVO vo) {
        if (vo.getReporterId() != null) {
            User reporter = userMapper.selectById(vo.getReporterId());
            if (reporter != null) {
                vo.setReporterName(displayName(reporter));
            }
        }
        if (vo.getHandlerId() != null) {
            User handler = userMapper.selectById(vo.getHandlerId());
            if (handler != null) {
                vo.setHandlerName(displayName(handler));
            }
        }
    }

    private String displayName(User user) {
        return user.getNickname() != null && !user.getNickname().isBlank()
                ? user.getNickname()
                : user.getUsername();
    }
}
