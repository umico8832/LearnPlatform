package com.learnplatform.controller;

import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.UserCourseVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.CourseLibraryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CourseLibraryControllerTest {

    private MockMvc mockMvc;

    @Mock private CourseLibraryService courseLibraryService;

    @BeforeEach
    void setUp() {
        CourseLibraryController controller = new CourseLibraryController(courseLibraryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CustomUserDetailsArgumentResolver())
                .build();
    }

    @Test
    void addCourseUsesAuthenticatedUser() throws Exception {
        UserCourseVO vo = new UserCourseVO();
        vo.setCourseId(10L);
        vo.setCourseName("408 数据结构");
        when(courseLibraryService.addCourse(7L, 10L)).thenReturn(vo);

        mockMvc.perform(post("/api/my-courses/10").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.courseId").value(10))
                .andExpect(jsonPath("$.data.courseName").value("408 数据结构"));

        verify(courseLibraryService).addCourse(7L, 10L);
    }

    @Test
    void getMyCoursesUsesAuthenticatedUser() throws Exception {
        when(courseLibraryService.getMyCourses(7L)).thenReturn(List.of());

        mockMvc.perform(get("/api/my-courses").with(mockUser(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(courseLibraryService).getMyCourses(7L);
    }

    private RequestPostProcessor mockUser(Long userId) {
        return request -> {
            CustomUserDetails details = new CustomUserDetails(userId, "testuser", "USER");
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    details, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            return request;
        };
    }
}
