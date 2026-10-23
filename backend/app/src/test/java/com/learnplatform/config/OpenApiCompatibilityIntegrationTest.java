package com.learnplatform.config;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.result.ResultCode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class OpenApiCompatibilityIntegrationTest extends IntegrationTestBase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void generatesRootAndGroupedOpenApiWithControllerAdvice() throws Exception {
        for (String path : new String[]{"/v3/api-docs", "/v3/api-docs/default"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.openapi").isNotEmpty())
                    .andExpect(jsonPath("$.paths['/api/auth/login']").exists());
        }
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configUrl").value("/v3/api-docs/swagger-config"));
    }

    @Test void keepsDocumentEntryAndLoadsSwaggerUiWithoutAuthentication() throws Exception {
        mvc.perform(get("/doc.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-initializer.js")).andExpect(status().isOk());
    }

    @Test void preservesDateNullAndJsonTreeSerialization() throws Exception {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("timestamp", Date.from(java.time.Instant.parse("2026-10-23T12:34:56Z")));
        payload.put("localTime", LocalDateTime.of(2026, 10, 23, 8, 34, 56));
        payload.put("missing", null);
        payload.put("tree", json.readTree("{\"count\":3,\"enabled\":false}"));
        payload.put("amount", new BigDecimal("1.25"));
        var actual = json.readTree(json.writeValueAsString(payload));
        org.junit.jupiter.api.Assertions.assertEquals("2026-10-23 08:34:56", actual.path("timestamp").asText());
        org.junit.jupiter.api.Assertions.assertEquals("2026-10-23T08:34:56", actual.path("localTime").asText());
        org.junit.jupiter.api.Assertions.assertFalse(actual.has("missing"));
        org.junit.jupiter.api.Assertions.assertEquals(3, actual.path("tree").path("count").intValue());
        org.junit.jupiter.api.Assertions.assertFalse(actual.path("tree").path("enabled").booleanValue());
        org.junit.jupiter.api.Assertions.assertEquals(1.25, actual.path("amount").doubleValue());
    }

    @Test void unauthenticatedApiStillReturns401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));
    }
}
