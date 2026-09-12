package com.learnplatform.service;

import com.learnplatform.mapper.ExamAnswerMapper;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.ExamRecordMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ExamServiceWiringTest {

    @Test
    void createsExamServicesWithProductionConstructors() {
        new ApplicationContextRunner()
                .withBean(ExamRecordMapper.class, () -> mock(ExamRecordMapper.class))
                .withBean(ExamAnswerMapper.class, () -> mock(ExamAnswerMapper.class))
                .withBean(ExamPaperMapper.class, () -> mock(ExamPaperMapper.class))
                .withBean(ExamQuestionMapper.class, () -> mock(ExamQuestionMapper.class))
                .withBean(QuestionMapper.class, () -> mock(QuestionMapper.class))
                .withBean(QuestionOptionMapper.class, () -> mock(QuestionOptionMapper.class))
                .withBean(ExamAnswerSubmissionService.class, () -> mock(ExamAnswerSubmissionService.class))
                .withBean(CacheEvictService.class, () -> mock(CacheEvictService.class))
                .withBean(AnswerEvaluator.class)
                .withUserConfiguration(ExamSessionService.class, ExamSubmissionService.class,
                        ExamRecordViewService.class, ExamService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ExamSessionService.class);
                    assertThat(context).hasSingleBean(ExamSubmissionService.class);
                    assertThat(context).hasSingleBean(ExamRecordViewService.class);
                    assertThat(context).hasSingleBean(ExamService.class);
                });
    }
}
