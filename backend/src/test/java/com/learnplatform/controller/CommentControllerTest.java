package com.learnplatform.controller;

import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.dto.CommentRequest;
import com.learnplatform.dto.CommentVO;
import com.learnplatform.security.CustomUserDetails;
import com.learnplatform.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CommentController MockMvc 集成测试
 */
@ExtendWith(MockitoExtension.class)
class CommentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CommentService commentService;

    @InjectMocks
    private CommentController commentController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(commentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new LongUserIdArgumentResolver())
                .build();
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

    private CommentVO buildCommentVO(Long id, Long questionId, Long userId, String content) {
        CommentVO vo = new CommentVO();
        vo.setId(id);
        vo.setQuestionId(questionId);
        vo.setUserId(userId);
        vo.setNickname("testuser");
        vo.setContent(content);
        vo.setParentId(0L);
        vo.setLikeCount(0);
        vo.setLikedByMe(false);
        vo.setCreateTime(LocalDateTime.now());
        return vo;
    }

    @Test
    void getComments_success() throws Exception {
        Long userId = 1L;
        Long questionId = 10L;
        CommentVO comment = buildCommentVO(1L, questionId, userId, "测试评论");
        when(commentService.getComments(eq(questionId), eq(userId))).thenReturn(List.of(comment));

        mockMvc.perform(get("/api/comments/question/{questionId}", questionId)
                        .with(mockUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].content").value("测试评论"));

        verify(commentService).getComments(eq(questionId), eq(userId));
    }

    @Test
    void addComment_success() throws Exception {
        Long userId = 1L;
        Long questionId = 10L;
        CommentVO comment = buildCommentVO(1L, questionId, userId, "新评论");
        when(commentService.addComment(any(CommentRequest.class), eq(userId))).thenReturn(comment);

        mockMvc.perform(post("/api/comments")
                        .with(mockUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":10,\"content\":\"新评论\",\"parentId\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").value("新评论"));
    }

    @Test
    void addComment_blankContent_returns400() throws Exception {
        Long userId = 1L;

        mockMvc.perform(post("/api/comments")
                        .with(mockUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":10,\"content\":\"\",\"parentId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void addComment_nullQuestionId_returns400() throws Exception {
        Long userId = 1L;

        mockMvc.perform(post("/api/comments")
                        .with(mockUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"评论\",\"parentId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void deleteComment_success() throws Exception {
        Long userId = 1L;
        Long commentId = 1L;
        doNothing().when(commentService).deleteComment(eq(commentId), eq(userId));

        mockMvc.perform(delete("/api/comments/{commentId}", commentId)
                        .with(mockUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(commentService).deleteComment(eq(commentId), eq(userId));
    }

    @Test
    void toggleLike_success() throws Exception {
        Long userId = 1L;
        Long commentId = 1L;
        when(commentService.toggleLike(eq(commentId), eq(userId))).thenReturn(true);

        mockMvc.perform(post("/api/comments/{commentId}/like", commentId)
                        .with(mockUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        verify(commentService).toggleLike(eq(commentId), eq(userId));
    }

    @Test
    void toggleLike_unlike_returnsFalse() throws Exception {
        Long userId = 1L;
        Long commentId = 1L;
        when(commentService.toggleLike(eq(commentId), eq(userId))).thenReturn(false);

        mockMvc.perform(post("/api/comments/{commentId}/like", commentId)
                        .with(mockUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void getCommentCount_success() throws Exception {
        Long questionId = 10L;
        when(commentService.getCommentCount(eq(questionId))).thenReturn(5);

        mockMvc.perform(get("/api/comments/count/{questionId}", questionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(5));

        verify(commentService).getCommentCount(eq(questionId));
    }
}