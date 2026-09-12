package com.learnplatform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI 接口文档配置
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI 题库与错题复习系统 - API 接口文档")
                        .version("1.0.0")
                        .description("AI 题库与错题复习系统后端接口文档")
                        .contact(new Contact()
                                .name("LearnPlatform")
                                .url("https://github.com/umico8832/LearnPlatform")));
    }
}