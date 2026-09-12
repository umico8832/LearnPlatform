package com.learnplatform.controller;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.CourseVO;
import com.learnplatform.service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminCourseController MockMvc 集成测试
 */
@ExtendWith(MockitoExtension.class)
class AdminCourseControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CourseService courseService;

    @InjectMocks
    private AdminCourseController adminCourseController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminCourseController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private CourseVO buildCourseVO(Long id, String name) {
        CourseVO vo = new CourseVO();
        vo.setId(id);
        vo.setName(name);
        vo.setDescription(name + "描述");
        vo.setSortOrder(0);
        vo.setStatus(1);
        return vo;
    }

    @Test
    void createCourse_success() throws Exception {
        CourseVO vo = buildCourseVO(1L, "Java 基础");
        when(courseService.createCourse(eq("Java 基础"), eq("Java 基础描述"), eq(0))).thenReturn(vo);

        mockMvc.perform(post("/api/admin/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Java 基础\",\"description\":\"Java 基础描述\",\"sortOrder\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("Java 基础"))
                .andExpect(jsonPath("$.data.id").value(1));

        verify(courseService).createCourse(eq("Java 基础"), eq("Java 基础描述"), eq(0));
    }

    @Test
    void createCourse_serviceError() throws Exception {
        when(courseService.createCourse(any(), any(), any()))
                .thenThrow(new BusinessException("课程名已存在"));

        mockMvc.perform(post("/api/admin/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"重复课程\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("课程名已存在"));
    }

    @Test
    void updateCourse_success() throws Exception {
        CourseVO vo = buildCourseVO(1L, "更新后的课程");
        when(courseService.updateCourse(eq(1L), eq("更新后的课程"), any(), any())).thenReturn(vo);

        mockMvc.perform(put("/api/admin/courses/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"更新后的课程\",\"description\":\"新描述\",\"sortOrder\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("更新后的课程"));

        verify(courseService).updateCourse(eq(1L), eq("更新后的课程"), eq("新描述"), eq(1));
    }

    @Test
    void updateCourse_notFound() throws Exception {
        when(courseService.updateCourse(eq(999L), any(), any(), any()))
                .thenThrow(new BusinessException("课程不存在"));

        mockMvc.perform(put("/api/admin/courses/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"不存在\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("课程不存在"));
    }

    @Test
    void deleteCourse_success() throws Exception {
        doNothing().when(courseService).deleteCourse(eq(1L));

        mockMvc.perform(delete("/api/admin/courses/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(courseService).deleteCourse(eq(1L));
    }

    @Test
    void deleteCourse_notFound() throws Exception {
        doThrow(new BusinessException("课程不存在")).when(courseService).deleteCourse(eq(999L));

        mockMvc.perform(delete("/api/admin/courses/{id}", 999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("课程不存在"));
    }
}