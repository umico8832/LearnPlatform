package com.learnplatform.config;

import com.learnplatform.security.GoogleOAuthFailureHandler;
import com.learnplatform.security.GoogleOAuthSuccessHandler;
import com.learnplatform.security.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SecurityConfigTest.TestConfiguration.class)
class SecurityConfigTest {

    @Autowired
    private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCanReadButCannotDeleteSharedAiAssets() throws Exception {
        mockMvc.perform(get("/api/ai/assets/{questionId}", 7L)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/ai/assets/{questionId}", 7L)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCanDeleteSharedAiAssets() throws Exception {
        mockMvc.perform(delete("/api/ai/assets/{questionId}", 7L)).andExpect(status().isOk());
    }

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestConfiguration {

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new NoOpJwtAuthenticationFilter();
        }

        @Bean
        GoogleOAuthSuccessHandler googleOAuthSuccessHandler() {
            return mock(GoogleOAuthSuccessHandler.class);
        }

        @Bean
        GoogleOAuthFailureHandler googleOAuthFailureHandler() {
            return mock(GoogleOAuthFailureHandler.class);
        }

        @Bean
        AssetEndpoint assetEndpoint() {
            return new AssetEndpoint();
        }
    }

    static class NoOpJwtAuthenticationFilter extends JwtAuthenticationFilter {

        NoOpJwtAuthenticationFilter() {
            super(null, null);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            filterChain.doFilter(request, response);
        }
    }

    @RestController
    @RequestMapping("/api/ai/assets")
    static class AssetEndpoint {

        @GetMapping("/{questionId}")
        void getAsset(@PathVariable Long questionId) {
        }

        @DeleteMapping("/{questionId}")
        void deleteAsset(@PathVariable Long questionId) {
        }
    }
}
