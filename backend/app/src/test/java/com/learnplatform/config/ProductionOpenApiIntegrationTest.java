package com.learnplatform.config;

import com.learnplatform.IntegrationTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"integration", "prod"})
class ProductionOpenApiIntegrationTest extends IntegrationTestBase {
    @Autowired MockMvc mvc;

    @Test void productionDoesNotExposeDocumentEndpointsOrUi() throws Exception {
        for (String path : new String[]{"/doc.html", "/v3/api-docs", "/v3/api-docs/default",
                "/v3/api-docs/swagger-config", "/swagger-ui/index.html", "/swagger-ui/swagger-initializer.js"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
