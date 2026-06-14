package com.learnplatform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.CourseVO;
import com.learnplatform.service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CourseController MockMvc 集成测试（standalone 模式）
 */
@ExtendWith(MockitoExtension.class)
class CourseControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CourseService courseService;

    @InjectMocks
    private CourseController courseController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(courseController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ======================== 课程列表（分页） ========================

    @Test
    void getCoursePage_defaultParams() throws Exception {
        Page<CourseVO> page = new Page<>(1, 10);
        CourseVO course = new CourseVO();
        course.setId(1L);
        course.setName("Java 基础");
        course.setDescription("Java 编程基础课程");
        page.setRecords(List.of(course));
        page.setTotal(1);

        when(courseService.getCoursePage(eq(1), eq(10), isNull())).thenReturn(page);

        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].name").value("Java 基础"));
    }

    @Test
    void getCoursePage_withKeyword() throws Exception {
        Page<CourseVO> page = new Page<>(1, 5);
        page.setRecords(List.of());
        page.setTotal(0);

        when(courseService.getCoursePage(eq(1), eq(5), eq("Spring"))).thenReturn(page);

        mockMvc.perform(get("/api/courses")
                        .param("pageNum", "1")
                        .param("pageSize", "5")
                        .param("keyword", "Spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isEmpty());
    }

    // ======================== 获取所有课程 ========================

    @Test
    void getAllCourses_returnsList() throws Exception {
        CourseVO course1 = new CourseVO();
        course1.setId(1L);
        course1.setName("Java");
        CourseVO course2 = new CourseVO();
        course2.setId(2L);
        course2.setName("Python");

        when(courseService.getAllEnabledCourses()).thenReturn(List.of(course1, course2));

        mockMvc.perform(get("/api/courses/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Java"))
                .andExpect(jsonPath("$.data[1].name").value("Python"));
    }

    // ======================== 课程详情 ========================

    @Test
    void getCourseById_found() throws Exception {
        CourseVO course = new CourseVO();
        course.setId(1L);
        course.setName("Java 基础");

        when(courseService.getCourseById(1L)).thenReturn(course);

        mockMvc.perform(get("/api/courses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("Java 基础"));
    }

    @Test
    void getCourseById_notFound_returnsBusinessError() throws Exception {
        when(courseService.getCourseById(999L))
                .thenThrow(new BusinessException(ResultCode.NOT_FOUND, "课程不存在"));

        mockMvc.perform(get("/api/courses/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.NOT_FOUND.getCode()));
    }
}