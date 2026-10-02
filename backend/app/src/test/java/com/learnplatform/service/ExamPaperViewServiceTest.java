package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamPaperViewServiceTest {
    @Mock private ExamPaperMapper paperMapper;
    @Mock private ExamQuestionMapper examQuestionMapper;
    @Mock private QuestionMapper questionMapper;
    @Mock private QuestionOptionMapper optionMapper;
    @Mock private CourseMapper courseMapper;

    @BeforeEach
    void initializeTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), ExamPaper.class);
    }

    @Test
    void filtersAccessiblePublishedPapersBeforeApplyingTypeAndTitleSearch() {
        Page<ExamPaper> result = new Page<>(1, 10, 0);
        when(paperMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(result);
        ExamPaperViewService service = new ExamPaperViewService(
                paperMapper, examQuestionMapper, questionMapper, optionMapper, courseMapper);

        service.getAccessiblePublishedPage(7L, 1, 10, 3L, "OFFICIAL_EXAM", "2026 真题");

        ArgumentCaptor<LambdaQueryWrapper<ExamPaper>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(paperMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        Collection<Object> values = captor.getValue().getParamNameValuePairs().values();
        assertTrue(sql.contains("status"));
        assertTrue(sql.contains("visibility"));
        assertTrue(sql.contains("courseId"));
        assertTrue(sql.contains("paperType"));
        assertTrue(sql.contains("title"));
        assertTrue(values.contains(1));
        assertTrue(values.contains("PUBLIC"));
        assertTrue(values.contains(7L));
        assertTrue(values.contains(3L));
        assertTrue(values.contains("OFFICIAL_EXAM"));
        assertTrue(values.contains("%2026 真题%"));
    }
}
