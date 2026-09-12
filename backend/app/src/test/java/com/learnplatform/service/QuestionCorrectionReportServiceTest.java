package com.learnplatform.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.QuestionCorrectionProcessRequest;
import com.learnplatform.dto.QuestionCorrectionReportRequest;
import com.learnplatform.dto.QuestionCorrectionReportVO;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionCorrectionReport;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.QuestionCorrectionReportMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionCorrectionReportServiceTest {

    @Mock private QuestionCorrectionReportMapper correctionReportMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private UserMapper userMapper;

    private QuestionCorrectionReportService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), QuestionCorrectionReport.class);
        service = new QuestionCorrectionReportService(correctionReportMapper, questionMapper, userMapper);
    }

    @Test
    void submitReport_validRequest_insertsOpenReport() {
        Question question = new Question();
        question.setId(1L);
        question.setContent("题干");
        question.setStatus(1);
        when(questionMapper.selectById(1L)).thenReturn(question);

        User reporter = new User();
        reporter.setId(2L);
        reporter.setUsername("alice");
        reporter.setNickname("Alice");
        when(userMapper.selectById(2L)).thenReturn(reporter);

        QuestionCorrectionReportRequest request = new QuestionCorrectionReportRequest();
        request.setReportType("answer");
        request.setDescription("答案应为 B");

        QuestionCorrectionReportVO vo = service.submitReport(1L, request, 2L);

        ArgumentCaptor<QuestionCorrectionReport> captor = ArgumentCaptor.forClass(QuestionCorrectionReport.class);
        verify(correctionReportMapper).insert(captor.capture());
        assertEquals("ANSWER", captor.getValue().getReportType());
        assertEquals("OPEN", captor.getValue().getStatus());
        assertEquals("答案应为 B", captor.getValue().getDescription());
        assertEquals("题干", vo.getQuestionContent());
        assertEquals("Alice", vo.getReporterName());
    }

    @Test
    void submitReport_invalidType_throwsValidationError() {
        Question question = new Question();
        question.setId(1L);
        question.setStatus(1);
        when(questionMapper.selectById(1L)).thenReturn(question);

        QuestionCorrectionReportRequest request = new QuestionCorrectionReportRequest();
        request.setReportType("BAD");
        request.setDescription("问题描述");

        assertThrows(BusinessException.class, () -> service.submitReport(1L, request, 2L));
        verify(correctionReportMapper, never()).insert(any());
    }

    @Test
    void submitReport_disabledQuestion_throwsNotFound() {
        Question question = new Question();
        question.setId(1L);
        question.setStatus(0);
        when(questionMapper.selectById(1L)).thenReturn(question);

        QuestionCorrectionReportRequest request = new QuestionCorrectionReportRequest();
        request.setReportType("CONTENT");
        request.setDescription("题干错字");

        assertThrows(BusinessException.class, () -> service.submitReport(1L, request, 2L));
    }

    @Test
    void processReport_resolved_setsHandlerAndHandledTime() {
        QuestionCorrectionReport report = new QuestionCorrectionReport();
        report.setId(10L);
        report.setQuestionId(1L);
        report.setReporterId(2L);
        report.setReportType("ANALYSIS");
        report.setDescription("解析不清楚");
        report.setStatus("OPEN");
        when(correctionReportMapper.selectById(10L)).thenReturn(report);

        QuestionCorrectionProcessRequest request = new QuestionCorrectionProcessRequest();
        request.setStatus("resolved");
        request.setHandlerComment("已补充解析");

        QuestionCorrectionReportVO vo = service.processReport(10L, request, 3L);

        ArgumentCaptor<QuestionCorrectionReport> captor = ArgumentCaptor.forClass(QuestionCorrectionReport.class);
        verify(correctionReportMapper).updateById(captor.capture());
        assertEquals("RESOLVED", captor.getValue().getStatus());
        assertEquals(3L, captor.getValue().getHandlerId());
        assertNotNull(captor.getValue().getHandledTime());
        assertEquals("已补充解析", vo.getHandlerComment());
    }

    @Test
    void processReport_invalidStatus_throwsValidationError() {
        QuestionCorrectionReport report = new QuestionCorrectionReport();
        report.setId(10L);
        when(correctionReportMapper.selectById(10L)).thenReturn(report);

        QuestionCorrectionProcessRequest request = new QuestionCorrectionProcessRequest();
        request.setStatus("DONE");
        request.setHandlerComment("处理说明");

        assertThrows(BusinessException.class, () -> service.processReport(10L, request, 3L));
    }

    @Test
    void getMyReports_mapsRecordsToVo() {
        QuestionCorrectionReport report = new QuestionCorrectionReport();
        report.setId(10L);
        report.setQuestionId(1L);
        report.setReporterId(2L);
        report.setReportType("CONTENT");
        report.setDescription("题干错字");
        report.setStatus("OPEN");
        Page<QuestionCorrectionReport> page = new Page<>(1, 10);
        page.setRecords(List.of(report));
        page.setTotal(1);
        when(correctionReportMapper.selectPage(any(), any())).thenReturn(page);

        Page<QuestionCorrectionReportVO> result = service.getMyReports(2L, 1, 10, "OPEN");

        assertEquals(1, result.getTotal());
        assertEquals("CONTENT", result.getRecords().get(0).getReportType());
    }
}
