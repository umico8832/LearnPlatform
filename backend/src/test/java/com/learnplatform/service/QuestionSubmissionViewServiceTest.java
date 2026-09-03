package com.learnplatform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.dto.QuestionSubmissionVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.QuestionSubmission;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionSubmissionViewServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private CourseMapper courseMapper;

    @InjectMocks
    private QuestionSubmissionViewService viewService;

    @Test
    void enrichesSubmitterReviewerAndCourseNames() {
        QuestionSubmission submission = new QuestionSubmission();
        submission.setId(10L);
        submission.setUserId(7L);
        submission.setReviewedBy(1L);
        submission.setCourseId(20L);
        User submitter = new User();
        submitter.setUsername("learner");
        submitter.setNickname("学习者");
        User reviewer = new User();
        reviewer.setUsername("admin");
        Course course = new Course();
        course.setName("数据结构");
        when(userMapper.selectById(7L)).thenReturn(submitter);
        when(userMapper.selectById(1L)).thenReturn(reviewer);
        when(courseMapper.selectById(20L)).thenReturn(course);

        QuestionSubmissionVO result = viewService.toView(submission);

        assertEquals("learner", result.getUsername());
        assertEquals("学习者", result.getNickname());
        assertEquals("admin", result.getReviewedByName());
        assertEquals("数据结构", result.getCourseName());
    }

    @Test
    void preservesMutablePageRecordCollection() {
        Page<QuestionSubmission> source = new Page<>(1, 10, 1);
        source.setRecords(List.of(new QuestionSubmission()));

        Page<QuestionSubmissionVO> result = viewService.toPage(source);
        result.getRecords().add(new QuestionSubmissionVO());

        assertEquals(2, result.getRecords().size());
    }
}
